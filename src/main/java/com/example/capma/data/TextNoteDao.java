package com.example.capma.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface TextNoteDao {
    @Insert
    long insert(TextNote note);
    
    @Update
    void update(TextNote note);
    
    @Delete
    void delete(TextNote note);
    
    @Query("SELECT * FROM text_notes ORDER BY timestamp DESC")
    List<TextNote> getAllNotes();
    
    @Query("SELECT * FROM text_notes WHERE id = :id")
    TextNote getNoteById(int id);
    
    /**
     * Get all notes that have embeddings (for similarity search)
     * @return List of notes with embeddings
     */
    @Query("SELECT * FROM text_notes WHERE embedding IS NOT NULL")
    List<TextNote> getNotesWithEmbeddings();
    
    /**
     * Get all pinned notes
     * @return List of pinned notes
     */
    @Query("SELECT * FROM text_notes WHERE pinned = 1")
    List<TextNote> getPinnedNotes();
    
    /**
     * Get all notes with embeddings that are not pinned (for similarity search)
     * @return List of unpinned notes with embeddings
     */
    @Query("SELECT * FROM text_notes WHERE embedding IS NOT NULL AND pinned = 0")
    List<TextNote> getUnpinnedNotesWithEmbeddings();
} 