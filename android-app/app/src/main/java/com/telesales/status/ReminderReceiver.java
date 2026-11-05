package com.telesales.status;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

/** v4.31: reminder alarm fire + dismiss. Alarm-er somoy hole notification dekhabe,
 *  fired reminder storage theke muchbe (abar fire hobe na). */
public class ReminderReceiver extends BroadcastReceiver {
    public static final String ACTION_REMIND = "com.telesales.status.REMIND";
    public static final String ACTION_DISMISS = "com.telesales.status.REMIND_DISMISS";
    /** Batch feature #3: notification theke 1 ghonta pore abar mone korano (snooze). */
    public static final String ACTION_SNOOZE = "com.telesales.status.REMIND_SNOOZE";
    public static final String EXTRA_NUMBER = "number";
    private static final String CHANNEL_ID = "telesales_reminders";

    /** Number theke stable requestCode — ekoi number-er purono alarm bodlano/cancel kora jabe. */
    public static int requestCodeFor(String number) {
        return (number == null ? 0 : number.hashCode()) & 0x7fffffff;
    }

    private static PendingIntent alarmIntent(Context ctx, String number, int flags) {
        Intent i = new Intent(ctx, ReminderReceiver.class);
        i.setAction(ACTION_REMIND);
        i.putExtra(EXTRA_NUMBER, number);
        return PendingIntent.getBroadcast(ctx, requestCodeFor(number), i, flags);
    }

    public static void schedule(Context ctx, String number, long epochMillis) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = alarmIntent(ctx, number, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, epochMillis, pi);
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, epochMillis, pi);
        }
    }

    public static void cancel(Context ctx, String number) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = alarmIntent(ctx, number, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.cancel(pi);
    }

    private static void ensureChannel(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "মনে করিয়ে দেওয়া", NotificationManager.IMPORTANCE_HIGH);
                ch.setDescription("কল করার সময় হলে জানানো হবে");
                nm.createNotificationChannel(ch);
            }
        }
    }

    /** Notification dekhano — number + Call action (chooser, row-er Call-er motoi) + Bondho action. */
    public static void notifyNow(Context ctx, String number) {
        ensureChannel(ctx);
        int id = requestCodeFor(number);
        Intent dial = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + number));
        Intent chooser = Intent.createChooser(dial, "কল করার অ্যাপ বেছে নিন");
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent callPi = PendingIntent.getActivity(ctx, id + 1, chooser, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent dismiss = new Intent(ctx, ReminderReceiver.class);
        dismiss.setAction(ACTION_DISMISS);
        dismiss.putExtra(EXTRA_NUMBER, number);
        PendingIntent dismissPi = PendingIntent.getBroadcast(ctx, id + 2, dismiss, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent snooze = new Intent(ctx, ReminderReceiver.class);
        snooze.setAction(ACTION_SNOOZE);
        snooze.putExtra(EXTRA_NUMBER, number);
        PendingIntent snoozePi = PendingIntent.getBroadcast(ctx, id + 4, snooze, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent open = new Intent(ctx, MainActivity.class);
        open.putExtra(EXTRA_NUMBER, number);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openPi = PendingIntent.getActivity(ctx, id + 3, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_logo_t)
                .setContentTitle("কল করার সময় হয়েছে")
                .setContentText(number + "-এ কল করার কথা ছিল — কল করুন")
                .setContentIntent(openPi)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .addAction(0, "কল করুন", callPi)
                .addAction(0, "১ ঘণ্টা পরে", snoozePi)
                .addAction(0, "বন্ধ করুন", dismissPi);
        try {
            NotificationManagerCompat.from(ctx).notify(id, b.build());
        } catch (SecurityException ignored) {
            // notification onumoti na thakle chupchap bad — crash noy
        }
    }

    /** Batch feature #9: fired/dismissed reminder-er sheet row-o badano (best-effort). */
    private static void removeSheetReminder(Context ctx, String number) {
        if (number == null || number.isEmpty()) return;
        new Thread(() -> {
            String token = ReminderSheetSync.getToken(ctx);
            String agentId = ctx.getSharedPreferences("telesales_prefs", Context.MODE_PRIVATE).getString("agent_id", null);
            ReminderSheetSync.remove(token, agentId, number);
        }).start();
    }

    @Override public void onReceive(Context ctx, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        String number = intent.getStringExtra(EXTRA_NUMBER);
        if (ACTION_DISMISS.equals(action)) {
            if (number != null) NotificationManagerCompat.from(ctx).cancel(requestCodeFor(number));
            removeSheetReminder(ctx, number);
            return;
        }
        if (ACTION_SNOOZE.equals(action)) {
            if (number == null || number.isEmpty()) return;
            NotificationManagerCompat.from(ctx).cancel(requestCodeFor(number));
            long t = System.currentTimeMillis() + 3600_000L;
            ReminderStore.save(ctx, number, t);
            schedule(ctx, number, t);
            return;
        }
        if (!ACTION_REMIND.equals(action)) return;
        if (number == null || number.isEmpty()) return;
        ReminderStore.remove(ctx, number);
        removeSheetReminder(ctx, number);
        notifyNow(ctx, number);
    }
}
