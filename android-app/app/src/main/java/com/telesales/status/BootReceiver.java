package com.telesales.status;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import java.util.List;

/** v4.31: phone restart-er por sob bhabishyoter reminder abar bosano;
 *  bondho thakar somoy par hoye gele notification sathe sathe dekhano
 *  (v6.8: storage-e thakbe, app open-e popup-e action neoa porjonto). */
public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context ctx, Intent intent) {
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        long now = System.currentTimeMillis();
        List<ReminderStore.Item> items = ReminderStore.list(ctx);
        for (ReminderStore.Item it : items) {
            if (it.time <= now) {
                // v6.8: par hoye jawa reminder muchbo na — notification dekhano hobe, app open-e popup asbe.
                ReminderReceiver.notifyNow(ctx, it.number);
            } else {
                ReminderReceiver.schedule(ctx, it.number, it.time);
            }
        }
        // Batch feature #10b: diner hisab-er alarm-o reboot-er por abar bosano.
        DaySummaryReceiver.schedule(ctx);
    }
}
