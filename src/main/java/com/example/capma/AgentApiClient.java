package com.example.capma;

import android.util.Log;

import com.example.capma.embedding.RetrievalManager;
import com.google.android.gms.location.DetectedActivity;
import com.google.android.libraries.places.api.model.PlaceLikelihood;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Client for communicating with the agent API server
 */
public class AgentApiClient {
    private static final String TAG = "AgentApiClient";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    
    private String apiUrl;
    private final OkHttpClient client;
    
    /**
     * Interface for receiving API call results
     */
    public interface ApiCallback {
        void onSuccess(String result);
        void onError(Exception e);
    }
    
    /**
     * Constructor
     * @param serverUrl Full URL of the API server endpoint (e.g., "http://192.168.1.100:5800/run")
     */
    public AgentApiClient(String serverUrl) {
        this.apiUrl = serverUrl;
        Log.d(TAG, "Initialized with API URL: " + apiUrl);
        
        // Configure OkHttpClient with timeouts
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }
    
    /**
     * Get the current server URL
     * @return The current server URL
     */
    public String getServerUrl() {
        return apiUrl;
    }
    
    /**
     * Set a new server URL
     * @param serverUrl The new server URL
     */
    public void setServerUrl(String serverUrl) {
        this.apiUrl = serverUrl;
        Log.d(TAG, "Updated API URL to: " + apiUrl);
    }
    
    /**
     * Format all collected data into a single instruction text
     * @param transcribedText The main instruction from transcribed speech
     * @param activities Detected activities
     * @param places Detected places
     * @param audioAmplitude Audio amplitude
     * @param retrievalResults Retrieval results
     * @return Formatted instruction text
     */
    public String formatInstruction(
            String transcribedText,
            List<DetectedActivity> activities,
            List<PlaceLikelihood> places,
            double audioAmplitude,
            List<RetrievalManager.SearchResult> retrievalResults) {
        
        StringBuilder builder = new StringBuilder();
        
        // Add context information
        builder.append("CONTEXT INFORMATION:\n\n");
        
        // Add activity data
        builder.append(ActivityRecognitionManager.formatActivityData(activities));
        builder.append("\n");
        
        // Add place data
        builder.append(PlacesManager.formatPlaceData(places));
        builder.append("\n");
        
        // Add audio data
        builder.append(AudioRecorder.formatAudioData(audioAmplitude));
        builder.append("\n");
        
        // Add the main instruction (transcribed text)
        builder.append("INSTRUCTION: ").append(transcribedText).append("\n\n");

        // Add retrieval results
        builder.append(RetrievalManager.formatSearchResults(retrievalResults));
        
        return builder.toString();
    }
    
    /**
     * Send an instruction to the agent API
     * @param instruction The instruction text
     * @param callback Callback to receive results
     */
    public void sendInstruction(String instruction, ApiCallback callback) {
        try {
            // Create JSON payload
            JSONObject jsonBody = new JSONObject();
            jsonBody.put("text", instruction);
            
            // Create request
            RequestBody body = RequestBody.create(jsonBody.toString(), JSON);
            Request request = new Request.Builder()
                    .url(apiUrl)
                    .post(body)
                    .build();
            
            Log.d(TAG, "Sending request to: " + apiUrl);
            
            // Execute request asynchronously
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "API call failed to " + apiUrl, e);
                    callback.onError(e);
                }
                
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        Log.d(TAG, "Received response code: " + response.code());
                        
                        if (!response.isSuccessful()) {
                            throw new IOException("Unexpected response code: " + response);
                        }
                        
                        String responseBody = response.body().string();
                        Log.d(TAG, "Response body: " + responseBody);
                        
                        JSONObject jsonResponse = new JSONObject(responseBody);
                        String result = jsonResponse.getString("result");
                        
                        callback.onSuccess(result);
                        
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing JSON response", e);
                        callback.onError(e);
                    } finally {
                        response.close();
                    }
                }
            });
            
        } catch (JSONException e) {
            Log.e(TAG, "Error creating JSON request", e);
            callback.onError(e);
        }
    }
} 