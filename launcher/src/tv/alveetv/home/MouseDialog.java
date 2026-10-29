package tv.alveetv.home;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.hardware.input.InputManager;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;


final class MouseDialog extends Dialog {
    private final int accent;
    private final Config cfg;
    private int currentSpeed = 0;
    private TextView speedLabel;
    private TextView testerLabel;

    MouseDialog(Context context, int accentColor) {
        super(context, Ui.DIALOG);
        this.accent = accentColor;
        this.cfg = Config.get(context);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        init();
    }

    private int dp(float v) {
        return Ui.dp(getContext(), v);
    }

    private void init() {
        Context c = getContext();
        cfg.refresh();

        // 1. Load saved pointer speed from Config, fallback to Settings.System
        try {
            String saved = cfg.get("mouse.speed", null);
            if (saved != null) {
                currentSpeed = Integer.parseInt(saved);
            } else {
                currentSpeed = Settings.System.getInt(c.getContentResolver(), Settings.System.POINTER_SPEED, 0);
            }
        } catch (Exception e) {
            currentSpeed = 0;
        }

        // Apply loaded speed
        applySpeed(currentSpeed);

        String mouseName = "USB Mouse";
        InputManager im = (InputManager) c.getSystemService(Context.INPUT_SERVICE);
        if (im != null) {
            for (int id : im.getInputDeviceIds()) {
                InputDevice dev = im.getInputDevice(id);
                if (dev != null && (dev.getSources() & InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE) {
                    mouseName = dev.getName();
                    break;
                }
            }
        }

        ScrollView scroll = new ScrollView(c);
        scroll.setFocusable(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setBackgroundColor(0xFF17181B);

        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(36), dp(28), dp(36), dp(28));
        scroll.addView(root, new ViewGroup.LayoutParams(dp(620), ViewGroup.LayoutParams.WRAP_CONTENT));

        // Title
        TextView title = Ui.text(c, 24, Ui.TEXT, Ui.MEDIUM);
        title.setText("Mouse & Pointer Settings");
        root.addView(title);

        // Subtitle / Device Name
        TextView deviceSub = Ui.text(c, 13, Ui.FAINT, Ui.MEDIUM);
        deviceSub.setText("Connected: " + mouseName + " (7 Buttons with DPI & Scroll Wheel)");
        deviceSub.setPadding(0, dp(4), 0, dp(20));
        root.addView(deviceSub);

        // SECTION 1: POINTER SENSITIVITY
        TextView speedHeading = Ui.text(c, 15, accent, Ui.MEDIUM);
        speedHeading.setText("POINTER SENSITIVITY (TRACKING SPEED)");
        root.addView(speedHeading);

        LinearLayout speedRow = new LinearLayout(c);
        speedRow.setOrientation(LinearLayout.HORIZONTAL);
        speedRow.setGravity(Gravity.CENTER_VERTICAL);
        speedRow.setPadding(0, dp(10), 0, dp(14));

        TextView btnMinus = actionChip("  - Slower  ", v -> adjustSpeed(-1));
        TextView btnPlus = actionChip("  + Faster  ", v -> adjustSpeed(1));

        speedLabel = Ui.text(c, 15, Ui.TEXT, Ui.MEDIUM);
        updateSpeedLabel();
        speedLabel.setPadding(dp(16), 0, dp(16), 0);

        speedRow.addView(btnMinus);
        speedRow.addView(speedLabel);
        speedRow.addView(btnPlus);
        root.addView(speedRow);

        TextView dpiNote = Ui.text(c, 12, Ui.MUTED, Ui.LIGHT);
        dpiNote.setText("• Mouse DPI Button: Cycles optical sensor DPI in hardware (800 / 1200 / 1600 / 2400 / 3200 DPI).\n• Sensitivity Buttons above: Adjust Android OS pointer tracking speed.");
        dpiNote.setPadding(0, 0, 0, dp(22));
        root.addView(dpiNote);

        // SECTION 2: 7-BUTTON DROPDOWN CONFIGURATION
        TextView buttonsHeading = Ui.text(c, 15, accent, Ui.MEDIUM);
        buttonsHeading.setText("BUTTON CONFIGURATION (CLICK TO CHANGE)");
        root.addView(buttonsHeading);

        // Button 1: Left Click
        root.addView(dropdownRow("Button 1 (Left Click)", "mouse.btn1", "select",
                new String[] {"select", "menu", "back"},
                new String[] {"Primary Click / Select", "Menu Button", "Back"}));

        // Button 2: Right Click (defaults to menu button as requested!)
        root.addView(dropdownRow("Button 2 (Right Click)", "mouse.btn2", "menu",
                new String[] {"menu", "back", "options", "settings", "none"},
                new String[] {"Menu Button (Tile Menu / Options)", "Back Navigation", "Open Options", "Open TV Settings", "Disabled"}));

        // Button 3: Middle Click (Wheel Click)
        root.addView(dropdownRow("Button 3 (Middle Click)", "mouse.btn3", "middle",
                new String[] {"middle", "menu", "options", "settings", "back"},
                new String[] {"Middle Click (Default)", "Menu Button", "Open Options", "Open TV Settings", "Back"}));

        // Button 4: Scroll Wheel
        root.addView(dropdownRow("Button 4 (Scroll Wheel)", "mouse.btn4", "scroll",
                new String[] {"scroll", "invert", "menu"},
                new String[] {"Vertical Scroll (Standard)", "Inverted Scroll", "Menu Button on Scroll"}));

        // Button 5: Side Button 1 (Back / Thumb Lower)
        root.addView(dropdownRow("Button 5 (Side Back)", "mouse.btn5", "back",
                new String[] {"back", "menu", "options", "settings", "none"},
                new String[] {"Back Navigation", "Menu Button", "Open Options", "Open TV Settings", "Disabled"}));

        // Button 6: Side Button 2 (Forward / Thumb Upper)
        root.addView(dropdownRow("Button 6 (Side Forward)", "mouse.btn6", "forward",
                new String[] {"forward", "menu", "options", "settings", "none"},
                new String[] {"Forward Navigation", "Menu Button", "Open Options", "Open TV Settings", "Disabled"}));

        // Button 7: DPI Switch Button
        root.addView(dropdownRow("Button 7 (DPI Switch)", "mouse.btn7", "dpi",
                new String[] {"dpi"},
                new String[] {"Hardware Optical Sensor Cycle (800 - 3200 DPI)"}));

        // SECTION 3: INTERACTIVE BUTTON TESTER
        TextView testHeading = Ui.text(c, 15, accent, Ui.MEDIUM);
        testHeading.setText("LIVE BUTTON TESTER");
        testHeading.setPadding(0, dp(16), 0, 0);
        root.addView(testHeading);

        FrameLayout testCard = new FrameLayout(c) {
            @Override
            public boolean onGenericMotionEvent(MotionEvent event) {
                if ((event.getSource() & InputDevice.SOURCE_CLASS_POINTER) != 0) {
                    float vscroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
                    if (vscroll != 0) {
                        String act = cfg.get("mouse.btn4", "scroll");
                        recordButton("Scroll Wheel (" + (vscroll > 0 ? "Up" : "Down") + ") -> Action: " + actionLabel(act));
                        return true;
                    }
                    int buttons = event.getButtonState();
                    if ((buttons & MotionEvent.BUTTON_BACK) != 0) {
                        String act = cfg.get("mouse.btn5", "back");
                        recordButton("Button 5 (Side Back) -> Action: " + actionLabel(act));
                        return true;
                    } else if ((buttons & MotionEvent.BUTTON_FORWARD) != 0) {
                        String act = cfg.get("mouse.btn6", "forward");
                        recordButton("Button 6 (Side Forward) -> Action: " + actionLabel(act));
                        return true;
                    } else if ((buttons & MotionEvent.BUTTON_TERTIARY) != 0) {
                        String act = cfg.get("mouse.btn3", "middle");
                        recordButton("Button 3 (Middle Click) -> Action: " + actionLabel(act));
                        return true;
                    }
                }
                return super.onGenericMotionEvent(event);
            }

            @Override
            public boolean onTouchEvent(MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    int btn = event.getButtonState();
                    if ((btn & MotionEvent.BUTTON_SECONDARY) != 0) {
                        String act = cfg.get("mouse.btn2", "menu");
                        recordButton("Button 2 (Right Click) -> Action: " + actionLabel(act));
                        return true;
                    } else if ((btn & MotionEvent.BUTTON_TERTIARY) != 0) {
                        String act = cfg.get("mouse.btn3", "middle");
                        recordButton("Button 3 (Middle Click) -> Action: " + actionLabel(act));
                        return true;
                    } else {
                        String act = cfg.get("mouse.btn1", "select");
                        recordButton("Button 1 (Left Click) -> Action: " + actionLabel(act));
                    }
                }
                return true;
            }
        };

        testCard.setBackground(Ui.round(0x22FFFFFF, dp(10)));
        testCard.setPadding(dp(18), dp(14), dp(18), dp(14));
        testCard.setClickable(true);
        testCard.setFocusable(true);

        testerLabel = Ui.text(c, 14, 0xFF34C759, Ui.MEDIUM);
        testerLabel.setText("Click any mouse button or scroll here to test...");
        testerLabel.setGravity(Gravity.CENTER);
        testCard.addView(testerLabel);

        LinearLayout.LayoutParams testLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        testLp.topMargin = dp(8);
        testLp.bottomMargin = dp(24);
        root.addView(testCard, testLp);

        // Done button
        TextView doneBtn = actionChip("                      Done                      ", v -> dismiss());
        doneBtn.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams doneLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        root.addView(doneBtn, doneLp);

        setContentView(scroll);
        if (getWindow() != null) {
            getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }

    private View dropdownRow(String buttonTitle, String configKey, String fallback, String[] keys, String[] labels) {
        Context c = getContext();
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(5), 0, dp(5));

        TextView label = Ui.text(c, 14, Ui.TEXT, Ui.MEDIUM);
        label.setText(buttonTitle);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(label, labelLp);

        String currentKey = cfg.get(configKey, fallback);
        int initialIdx = 0;
        for (int i = 0; i < keys.length; i++) {
            if (keys[i].equals(currentKey)) {
                initialIdx = i;
                break;
            }
        }

        TextView dropdownBtn = Ui.text(c, 13, accent, Ui.MEDIUM);
        dropdownBtn.setText(labels[initialIdx] + "  ▾");
        dropdownBtn.setPadding(dp(14), dp(8), dp(14), dp(8));
        dropdownBtn.setFocusable(true);
        dropdownBtn.setClickable(true);
        Ui.focusStyle(dropdownBtn, accent, 0x1AFFFFFF, dp(14));
        dropdownBtn.setOnFocusChangeListener((view, has) -> {
            view.animate().scaleX(has ? 1.05f : 1f).scaleY(has ? 1.05f : 1f).setDuration(100).start();
        });

        dropdownBtn.setOnClickListener(v -> {
            String active = cfg.get(configKey, fallback);
            int selectedIdx = 0;
            for (int i = 0; i < keys.length; i++) {
                if (keys[i].equals(active)) {
                    selectedIdx = i;
                    break;
                }
            }

            new AlertDialog.Builder(c, Ui.DIALOG)
                    .setTitle(buttonTitle)
                    .setSingleChoiceItems(labels, selectedIdx, (d, which) -> {
                        cfg.set(configKey, keys[which]);
                        dropdownBtn.setText(labels[which] + "  ▾");
                        d.dismiss();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        row.addView(dropdownBtn);
        return row;
    }

    private String actionLabel(String key) {
        if ("menu".equals(key)) return "Menu Button";
        if ("select".equals(key)) return "Select / Click";
        if ("back".equals(key)) return "Back";
        if ("options".equals(key)) return "Open Options";
        if ("settings".equals(key)) return "Open TV Settings";
        if ("forward".equals(key)) return "Forward";
        if ("middle".equals(key)) return "Middle Click";
        if ("scroll".equals(key)) return "Vertical Scroll";
        if ("invert".equals(key)) return "Inverted Scroll";
        if ("dpi".equals(key)) return "Hardware Sensor DPI Switch";
        return key;
    }

    private void recordButton(String text) {
        if (testerLabel != null) {
            testerLabel.setText("✓ " + text);
        }
    }

    private void adjustSpeed(int delta) {
        int next = Math.max(-7, Math.min(7, currentSpeed + delta));
        if (next != currentSpeed) {
            currentSpeed = next;
            applySpeed(currentSpeed);
            updateSpeedLabel();
        }
    }

    private void applySpeed(int speed) {
        Context c = getContext();
        cfg.set("mouse.speed", String.valueOf(speed));
        setSystemPointerSpeed(c, speed);
    }

    static void setSystemPointerSpeed(Context c, int speed) {
        // 1. Try su execution (available on Android TV boxes like H96 MAX to bypass private settings restriction)
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", "settings put system pointer_speed " + speed});
            p.waitFor();
            if (p.exitValue() == 0) {
                Log.i("AlveeTV", "su settings put system pointer_speed succeeded: " + speed);
                return;
            }
        } catch (Throwable t) {
            Log.d("AlveeTV", "su not available or failed: " + t);
        }

        // 2. Direct SettingsProvider call (bypasses client-side PRIVATE_SETTINGS check)
        try {
            android.os.Bundle extras = new android.os.Bundle();
            extras.putString("value", String.valueOf(speed));
            c.getContentResolver().call(android.net.Uri.parse("content://settings/system"), "PUT_system", "pointer_speed", extras);
            Log.i("AlveeTV", "SettingsProvider PUT_system pointer_speed set to: " + speed);
        } catch (Throwable t) {
            Log.w("AlveeTV", "SettingsProvider PUT_system error: " + t);
        }

        // 3. Try IBinder transact (TRANSACTION_tryPointerSpeed = 7 on IInputManager)
        try {
            Class<?> sm = Class.forName("android.os.ServiceManager");
            android.os.IBinder binder = (android.os.IBinder) sm.getMethod("getService", String.class).invoke(null, "input");
            if (binder != null) {
                android.os.Parcel data = android.os.Parcel.obtain();
                android.os.Parcel reply = android.os.Parcel.obtain();
                try {
                    data.writeInterfaceToken("android.hardware.input.IInputManager");
                    data.writeInt(speed);
                    binder.transact(7, data, reply, 0);
                    reply.readException();
                    Log.i("AlveeTV", "IBinder tryPointerSpeed succeeded: " + speed);
                } finally {
                    data.recycle();
                    reply.recycle();
                }
            }
        } catch (Throwable t) {
            Log.w("AlveeTV", "IBinder error: " + t);
        }

        // 4. Try settings shell command
        try {
            Runtime.getRuntime().exec(new String[]{"settings", "put", "system", "pointer_speed", String.valueOf(speed)});
        } catch (Throwable ignored) {}
    }

    private void updateSpeedLabel() {
        if (speedLabel != null) {
            String sign = currentSpeed > 0 ? "+" : "";
            speedLabel.setText("Speed: " + sign + currentSpeed + " (Range: -7 to +7)");
        }
    }

    private TextView actionChip(String label, View.OnClickListener click) {
        TextView v = Ui.text(getContext(), 14, Ui.TEXT, Ui.MEDIUM);
        v.setText(label);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(18), dp(10), dp(18), dp(10));
        v.setFocusable(true);
        v.setClickable(true);
        v.setOnClickListener(click);
        Ui.focusStyle(v, accent, 0x14FFFFFF, dp(18));
        v.setOnFocusChangeListener((view, has) -> {
            view.animate().scaleX(has ? 1.05f : 1f).scaleY(has ? 1.05f : 1f).setDuration(100).start();
        });
        return v;
    }
}
