package tv.alveetv.home;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * All Apps management page: shows installed apps (filtered to 'Not on Home' by default),
 * and allows launching, adding to home screen, hiding, or uninstalling.
 */
public final class AllAppsActivity extends Activity {
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;

    private static final int FILTER_NOT_ON_HOME = 0;
    private static final int FILTER_ALL = 1;
    private static final int FILTER_HIDDEN = 2;

    private int currentFilter = FILTER_NOT_ON_HOME;
    private Config cfg;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private List<Item> allApps = new ArrayList<>();
    private Grid grid;
    private TextView subtitle;
    private TextView tabNotOnHome, tabAll, tabHidden;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        cfg = Config.get(this);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF0C0C0E);

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(48), dp(32), dp(48), dp(32));
        root.addView(column, new FrameLayout.LayoutParams(MATCH, MATCH));

        // 1. Header Bar: Title, Count, Filter Tabs
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, 0, dp(16));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);

        TextView title = Ui.text(this, 28, Ui.TEXT, Ui.MEDIUM);
        title.setText("AlveeTV  •  All Apps");
        titles.addView(title);

        subtitle = Ui.text(this, 13, Ui.MUTED, Ui.LIGHT);
        subtitle.setText("Loading apps...");
        subtitle.setPadding(0, dp(3), 0, 0);
        titles.addView(subtitle);

        header.addView(titles, new LinearLayout.LayoutParams(0, WRAP, 1f));

        // Filter Tabs
        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setGravity(Gravity.CENTER_VERTICAL);

        tabNotOnHome = filterChip("Not on Home Screen", FILTER_NOT_ON_HOME);
        tabAll = filterChip("All Installed Apps", FILTER_ALL);
        tabHidden = filterChip("Hidden Apps", FILTER_HIDDEN);

        tabs.addView(tabNotOnHome);
        tabs.addView(tabAll);
        tabs.addView(tabHidden);

        header.addView(tabs);
        column.addView(header, new LinearLayout.LayoutParams(MATCH, WRAP));

        // 2. Apps ScrollView + Grid
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setFocusable(false);

        grid = new Grid(this);
        grid.setPadding(0, dp(12), 0, dp(36));
        scroll.addView(grid, new ViewGroup.LayoutParams(MATCH, WRAP));

        column.addView(scroll, new LinearLayout.LayoutParams(MATCH, 0, 1f));

        setContentView(root);
        loadApps();
    }

    @Override
    protected void onResume() {
        super.onResume();
        cfg.refresh();
        loadApps();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdown();
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }

    private TextView filterChip(String text, int filterId) {
        TextView chip = Ui.text(this, 13, Ui.TEXT, Ui.MEDIUM);
        chip.setText(text);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(14), dp(8), dp(14), dp(8));
        chip.setFocusable(true);
        chip.setClickable(true);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP, WRAP);
        lp.leftMargin = dp(8);
        chip.setLayoutParams(lp);

        chip.setOnClickListener(v -> {
            if (currentFilter != filterId) {
                currentFilter = filterId;
                updateTabStyles();
                renderGrid();
            }
        });

        chip.setOnFocusChangeListener((v, has) -> {
            v.animate().scaleX(has ? 1.05f : 1f).scaleY(has ? 1.05f : 1f).setDuration(100).start();
        });

        return chip;
    }

    private void updateTabStyles() {
        int accent = cfg.accent();
        styleTab(tabNotOnHome, currentFilter == FILTER_NOT_ON_HOME, accent);
        styleTab(tabAll, currentFilter == FILTER_ALL, accent);
        styleTab(tabHidden, currentFilter == FILTER_HIDDEN, accent);
    }

    private void styleTab(TextView tab, boolean active, int accent) {
        if (tab == null) return;
        int normalBg = active ? ((accent & 0x00FFFFFF) | 0x33000000) : 0x14FFFFFF;
        int textColor = active ? accent : Ui.TEXT;
        tab.setTextColor(textColor);

        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_focused},
                Ui.round((accent & 0x00FFFFFF) | 0x66000000, dp(16)));
        bg.addState(new int[0], Ui.round(normalBg, dp(16)));
        tab.setBackground(bg);
    }

    private void loadApps() {
        io.execute(() -> {
            List<Item> apps = Catalog.apps(this);
            ui.post(() -> {
                if (isDestroyed()) return;
                allApps = apps;
                updateTabStyles();
                renderGrid();
            });
        });
    }

    private void renderGrid() {
        if (grid == null) return;
        grid.removeAllViews();

        List<Item> filtered = new ArrayList<>();
        List<String> hiddenList = cfg.list("apps.hide");

        for (Item it : allApps) {
            boolean isShown = Catalog.shown(cfg, Catalog.APPS, it);
            boolean isHidden = hiddenList.contains(it.id);

            if (currentFilter == FILTER_NOT_ON_HOME) {
                if (!isShown) filtered.add(it);
            } else if (currentFilter == FILTER_HIDDEN) {
                if (isHidden) filtered.add(it);
            } else {
                filtered.add(it);
            }
        }

        // Update Subtitle
        String filterName = (currentFilter == FILTER_NOT_ON_HOME) ? "apps not on home screen"
                : (currentFilter == FILTER_HIDDEN ? "hidden apps" : "total installed apps");
        subtitle.setText(filtered.size() + " " + filterName + "  •  Click any app to Add, Launch or Uninstall");

        if (filtered.isEmpty()) {
            TextView empty = Ui.text(this, 15, Ui.MUTED, Ui.LIGHT);
            empty.setText(currentFilter == FILTER_NOT_ON_HOME
                    ? "Great! All installed apps are currently placed on your home screen."
                    : (currentFilter == FILTER_HIDDEN ? "No hidden apps." : "No applications found."));
            empty.setPadding(dp(16), dp(40), dp(16), dp(40));
            grid.addView(empty, new ViewGroup.LayoutParams(MATCH, WRAP));
            return;
        }

        int cardW = dp(310), cardH = dp(174);
        int accent = cfg.accent();
        String labels = cfg.get(Config.LABELS);

        for (Item it : filtered) {
            CharSequence label = Catalog.label(cfg, it);

            Tile tile = new Tile(this, it, Catalog.APPS, label, cardW, cardH, labels, accent);
            tile.setOnClickListener(v -> showAppActions(it));
            tile.setOnLongClickListener(v -> {
                showAppActions(it);
                return true;
            });

            grid.addView(tile, new ViewGroup.LayoutParams(WRAP, WRAP));

            if (!it.artRequested) {
                it.artRequested = true;
                io.execute(() -> {
                    Catalog.art(getPackageManager(), it, "afterglow".equals(cfg.get(Config.TILE_STYLE)));
                    ui.post(tile::invalidate);
                });
            }
        }

        if (grid.getChildCount() > 0) {
            grid.getChildAt(0).requestFocus();
        }
    }

    private void showAppActions(Item it) {
        boolean onHome = Catalog.shown(cfg, Catalog.APPS, it);
        boolean isHidden = cfg.list("apps.hide").contains(it.id);

        List<String> options = new ArrayList<>();
        options.add("▶   Open App");
        options.add(onHome ? "➖  Remove from Home Screen" : "➕  Add to Home Screen");
        options.add(isHidden ? "👁   Unhide App" : "👁   Hide App");
        options.add("🗑   Uninstall App");

        String[] actionItems = options.toArray(new String[0]);

        new AlertDialog.Builder(this, Ui.DIALOG)
                .setTitle(Catalog.label(cfg, it) + "\n(" + it.id + ")")
                .setItems(actionItems, (d, which) -> {
                    switch (which) {
                        case 0: // Open App
                            try {
                                if (it.intent != null) {
                                    startActivity(it.intent);
                                } else {
                                    Intent launch = getPackageManager().getLaunchIntentForPackage(it.id);
                                    if (launch != null) startActivity(launch);
                                    else Toast.makeText(this, "Cannot launch app", Toast.LENGTH_SHORT).show();
                                }
                            } catch (Throwable t) {
                                Toast.makeText(this, "Error opening app: " + t.getMessage(), Toast.LENGTH_LONG).show();
                            }
                            break;

                        case 1: // Add or Remove from Home Screen
                            if (onHome) {
                                Catalog.setShown(cfg, Catalog.APPS, it, false);
                                Toast.makeText(this, "Removed from home screen", Toast.LENGTH_SHORT).show();
                            } else {
                                Catalog.setShown(cfg, Catalog.APPS, it, true);
                                // Ensure it's included in order
                                List<String> order = cfg.list("apps.order");
                                if (!order.isEmpty() && !order.contains(it.id)) {
                                    order.add(it.id);
                                    cfg.setList("apps.order", order);
                                }
                                Toast.makeText(this, "Added to home screen!", Toast.LENGTH_SHORT).show();
                            }
                            renderGrid();
                            break;

                        case 2: // Hide or Unhide App
                            if (isHidden) {
                                List<String> hide = cfg.list("apps.hide");
                                hide.remove(it.id);
                                cfg.setList("apps.hide", hide);
                                Toast.makeText(this, "App unhidden", Toast.LENGTH_SHORT).show();
                            } else {
                                Catalog.setShown(cfg, Catalog.APPS, it, false);
                                Toast.makeText(this, "App hidden", Toast.LENGTH_SHORT).show();
                            }
                            renderGrid();
                            break;

                        case 3: // Uninstall App
                            confirmUninstall(it);
                            break;
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmUninstall(Item it) {
        new AlertDialog.Builder(this, Ui.DIALOG)
                .setTitle("Uninstall " + Catalog.label(cfg, it) + "?")
                .setMessage("This will remove " + it.id + " from your TV.")
                .setPositiveButton("Uninstall", (d, w) -> {
                    performUninstall(it);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void performUninstall(Item it) {
        // 1. Try standard uninstaller intent
        try {
            Intent un = new Intent(Intent.ACTION_UNINSTALL_PACKAGE);
            un.setData(Uri.parse("package:" + it.id));
            un.putExtra(Intent.EXTRA_RETURN_RESULT, true);
            startActivity(un);
        } catch (Throwable ignored) {}

        // 2. Also execute root pm uninstall as TV box has su
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", "pm uninstall " + it.id});
            io.execute(() -> {
                try {
                    p.waitFor();
                    // Clean up from config
                    List<String> show = cfg.list("apps.show");
                    show.remove(it.id);
                    cfg.setList("apps.show", show);

                    List<String> hide = cfg.list("apps.hide");
                    hide.remove(it.id);
                    cfg.setList("apps.hide", hide);

                    List<String> order = cfg.list("apps.order");
                    order.remove(it.id);
                    cfg.setList("apps.order", order);

                    ui.post(() -> {
                        Toast.makeText(this, it.label + " uninstalled", Toast.LENGTH_SHORT).show();
                        loadApps();
                    });
                } catch (Throwable ignored) {}
            });
        } catch (Throwable ignored) {}
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
            int btn = ev.getButtonState();
            if ((btn & MotionEvent.BUTTON_SECONDARY) != 0) {
                View focused = getCurrentFocus();
                if (focused instanceof Tile) {
                    showAppActions(((Tile) focused).item);
                    return true;
                }
            }
        }
        return super.dispatchTouchEvent(ev);
    }
}
