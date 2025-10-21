package com.example.capma;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

/**
 * Foreground service to keep the app running in the background
 * even when other apps take focus or ADB commands are executed
 */
public class BackgroundService extends Service {
    private static final String TAG = "BackgroundService";
    
    // Notification constants
    private static final String CHANNEL_ID = "CAPMAServiceChannel";
    private static final int NOTIFICATION_ID = 1001;
    
    // Broadcast action for service restart
    public static final String ACTION_RESTART_SERVICE = "com.example.capma.RESTART_SERVICE";
    
    // Binder for clients
    private final IBinder binder = new LocalBinder();
    
    // Flag to track if service is running
    private static boolean isRunning = false;
    
    // Current status message
    private String currentStatus = "App is running in the background";
    
    /**
     * Class for clients to access this service
     */
    public class LocalBinder extends Binder {
        public BackgroundService getService() {
            return BackgroundService.this;
        }
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Background service created");
        createNotificationChannel();
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Background service started");
        
        // Create the notification
        Notification notification = createNotification();
        
        // Start as a foreground service with a persistent notification
        try {
            if (Build.VERSION.SDK_INT >= 34) { // Android 14 (API 34)
                // For Android 14+, we need to specify the foreground service type
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } else {
                // For older versions
                startForeground(NOTIFICATION_ID, notification);
            }
            isRunning = true;
            Log.d(TAG, "Service started in foreground successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error starting foreground service", e);
        }
        
        // If the service gets killed, restart it
        return START_STICKY;
    }
    
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Background service destroyed");
        isRunning = false;
        
        // Send broadcast to restart the service
        Intent broadcastIntent = new Intent(ACTION_RESTART_SERVICE);
        broadcastIntent.setClass(this, ServiceRestartReceiver.class);
        sendBroadcast(broadcastIntent);
    }
    
    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        Log.d(TAG, "Task removed, sending restart broadcast");
        
        // Send broadcast to restart the service when the app is removed from recent apps
        Intent broadcastIntent = new Intent(ACTION_RESTART_SERVICE);
        broadcastIntent.setClass(this, ServiceRestartReceiver.class);
        sendBroadcast(broadcastIntent);
    }
    
    /**
     * Create the notification channel for Android 8.0+
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "CAPMA Service Channel",
                    NotificationManager.IMPORTANCE_LOW
            );
            serviceChannel.setDescription("Channel for CAPMA background service");
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }
    
    /**
     * Create the notification for the foreground service
     */
    private Notification createNotification() {
        // Create an intent to open the main activity when notification is clicked
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                notificationIntent,
                PendingIntent.FLAG_IMMUTABLE
        );
        
        // Build the notification
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("CAPMA Running")
                .setContentText(currentStatus)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }
    
    /**
     * Update the notification with a new status message
     * @param status New status message to display
     */
    public void updateNotification(String status) {
        if (status != null && !status.isEmpty()) {
            this.currentStatus = status;
            
            // Update the notification
            NotificationManager notificationManager = 
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                notificationManager.notify(NOTIFICATION_ID, createNotification());
            }
            
            Log.d(TAG, "Updated notification: " + status);
        }
    }
    
    /**
     * Check if the service is currently running
     */
    public static boolean isServiceRunning() {
        return isRunning;
    }
} 