package tv.alveetv.home;

import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.util.TimeZone;

/**
 * Ensures system time and timezone (Asia/Dhaka GMT+6) are always accurate.
 * Solves the clock reset issue on TV boxes lacking an RTC hardware battery.
 */
final class TimeSync {
    private static final String TAG = "AlveeTV";
    private static final String TIMEZONE = "Asia/Dhaka";
    private static final long MIN_VALID_EPOCH_MS = 1700000000000L; // Nov 2023 minimum
    private static volatile boolean syncing = false;
    private static volatile long lastSyncTime = 0;

    private static final String[] NTP_SERVERS = {
        "pool.ntp.org",
        "bd.pool.ntp.org",
        "asia.pool.ntp.org",
        "time.google.com",
        "time.windows.com",
        "time.cloudflare.com"
    };

    private static final String[] HTTP_FALLBACK_URLS = {
        "http://connectivitycheck.gstatic.com/generate_204",
        "http://clients3.google.com/generate_204",
        "http://www.google.com",
        "http://www.cloudflare.com"
    };

    private TimeSync() {}

    /**
     * Trigger background time synchronization.
     * Guaranteed safe to call from any thread at any time.
     */
    static void sync(Context context) {
        if (context == null) return;
        final Context app = context.getApplicationContext();

        // Enforce timezone Asia/Dhaka immediately
        applyTimezone(app);

        // Debounce: sync at most once every 30 seconds unless clock is obviously broken (< 2024)
        long now = System.currentTimeMillis();
        boolean clockReset = (now < MIN_VALID_EPOCH_MS);
        if (syncing && !clockReset) return;
        if (!clockReset && (now - lastSyncTime < 30000)) return;

        syncing = true;
        new Thread(() -> {
            try {
                // Configure Android system global settings
                setupSystemSettings(app);

                // Fetch accurate network time
                long networkTime = fetchNetworkTime();
                if (networkTime >= MIN_VALID_EPOCH_MS) {
                    setSystemTime(app, networkTime);
                    lastSyncTime = System.currentTimeMillis();
                    Log.i(TAG, "TimeSync: Time successfully synced to " + new java.util.Date(networkTime));
                } else {
                    Log.w(TAG, "TimeSync: Could not retrieve valid network time");
                }
            } catch (Throwable t) {
                Log.w(TAG, "TimeSync error: " + t);
            } finally {
                syncing = false;
            }
        }, "TimeSyncThread").start();
    }

    private static void applyTimezone(Context c) {
        try {
            TimeZone.setDefault(TimeZone.getTimeZone(TIMEZONE));
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            if (am != null) {
                am.setTimeZone(TIMEZONE);
            }
        } catch (Throwable ignored) {}

        try {
            Runtime.getRuntime().exec(new String[]{"su", "-c", "setprop persist.sys.timezone " + TIMEZONE}).waitFor();
        } catch (Throwable ignored) {}
    }

    private static void setupSystemSettings(Context c) {
        try {
            Runtime.getRuntime().exec(new String[]{
                "su", "-c",
                "settings put global ntp_server pool.ntp.org && " +
                "settings put global auto_time 1 && " +
                "settings put global auto_time_zone 0 && " +
                "setprop persist.sys.timezone " + TIMEZONE
            }).waitFor();
        } catch (Throwable ignored) {}
    }

    private static long fetchNetworkTime() {
        // 1. Try NTP servers via UDP port 123
        for (String server : NTP_SERVERS) {
            try (DatagramSocket socket = new DatagramSocket()) {
                socket.setSoTimeout(2500);
                InetAddress address = InetAddress.getByName(server);
                byte[] buf = new byte[48];
                buf[0] = 0x1B; // Leap=0, Version=3, Mode=3 (Client)
                DatagramPacket packet = new DatagramPacket(buf, buf.length, address, 123);
                socket.send(packet);

                packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);

                long secondsSince1900 = 0;
                for (int i = 40; i <= 43; i++) {
                    secondsSince1900 = (secondsSince1900 << 8) | (buf[i] & 0xFF);
                }
                long epochSeconds = secondsSince1900 - 2208988800L;
                long epochMs = epochSeconds * 1000L;
                if (epochMs >= MIN_VALID_EPOCH_MS) {
                    Log.i(TAG, "TimeSync: Acquired time from NTP server " + server);
                    return epochMs;
                }
            } catch (Throwable ignored) {}
        }

        // 2. HTTP Fallback (Reads Date header from HTTP server, works over port 80/443 even if UDP 123 is blocked)
        for (String urlStr : HTTP_FALLBACK_URLS) {
            try {
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);
                conn.setInstanceFollowRedirects(false);
                conn.setRequestMethod("HEAD");
                long date = conn.getDate();
                conn.disconnect();
                if (date >= MIN_VALID_EPOCH_MS) {
                    Log.i(TAG, "TimeSync: Acquired time from HTTP server " + urlStr);
                    return date;
                }
            } catch (Throwable ignored) {}
        }

        return 0;
    }

    private static void setSystemTime(Context c, long epochMs) {
        long current = System.currentTimeMillis();
        // If system time differs by more than 2 seconds, update it
        if (Math.abs(current - epochMs) > 2000) {
            long epochSec = epochMs / 1000L;
            try {
                Process p = Runtime.getRuntime().exec(new String[]{
                    "su", "-c",
                    "date -u @" + epochSec + " && (hwclock -w 2>/dev/null || true)"
                });
                p.waitFor();
            } catch (Throwable ignored) {}

            try {
                SystemClock.setCurrentTimeMillis(epochMs);
            } catch (Throwable ignored) {}

            // Broadcast time change to update all UI clocks
            try {
                c.sendBroadcast(new Intent(Intent.ACTION_TIME_CHANGED));
            } catch (Throwable ignored) {}
        }
    }
}
