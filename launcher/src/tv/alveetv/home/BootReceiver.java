package tv.alveetv.home;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Puts the home keeper back after a reboot or an update. */
public final class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Keeper.start(context);
        TimeSync.sync(context);
        Config cfg = Config.get(context);
        if (!"off".equals(cfg.get(Config.WEB_REMOTE))) {
            RemoteServer.start(context);
        }
    }
}
