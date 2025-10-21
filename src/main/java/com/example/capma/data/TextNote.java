package com.example.capma.data;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;

import java.util.Arrays;

@Entity(tableName = "text_notes")
@TypeConverters(FloatArrayConverter.class)
public class TextNote {
    @PrimaryKey(autoGenerate = true)
    private int id;
    
    private String text;
    private long timestamp;
    private float[] embedding;
    private boolean pinned;
    
    public TextNote(String text) {
        this.text = text;
        this.timestamp = System.currentTimeMillis();
        this.pinned = false;
    }
    
    @Ignore
    public TextNote(String text, float[] embedding) {
        this.text = text;
        this.timestamp = System.currentTimeMillis();
        this.embedding = embedding;
        this.pinned = false;
    }
    
    public int getId() {
        return id;
    }
    
    public void setId(int id) {
        this.id = id;
    }
    
    public String getText() {
        return text;
    }
    
    public void setText(String text) {
        this.text = text;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
    
    public float[] getEmbedding() {
        return embedding;
    }
    
    public void setEmbedding(float[] embedding) {
        this.embedding = embedding;
    }
    
    public boolean isPinned() {
        return pinned;
    }
    
    public void setPinned(boolean pinned) {
        this.pinned = pinned;
    }
    
    /**
     * Calculate cosine similarity between this note's embedding and another embedding
     * @param queryEmbedding The embedding to compare with
     * @return Cosine similarity score (-1 to 1, higher is more similar)
     */
    public float cosineSimilarity(float[] queryEmbedding) {
        if (embedding == null || queryEmbedding == null) {
            return -1f;
        }
        
        // Check if embedding lengths match
        if (embedding.length != queryEmbedding.length) {
            android.util.Log.e("TextNote", "Embedding length mismatch: " + embedding.length + 
                              " vs " + queryEmbedding.length);
            return -1f;
        }
        
        float dotProduct = 0.0f;
        float normA = 0.0f;
        float normB = 0.0f;
        
        for (int i = 0; i < embedding.length; i++) {
            dotProduct += embedding[i] * queryEmbedding[i];
            normA += embedding[i] * embedding[i];
            normB += queryEmbedding[i] * queryEmbedding[i];
        }
        
        if (normA <= 0 || normB <= 0) {
            android.util.Log.w("TextNote", "Zero norm detected - normA: " + normA + ", normB: " + normB);
            return 0;
        }
        
        float similarity = dotProduct / (float) (Math.sqrt(normA) * Math.sqrt(normB));
        
        // Log similarity details for debugging
        android.util.Log.d("TextNote", "Similarity: " + similarity + 
                          " (dotProduct: " + dotProduct + 
                          ", normA: " + normA + 
                          ", normB: " + normB + 
                          ", text: " + (text.length() > 20 ? text.substring(0, 20) + "..." : text) + ")");
        
        return similarity;
    }
} 