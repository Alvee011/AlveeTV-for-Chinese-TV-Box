package tv.alveetv.home;

import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.text.format.Formatter;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * Lightweight local HTTP remote control server for AlveeTV.
 * Runs on port 8080 and handles key events, trackpad gestures, and app launches.
 */
final class RemoteServer {
    private static final String TAG = "AlveeTV";
    static final int PORT = 8080;

    private static volatile RemoteServer instance;
    private ServerSocket serverSocket;
    private Thread serverThread;
    private volatile boolean running = false;
    private final Context appContext;

    private Process shellProcess;
    private OutputStream shellOutput;

    private RemoteServer(Context context) {
        this.appContext = context.getApplicationContext();
        initShell();
    }

    private synchronized void initShell() {
        try {
            shellProcess = Runtime.getRuntime().exec("su");
            shellOutput = shellProcess.getOutputStream();
            // Ensure input permissions
            writeShell("chmod 666 /dev/input/event0 /dev/input/event3 2>/dev/null || true\n");
        } catch (Throwable t) {
            try {
                shellProcess = Runtime.getRuntime().exec("sh");
                shellOutput = shellProcess.getOutputStream();
            } catch (Throwable ignored) {}
        }
    }

    private synchronized void writeShell(String cmd) {
        try {
            if (shellOutput == null) {
                initShell();
            }
            if (shellOutput != null) {
                shellOutput.write(cmd.getBytes());
                shellOutput.flush();
            }
        } catch (Throwable t) {
            initShell();
            try {
                if (shellOutput != null) {
                    shellOutput.write(cmd.getBytes());
                    shellOutput.flush();
                }
            } catch (Throwable ignored) {}
        }
    }

    static synchronized void start(Context context) {
        if (instance != null && instance.running) return;
        if (instance == null) {
            instance = new RemoteServer(context);
        }
        instance.startServer();
    }

    static synchronized void stop() {
        if (instance != null) {
            instance.stopServer();
            instance = null;
        }
    }

    static boolean isRunning() {
        return instance != null && instance.running;
    }

    private synchronized void startServer() {
        if (running && serverSocket != null && !serverSocket.isClosed()) {
            return;
        }
        running = true;
        serverThread = new Thread(() -> {
            while (running) {
                try {
                    if (serverSocket == null || serverSocket.isClosed()) {
                        serverSocket = new ServerSocket();
                        serverSocket.setReuseAddress(true);
                        serverSocket.bind(new java.net.InetSocketAddress(PORT));
                        Log.i(TAG, "RemoteServer listening on port " + PORT);
                    }
                    Socket client = serverSocket.accept();
                    new Thread(() -> handleClient(client), "AlveeTVRemoteClient").start();
                } catch (Throwable t) {
                    if (!running) break;
                    Log.w(TAG, "RemoteServer accept error: " + t);
                    try { Thread.sleep(500); } catch (InterruptedException ignored) {}
                }
            }
            Log.i(TAG, "RemoteServer loop terminated");
        }, "AlveeTVRemoteServer");
        serverThread.start();
    }

    private synchronized void stopServer() {
        running = false;
        try {
            if (serverSocket != null) {
                serverSocket.close();
                serverSocket = null;
            }
        } catch (Throwable ignored) {}
        if (serverThread != null) {
            serverThread.interrupt();
            serverThread = null;
        }
        try {
            if (shellOutput != null) shellOutput.close();
            if (shellProcess != null) shellProcess.destroy();
        } catch (Throwable ignored) {}
    }

    private void handleClient(Socket socket) {
        try {
            socket.setSoTimeout(5000);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            OutputStream out = socket.getOutputStream();

            String requestLine = in.readLine();
            if (requestLine == null) {
                socket.close();
                return;
            }

            String[] parts = requestLine.split(" ");
            String method = parts.length > 0 ? parts[0] : "GET";
            String fullPath = parts.length > 1 ? parts[1] : "/";

            // Read remaining headers
            Map<String, String> headers = new HashMap<>();
            String headerLine;
            int contentLength = 0;
            while ((headerLine = in.readLine()) != null && !headerLine.isEmpty()) {
                int colon = headerLine.indexOf(':');
                if (colon > 0) {
                    String k = headerLine.substring(0, colon).trim().toLowerCase();
                    String v = headerLine.substring(colon + 1).trim();
                    headers.put(k, v);
                    if ("content-length".equals(k)) {
                        try { contentLength = Integer.parseInt(v); } catch (Exception ignored) {}
                    }
                }
            }

            // Read body if POST
            StringBuilder body = new StringBuilder();
            if ("POST".equalsIgnoreCase(method) && contentLength > 0) {
                char[] buf = new char[contentLength];
                int read = 0;
                while (read < contentLength) {
                    int r = in.read(buf, read, contentLength - read);
                    if (r < 0) break;
                    read += r;
                }
                body.append(buf, 0, read);
            }

            // Handle CORS OPTIONS preflight
            if ("OPTIONS".equalsIgnoreCase(method)) {
                sendCorsOk(out);
                socket.close();
                return;
            }

            // Parse path and query parameters
            String path = fullPath;
            Map<String, String> params = new HashMap<>();
            int qIdx = fullPath.indexOf('?');
            if (qIdx >= 0) {
                path = fullPath.substring(0, qIdx);
                parseQuery(fullPath.substring(qIdx + 1), params);
            }
            if (body.length() > 0) {
                parseQuery(body.toString(), params);
            }

            // Route handling
            if ("/ping".equals(path) || "/status".equals(path)) {
                String ip = getLocalIpAddress(appContext);
                String json = "{\"status\":\"ok\",\"app\":\"AlveeTV\",\"version\":\"2.4\",\"ip\":\"" + ip + "\",\"port\":" + PORT + "}";
                sendJsonResponse(out, 200, json);
            } else if ("/stream.h264".equals(path) || "/screen.h264".equals(path) || "/stream".equals(path)) {
                handleStreamH264(out, socket, params);
                return;
            } else if ("/tap".equals(path)) {
                String xStr = params.get("x");
                String yStr = params.get("y");
                if (xStr != null && yStr != null) {
                    writeShell("input tap " + xStr + " " + yStr + "\n");
                    sendJsonResponse(out, 200, "{\"status\":\"ok\",\"action\":\"tap\",\"x\":" + xStr + ",\"y\":" + yStr + "}");
                } else {
                    sendJsonResponse(out, 400, "{\"status\":\"error\",\"message\":\"Missing x or y\"}");
                }
            } else if ("/key".equals(path)) {
                String codeStr = params.get("code");
                if (codeStr != null) {
                    int keycode = parseKeycode(codeStr);
                    if (keycode > 0) {
                        injectKey(keycode);
                        sendJsonResponse(out, 200, "{\"status\":\"ok\",\"action\":\"key\",\"code\":" + keycode + "}");
                    } else {
                        sendJsonResponse(out, 400, "{\"status\":\"error\",\"message\":\"Unknown keycode: " + codeStr + "\"}");
                    }
                } else {
                    sendJsonResponse(out, 400, "{\"status\":\"error\",\"message\":\"Missing 'code' parameter\"}");
                }
            } else if ("/mouse".equals(path)) {
                handleMouse(params);
                sendJsonResponse(out, 200, "{\"status\":\"ok\",\"action\":\"mouse\"}");
            } else if ("/text".equals(path)) {
                String text = params.get("text");
                if (text != null) {
                    injectText(text);
                    sendJsonResponse(out, 200, "{\"status\":\"ok\",\"action\":\"text\"}");
                } else {
                    sendJsonResponse(out, 400, "{\"status\":\"error\",\"message\":\"Missing 'text' parameter\"}");
                }
            } else if ("/launch".equals(path)) {
                String pkg = params.get("pkg");
                if (pkg != null) {
                    if ("com.android.settings".equals(pkg) || "settings".equalsIgnoreCase(pkg)) {
                        try {
                            Intent settingsIntent = new Intent(android.provider.Settings.ACTION_SETTINGS);
                            settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            appContext.startActivity(settingsIntent);
                            sendJsonResponse(out, 200, "{\"status\":\"ok\",\"launched\":\"settings\"}");
                        } catch (Throwable t) {
                            writeShell("am start -a android.settings.SETTINGS\n");
                            sendJsonResponse(out, 200, "{\"status\":\"ok\",\"launched\":\"settings\"}");
                        }
                    } else {
                        Intent launch = appContext.getPackageManager().getLaunchIntentForPackage(pkg);
                        if (launch != null) {
                            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            appContext.startActivity(launch);
                            sendJsonResponse(out, 200, "{\"status\":\"ok\",\"launched\":\"" + pkg + "\"}");
                        } else {
                            writeShell("monkey -p " + pkg + " -c android.intent.category.LAUNCHER 1 2>/dev/null || am start -p " + pkg + " 2>/dev/null\n");
                            sendJsonResponse(out, 200, "{\"status\":\"ok\",\"launched\":\"" + pkg + "\"}");
                        }
                    }
                } else {
                    sendJsonResponse(out, 400, "{\"status\":\"error\",\"message\":\"Missing 'pkg' parameter\"}");
                }
            } else {
                if (!tryServeStaticFile(path, out)) {
                    sendJsonResponse(out, 404, "{\"status\":\"error\",\"message\":\"Not found\"}");
                }
            }

            socket.close();
        } catch (Throwable ignored) {}
    }

    private void parseQuery(String query, Map<String, String> out) {
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                try {
                    String k = URLDecoder.decode(pair.substring(0, eq), "UTF-8");
                    String v = URLDecoder.decode(pair.substring(eq + 1), "UTF-8");
                    out.put(k, v);
                } catch (Exception ignored) {}
            }
        }
    }

    private int parseKeycode(String code) {
        String c = code.trim().toUpperCase();
        switch (c) {
            case "POWER": return 26;          // KEYCODE_POWER
            case "MENU": return 82;           // KEYCODE_MENU
            case "HOME": return 3;            // KEYCODE_HOME
            case "UP": return 19;             // KEYCODE_DPAD_UP
            case "DOWN": return 20;           // KEYCODE_DPAD_DOWN
            case "LEFT": return 21;           // KEYCODE_DPAD_LEFT
            case "RIGHT": return 22;          // KEYCODE_DPAD_RIGHT
            case "OK":
            case "ENTER":
            case "CENTER": return 23;         // KEYCODE_DPAD_CENTER
            case "BACK": return 4;            // KEYCODE_BACK
            case "VOL_UP":
            case "VOLUP":
            case "VOLUME_UP": return 24;      // KEYCODE_VOLUME_UP
            case "VOL_DOWN":
            case "VOLDOWN":
            case "VOLUME_DOWN": return 25;    // KEYCODE_VOLUME_DOWN
            case "MUTE": return 164;          // KEYCODE_VOLUME_MUTE
            default:
                try {
                    return Integer.parseInt(c);
                } catch (Exception e) {
                    return 0;
                }
        }
    }

    private void injectKey(int keycode) {
        // High-speed key injection via persistent shell
        writeShell("input keyevent " + keycode + "\n");
    }

    private void injectText(String text) {
        String sanitized = text.replace(" ", "%s").replace("\"", "\\\"").replace("'", "\\'");
        writeShell("input text \"" + sanitized + "\"\n");
    }

    private void handleMouse(Map<String, String> params) {
        String click = params.get("click");
        if ("left".equalsIgnoreCase(click)) {
            writeShell("sendevent /dev/input/event3 1 272 1\nsendevent /dev/input/event3 0 0 0\n" +
                       "sendevent /dev/input/event3 1 272 0\nsendevent /dev/input/event3 0 0 0\n");
            return;
        } else if ("right".equalsIgnoreCase(click)) {
            writeShell("sendevent /dev/input/event3 1 273 1\nsendevent /dev/input/event3 0 0 0\n" +
                       "sendevent /dev/input/event3 1 273 0\nsendevent /dev/input/event3 0 0 0\n");
            return;
        }

        String dxStr = params.get("dx");
        String dyStr = params.get("dy");
        if (dxStr != null || dyStr != null) {
            try {
                int dx = dxStr != null ? Integer.parseInt(dxStr) : 0;
                int dy = dyStr != null ? Integer.parseInt(dyStr) : 0;
                writeShell("sendevent /dev/input/event3 2 0 " + dx + "\n" +
                           "sendevent /dev/input/event3 2 1 " + dy + "\n" +
                           "sendevent /dev/input/event3 0 0 0\n");
            } catch (Exception ignored) {}
        }

        String scrollStr = params.get("scroll");
        if (scrollStr != null) {
            try {
                int scroll = Integer.parseInt(scrollStr);
                writeShell("sendevent /dev/input/event3 2 8 " + scroll + "\n" +
                           "sendevent /dev/input/event3 0 0 0\n");
            } catch (Exception ignored) {}
        }
    }

    private void handleStreamH264(OutputStream out, Socket socket, Map<String, String> params) {
        Process p = null;
        try {
            writeShell("pkill -9 screenrecord 2>/dev/null || true\n");
            String headers = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: video/h264\r\n" +
                    "Access-Control-Allow-Origin: *\r\n" +
                    "Access-Control-Allow-Methods: GET, OPTIONS\r\n" +
                    "Access-Control-Allow-Headers: *\r\n" +
                    "Connection: close\r\n" +
                    "Cache-Control: no-cache, no-store, must-revalidate\r\n\r\n";
            out.write(headers.getBytes());
            out.flush();

            String size = (params != null && "720".equals(params.get("res"))) ? "1280x720" : "848x480";
            String bitrate = (params != null && "720".equals(params.get("res"))) ? "2000000" : "1500000";

            byte[] buf = new byte[8192];
            while (running && !socket.isClosed()) {
                p = Runtime.getRuntime().exec(new String[]{"su", "-c", "screenrecord --output-format=h264 --size " + size + " --bit-rate " + bitrate + " --time-limit 180 -"});
                InputStream in = p.getInputStream();
                int r;
                while (running && (r = in.read(buf)) != -1) {
                    out.write(buf, 0, r);
                    out.flush();
                }
                p.destroy();
                p = null;
            }
        } catch (Throwable ignored) {
        } finally {
            if (p != null) {
                try { p.destroy(); } catch (Throwable ignored) {}
            }
            writeShell("pkill -9 screenrecord 2>/dev/null || true\n");
            try { socket.close(); } catch (Throwable ignored) {}
        }
    }

    private boolean tryServeStaticFile(String path, OutputStream out) {
        if ("/".equals(path) || path == null || path.isEmpty()) {
            path = "/index.html";
        }
        File remoteDir = new File(appContext.getFilesDir(), "remote");
        File file = new File(remoteDir, path.startsWith("/") ? path.substring(1) : path);
        if (!file.exists() || file.isDirectory()) {
            File alt = new File("/sdcard/Android/media/tv.alveetv.home/remote", path.startsWith("/") ? path.substring(1) : path);
            if (alt.exists() && !alt.isDirectory()) file = alt;
        }
        if (file.exists() && file.isFile()) {
            try {
                String contentType = "text/plain";
                String pLower = path.toLowerCase();
                if (pLower.endsWith(".html")) contentType = "text/html; charset=utf-8";
                else if (pLower.endsWith(".css")) contentType = "text/css; charset=utf-8";
                else if (pLower.endsWith(".js")) contentType = "application/javascript; charset=utf-8";
                else if (pLower.endsWith(".svg")) contentType = "image/svg+xml";
                else if (pLower.endsWith(".json")) contentType = "application/json";
                else if (pLower.endsWith(".png")) contentType = "image/png";

                byte[] data = new byte[(int) file.length()];
                FileInputStream fis = new FileInputStream(file);
                int offset = 0;
                while (offset < data.length) {
                    int r = fis.read(data, offset, data.length - offset);
                    if (r < 0) break;
                    offset += r;
                }
                fis.close();

                String header = "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: " + contentType + "\r\n" +
                        "Content-Length: " + data.length + "\r\n" +
                        "Access-Control-Allow-Origin: *\r\n" +
                        "Access-Control-Allow-Methods: GET, OPTIONS\r\n" +
                        "Cache-Control: public, max-age=60\r\n" +
                        "Connection: close\r\n\r\n";
                out.write(header.getBytes());
                out.write(data);
                out.flush();
                return true;
            } catch (Throwable ignored) {}
        }
        return false;
    }

    private void sendCorsOk(OutputStream out) throws Exception {
        String resp = "HTTP/1.1 200 OK\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
                "Access-Control-Allow-Headers: *\r\n" +
                "Content-Length: 0\r\n" +
                "Connection: close\r\n\r\n";
        out.write(resp.getBytes());
        out.flush();
    }

    private void sendJsonResponse(OutputStream out, int code, String json) throws Exception {
        byte[] bytes = json.getBytes("UTF-8");
        String resp = "HTTP/1.1 " + code + " " + (code == 200 ? "OK" : "Error") + "\r\n" +
                "Content-Type: application/json; charset=utf-8\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
                "Access-Control-Allow-Headers: *\r\n" +
                "Content-Length: " + bytes.length + "\r\n" +
                "Connection: close\r\n\r\n";
        out.write(resp.getBytes());
        out.write(bytes);
        out.flush();
    }

    static String getLocalIpAddress(Context context) {
        try {
            WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null && wm.getConnectionInfo() != null) {
                int ip = wm.getConnectionInfo().getIpAddress();
                if (ip != 0) {
                    return Formatter.formatIpAddress(ip);
                }
            }
        } catch (Throwable ignored) {}

        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface iface : Collections.list(interfaces)) {
                for (InetAddress addr : Collections.list(iface.getInetAddresses())) {
                    if (!addr.isLoopbackAddress() && !addr.isLinkLocalAddress() && addr.getHostAddress().contains(".")) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Throwable ignored) {}

        return "192.168.0.54";
    }
}
