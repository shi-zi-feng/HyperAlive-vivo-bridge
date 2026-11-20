package com.qihang.vivobridge;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.app.BroadcastOptions;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import java.util.WeakHashMap;

/** Ephemeral handoff between the two system-UID AOD hosts, scoped to one sleep cycle. */
final class NativeAodSession {
    private static final String STATE = "com.hyperalive.aod.NATIVE_AOD_HOST";
    private static final String QUERY = "com.hyperalive.aod.QUERY_NATIVE_AOD_HOST";
    private static final String PERMISSION = "android.permission.STATUS_BAR_SERVICE";
    private static final WeakHashMap<View, Boolean> HOSTS = new WeakHashMap<>();
    private static boolean listening, present;
    private static long epoch, sequence;

    static void listenSystemUi(Context context) {
        if (listening) return;
        Context app = context.getApplicationContext();
        final Context ctx = app != null ? app : context;
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context ignored, Intent intent) {
                if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                    present = false;
                    epoch = 0;
                } else {
                    if (Build.VERSION.SDK_INT >= 34 && getSentFromUid() != 1000) return;
                    long incoming = intent.getLongExtra("sequence", 0);
                    if (incoming <= sequence) return;
                    sequence = incoming;
                    present = intent.getBooleanExtra("present", false);
                    epoch = intent.getLongExtra("epoch", 0);
                }
                Log.i("HyperAliveVivo", "Native AOD handoff present=" + present + " epoch=" + epoch);
                NativeClockController.refreshAll();
                com.hyperalive.aod.hook.ViewTreeSweeper.sweep(
                        com.hyperalive.aod.hook.StateCoordinator.sAodRoot);
            }
        };
        IntentFilter filter = new IntentFilter(STATE);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        register(ctx, receiver, filter);
        listening = true;
        send(ctx, new Intent(QUERY).setPackage("com.vivo.nightpearl"));
    }

    static boolean active() {
        return BridgePolicy.currentNativeHost(present, epoch,
                VivoBridge.propLong("sys.qihang.sleep_at"), VivoBridge.propLong("sys.qihang.wake_at"));
    }

    static void update(Context context, View host, boolean active) {
        if (active) HOSTS.put(host, Boolean.TRUE);
        else HOSTS.remove(host);
        publish(context);
    }

    static BroadcastReceiver listenQueries(final Context context) {
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context ignored, Intent intent) {
                if (Build.VERSION.SDK_INT >= 34 && getSentFromUid() != 1000) return;
                publish(context);
            }
        };
        register(context, receiver, new IntentFilter(QUERY));
        return receiver;
    }

    private static void publish(Context context) {
        Intent event = new Intent(STATE).setPackage("com.android.systemui")
                .putExtra("present", !HOSTS.isEmpty())
                .putExtra("epoch", VivoBridge.propLong("sys.qihang.sleep_at"))
                .putExtra("sequence", SystemClock.elapsedRealtimeNanos())
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        send(context, event);
    }

    private static void send(Context context, Intent intent) {
        if (Build.VERSION.SDK_INT >= 34) {
            context.sendBroadcast(intent, PERMISSION,
                    BroadcastOptions.makeBasic().setShareIdentityEnabled(true).toBundle());
        } else context.sendBroadcast(intent, PERMISSION);
    }

    private static void register(Context context, BroadcastReceiver receiver, IntentFilter filter) {
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter, PERMISSION, new Handler(Looper.getMainLooper()),
                    Context.RECEIVER_EXPORTED);
        } else context.registerReceiver(receiver, filter, PERMISSION, new Handler(Looper.getMainLooper()));
    }
}
