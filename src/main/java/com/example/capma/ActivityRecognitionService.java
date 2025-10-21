package com.example.capma;

import android.app.IntentService;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;

import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.DetectedActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Service to receive activity recognition updates
 */
@SuppressWarnings("deprecation") // IntentService is deprecated but still works for our use case
public class ActivityRecognitionService extends IntentService {
    private static final String TAG = "ActivityRecognitionSvc";
    public static final String ACTION_PROCESS_ACTIVITY = "com.example.capma.ACTION_PROCESS_ACTIVITY";
    
    // Confidence threshold for including activities in the result
    private static final int CONFIDENCE_THRESHOLD = 10;
    
    // Maximum number of activities to return
    private static final int MAX_ACTIVITIES = 3;
    
    // Static callback reference
    private static volatile ActivityRecognitionManager.ActivityRecognitionCallback callback;
    
    // Handler for main thread callbacks
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    
    public ActivityRecognitionService() {
        super(TAG);
    }
    
    /**
     * Set the callback to receive activity updates
     * @param callback The callback
     */
    public static synchronized void setCallback(ActivityRecognitionManager.ActivityRecognitionCallback callback) {
        ActivityRecognitionService.callback = callback;
        Log.d(TAG, "Callback " + (callback != null ? "set" : "cleared"));
    }
    
    /**
     * Get the current callback (thread-safe)
     */
    private static synchronized ActivityRecognitionManager.ActivityRecognitionCallback getCallback() {
        return callback;
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "ActivityRecognitionService created");
    }
    
    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        if (intent == null) {
            Log.d(TAG, "Received null intent");
            return;
        }
        
        Log.d(TAG, "Received intent: " + intent.getAction());
        
        if (ACTION_PROCESS_ACTIVITY.equals(intent.getAction())) {
            if (ActivityRecognitionResult.hasResult(intent)) {
                ActivityRecognitionResult result = ActivityRecognitionResult.extractResult(intent);
                Log.d(TAG, "Successfully extracted ActivityRecognitionResult");
                
                // Process the activities on a background thread (this service thread)
                List<DetectedActivity> filteredActivities = filterAndProcessActivities(result.getProbableActivities());
                
                // Log the filtered activities
                logActivities(filteredActivities);
                
                // Get a copy of the callback to avoid race conditions
                final ActivityRecognitionManager.ActivityRecognitionCallback currentCallback = getCallback();
                
                // Notify the callback if available, on the main thread
                if (currentCallback != null) {
                    final List<DetectedActivity> finalActivities = new ArrayList<>(filteredActivities);
                    mainHandler.post(() -> {
                        try {
                            Log.d(TAG, "Delivering activity results to callback");
                            currentCallback.onActivitiesDetected(finalActivities);
                        } catch (Exception e) {
                            Log.e(TAG, "Error delivering activity results", e);
                        }
                    });
                } else {
                    Log.d(TAG, "Callback is null, cannot deliver activity results");
                }
            } else {
                Log.d(TAG, "No ActivityRecognitionResult in intent");
            }
        }
    }
    
    /**
     * Log the detected activities
     */
    private void logActivities(List<DetectedActivity> activities) {
        Log.d(TAG, "Activities detected: " + activities.size());
        for (DetectedActivity activity : activities) {
            Log.d(TAG, "Activity: " + ActivityRecognitionManager.getActivityString(activity.getType()) + 
                    ", Confidence: " + activity.getConfidence() + "%");
        }
    }
    
    /**
     * Filter and process activities to get the most relevant ones
     * @param activities Raw detected activities
     * @return Filtered and processed activities
     */
    private List<DetectedActivity> filterAndProcessActivities(List<DetectedActivity> activities) {
        if (activities == null || activities.isEmpty()) {
            // Return a default activity if no activities are detected
            return Collections.singletonList(new DetectedActivity(DetectedActivity.STILL, 70));
        }
        
        // Filter activities by confidence threshold
        List<DetectedActivity> filteredActivities = new ArrayList<>();
        for (DetectedActivity activity : activities) {
            if (activity.getConfidence() >= CONFIDENCE_THRESHOLD) {
                filteredActivities.add(activity);
            }
        }
        
        // If no activities meet the threshold, include the most confident one
        if (filteredActivities.isEmpty() && !activities.isEmpty()) {
            // Sort by confidence (descending)
            activities.sort((a1, a2) -> Integer.compare(a2.getConfidence(), a1.getConfidence()));
            filteredActivities.add(activities.get(0));
        }
        
        // Sort filtered activities by confidence (descending)
        filteredActivities.sort((a1, a2) -> Integer.compare(a2.getConfidence(), a1.getConfidence()));
        
        // Limit the number of activities
        if (filteredActivities.size() > MAX_ACTIVITIES) {
            filteredActivities = filteredActivities.subList(0, MAX_ACTIVITIES);
        }
        
        // Ensure the confidence values sum to 100%
        normalizeConfidences(filteredActivities);
        
        return filteredActivities;
    }
    
    /**
     * Normalize confidence values to sum to 100%
     * @param activities Activities to normalize
     */
    private void normalizeConfidences(List<DetectedActivity> activities) {
        if (activities == null || activities.isEmpty()) {
            return;
        }
        
        // Calculate total confidence
        int totalConfidence = 0;
        for (DetectedActivity activity : activities) {
            totalConfidence += activity.getConfidence();
        }
        
        // If total is 0 or 100, no need to normalize
        if (totalConfidence == 0 || totalConfidence == 100) {
            return;
        }
        
        // Create new activities with normalized confidences
        List<DetectedActivity> normalizedActivities = new ArrayList<>();
        int normalizedTotal = 0;
        
        // Normalize all but the last activity
        for (int i = 0; i < activities.size() - 1; i++) {
            DetectedActivity activity = activities.get(i);
            int normalizedConfidence = Math.round(activity.getConfidence() * 100f / totalConfidence);
            normalizedTotal += normalizedConfidence;
            
            // Create a new activity with the normalized confidence
            normalizedActivities.add(new DetectedActivity(activity.getType(), normalizedConfidence));
        }
        
        // The last activity gets the remaining confidence to ensure sum is exactly 100
        DetectedActivity lastActivity = activities.get(activities.size() - 1);
        int lastConfidence = 100 - normalizedTotal;
        if (lastConfidence < 1) lastConfidence = 1; // Ensure at least 1%
        normalizedActivities.add(new DetectedActivity(lastActivity.getType(), lastConfidence));
        
        // Replace the original activities with normalized ones
        activities.clear();
        activities.addAll(normalizedActivities);
    }
} 