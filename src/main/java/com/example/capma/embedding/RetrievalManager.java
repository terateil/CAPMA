package com.example.capma.embedding;

import android.content.Context;
import android.util.Log;

import com.example.capma.data.AppDatabase;
import com.example.capma.data.TextNote;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Manages retrieval operations using embeddings
 */
public class RetrievalManager {
    private static final String TAG = "RetrievalManager";
    private static final int DEFAULT_TOP_K = 3;
    
    private final Context context;
    private final SentenceEncoder encoder;
    private final AppDatabase database;
    private final ExecutorService executor;
    
    /**
     * Constructor with default embedding method (USE)
     */
    public RetrievalManager(Context context) {
        this(context, SentenceEncoder.EmbeddingMethod.MEDIAPIPE_USE);
    }
    
    /**
     * Constructor with specified embedding method
     */
    public RetrievalManager(Context context, SentenceEncoder.EmbeddingMethod embeddingMethod) {
        this.context = context;
        this.encoder = new SentenceEncoder(context);
        this.database = AppDatabase.getInstance(context);
        this.executor = Executors.newSingleThreadExecutor();
        
        // Set embedding method
        encoder.setEmbeddingMethod(embeddingMethod);
        
        // Initialize encoder
        encoder.initialize();
        
        Log.d(TAG, "RetrievalManager initialized with embedding method: " + embeddingMethod);
    }
    
    /**
     * Change the embedding method
     * @param embeddingMethod The new embedding method to use
     */
    public void setEmbeddingMethod(SentenceEncoder.EmbeddingMethod embeddingMethod) {
        encoder.setEmbeddingMethod(embeddingMethod);
        encoder.initialize();
        Log.d(TAG, "Changed embedding method to: " + embeddingMethod);
    }
    
    /**
     * Get the current embedding method
     * @return The current embedding method
     */
    public SentenceEncoder.EmbeddingMethod getEmbeddingMethod() {
        return encoder.currentMethod;
    }
    
    /**
     * Represents a search result with the note and its similarity score
     */
    public static class SearchResult {
        private final TextNote note;
        private final float score;
        
        public SearchResult(TextNote note, float score) {
            this.note = note;
            this.score = score;
        }
        
        public TextNote getNote() {
            return note;
        }
        
        public float getScore() {
            return score;
        }
    }
    
    /**
     * Interface for receiving search results
     */
    public interface SearchCallback {
        void onSearchComplete(List<SearchResult> results);
        void onSearchError(Exception e);
    }
    
    /**
     * Generate embeddings for all notes that don't have them yet
     * @param callback Callback to be notified when the process is complete
     */
    public void generateEmbeddingsForAllNotes(final Runnable callback) {
        executor.execute(() -> {
            try {
                Log.d(TAG, "Starting to generate embeddings for notes without embeddings");
                
                // Get all notes
                List<TextNote> allNotes = database.textNoteDao().getAllNotes();
                int processedCount = 0;
                
                for (TextNote note : allNotes) {
                    // Skip notes that already have embeddings
                    if (note.getEmbedding() != null) {
                        continue;
                    }
                    
                    // Generate embedding for this note
                    String text = note.getText();
                    if (text != null && !text.isEmpty()) {
                        float[] embedding = encoder.encode(text);
                        if (embedding != null) {
                            // Update the note with the new embedding
                            note.setEmbedding(embedding);
                            database.textNoteDao().update(note);
                            processedCount++;
                            Log.d(TAG, "Generated embedding for note: " + note.getId());
                        }
                    }
                }
                
                Log.d(TAG, "Finished generating embeddings for " + processedCount + " notes");
                
                // Notify that we're done
                if (callback != null) {
                    callback.run();
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error generating embeddings", e);
                // Still call the callback even on error
                if (callback != null) {
                    callback.run();
                }
            }
        });
    }
    
    /**
     * Regenerate embeddings for all notes using the current embedding method
     * This is useful when switching between embedding methods
     * @param callback Callback to be notified when the process is complete
     */
    public void regenerateAllEmbeddings(final Runnable callback) {
        executor.execute(() -> {
            try {
                Log.d(TAG, "Starting to regenerate all embeddings with method: " + encoder.currentMethod);
                
                // Get all notes
                List<TextNote> allNotes = database.textNoteDao().getAllNotes();
                int processedCount = 0;
                
                for (TextNote note : allNotes) {
                    // Generate embedding for this note
                    String text = note.getText();
                    if (text != null && !text.isEmpty()) {
                        float[] embedding = encoder.encode(text);
                        if (embedding != null) {
                            // Update the note with the new embedding
                            note.setEmbedding(embedding);
                            database.textNoteDao().update(note);
                            processedCount++;
                            Log.d(TAG, "Regenerated embedding for note: " + note.getId());
                        }
                    }
                }
                
                Log.d(TAG, "Finished regenerating embeddings for " + processedCount + " notes");
                
                // Notify that we're done
                if (callback != null) {
                    callback.run();
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error regenerating embeddings", e);
                // Still call the callback even on error
                if (callback != null) {
                    callback.run();
                }
            }
        });
    }
    
    /**
     * Perform top-k retrieval based on query text
     * @param query The query text
     * @param k Number of results to return
     * @param callback Callback to receive results
     */
    public void search(String query, int k, SearchCallback callback) {
        executor.execute(() -> {
            try {
                Log.d(TAG, "Starting search with query: '" + query + "' using embedding method: " + encoder.currentMethod);
                
                // First, ensure all notes have embeddings
                generateEmbeddingsForAllNotes(() -> {
                    try {
                        // Get query embedding
                        float[] queryEmbedding = encoder.encode(query);
                        if (queryEmbedding == null) {
                            Log.e(TAG, "Failed to encode query: '" + query + "'");
                            callback.onSearchError(new RuntimeException("Failed to encode query"));
                            return;
                        }
                        
                        Log.d(TAG, "Generated query embedding with length: " + queryEmbedding.length);
                        
                        // Get all pinned notes
                        List<TextNote> pinnedNotes = database.textNoteDao().getPinnedNotes();
                        Log.d(TAG, "Retrieved " + pinnedNotes.size() + " pinned notes");
                        
                        // Get all unpinned notes with embeddings
                        List<TextNote> unpinnedNotes = database.textNoteDao().getUnpinnedNotesWithEmbeddings();
                        Log.d(TAG, "Retrieved " + unpinnedNotes.size() + " unpinned notes with embeddings for search");
                        
                        if (unpinnedNotes.isEmpty() && pinnedNotes.isEmpty()) {
                            callback.onSearchComplete(Collections.emptyList());
                            return;
                        }
                        
                        // Check if any note has an embedding with a different dimension
                        // This could happen if the embedding model was changed
                        boolean needsRegeneration = false;
                        for (TextNote note : unpinnedNotes) {
                            float[] noteEmbedding = note.getEmbedding();
                            if (noteEmbedding != null && noteEmbedding.length != queryEmbedding.length) {
                                Log.w(TAG, "Found note with embedding length mismatch: " + 
                                      noteEmbedding.length + " vs query embedding length: " + 
                                      queryEmbedding.length + ". Regenerating all embeddings.");
                                needsRegeneration = true;
                                break;
                            }
                        }
                        
                        // If we need to regenerate embeddings, do it and then retry the search
                        if (needsRegeneration) {
                            regenerateAllEmbeddings(() -> search(query, k, callback));
                            return;
                        }
                        
                        // Calculate similarities for unpinned notes
                        List<SearchResult> unpinnedResults = new ArrayList<>();
                        for (TextNote note : unpinnedNotes) {
                            float similarity = note.cosineSimilarity(queryEmbedding);
                            unpinnedResults.add(new SearchResult(note, similarity));
                        }
                        
                        // Sort unpinned results by similarity (descending)
                        Collections.sort(unpinnedResults, (a, b) -> Float.compare(b.getScore(), a.getScore()));
                        
                        // Take top k from unpinned notes
                        int resultCount = Math.min(k, unpinnedResults.size());
                        List<SearchResult> topKUnpinned = unpinnedResults.subList(0, resultCount);
                        
                        // Create results for pinned notes
                        List<SearchResult> pinnedResults = new ArrayList<>();
                        for (TextNote note : pinnedNotes) {
                            // For pinned notes with embeddings, calculate similarity
                            float similarity = 1.0f; // Default high similarity for pinned notes
                            if (note.getEmbedding() != null) {
                                similarity = note.cosineSimilarity(queryEmbedding);
                                // Ensure pinned notes have a minimum similarity score
                                similarity = Math.max(similarity, 0.5f);
                            }
                            pinnedResults.add(new SearchResult(note, similarity));
                        }
                        
                        // Combine pinned and unpinned results
                        List<SearchResult> finalResults = new ArrayList<>();
                        finalResults.addAll(pinnedResults);
                        finalResults.addAll(topKUnpinned);
                        
                        // Sort the final results by similarity (descending)
                        Collections.sort(finalResults, (a, b) -> {
                            // If both are pinned or both are unpinned, sort by score
                            if (a.getNote().isPinned() == b.getNote().isPinned()) {
                                return Float.compare(b.getScore(), a.getScore());
                            }
                            // Otherwise, pinned notes come first
                            return a.getNote().isPinned() ? -1 : 1;
                        });
                        
                        // Log results for debugging
                        StringBuilder resultLog = new StringBuilder("Search results:\n");
                        for (int i = 0; i < finalResults.size(); i++) {
                            SearchResult result = finalResults.get(i);
                            String noteText = result.getNote().getText();
                            if (noteText.length() > 30) {
                                noteText = noteText.substring(0, 30) + "...";
                            }
                            resultLog.append(i + 1).append(". ");
                            if (result.getNote().isPinned()) {
                                resultLog.append("[PINNED] ");
                                resultLog.append(noteText);
                            } else {
                                resultLog.append("Score: ").append(String.format("%.4f", result.getScore()));
                                resultLog.append(" - ").append(noteText);
                            }
                            resultLog.append("\n");
                        }
                        Log.d(TAG, resultLog.toString());
                        
                        // Return results
                        callback.onSearchComplete(finalResults);
                        
                    } catch (Exception e) {
                        Log.e(TAG, "Error during search", e);
                        callback.onSearchError(e);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Error initiating search", e);
                callback.onSearchError(e);
            }
        });
    }
    
    /**
     * Perform top-k retrieval with default k value
     * @param query The query text
     * @param callback Callback to receive results
     */
    public void search(String query, SearchCallback callback) {
        search(query, DEFAULT_TOP_K, callback);
    }
    
    /**
     * Release resources
     */
    public void close() {
        encoder.close();
        executor.shutdown();
    }
    
    /**
     * Format search results as readable text
     * @param results The search results
     * @return Formatted string
     */
    public static String formatSearchResults(List<SearchResult> results) {
        StringBuilder builder = new StringBuilder();
        builder.append("TOP RETRIEVAL RESULTS:\n");
        
        if (results.isEmpty()) {
            builder.append("No matching notes found.\n");
            return builder.toString();
        }
        
        for (int i = 0; i < results.size(); i++) {
            SearchResult result = results.get(i);
            builder.append(i + 1).append(". ");
            if (result.getNote().isPinned()) {
                builder.append("📌 ");  // Pin emoji to indicate pinned notes
                builder.append(result.getNote().getText());
                // No score for pinned notes
            } else {
                builder.append(result.getNote().getText());
                builder.append(" (Score: ").append(String.format("%.2f", result.getScore())).append(")");
            }
            builder.append("\n");
        }
        
        return builder.toString();
    }
} 