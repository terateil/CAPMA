package com.example.capma;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.capma.embedding.RetrievalManager;
import com.example.capma.embedding.SentenceEncoder;
import com.example.capma.ui.NotesActivity;
import com.example.capma.ui.SettingsActivity;
import com.google.android.gms.location.DetectedActivity;
import com.google.android.libraries.places.api.model.PlaceLikelihood;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {
    private static final String TAG = "MainActivity";
    private static final int REQUEST_PERMISSIONS_CODE = 100;
    
    // Define all required permissions
    private static final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
    };
    
    // Activity recognition permission needs special handling on Android 10+
    private static final String ACTIVITY_RECOGNITION_PERMISSION = "android.permission.ACTIVITY_RECOGNITION";

    // UI components
    private Button sensingButton;
    private Button notesButton;
    private Button settingsButton;
    private TextView resultTextView;
    
    // Feature managers
    private PlacesManager placesManager;
    private ActivityRecognitionManager activityManager;
    private AudioRecorder audioRecorder;
    private WhisperApiClient whisperApiClient;
    private RetrievalManager retrievalManager;
    private AgentApiClient agentApiClient;
    
    // Text-to-Speech
    private TextToSpeech textToSpeech;
    private boolean ttsReady = false;
    
    // Background service
    private BackgroundService backgroundService;
    private boolean serviceBound = false;
    
    // Service connection for binding to the background service
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            BackgroundService.LocalBinder binder = (BackgroundService.LocalBinder) service;
            backgroundService = binder.getService();
            serviceBound = true;
            Log.d(TAG, "Background service connected");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
            Log.d(TAG, "Background service disconnected");
        }
    };
    
    // Data collection state
    private final StringBuilder resultBuilder = new StringBuilder();
    private final AtomicInteger modulesCompleted = new AtomicInteger(0);
    private final AtomicBoolean isRecording = new AtomicBoolean(false);
    
    // Results - these will store the final data
    private List<DetectedActivity> finalActivities = new ArrayList<>();
    private List<PlaceLikelihood> finalPlaces = new ArrayList<>();
    private double audioAmplitude = 0;
    private String transcribedText = "";
    private List<RetrievalManager.SearchResult> retrievalResults = new ArrayList<>();
    private String agentResponse = "";
    
    // Handler for UI updates
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Initialize UI components
        sensingButton = findViewById(R.id.startSensingButton);
        notesButton = findViewById(R.id.notesButton);
        settingsButton = findViewById(R.id.settingsButton);
        resultTextView = findViewById(R.id.resultTextView);
        resultTextView.setMovementMethod(new ScrollingMovementMethod());
        
        // Initialize Text-to-Speech
        textToSpeech = new TextToSpeech(this, this);

        // Initialize feature managers
        String placesApiKey = getString(R.string.google_places_api_key);
        String openaiApiKey = getString(R.string.openai_api_key);
        
        // Get server URL from settings or fallback to strings.xml
        String agentApiUrl = SettingsActivity.getServerUrl(this);
        
        placesManager = new PlacesManager(this, placesApiKey);
        activityManager = new ActivityRecognitionManager(this);
        audioRecorder = new AudioRecorder(this);
        whisperApiClient = new WhisperApiClient(openaiApiKey);
        
        // Initialize retrieval manager with the selected embedding method from settings
        SentenceEncoder.EmbeddingMethod embeddingMethod = SettingsActivity.getEmbeddingMethod(this);
        retrievalManager = new RetrievalManager(this, embeddingMethod);
        Log.d(TAG, "Initialized RetrievalManager with embedding method: " + embeddingMethod);
        
        agentApiClient = new AgentApiClient(agentApiUrl);
        
        // Request notification permission if needed
        requestNotificationPermissionIfNeeded();
        
        // Start the background service
        startBackgroundService();
        
        // Initialize embeddings for all notes in the background
        initializeEmbeddings();

        // Set up button click listeners
        sensingButton.setOnClickListener(v -> {
            if (isRecording.get()) {
                stopSensingAndProcess();
            } else {
                if (checkAndRequestPermissions()) {
                    startSensing();
                }
            }
        });
        
        // Set up notes button click listener
        notesButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, NotesActivity.class);
            startActivity(intent);
        });
        
        // Set up settings button click listener
        settingsButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
        });
    }
    
    /**
     * Request notification permission for Android 13+ (API 33+)
     */
    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                // Request the permission directly
                ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_PERMISSIONS_CODE
                );
                Log.d(TAG, "Explicitly requesting POST_NOTIFICATIONS permission");
            }
        }
    }
    
    /**
     * Start the background service to keep the app running
     */
    private void startBackgroundService() {
        // For Android 13+, check if we have notification permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                // Request the permission first
                ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_PERMISSIONS_CODE
                );
                Log.d(TAG, "Requesting POST_NOTIFICATIONS permission");
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
        
        // Bind to the service
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
        
        Log.d(TAG, "Background service started");
    }
    
    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            // Set language to US English
            int result = textToSpeech.setLanguage(Locale.US);
            
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TAG, "Language not supported");
                Toast.makeText(this, "Text-to-speech language not supported", Toast.LENGTH_SHORT).show();
            } else {
                ttsReady = true;
                Log.d(TAG, "Text-to-speech initialized successfully");
            }
        } else {
            Log.e(TAG, "Text-to-speech initialization failed");
            Toast.makeText(this, "Text-to-speech initialization failed", Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Speak the given text using text-to-speech
     * @param text Text to speak
     */
    private void speak(String text) {
        if (ttsReady && text != null && !text.isEmpty()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "agentResponse");
            } else {
                textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null);
            }
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        
        // Store the current text to restore it later if needed
        CharSequence currentText = resultTextView.getText();
        boolean shouldPreserveText = currentText != null && currentText.length() > 0 && 
                                    !currentText.toString().contains("Ready to start sensing");
        
        // Check if the embedding method has changed and update if needed
        SentenceEncoder.EmbeddingMethod currentMethod = retrievalManager.getEmbeddingMethod();
        SentenceEncoder.EmbeddingMethod selectedMethod = SettingsActivity.getEmbeddingMethod(this);
        
        if (currentMethod != selectedMethod) {
            Log.d(TAG, "Embedding method changed from " + currentMethod + " to " + selectedMethod);
            
            // Update the embedding method
            retrievalManager.setEmbeddingMethod(selectedMethod);
            
            // Regenerate embeddings with the new method
            resultTextView.setText("Updating embeddings with new model: " + selectedMethod);
            shouldPreserveText = false; // Don't preserve text since we're regenerating embeddings
            
            retrievalManager.regenerateAllEmbeddings(() -> {
                runOnUiThread(() -> {
                    if (isRecording.get()) {
                        // If we're recording, don't change the text
                        return;
                    }
                    resultTextView.setText("Ready to start sensing.");
                    Log.d(TAG, "Embeddings regenerated with new method: " + selectedMethod);
                });
            });
        }
        
        // If the server URL has changed, update the AgentApiClient
        String currentUrl = agentApiClient.getServerUrl();
        String newUrl = SettingsActivity.getServerUrl(this);
        
        if (!currentUrl.equals(newUrl)) {
            Log.d(TAG, "Server URL changed from " + currentUrl + " to " + newUrl);
            agentApiClient.setServerUrl(newUrl);
        }
        
        // Only update the text view if we don't need to preserve existing content
        // or if we're not currently recording
        if (!shouldPreserveText && !isRecording.get()) {
            resultTextView.setText("Server URL: " + newUrl + "\n\nReady to start sensing.");
        }
        
        // Ensure the background service is running
        if (!BackgroundService.isServiceRunning()) {
            startBackgroundService();
        }
    }
    
    /**
     * Initialize embeddings for all notes in the database
     */
    private void initializeEmbeddings() {
        // Only update the text if we're not currently recording
        if (!isRecording.get()) {
            resultTextView.setText("Initializing embeddings for notes...");
        }
        
        retrievalManager.generateEmbeddingsForAllNotes(() -> {
            // This runs when embedding generation is complete
            runOnUiThread(() -> {
                // Only update the text if we're not currently recording
                if (!isRecording.get()) {
                    // Check if the current text indicates we're displaying results
                    CharSequence currentText = resultTextView.getText();
                    boolean isDisplayingResults = currentText != null && 
                                               currentText.toString().contains("=== SENSING RESULTS ===");
                    
                    if (!isDisplayingResults) {
                        resultTextView.setText("Ready to start sensing.");
                    }
                }
                Log.d(TAG, "Embeddings initialized for all notes");
            });
        });
    }

    private boolean checkAndRequestPermissions() {
        List<String> permissionsNeeded = new ArrayList<>();

        // Check standard permissions
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(permission);
            }
        }
        
        // Check for activity recognition permission on Android 10+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, ACTIVITY_RECOGNITION_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(ACTIVITY_RECOGNITION_PERMISSION);
            }
        }
        
        // Check for POST_NOTIFICATIONS permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }

        if (!permissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsNeeded.toArray(new String[0]), REQUEST_PERMISSIONS_CODE);
            return false;
        }

        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS_CODE) {
            boolean allGranted = true;
            boolean notificationPermissionGranted = false;
            
            // Check which permissions were granted
            for (int i = 0; i < permissions.length; i++) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && 
                           Manifest.permission.POST_NOTIFICATIONS.equals(permissions[i])) {
                    notificationPermissionGranted = true;
                }
            }

            // If notification permission was granted, start the background service
            if (notificationPermissionGranted) {
                Log.d(TAG, "POST_NOTIFICATIONS permission granted, starting background service");
                startBackgroundService();
            }
            
            // If all permissions were granted, start sensing
            if (allGranted) {
                startSensing();
            } else {
                Toast.makeText(this, "All permissions are required to use this feature", Toast.LENGTH_LONG).show();
                // Show which permissions are missing
                StringBuilder missingPermissions = new StringBuilder("Missing permissions: ");
                for (int i = 0; i < permissions.length; i++) {
                    if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                        missingPermissions.append(permissions[i]).append(", ");
                    }
                }
                Log.e(TAG, missingPermissions.toString());
            }
        }
    }

    private void startSensing() {
        // Double-check permissions before starting
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, ACTIVITY_RECOGNITION_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Activity recognition permission is required", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Location permission is required", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Audio recording permission is required", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Reset data
        finalActivities.clear();
        finalPlaces.clear();
        audioAmplitude = 0;
        transcribedText = "";
        retrievalResults.clear();
        agentResponse = "";
        modulesCompleted.set(0);
        
        // Update UI
        resultTextView.setText("Starting sensing...");
        sensingButton.setText("Stop Sensing");
        isRecording.set(true);
        
        // Update background service notification
        if (serviceBound && backgroundService != null) {
            backgroundService.updateNotification("Collecting context data...");
        }
        
        // Get sensing settings
        boolean enableActivityRecognition = SettingsActivity.isActivityRecognitionEnabled(this);
        boolean enablePlaceData = SettingsActivity.isPlaceDataEnabled(this);
        boolean enableAudioNoisiness = SettingsActivity.isAudioNoisinessEnabled(this);
        
        // Start collecting data based on settings
        if (enableActivityRecognition) {
            collectActivityData();
        } else {
            // Skip activity recognition
            Log.d(TAG, "Activity recognition disabled in settings");
            modulesCompleted.incrementAndGet();
            updateStatus();
        }
        
        if (enablePlaceData) {
            collectPlaceData();
        } else {
            // Skip place data collection
            Log.d(TAG, "Place data collection disabled in settings");
            modulesCompleted.incrementAndGet();
            updateStatus();
        }
        
        if (enableAudioNoisiness) {
            startAudioRecording();
        } else {
            // Skip audio recording but still allow speech input
            Log.d(TAG, "Audio noisiness measurement disabled in settings");
            // We'll still need to record audio for speech transcription, but won't use amplitude
        }
    }
    
    private void stopSensingAndProcess() {
        // Update UI
        sensingButton.setText("Start Sensing");
        sensingButton.setEnabled(false); // Disable the button until agent response comes back
        isRecording.set(false);
        
        // Update background service notification
        if (serviceBound && backgroundService != null) {
            backgroundService.updateNotification("Processing audio data...");
        }
        
        // Stop audio recording
        if (audioRecorder != null) {
            audioRecorder.stopRecording();
        }
        
        // Get sensing settings
        boolean enableAudioNoisiness = SettingsActivity.isAudioNoisinessEnabled(this);
        
        // If audio noisiness is disabled, set amplitude to 0
        if (!enableAudioNoisiness) {
            audioAmplitude = 0;
        }
        
        // Process the recorded audio with Whisper API
        processAudioWithWhisper();
    }

    private void collectActivityData() {
        // Show that we're collecting activity data
        updateStatus();
        
        activityManager.requestActivityUpdates(new ActivityRecognitionManager.ActivityRecognitionCallback() {
            @Override
            public void onActivitiesDetected(List<DetectedActivity> activities) {
                finalActivities = new ArrayList<>(activities);
                Log.d(TAG, "Activity data collected: " + activities.size() + " activities");
                
                // Mark this module as completed
                modulesCompleted.incrementAndGet();
                updateStatus();
            }

            @Override
            public void onActivityRecognitionError(Exception e) {
                Log.e(TAG, "Activity recognition error", e);
                Toast.makeText(MainActivity.this, "Activity recognition error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                
                // Mark this module as completed even on error
                modulesCompleted.incrementAndGet();
                updateStatus();
            }
        });
    }

    private void collectPlaceData() {
        // Show that we're collecting place data
        updateStatus();
        
        placesManager.getCurrentPlace(new PlacesManager.PlacesCallback() {
            @Override
            public void onPlacesDetected(List<PlaceLikelihood> places) {
                finalPlaces = new ArrayList<>(places);
                Log.d(TAG, "Place data collected: " + places.size() + " places");
                
                // Mark this module as completed
                modulesCompleted.incrementAndGet();
                updateStatus();
            }

            @Override
            public void onPlacesError(Exception e) {
                Log.e(TAG, "Places API error", e);
                Toast.makeText(MainActivity.this, "Places API error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                
                // Mark this module as completed even on error
                modulesCompleted.incrementAndGet();
                updateStatus();
            }
        });
    }

    private void startAudioRecording() {
        // Start continuous audio recording
        audioRecorder.startContinuousRecording(new AudioRecorder.AudioRecorderCallback() {
            @Override
            public void onRecordingComplete(double amplitude) {
                // This will be called when stopRecording is called
                audioAmplitude = amplitude;
                Log.d(TAG, "Audio recording completed with amplitude: " + amplitude);
            }

            @Override
            public void onRecordingError(Exception e) {
                Log.e(TAG, "Audio recording error", e);
                Toast.makeText(MainActivity.this, "Audio recording error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    private void processAudioWithWhisper() {
        // Get the recorded audio file
        File audioFile = audioRecorder.getAudioFile();
        
        if (audioFile == null || !audioFile.exists()) {
            Log.e(TAG, "Audio file not found");
            Toast.makeText(this, "Audio file not found", Toast.LENGTH_SHORT).show();
            displayResults();
            sensingButton.setEnabled(true); // Re-enable the button on error
            return;
        }
        
        // Send the audio file to Whisper API
        whisperApiClient.transcribeAudio(audioFile, new WhisperApiClient.TranscriptionCallback() {
            @Override
            public void onTranscriptionComplete(String text) {
                transcribedText = text;
                Log.d(TAG, "Transcription complete: " + text);
                
                // Perform retrieval using the transcribed text
                performRetrieval(text);
            }
            
            @Override
            public void onTranscriptionError(Exception e) {
                Log.e(TAG, "Transcription error", e);
                
                // Update UI on the main thread
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Transcription error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    transcribedText = "Error during transcription: " + e.getMessage();
                    displayResults();
                    sensingButton.setEnabled(true); // Re-enable the button on error
                });
            }
        });
    }
    
    private void performRetrieval(String query) {
        if (query == null || query.isEmpty()) {
            // No query to search with
            displayResults();
            sensingButton.setEnabled(true); // Re-enable the button if there's no query
            return;
        }
        
        // Check if retrieval is enabled in settings
        boolean retrievalEnabled = SettingsActivity.isRetrievalEnabled(this);
        if (!retrievalEnabled) {
            Log.d(TAG, "Note retrieval disabled in settings");
            retrievalResults = new ArrayList<>(); // Empty list
            
            // Display results before sending to server
            runOnUiThread(() -> {
                displayResults(); // Use displayResults instead of displayCollectedData
                resultTextView.append("\n\nSending data to agent API...");
            });
            
            // Send data to agent API without retrieval results
            sendDataToAgentApi();
            return;
        }
        
        // Get the maximum number of retrieval results from settings
        int maxResults = SettingsActivity.getMaxRetrievalResults(this);
        Log.d(TAG, "Performing retrieval with max results: " + maxResults);
        
        // Perform retrieval with the configured max results
        retrievalManager.search(query, maxResults, new RetrievalManager.SearchCallback() {
            @Override
            public void onSearchComplete(List<RetrievalManager.SearchResult> results) {
                retrievalResults = new ArrayList<>(results);
                Log.d(TAG, "Retrieval complete: " + results.size() + " results");
                
                // Display results before sending to server
                runOnUiThread(() -> {
                    displayResults(); // Use displayResults instead of displayCollectedData
                    resultTextView.append("\n\nSending data to agent API...");
                });
                
                // Send data to agent API
                sendDataToAgentApi();
            }
            
            @Override
            public void onSearchError(Exception e) {
                Log.e(TAG, "Retrieval error", e);
                
                // Update UI on the main thread
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Retrieval error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    displayResults();
                    sensingButton.setEnabled(true); // Re-enable the button on error
                });
            }
        });
    }
    
    // displayCollectedData method removed - now using displayResults instead
    
    /**
     * Send the collected data to the agent API
     */
    private void sendDataToAgentApi() {
        // Update background service notification
        if (serviceBound && backgroundService != null) {
            backgroundService.updateNotification("Sending data to agent API...");
        }
        
        // Get sensing settings
        boolean enableActivityRecognition = SettingsActivity.isActivityRecognitionEnabled(this);
        boolean enablePlaceData = SettingsActivity.isPlaceDataEnabled(this);
        boolean enableAudioNoisiness = SettingsActivity.isAudioNoisinessEnabled(this);
        
        // Format the instruction with all collected data
        String formattedInstruction = agentApiClient.formatInstruction(
                transcribedText,
                enableActivityRecognition ? finalActivities : new ArrayList<>(),
                enablePlaceData ? finalPlaces : new ArrayList<>(),
                enableAudioNoisiness ? audioAmplitude : 0,
                retrievalResults
        );
        
        // Send the instruction to the agent API
        agentApiClient.sendInstruction(formattedInstruction, new AgentApiClient.ApiCallback() {
            @Override
            public void onSuccess(String result) {
                agentResponse = result;
                Log.d(TAG, "Agent API response: " + result);
                
                // Update background service notification
                if (serviceBound && backgroundService != null) {
                    backgroundService.updateNotification("Agent response received");
                }
                
                // Update UI on the main thread
                runOnUiThread(() -> {
                    displayResultsWithAgentResponse();
                    sensingButton.setEnabled(true);
                    
                    // Speak the agent response
                    speak(agentResponse);
                });
            }
            
            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Agent API error", e);
                
                // Format a more user-friendly error message
                String errorMessage;
                if (e instanceof java.net.UnknownServiceException) {
                    errorMessage = "Network security error: CLEARTEXT communication not permitted. Please check your network security settings.";
                } else if (e instanceof java.net.UnknownHostException) {
                    errorMessage = "Cannot connect to server. Please check the server IP address and network connection.";
                } else if (e instanceof java.net.ConnectException) {
                    errorMessage = "Connection refused. Please verify the server is running and the port is correct.";
                } else if (e instanceof java.net.SocketTimeoutException) {
                    errorMessage = "Connection timed out. The server might be slow or unreachable.";
                } else {
                    errorMessage = e.getMessage();
                }
                
                final String finalErrorMessage = errorMessage;
                
                // Update background service notification
                if (serviceBound && backgroundService != null) {
                    backgroundService.updateNotification("Error: " + finalErrorMessage);
                }
                
                // Update UI on the main thread
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Agent API error: " + finalErrorMessage, Toast.LENGTH_LONG).show();
                    agentResponse = "Error contacting agent: " + finalErrorMessage;
                    displayResultsWithAgentResponse();
                    sensingButton.setEnabled(true);
                    
                    // Speak the error message
                    speak("Error contacting agent: " + finalErrorMessage);
                });
            }
        });
    }
    
    /**
     * Display the final results with agent response
     */
    private void displayResultsWithAgentResponse() {
        // Check if the current text already contains the sensing results
        CharSequence currentText = resultTextView.getText();
        boolean hasResults = currentText != null && 
                           currentText.toString().contains("=== SENSING RESULTS ===");
        
        if (!hasResults) {
            // If the results are not displayed, show them first
            displayResults();
        } else {
            // Replace "Sending data to agent API..." with the actual response
            String currentTextStr = currentText.toString();
            if (currentTextStr.contains("Sending data to agent API...")) {
                // Replace the "Sending data..." text with the agent response
                String updatedText = currentTextStr.replace("Sending data to agent API...", 
                                                          "AGENT RESPONSE:\n" + agentResponse);
                resultTextView.setText(updatedText);
            } else if (!currentTextStr.contains("AGENT RESPONSE:")) {
                // If results are displayed but agent response is not, append it
                resultTextView.append("\n\nAGENT RESPONSE:\n" + agentResponse);
            } else {
                // If the text already contains an agent response, update it
                // First find where the agent response starts
                int agentResponseIndex = currentTextStr.indexOf("AGENT RESPONSE:");
                if (agentResponseIndex >= 0) {
                    // Replace the old agent response with the new one
                    String textBeforeResponse = currentTextStr.substring(0, agentResponseIndex);
                    resultTextView.setText(textBeforeResponse + "AGENT RESPONSE:\n" + agentResponse);
                } else {
                    // Fallback: just display everything
                    displayResults();
                }
            }
        }
    }
    
    private void updateStatus() {
        int completed = modulesCompleted.get();
        
        // Check if we're already displaying results
        CharSequence currentText = resultTextView.getText();
        boolean isDisplayingResults = currentText != null && 
                                     currentText.toString().contains("=== SENSING RESULTS ===");
        
        // Only update status if we're not already displaying results
        if (!isDisplayingResults) {
            // Update status based on how many modules have completed
            if (completed == 0) {
                resultTextView.setText("Collecting context data...");
            } else if (completed == 1) {
                resultTextView.setText("Collecting context data... (1/2 completed)");
            } else if (completed >= 2) {
                resultTextView.setText("Context data collected. You can speak now and press the button when done.");
            }
        }
    }

    private void displayResults() {
        resultBuilder.setLength(0);
        resultBuilder.append("=== SENSING RESULTS ===\n\n");

        // Get sensing settings
        boolean enableActivityRecognition = SettingsActivity.isActivityRecognitionEnabled(this);
        boolean enablePlaceData = SettingsActivity.isPlaceDataEnabled(this);
        boolean enableAudioNoisiness = SettingsActivity.isAudioNoisinessEnabled(this);
        boolean enableRetrieval = SettingsActivity.isRetrievalEnabled(this);
        
        // Add activity data if enabled
        if (enableActivityRecognition) {
            resultBuilder.append(ActivityRecognitionManager.formatActivityData(finalActivities));
            resultBuilder.append("\n");
        } else {
            resultBuilder.append("Activity recognition disabled\n\n");
        }
        
        // Add place data if enabled
        if (enablePlaceData) {
            resultBuilder.append(PlacesManager.formatPlaceData(finalPlaces));
            resultBuilder.append("\n");
        } else {
            resultBuilder.append("Place data collection disabled\n\n");
        }
        
        // Add audio data if enabled
        if (enableAudioNoisiness) {
            resultBuilder.append(AudioRecorder.formatAudioData(audioAmplitude));
            resultBuilder.append("\n");
        } else {
            resultBuilder.append("Audio noisiness measurement disabled\n\n");
        }
        
        // Add transcribed text
        resultBuilder.append("TRANSCRIBED TEXT:\n");
        if (!transcribedText.isEmpty()) {
            resultBuilder.append(transcribedText);
        } else {
            resultBuilder.append("No transcription available");
        }
        resultBuilder.append("\n\n");
        
        // Add retrieval results if enabled
        if (enableRetrieval) {
            resultBuilder.append(RetrievalManager.formatSearchResults(retrievalResults));
        } else {
            resultBuilder.append("Note retrieval disabled\n\n");
        }
        
        // Add agent response if available
        if (!agentResponse.isEmpty()) {
            resultBuilder.append("\n\nAGENT RESPONSE:\n");
            resultBuilder.append(agentResponse);
        }

        // Update the UI
        resultTextView.setText(resultBuilder.toString());
        
        // Save this state as the final result
        Log.d(TAG, "Final results displayed");
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // Ensure audio recorder is stopped
        audioRecorder.stopRecording();
        activityManager.stopActivityUpdates();
        
        // Close retrieval manager
        if (retrievalManager != null) {
            retrievalManager.close();
        }
        
        // Shutdown text-to-speech
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        
        // Unbind from the service but don't stop it
        if (serviceBound) {
            unbindService(serviceConnection);
            serviceBound = false;
        }
    }
}