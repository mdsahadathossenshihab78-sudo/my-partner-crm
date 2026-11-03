package com.telesales.status;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import java.util.List;

/** v4.31: phone restart-er por sob bhabishyoter reminder abar bosano;
 *  bondho thakar somoy par hoye gele notification sathe sathe dekhano + tarpor clear. */
public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context ctx, Intent intent) {
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        long now = System.currentTimeMillis();
        List<ReminderStore.Item> items = ReminderStore.list(ctx);
        for (ReminderStore.Item it : items) {
            if (it.time <= now) {
                ReminderStore.remove(ctx, it.number);
                ReminderReceiver.notifyNow(ctx, it.number);
            } else {
                ReminderReceiver.schedule(ctx, it.number, it.time);
            }
        }
    }
}
