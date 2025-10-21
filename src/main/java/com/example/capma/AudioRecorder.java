package com.example.capma;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicBoolean;

import com.example.capma.ui.SettingsActivity;

/**
 * Class for handling audio recording and amplitude calculation
 */
public class AudioRecorder {
    private static final String TAG = "AudioRecorder";
    private static final int SAMPLE_RATE = 44100;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int CHANNELS = 1; // Mono
    private static final int BITS_PER_SAMPLE = 16; // From ENCODING_PCM_16BIT
    
    private final Context context;
    private AudioRecord audioRecord;
    private final AtomicBoolean isRecording = new AtomicBoolean(false);
    private double audioAmplitude = 0;
    private Thread recordingThread;
    private AudioRecorderCallback callback;
    private File audioFile;
    private File wavFile;
    private final Object audioRecordLock = new Object();
    
    /**
     * Interface for receiving audio recording results
     */
    public interface AudioRecorderCallback {
        void onRecordingComplete(double amplitude);
        void onRecordingError(Exception e);
    }
    
    /**
     * Constructor
     * @param context Application context
     */
    public AudioRecorder(Context context) {
        this.context = context;
    }
    
    /**
     * Start recording audio for the specified duration
     * @param durationMs Recording duration in milliseconds
     * @param callback Callback to receive results
     */
    public void startRecording(int durationMs, AudioRecorderCallback callback) {
        if (isRecording.get()) {
            Log.w(TAG, "Recording already in progress");
            return;
        }
        
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) 
                != PackageManager.PERMISSION_GRANTED) {
            callback.onRecordingError(new SecurityException("Audio recording permission not granted"));
            return;
        }
        
        // Start recording in a separate thread
        recordingThread = new Thread(() -> {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);
            
            int bufferSize = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT
            );
            
            if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
                Log.e(TAG, "Invalid buffer size");
                callback.onRecordingError(new RuntimeException("Invalid buffer size"));
                return;
            }
            
            short[] audioBuffer = new short[bufferSize / 2];
            
            try {
                synchronized (audioRecordLock) {
                    audioRecord = new AudioRecord(
                            MediaRecorder.AudioSource.MIC,
                            SAMPLE_RATE,
                            CHANNEL_CONFIG,
                            AUDIO_FORMAT,
                            bufferSize
                    );
                    
                    if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                        Log.e(TAG, "AudioRecord not initialized");
                        callback.onRecordingError(new RuntimeException("AudioRecord not initialized"));
                        return;
                    }
                    
                    isRecording.set(true);
                    audioRecord.startRecording();
                }
                
                // Calculate average amplitude while recording
                long totalAmplitude = 0;
                int readCount = 0;
                long startTime = System.currentTimeMillis();
                
                while (isRecording.get() && (System.currentTimeMillis() - startTime) < durationMs) {
                    synchronized (audioRecordLock) {
                        if (audioRecord == null || !isRecording.get()) {
                            break;
                        }
                        
                        int read = audioRecord.read(audioBuffer, 0, audioBuffer.length);
                        
                        if (read > 0) {
                            // Calculate amplitude
                            long sum = 0;
                            for (int i = 0; i < read; i++) {
                                sum += Math.abs(audioBuffer[i]);
                            }
                            totalAmplitude += sum / read;
                            readCount++;
                        }
                    }
                }
                
                // Calculate average amplitude
                if (readCount > 0) {
                    audioAmplitude = totalAmplitude / readCount;
                }
                
                // Stop recording
                stopRecording();
                
                // Notify callback
                callback.onRecordingComplete(audioAmplitude);
                
            } catch (Exception e) {
                Log.e(TAG, "Error during audio recording", e);
                stopRecording();
                callback.onRecordingError(e);
            }
        });
        
        recordingThread.start();
    }
    
    /**
     * Start continuous recording until explicitly stopped
     * @param callback Callback to receive results
     */
    public void startContinuousRecording(AudioRecorderCallback callback) {
        if (isRecording.get()) {
            Log.w(TAG, "Recording already in progress");
            return;
        }
        
        this.callback = callback;
        
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) 
                != PackageManager.PERMISSION_GRANTED) {
            callback.onRecordingError(new SecurityException("Audio recording permission not granted"));
            return;
        }
        
        // Create a file to save the audio for STT processing
        try {
            audioFile = new File(context.getCacheDir(), "audio_recording.pcm");
            wavFile = new File(context.getCacheDir(), "audio_recording.wav");
            
            if (audioFile.exists()) {
                audioFile.delete();
            }
            if (wavFile.exists()) {
                wavFile.delete();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error creating audio file", e);
            callback.onRecordingError(e);
            return;
        }
        
        // Start recording in a separate thread
        recordingThread = new Thread(() -> {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);
            
            int bufferSize = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT
            );
            
            if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
                Log.e(TAG, "Invalid buffer size");
                callback.onRecordingError(new RuntimeException("Invalid buffer size"));
                return;
            }
            
            short[] audioBuffer = new short[bufferSize / 2];
            
            try {
                synchronized (audioRecordLock) {
                    audioRecord = new AudioRecord(
                            MediaRecorder.AudioSource.MIC,
                            SAMPLE_RATE,
                            CHANNEL_CONFIG,
                            AUDIO_FORMAT,
                            bufferSize
                    );
                    
                    if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                        Log.e(TAG, "AudioRecord not initialized");
                        callback.onRecordingError(new RuntimeException("AudioRecord not initialized"));
                        return;
                    }
                    
                    isRecording.set(true);
                    audioRecord.startRecording();
                }
                
                // Check if audio noisiness measurement is enabled
                boolean measureNoisiness = SettingsActivity.isAudioNoisinessEnabled(context);
                
                // Variables for amplitude calculation
                long totalAmplitude = 0;
                int readCount = 0;
                
                // File output stream for saving audio
                FileOutputStream fos = new FileOutputStream(audioFile);
                
                // Buffer for converting shorts to bytes
                byte[] byteBuffer = new byte[audioBuffer.length * 2];
                
                while (isRecording.get()) {
                    synchronized (audioRecordLock) {
                        if (audioRecord == null || !isRecording.get()) {
                            break;
                        }
                        
                        int read = audioRecord.read(audioBuffer, 0, audioBuffer.length);
                        
                        if (read > 0) {
                            // Convert shorts to bytes for file output
                            ByteBuffer.wrap(byteBuffer)
                                    .order(ByteOrder.LITTLE_ENDIAN)
                                    .asShortBuffer()
                                    .put(audioBuffer, 0, read);
                            
                            // Write to file
                            fos.write(byteBuffer, 0, read * 2);
                            
                            // Calculate amplitude if enabled
                            if (measureNoisiness) {
                                long sum = 0;
                                for (int i = 0; i < read; i++) {
                                    sum += Math.abs(audioBuffer[i]);
                                }
                                totalAmplitude += sum / read;
                                readCount++;
                            }
                        }
                    }
                }
                
                // Close file output stream
                fos.close();
                
                // Calculate average amplitude if enabled
                if (measureNoisiness && readCount > 0) {
                    audioAmplitude = totalAmplitude / readCount;
                } else {
                    audioAmplitude = 0; // Not measuring noisiness
                }
                
                // Convert PCM to WAV
                convertPcmToWav();
                
                // Notify callback
                callback.onRecordingComplete(audioAmplitude);
                
            } catch (Exception e) {
                Log.e(TAG, "Error during audio recording", e);
                stopRecording();
                callback.onRecordingError(e);
            }
        });
        
        recordingThread.start();
    }
    
    /**
     * Convert PCM audio file to WAV format
     */
    private void convertPcmToWav() throws IOException {
        FileInputStream in = null;
        FileOutputStream out = null;
        
        try {
            in = new FileInputStream(audioFile);
            out = new FileOutputStream(wavFile);
            
            // Get PCM file size
            long audioLength = audioFile.length();
            long dataLength = audioLength;
            
            // Calculate WAV file size (44 bytes for header + data)
            long wavLength = 44 + dataLength;
            
            // WAV header (44 bytes)
            byte[] header = new byte[44];
            
            // RIFF chunk descriptor
            header[0] = 'R';  // RIFF header
            header[1] = 'I';
            header[2] = 'F';
            header[3] = 'F';
            header[4] = (byte) (wavLength & 0xff);  // File size in bytes
            header[5] = (byte) ((wavLength >> 8) & 0xff);
            header[6] = (byte) ((wavLength >> 16) & 0xff);
            header[7] = (byte) ((wavLength >> 24) & 0xff);
            header[8] = 'W';  // WAVE format
            header[9] = 'A';
            header[10] = 'V';
            header[11] = 'E';
            
            // "fmt " sub-chunk
            header[12] = 'f';  // 'fmt ' chunk
            header[13] = 'm';
            header[14] = 't';
            header[15] = ' ';
            header[16] = 16;  // 4 bytes: size of 'fmt ' chunk
            header[17] = 0;
            header[18] = 0;
            header[19] = 0;
            header[20] = 1;  // Format = 1 (PCM)
            header[21] = 0;
            header[22] = (byte) CHANNELS;  // Channels
            header[23] = 0;
            header[24] = (byte) (SAMPLE_RATE & 0xff);  // Sample rate
            header[25] = (byte) ((SAMPLE_RATE >> 8) & 0xff);
            header[26] = (byte) ((SAMPLE_RATE >> 16) & 0xff);
            header[27] = (byte) ((SAMPLE_RATE >> 24) & 0xff);
            
            // Calculate byte rate: SampleRate * NumChannels * BitsPerSample/8
            int byteRate = SAMPLE_RATE * CHANNELS * BITS_PER_SAMPLE / 8;
            header[28] = (byte) (byteRate & 0xff);
            header[29] = (byte) ((byteRate >> 8) & 0xff);
            header[30] = (byte) ((byteRate >> 16) & 0xff);
            header[31] = (byte) ((byteRate >> 24) & 0xff);
            
            // Block align: NumChannels * BitsPerSample/8
            header[32] = (byte) (CHANNELS * BITS_PER_SAMPLE / 8);
            header[33] = 0;
            
            // Bits per sample
            header[34] = (byte) BITS_PER_SAMPLE;
            header[35] = 0;
            
            // "data" sub-chunk
            header[36] = 'd';  // 'data' chunk
            header[37] = 'a';
            header[38] = 't';
            header[39] = 'a';
            header[40] = (byte) (dataLength & 0xff);  // Data size in bytes
            header[41] = (byte) ((dataLength >> 8) & 0xff);
            header[42] = (byte) ((dataLength >> 16) & 0xff);
            header[43] = (byte) ((dataLength >> 24) & 0xff);
            
            // Write header
            out.write(header, 0, 44);
            
            // Write audio data
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
            
            Log.d(TAG, "Successfully converted PCM to WAV: " + wavFile.getAbsolutePath());
            
        } finally {
            if (in != null) {
                in.close();
            }
            if (out != null) {
                out.close();
            }
        }
    }
    
    /**
     * Stop recording
     */
    public void stopRecording() {
        // Set isRecording flag to false first to signal all threads to stop
        isRecording.set(false);
        
        synchronized (audioRecordLock) {
            if (audioRecord != null) {
                try {
                    if (audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                        try {
                            audioRecord.stop();
                            Log.d(TAG, "AudioRecord stopped successfully");
                        } catch (IllegalStateException e) {
                            Log.e(TAG, "Error stopping AudioRecord: " + e.getMessage());
                        }
                    }
                    
                    try {
                        audioRecord.release();
                        Log.d(TAG, "AudioRecord released successfully");
                    } catch (Exception e) {
                        Log.e(TAG, "Error releasing AudioRecord: " + e.getMessage());
                    }
                    
                    audioRecord = null;
                } catch (Exception e) {
                    Log.e(TAG, "Error during AudioRecord cleanup: " + e.getMessage());
                }
            }
        }
        
        // Wait for recording thread to finish
        if (recordingThread != null && recordingThread.isAlive()) {
            try {
                recordingThread.join(1000); // Wait up to 1 second
                if (recordingThread.isAlive()) {
                    Log.w(TAG, "Recording thread did not terminate within timeout");
                }
            } catch (InterruptedException e) {
                Log.e(TAG, "Error joining recording thread", e);
            }
        }
    }
    
    /**
     * Get the recorded audio file in WAV format
     * @return The WAV audio file or null if no recording exists
     */
    public File getAudioFile() {
        return wavFile != null && wavFile.exists() ? wavFile : null;
    }
    
    /**
     * Format audio data as readable text
     * @param amplitude Audio amplitude value
     * @return Formatted string
     */
    public static String formatAudioData(double amplitude) {
        StringBuilder builder = new StringBuilder();
        builder.append("AUDIO DATA:\n");
        builder.append("Average amplitude: ").append(amplitude).append("\n");
        
        // Add a simple interpretation of the amplitude
        builder.append("Environment: ");
        if (amplitude < 500) {
            builder.append("Very quiet");
        } else if (amplitude < 2000) {
            builder.append("Quiet");
        } else if (amplitude < 5000) {
            builder.append("Moderate noise");
        } else if (amplitude < 10000) {
            builder.append("Noisy");
        } else {
            builder.append("Very noisy");
        }
        builder.append("\n");
        
        return builder.toString();
    }
} 