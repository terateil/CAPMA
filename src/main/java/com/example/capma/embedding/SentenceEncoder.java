package com.example.capma.embedding;

import android.content.Context;
import android.util.Log;

import com.example.capma.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

// MediaPipe imports
import com.google.mediapipe.tasks.components.containers.Classifications;
import com.google.mediapipe.tasks.components.containers.Embedding;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder;
import com.google.mediapipe.tasks.text.textembedder.TextEmbedderResult;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Handles encoding sentences to embeddings using either ONNX model or MediaPipe
 */
public class SentenceEncoder {
    private static final String TAG = "SentenceEncoder";
    private static final String ONNX_MODEL_FILE = "sentence_transformer.onnx";
    private static final String MEDIAPIPE_USE_MODEL_FILE = "universal_sentence_encoder.tflite";
    private static final String MEDIAPIPE_BERT_MODEL_FILE = "bert_embedder.tflite";
    private static final int USE_EMBEDDING_DIM = 384; // Universal Sentence Encoder dimension
    private static final int BERT_EMBEDDING_DIM = 512; // BERT embedder dimension
    private static final int OPENAI_EMBEDDING_DIM = 1536; // OpenAI embedding dimension
    private static final String OPENAI_EMBEDDING_API_URL = "https://api.openai.com/v1/embeddings";
    private static final String OPENAI_EMBEDDING_MODEL = "text-embedding-ada-002";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    
    // Embedding method options
    public enum EmbeddingMethod {
        ONNX,
        MEDIAPIPE_USE,  // Universal Sentence Encoder
        MEDIAPIPE_BERT, // BERT embedder
        OPENAI          // OpenAI API embedding
    }
    
    private final Context context;
    private OrtEnvironment environment;
    private OrtSession session;
    private TextEmbedder textEmbedder;
    private OkHttpClient httpClient;
    private String openaiApiKey;
    private boolean isOnnxInitialized = false;
    private boolean isMediaPipeInitialized = false;
    private boolean isOpenAIInitialized = false;
    // Changed to package-private to allow access from RetrievalManager
    public EmbeddingMethod currentMethod = EmbeddingMethod.MEDIAPIPE_USE; // Default to USE
    
    public SentenceEncoder(Context context) {
        this.context = context;
        // Initialize OpenAI API key
        this.openaiApiKey = context.getString(R.string.openai_api_key);
        // Initialize HTTP client for OpenAI API
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }
    
    /**
     * Set the embedding method to use
     * @param method The embedding method (ONNX, MEDIAPIPE_USE, MEDIAPIPE_BERT, or OPENAI)
     */
    public void setEmbeddingMethod(EmbeddingMethod method) {
        // If changing methods, always clean up the current embedder
        if (this.currentMethod != method) {
            Log.d(TAG, "Changing embedding method from " + this.currentMethod + " to " + method);
            
            // Close the current embedder if it's initialized
            if (textEmbedder != null) {
                textEmbedder.close();
                textEmbedder = null;
            }
            
            // Reset initialization flags
            if ((this.currentMethod == EmbeddingMethod.MEDIAPIPE_USE || this.currentMethod == EmbeddingMethod.MEDIAPIPE_BERT) &&
                (method == EmbeddingMethod.MEDIAPIPE_USE || method == EmbeddingMethod.MEDIAPIPE_BERT)) {
                isMediaPipeInitialized = false;
            } else if (this.currentMethod == EmbeddingMethod.ONNX && method != EmbeddingMethod.ONNX) {
                isOnnxInitialized = false;
            } else if (this.currentMethod == EmbeddingMethod.OPENAI && method != EmbeddingMethod.OPENAI) {
                isOpenAIInitialized = false;
            }
            
            this.currentMethod = method;
        }
    }
    
    /**
     * Get the current embedding dimension based on the selected method
     */
    public int getCurrentEmbeddingDimension() {
        switch (currentMethod) {
            case MEDIAPIPE_BERT:
                return BERT_EMBEDDING_DIM;
            case MEDIAPIPE_USE:
                return USE_EMBEDDING_DIM;
            case OPENAI:
                return OPENAI_EMBEDDING_DIM;
            case ONNX:
            default:
                return USE_EMBEDDING_DIM; // Default to USE dimension
        }
    }
    
    /**
     * Initialize the selected embedding model
     * @return true if initialization was successful
     */
    public boolean initialize() {
        switch (currentMethod) {
            case ONNX:
                return initializeOnnx();
            case MEDIAPIPE_USE:
                return initializeMediaPipeUSE();
            case MEDIAPIPE_BERT:
                return initializeMediaPipeBERT();
            case OPENAI:
                return initializeOpenAI();
            default:
                return false;
        }
    }
    
    /**
     * Initialize OpenAI embedding API
     * @return true if initialization was successful
     */
    private boolean initializeOpenAI() {
        // Check if API key is available
        if (openaiApiKey == null || openaiApiKey.isEmpty()) {
            Log.e(TAG, "OpenAI API key not available");
            return false;
        }
        
        // No actual initialization needed for API, just validate the key
        isOpenAIInitialized = true;
        Log.d(TAG, "OpenAI embedding API initialized successfully");
        return true;
    }
    
    /**
     * Initialize the ONNX model
     * @return true if initialization was successful
     */
    private boolean initializeOnnx() {
        if (isOnnxInitialized) return true;
        
        try {
            // Create ONNX Runtime environment
            environment = OrtEnvironment.getEnvironment();
            
            // Load model from assets
            File modelFile = new File(context.getCacheDir(), ONNX_MODEL_FILE);
            if (!modelFile.exists()) {
                // Model needs to be copied from assets
                try {
                    copyModelFromAssets(ONNX_MODEL_FILE);
                } catch (IOException e) {
                    Log.e(TAG, "Failed to copy ONNX model from assets", e);
                    return false;
                }
            }
            
            // Create session
            OrtSession.SessionOptions sessionOptions = new OrtSession.SessionOptions();
            sessionOptions.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
            
            session = environment.createSession(modelFile.getAbsolutePath(), sessionOptions);
            isOnnxInitialized = true;
            
            Log.d(TAG, "ONNX model initialized successfully");
            return true;
            
        } catch (OrtException e) {
            Log.e(TAG, "Failed to initialize ONNX model", e);
            return false;
        }
    }
    
    /**
     * Initialize the MediaPipe Universal Sentence Encoder
     * @return true if initialization was successful
     */
    private boolean initializeMediaPipeUSE() {
        if (isMediaPipeInitialized && currentMethod == EmbeddingMethod.MEDIAPIPE_USE && textEmbedder != null) 
            return true;
        
        // Close any existing embedder
        if (textEmbedder != null) {
            textEmbedder.close();
            textEmbedder = null;
        }
        
        try {
            // Load model from assets
            File modelFile = new File(context.getCacheDir(), MEDIAPIPE_USE_MODEL_FILE);
            if (!modelFile.exists()) {
                // Model needs to be copied from assets
                try {
                    copyModelFromAssets(MEDIAPIPE_USE_MODEL_FILE);
                } catch (IOException e) {
                    Log.e(TAG, "Failed to copy MediaPipe USE model from assets", e);
                    return false;
                }
            }
            
            // Setup the MediaPipe text embedder
            BaseOptions baseOptions = BaseOptions.builder()
                    .setModelAssetPath(modelFile.getAbsolutePath())
                    .build();
                    
            TextEmbedder.TextEmbedderOptions options = TextEmbedder.TextEmbedderOptions.builder()
                    .setBaseOptions(baseOptions)
                    .setL2Normalize(true) // Normalize embeddings
                    .build();
                    
            textEmbedder = TextEmbedder.createFromOptions(context, options);
            isMediaPipeInitialized = true;
            
            Log.d(TAG, "MediaPipe Universal Sentence Encoder initialized successfully");
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize MediaPipe Universal Sentence Encoder", e);
            return false;
        }
    }
    
    /**
     * Initialize the MediaPipe BERT embedder
     * @return true if initialization was successful
     */
    private boolean initializeMediaPipeBERT() {
        if (isMediaPipeInitialized && currentMethod == EmbeddingMethod.MEDIAPIPE_BERT && textEmbedder != null) 
            return true;
        
        // Close any existing embedder
        if (textEmbedder != null) {
            textEmbedder.close();
            textEmbedder = null;
        }
        
        try {
            // Load model from assets
            File modelFile = new File(context.getCacheDir(), MEDIAPIPE_BERT_MODEL_FILE);
            if (!modelFile.exists()) {
                // Model needs to be copied from assets
                try {
                    copyModelFromAssets(MEDIAPIPE_BERT_MODEL_FILE);
                } catch (IOException e) {
                    Log.e(TAG, "Failed to copy MediaPipe BERT model from assets", e);
                    return false;
                }
            }
            
            // Setup the MediaPipe text embedder with BERT configuration
            BaseOptions baseOptions = BaseOptions.builder()
                    .setModelAssetPath(modelFile.getAbsolutePath())
                    .build();
                    
            TextEmbedder.TextEmbedderOptions options = TextEmbedder.TextEmbedderOptions.builder()
                    .setBaseOptions(baseOptions)
                    .setL2Normalize(true) // Keep normalization for cosine similarity
                    .setQuantize(false) // Use full precision for better accuracy
                    .build();
                    
            textEmbedder = TextEmbedder.createFromOptions(context, options);
            isMediaPipeInitialized = true;
            
            Log.d(TAG, "MediaPipe BERT embedder initialized successfully");
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize MediaPipe BERT embedder", e);
            return false;
        }
    }
    
    /**
     * Copy the model file from assets to the app's cache directory
     */
    private void copyModelFromAssets(String modelFileName) throws IOException {
        try (InputStream is = context.getAssets().open(modelFileName)) {
            File outFile = new File(context.getCacheDir(), modelFileName);
            try (FileOutputStream fos = new FileOutputStream(outFile)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }
            }
        }
    }
    
    /**
     * Encode a sentence to an embedding vector using the selected method
     * @param text Text to encode
     * @return Embedding vector or null if encoding failed
     */
    public float[] encode(String text) {
        switch (currentMethod) {
            case ONNX:
                return encodeWithOnnx(text);
            case MEDIAPIPE_USE:
                return encodeWithMediaPipe(text);
            case MEDIAPIPE_BERT:
                return encodeWithMediaPipe(text); // Both USE and BERT use the same encoding method
            case OPENAI:
                return encodeWithOpenAI(text);
            default:
                return null;
        }
    }
    
    /**
     * Encode a sentence using ONNX model
     * @param text Text to encode
     * @return Embedding vector or null if encoding failed
     */
    private float[] encodeWithOnnx(String text) {
        if (!isOnnxInitialized) {
            if (!initializeOnnx()) {
                Log.e(TAG, "ONNX model not initialized");
                return null;
            }
        }
        
        try {
            // Prepare input
            Map<String, OnnxTensor> inputs = new HashMap<>();
            
            // This is a simplified example - in a real implementation, you would need
            // proper tokenization and preprocessing based on your model requirements
            float[] dummyEmbedding = new float[USE_EMBEDDING_DIM];
            Arrays.fill(dummyEmbedding, 0.1f);
            
            // For a real implementation, replace this with actual model inference
            // This is just a placeholder to show the structure
            Log.d(TAG, "Using dummy embedding for ONNX - replace with actual model inference");
            
            return dummyEmbedding;
            
            /* Uncomment and adapt this code when you have the actual model
            
            // Create input tensor (depends on your model's expected input format)
            OnnxTensor inputTensor = OnnxTensor.createTensor(environment, 
                    FloatBuffer.wrap(preprocessedInput), new long[]{1, inputLength});
            inputs.put("input_ids", inputTensor);
            
            // Run inference
            OrtSession.Result result = session.run(inputs);
            
            // Process output (depends on your model's output format)
            float[][] embeddings = (float[][]) result.get(0).getValue();
            
            return embeddings[0];  // Return the embedding vector
            */
            
        } catch (Exception e) {
            Log.e(TAG, "Error during ONNX encoding", e);
            return null;
        }
    }
    
    /**
     * Encode a sentence using MediaPipe text embedder (works for both USE and BERT)
     * @param text Text to encode
     * @return Embedding vector or null if encoding failed
     */
    private float[] encodeWithMediaPipe(String text) {
        // Initialize the appropriate model based on current method
        if (!isMediaPipeInitialized || textEmbedder == null) {
            boolean success = false;
            if (currentMethod == EmbeddingMethod.MEDIAPIPE_USE) {
                success = initializeMediaPipeUSE();
            } else if (currentMethod == EmbeddingMethod.MEDIAPIPE_BERT) {
                success = initializeMediaPipeBERT();
            }
            
            if (!success) {
                Log.e(TAG, "MediaPipe text embedder not initialized");
                return null;
            }
        }
        
        try {
            // Process the text with MediaPipe text embedder
            TextEmbedderResult result = textEmbedder.embed(text);
            
            // Get the embeddings
            if (result.embeddingResult().embeddings().isEmpty()) {
                Log.e(TAG, "No embeddings generated by MediaPipe");
                return null;
            }
            
            // Get the first embedding (usually there's only one)
            Embedding embedding = result.embeddingResult().embeddings().get(0);
            
            // Convert to float array
            float[] embeddingValues = embedding.floatEmbedding();
            float[] embeddingArray = new float[embeddingValues.length];
            for (int i = 0; i < embeddingArray.length; i++) {
                embeddingArray[i] = embeddingValues[i];
            }
            
            String modelType = (currentMethod == EmbeddingMethod.MEDIAPIPE_USE) ? "USE" : "BERT";
            Log.d(TAG, "MediaPipe " + modelType + " embedding generated successfully with dimension: " + embeddingArray.length);
            
            // Log some statistics about the embedding for debugging
            float sum = 0;
            float min = Float.MAX_VALUE;
            float max = Float.MIN_VALUE;
            for (float v : embeddingArray) {
                sum += v;
                min = Math.min(min, v);
                max = Math.max(max, v);
            }
            float mean = sum / embeddingArray.length;
            
            Log.d(TAG, "Embedding stats for " + modelType + " - Min: " + min + ", Max: " + max + 
                  ", Mean: " + mean + ", First 5 values: " + 
                  embeddingArray[0] + ", " + embeddingArray[1] + ", " + embeddingArray[2] + 
                  ", " + embeddingArray[3] + ", " + embeddingArray[4]);
            
            return embeddingArray;
            
        } catch (Exception e) {
            Log.e(TAG, "Error during MediaPipe encoding", e);
            return null;
        }
    }
    
    /**
     * Encode a sentence using OpenAI's embedding API
     * This is a synchronous wrapper around the asynchronous API call
     * @param text Text to encode
     * @return Embedding vector or null if encoding failed
     */
    private float[] encodeWithOpenAI(String text) {
        if (!isOpenAIInitialized) {
            if (!initializeOpenAI()) {
                Log.e(TAG, "OpenAI embedding API not initialized");
                return null;
            }
        }
        
        // Create a holder for the result
        final float[][] resultHolder = new float[1][];
        final boolean[] done = new boolean[1];
        final Exception[] error = new Exception[1];
        
        try {
            // Create JSON payload
            JSONObject jsonBody = new JSONObject();
            jsonBody.put("model", OPENAI_EMBEDDING_MODEL);
            jsonBody.put("input", text);
            
            // Create request
            RequestBody body = RequestBody.create(jsonBody.toString(), JSON);
            Request request = new Request.Builder()
                    .url(OPENAI_EMBEDDING_API_URL)
                    .header("Authorization", "Bearer " + openaiApiKey)
                    .header("Content-Type", "application/json")
                    .post(body)
                    .build();
            
            // Execute request synchronously (on the same thread)
            Response response = httpClient.newCall(request).execute();
            
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                Log.e(TAG, "OpenAI API error: " + response.code() + " - " + errorBody);
                return null;
            }
            
            String responseBody = response.body().string();
            JSONObject jsonResponse = new JSONObject(responseBody);
            
            // Extract embedding from response
            JSONArray data = jsonResponse.getJSONArray("data");
            JSONObject firstItem = data.getJSONObject(0);
            JSONArray embeddingArray = firstItem.getJSONArray("embedding");
            
            // Convert JSON array to float array
            float[] embedding = new float[embeddingArray.length()];
            for (int i = 0; i < embeddingArray.length(); i++) {
                embedding[i] = (float) embeddingArray.getDouble(i);
            }
            
            // Log some statistics about the embedding for debugging
            float sum = 0;
            float min = Float.MAX_VALUE;
            float max = Float.MIN_VALUE;
            for (float v : embedding) {
                sum += v;
                min = Math.min(min, v);
                max = Math.max(max, v);
            }
            float mean = sum / embedding.length;
            
            Log.d(TAG, "OpenAI embedding generated successfully with dimension: " + embedding.length);
            Log.d(TAG, "Embedding stats for OpenAI - Min: " + min + ", Max: " + max + 
                  ", Mean: " + mean + ", First 5 values: " + 
                  embedding[0] + ", " + embedding[1] + ", " + embedding[2] + 
                  ", " + embedding[3] + ", " + embedding[4]);
            
            return embedding;
            
        } catch (Exception e) {
            Log.e(TAG, "Error during OpenAI encoding", e);
            return null;
        }
    }
    
    /**
     * Release resources
     */
    public void close() {
        // Close ONNX resources
        try {
            if (session != null) {
                session.close();
            }
            if (environment != null) {
                environment.close();
            }
            isOnnxInitialized = false;
        } catch (OrtException e) {
            Log.e(TAG, "Error closing ONNX resources", e);
        }
        
        // Close MediaPipe resources
        if (textEmbedder != null) {
            textEmbedder.close();
            isMediaPipeInitialized = false;
        }
        
        // No need to close OpenAI resources as it's just an API call
        isOpenAIInitialized = false;
    }
} 