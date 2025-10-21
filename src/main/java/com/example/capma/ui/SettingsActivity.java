package com.example.capma.ui;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import com.example.capma.R;
import com.example.capma.embedding.SentenceEncoder;

public class SettingsActivity extends AppCompatActivity {
    private static final String TAG = "SettingsActivity";
    
    // Keys for SharedPreferences
    public static final String KEY_SERVER_IP = "server_ip";
    public static final String KEY_SERVER_PORT = "server_port";
    public static final String KEY_ENABLE_ACTIVITY_RECOGNITION = "enable_activity_recognition";
    public static final String KEY_ENABLE_PLACE_DATA = "enable_place_data";
    public static final String KEY_ENABLE_AUDIO_NOISINESS = "enable_audio_noisiness";
    public static final String KEY_ENABLE_RETRIEVAL = "enable_retrieval";
    public static final String KEY_MAX_RETRIEVAL_RESULTS = "max_retrieval_results";
    public static final String KEY_EMBEDDING_MODEL = "embedding_model";
    
    // Default values
    public static final int DEFAULT_MAX_RETRIEVAL_RESULTS = 5;
    
    // UI components
    private EditText serverIpEditText;
    private EditText serverPortEditText;
    private CheckBox activityRecognitionCheckbox;
    private CheckBox placeDataCheckbox;
    private CheckBox audioNoisinessCheckbox;
    private CheckBox enableRetrievalCheckbox;
    private EditText maxResultsEditText;
    private RadioGroup embeddingModelRadioGroup;
    private RadioButton useRadioButton;
    private RadioButton bertRadioButton;
    private RadioButton openaiRadioButton;
    private Button saveButton;
    
    // SharedPreferences
    private SharedPreferences sharedPreferences;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        
        // Initialize SharedPreferences
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        
        // Initialize UI components
        serverIpEditText = findViewById(R.id.serverIpEditText);
        serverPortEditText = findViewById(R.id.serverPortEditText);
        activityRecognitionCheckbox = findViewById(R.id.activityRecognitionCheckbox);
        placeDataCheckbox = findViewById(R.id.placeDataCheckbox);
        audioNoisinessCheckbox = findViewById(R.id.audioNoisinessCheckbox);
        enableRetrievalCheckbox = findViewById(R.id.enableRetrievalCheckbox);
        maxResultsEditText = findViewById(R.id.maxResultsEditText);
        embeddingModelRadioGroup = findViewById(R.id.embeddingModelRadioGroup);
        useRadioButton = findViewById(R.id.useRadioButton);
        bertRadioButton = findViewById(R.id.bertRadioButton);
        openaiRadioButton = findViewById(R.id.openaiRadioButton);
        saveButton = findViewById(R.id.saveSettingsButton);
        
        // Load current settings
        loadSettings();
        
        // Set up save button click listener
        saveButton.setOnClickListener(v -> saveSettings());
    }
    
    /**
     * Load current settings from SharedPreferences
     */
    private void loadSettings() {
        // Get the current server URL from strings.xml
        String currentUrl = getString(R.string.agent_api_url);
        
        // Parse the URL to get default values
        String defaultIp = "100.78.90.55";
        String defaultPort = "5800";
        
        if (currentUrl != null && currentUrl.startsWith("http://")) {
            // Remove "http://" prefix
            String hostPort = currentUrl.substring(7);
            
            // Split by colon to separate host and port
            String[] parts = hostPort.split(":");
            if (parts.length >= 1) {
                defaultIp = parts[0];
            }
            if (parts.length >= 2) {
                // Remove "/run" suffix if present
                String port = parts[1];
                if (port.contains("/")) {
                    port = port.substring(0, port.indexOf("/"));
                }
                defaultPort = port;
            }
        }
        
        // Get saved values or use defaults
        String serverIp = sharedPreferences.getString(KEY_SERVER_IP, defaultIp);
        String serverPort = sharedPreferences.getString(KEY_SERVER_PORT, defaultPort);
        
        // Set values to UI
        serverIpEditText.setText(serverIp);
        serverPortEditText.setText(serverPort);
        
        // Load sensing settings (default to enabled)
        boolean enableActivityRecognition = sharedPreferences.getBoolean(KEY_ENABLE_ACTIVITY_RECOGNITION, true);
        boolean enablePlaceData = sharedPreferences.getBoolean(KEY_ENABLE_PLACE_DATA, true);
        boolean enableAudioNoisiness = sharedPreferences.getBoolean(KEY_ENABLE_AUDIO_NOISINESS, true);
        
        activityRecognitionCheckbox.setChecked(enableActivityRecognition);
        placeDataCheckbox.setChecked(enablePlaceData);
        audioNoisinessCheckbox.setChecked(enableAudioNoisiness);
        
        // Load retrieval settings
        boolean enableRetrieval = sharedPreferences.getBoolean(KEY_ENABLE_RETRIEVAL, true);
        int maxRetrievalResults = sharedPreferences.getInt(KEY_MAX_RETRIEVAL_RESULTS, DEFAULT_MAX_RETRIEVAL_RESULTS);
        
        enableRetrievalCheckbox.setChecked(enableRetrieval);
        maxResultsEditText.setText(String.valueOf(maxRetrievalResults));
        
        // Load embedding model setting
        String embeddingModel = sharedPreferences.getString(KEY_EMBEDDING_MODEL, SentenceEncoder.EmbeddingMethod.MEDIAPIPE_USE.name());
        if (embeddingModel.equals(SentenceEncoder.EmbeddingMethod.MEDIAPIPE_BERT.name())) {
            bertRadioButton.setChecked(true);
        } else if (embeddingModel.equals(SentenceEncoder.EmbeddingMethod.OPENAI.name())) {
            openaiRadioButton.setChecked(true);
        } else {
            useRadioButton.setChecked(true);
        }
    }
    
    /**
     * Save settings to SharedPreferences
     */
    private void saveSettings() {
        // Get values from UI
        String serverIp = serverIpEditText.getText().toString().trim();
        String serverPort = serverPortEditText.getText().toString().trim();
        boolean enableActivityRecognition = activityRecognitionCheckbox.isChecked();
        boolean enablePlaceData = placeDataCheckbox.isChecked();
        boolean enableAudioNoisiness = audioNoisinessCheckbox.isChecked();
        boolean enableRetrieval = enableRetrievalCheckbox.isChecked();
        
        // Validate input
        if (serverIp.isEmpty()) {
            serverIpEditText.setError("Server IP cannot be empty");
            return;
        }
        
        if (serverPort.isEmpty()) {
            serverPortEditText.setError("Server port cannot be empty");
            return;
        }
        
        // Parse and validate max results
        int maxResults = DEFAULT_MAX_RETRIEVAL_RESULTS;
        String maxResultsStr = maxResultsEditText.getText().toString().trim();
        if (!maxResultsStr.isEmpty()) {
            try {
                maxResults = Integer.parseInt(maxResultsStr);
                if (maxResults < 1) {
                    maxResultsEditText.setError("Value must be at least 1");
                    return;
                }
            } catch (NumberFormatException e) {
                maxResultsEditText.setError("Invalid number");
                return;
            }
        } else {
            maxResultsEditText.setError("Cannot be empty");
            return;
        }
        
        // Get selected embedding model
        String embeddingModel = SentenceEncoder.EmbeddingMethod.MEDIAPIPE_USE.name();
        if (bertRadioButton.isChecked()) {
            embeddingModel = SentenceEncoder.EmbeddingMethod.MEDIAPIPE_BERT.name();
        } else if (openaiRadioButton.isChecked()) {
            embeddingModel = SentenceEncoder.EmbeddingMethod.OPENAI.name();
        }
        
        // Save values to SharedPreferences
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(KEY_SERVER_IP, serverIp);
        editor.putString(KEY_SERVER_PORT, serverPort);
        editor.putBoolean(KEY_ENABLE_ACTIVITY_RECOGNITION, enableActivityRecognition);
        editor.putBoolean(KEY_ENABLE_PLACE_DATA, enablePlaceData);
        editor.putBoolean(KEY_ENABLE_AUDIO_NOISINESS, enableAudioNoisiness);
        editor.putBoolean(KEY_ENABLE_RETRIEVAL, enableRetrieval);
        editor.putInt(KEY_MAX_RETRIEVAL_RESULTS, maxResults);
        editor.putString(KEY_EMBEDDING_MODEL, embeddingModel);
        editor.apply();
        
        // Show success message
        Toast.makeText(this, "Settings saved successfully", Toast.LENGTH_SHORT).show();
        
        // Finish activity
        finish();
    }
    
    /**
     * Get the full server URL from SharedPreferences
     * @param context Context to access SharedPreferences
     * @return Full server URL (e.g., "http://100.78.90.55:5800/run")
     */
    public static String getServerUrl(android.content.Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        
        // Get saved values or use defaults from strings.xml
        String currentUrl = context.getString(R.string.agent_api_url);
        
        // Parse the URL to get default values
        String defaultIp = "100.78.90.55";
        String defaultPort = "5800";
        
        if (currentUrl != null && currentUrl.startsWith("http://")) {
            // Remove "http://" prefix
            String hostPort = currentUrl.substring(7);
            
            // Split by colon to separate host and port
            String[] parts = hostPort.split(":");
            if (parts.length >= 1) {
                defaultIp = parts[0];
            }
            if (parts.length >= 2) {
                // Remove "/run" suffix if present
                String port = parts[1];
                if (port.contains("/")) {
                    port = port.substring(0, port.indexOf("/"));
                }
                defaultPort = port;
            }
        }
        
        String serverIp = prefs.getString(KEY_SERVER_IP, defaultIp);
        String serverPort = prefs.getString(KEY_SERVER_PORT, defaultPort);
        
        return "http://" + serverIp + ":" + serverPort + "/run";
    }
    
    /**
     * Check if activity recognition is enabled
     * @param context Context to access SharedPreferences
     * @return True if activity recognition is enabled, false otherwise
     */
    public static boolean isActivityRecognitionEnabled(android.content.Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean(KEY_ENABLE_ACTIVITY_RECOGNITION, true);
    }
    
    /**
     * Check if place data collection is enabled
     * @param context Context to access SharedPreferences
     * @return True if place data collection is enabled, false otherwise
     */
    public static boolean isPlaceDataEnabled(android.content.Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean(KEY_ENABLE_PLACE_DATA, true);
    }
    
    /**
     * Check if audio noisiness measurement is enabled
     * @param context Context to access SharedPreferences
     * @return True if audio noisiness measurement is enabled, false otherwise
     */
    public static boolean isAudioNoisinessEnabled(android.content.Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean(KEY_ENABLE_AUDIO_NOISINESS, true);
    }
    
    /**
     * Check if retrieval is enabled
     * @param context Context to access SharedPreferences
     * @return True if retrieval is enabled, false otherwise
     */
    public static boolean isRetrievalEnabled(android.content.Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean(KEY_ENABLE_RETRIEVAL, true);
    }
    
    /**
     * Get the maximum number of retrieval results
     * @param context Context to access SharedPreferences
     * @return Maximum number of retrieval results
     */
    public static int getMaxRetrievalResults(android.content.Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getInt(KEY_MAX_RETRIEVAL_RESULTS, DEFAULT_MAX_RETRIEVAL_RESULTS);
    }
    
    /**
     * Get the selected embedding model method
     * @param context Context to access SharedPreferences
     * @return The selected embedding method
     */
    public static SentenceEncoder.EmbeddingMethod getEmbeddingMethod(android.content.Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String methodName = prefs.getString(KEY_EMBEDDING_MODEL, SentenceEncoder.EmbeddingMethod.MEDIAPIPE_USE.name());
        
        try {
            return SentenceEncoder.EmbeddingMethod.valueOf(methodName);
        } catch (IllegalArgumentException e) {
            // Default to USE if there's an error
            return SentenceEncoder.EmbeddingMethod.MEDIAPIPE_USE;
        }
    }
} 