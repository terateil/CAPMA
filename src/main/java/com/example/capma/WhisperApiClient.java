package com.example.capma;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Client for interacting with OpenAI's Whisper API for speech-to-text
 */
public class WhisperApiClient {
    private static final String TAG = "WhisperApiClient";
    private static final String API_URL = "https://api.openai.com/v1/audio/transcriptions";
    private static final MediaType MEDIA_TYPE_AUDIO = MediaType.parse("audio/wav");
    
    private final String apiKey;
    private final OkHttpClient client;
    
    /**
     * Interface for receiving transcription results
     */
    public interface TranscriptionCallback {
        void onTranscriptionComplete(String text);
        void onTranscriptionError(Exception e);
    }
    
    /**
     * Constructor
     * @param apiKey OpenAI API key
     */
    public WhisperApiClient(String apiKey) {
        this.apiKey = apiKey;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
    }
    
    /**
     * Transcribe audio file using OpenAI's Whisper API
     * @param audioFile Audio file to transcribe
     * @param callback Callback to receive results
     */
    public void transcribeAudio(File audioFile, TranscriptionCallback callback) {
        if (audioFile == null || !audioFile.exists()) {
            callback.onTranscriptionError(new IllegalArgumentException("Audio file does not exist"));
            return;
        }
        
        // Build multipart request body
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", audioFile.getName(), 
                        RequestBody.create(audioFile, MEDIA_TYPE_AUDIO))
                .addFormDataPart("model", "whisper-1")
                .addFormDataPart("response_format", "json")
                .build();
        
        // Build request
        Request request = new Request.Builder()
                .url(API_URL)
                .header("Authorization", "Bearer " + apiKey)
                .post(requestBody)
                .build();
        
        // Execute request asynchronously
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "API call failed", e);
                callback.onTranscriptionError(e);
            }
            
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    if (!response.isSuccessful()) {
                        String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                        Log.e(TAG, "API error: " + response.code() + " - " + errorBody);
                        callback.onTranscriptionError(new IOException("API error: " + response.code() + " - " + errorBody));
                        return;
                    }
                    
                    String responseBody = response.body().string();
                    JSONObject jsonResponse = new JSONObject(responseBody);
                    String transcribedText = jsonResponse.getString("text");
                    
                    Log.d(TAG, "Transcription successful: " + transcribedText);
                    callback.onTranscriptionComplete(transcribedText);
                    
                } catch (JSONException e) {
                    Log.e(TAG, "Error parsing JSON response", e);
                    callback.onTranscriptionError(e);
                } finally {
                    if (response.body() != null) {
                        response.body().close();
                    }
                }
            }
        });
    }
} 