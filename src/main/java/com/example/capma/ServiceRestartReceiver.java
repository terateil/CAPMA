package com.example.capma;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

/**
 * Broadcast receiver to restart the background service if it gets killed
 * Also starts the service on device boot
 */
public class ServiceRestartReceiver extends BroadcastReceiver {
    private static final String TAG = "ServiceRestartReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, "Received action: " + action);
        
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            Log.d(TAG, "Boot completed, starting service");
        } else if (BackgroundService.ACTION_RESTART_SERVICE.equals(action)) {
            Log.d(TAG, "Service stopped, restarting...");
        } else {
            Log.d(TAG, "Unknown action, starting service anyway");
        }
        
        // Restart the service regardless of the action
        Intent serviceIntent = new Intent(context, BackgroundService.class);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent);
        } else {
            context.startService(serviceIntent);
        }
    }
} 