package com.telesales.status;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import java.util.ArrayList;
import java.util.List;

// v7.9.4: in-app call-app choice. The user picks a dialer app (for example
// Brilliant Connect or WeTalk) inside the app; the choice is saved in
// SharedPreferences (telesales_prefs / call_app_package) and used for the
// next call. No single dialer is hardcoded and the phone system default
// dialer is never changed. If no app is saved, an in-app picker dialog
// is shown; picking Ask every time dials through the system chooser
// without saving anything.
public class DialHelper {
    public static final String PREFS_NAME = "telesales_prefs";
    public static final String KEY_CALL_APP_PACKAGE = "call_app_package";
    public static final String ASK_EVERY_TIME = "প্রতিবার জিজ্ঞেস করুন";

    public static String getSavedPackage(Context ctx) {
        if (ctx == null) return null;
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String pkg = prefs.getString(KEY_CALL_APP_PACKAGE, null);
        if (pkg == null || pkg.isEmpty()) return null;
        return pkg;
    }

    public static void savePackage(Context ctx, String pkg) {
        if (ctx == null) return;
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (pkg == null || pkg.isEmpty()) {
            prefs.edit().remove(KEY_CALL_APP_PACKAGE).apply();
        } else {
            prefs.edit().putString(KEY_CALL_APP_PACKAGE, pkg).apply();
        }
    }

    public static void clearSavedPackage(Context ctx) {
        savePackage(ctx, null);
    }

    public static Intent buildDialIntent(String number) {
        String n = number == null ? "" : number.trim();
        return new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + n));
    }

    public static List<ResolveInfo> getDialApps(Context ctx) {
        List<ResolveInfo> apps = new ArrayList<>();
        if (ctx == null) return apps;
        Intent dial = buildDialIntent("0");
        PackageManager pm = ctx.getPackageManager();
        List<ResolveInfo> found = pm.queryIntentActivities(dial, PackageManager.MATCH_DEFAULT_ONLY);
        if (found != null) apps.addAll(found);
        return apps;
    }

    public static boolean isPackageInstalled(Context ctx, String pkg) {
        if (ctx == null || pkg == null || pkg.isEmpty()) return false;
        try {
            ctx.getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void dialWithPackage(Context ctx, String number, String pkg) {
        Intent dial = buildDialIntent(number);
        dial.setPackage(pkg);
        try {
            ctx.startActivity(dial);
        } catch (ActivityNotFoundException e) {
            clearSavedPackage(ctx);
            dial(ctx, number);
        } catch (Exception e) {
            clearSavedPackage(ctx);
        }
    }

    public static void dial(Context ctx, String number) {
        if (ctx == null) return;
        String n = number == null ? "" : number.trim();
        if (n.isEmpty()) return;
        String saved = getSavedPackage(ctx);
        if (saved != null && isPackageInstalled(ctx, saved)) {
            dialWithPackage(ctx, n, saved);
            return;
        }
        if (ctx instanceof Activity) {
            showPicker((Activity) ctx, n);
        } else {
            Intent chooser = Intent.createChooser(buildDialIntent(n), "কল করার অ্যাপ বেছে নিন");
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                ctx.startActivity(chooser);
            } catch (Exception ignored) {
            }
        }
    }

    // Build the dial intent for a receiver (no Activity to show a dialog):
    // saved app -> setPackage; otherwise the system chooser.
    public static Intent buildDialIntentForReceiver(Context ctx, String number) {
        Intent dial = buildDialIntent(number);
        String saved = getSavedPackage(ctx);
        if (saved != null && isPackageInstalled(ctx, saved)) {
            dial.setPackage(saved);
            return dial;
        }
        return Intent.createChooser(dial, "কল করার অ্যাপ বেছে নিন");
    }

    // Open the picker dialog so the saved call app can be changed later.
    // number may be null: then picking only saves the choice, it does not dial.
    public static void showPicker(Activity activity, String number) {
        if (activity == null) return;
        final String dialNumber = number == null ? "" : number.trim();
        List<ResolveInfo> apps = getDialApps(activity);
        PackageManager pm = activity.getPackageManager();
        final List<String> labels = new ArrayList<>();
        final List<String> packages = new ArrayList<>();
        for (ResolveInfo info : apps) {
            String pkg = null;
            if (info.activityInfo != null) pkg = info.activityInfo.packageName;
            if (pkg == null || pkg.isEmpty()) continue;
            CharSequence label = info.loadLabel(pm);
            labels.add(label == null ? pkg : label.toString());
            packages.add(pkg);
        }
        labels.add(ASK_EVERY_TIME);
        packages.add("");
        String[] items = labels.toArray(new String[0]);
        String saved = getSavedPackage(activity);
        int checked = -1;
        for (int i = 0; i < packages.size(); i++) {
            if (saved != null && saved.equals(packages.get(i))) {
                checked = i;
                break;
            }
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("কল অ্যাপ বেছে নিন");
        builder.setSingleChoiceItems(items, checked, (d, w) -> {
            String picked = packages.get(w);
            d.dismiss();
            if (picked == null || picked.isEmpty()) {
                clearSavedPackage(activity);
                if (!dialNumber.isEmpty()) {
                    Intent chooser = Intent.createChooser(buildDialIntent(dialNumber), "কল করার অ্যাপ বেছে নিন");
                    try {
                        activity.startActivity(chooser);
                    } catch (Exception ignored) {
                    }
                }
            } else {
                savePackage(activity, picked);
                if (!dialNumber.isEmpty()) {
                    dialWithPackage(activity, dialNumber, picked);
                }
            }
        });
        builder.setNegativeButton("বাতিল", null);
        builder.show();
    }

    public static void showCallAppPicker(Activity activity) {
        showPicker(activity, null);
    }
}
