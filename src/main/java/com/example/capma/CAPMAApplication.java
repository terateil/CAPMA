package com.example.capma;

import android.Manifest;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.core.content.ContextCompat;

/**
 * Application class for CAPMA
 * This ensures the background service stays running even when the app is killed
 */
public class CAPMAApplication extends Application {
    private static final String TAG = "CAPMAApplication";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Application created");
        
        // Start the background service when the application is created
        startBackgroundService();
    }
    
    /**
     * Start the background service to keep the app running
     */
    private void startBackgroundService() {
        // For Android 13+, check if we have notification permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                // We don't have permission, so we can't start the service
                // The MainActivity will request the permission and start the service
                Log.d(TAG, "Cannot start service from Application: POST_NOTIFICATIONS permission not granted");
                return;
            }
        }
        
        Intent serviceIntent = new Intent(this, BackgroundService.class);
        
        // Start the service as a foreground service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        
        Log.d(TAG, "Background service started from Application");
    }
} 