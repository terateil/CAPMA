package com.example.capma.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.capma.R;
import com.example.capma.data.AppDatabase;
import com.example.capma.data.TextNote;
import com.example.capma.data.TextNoteDao;
import com.example.capma.embedding.SentenceEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NotesActivity extends AppCompatActivity implements NoteAdapter.OnNoteClickListener {
    private static final String TAG = "NotesActivity";
    
    private EditText editTextNote;
    private ImageButton buttonAdd;
    private ImageButton buttonUpdate;
    private ImageButton buttonCancel;
    private RecyclerView recyclerViewNotes;
    
    private NoteAdapter adapter;
    private TextNoteDao noteDao;
    private TextNote currentNote;
    private SentenceEncoder encoder;
    
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notes);
        
        // Initialize UI components
        editTextNote = findViewById(R.id.editTextNote);
        buttonAdd = findViewById(R.id.buttonAdd);
        buttonUpdate = findViewById(R.id.buttonUpdate);
        buttonCancel = findViewById(R.id.buttonCancel);
        recyclerViewNotes = findViewById(R.id.recyclerViewNotes);
        
        // Initialize database and encoder
        noteDao = AppDatabase.getInstance(this).textNoteDao();
        encoder = new SentenceEncoder(this);
        
        // Set the embedding method from settings
        SentenceEncoder.EmbeddingMethod embeddingMethod = SettingsActivity.getEmbeddingMethod(this);
        encoder.setEmbeddingMethod(embeddingMethod);
        Log.d(TAG, "Using embedding method from settings: " + embeddingMethod);
        
        encoder.initialize();
        
        // Set up RecyclerView
        recyclerViewNotes.setLayoutManager(new LinearLayoutManager(this));
        adapter = new NoteAdapter(new ArrayList<>(), this);
        recyclerViewNotes.setAdapter(adapter);
        
        // Load notes
        loadNotes();
        
        // Set up button click listeners
        buttonAdd.setOnClickListener(v -> addNote());
        buttonUpdate.setOnClickListener(v -> updateNote());
        buttonCancel.setOnClickListener(v -> cancelEdit());
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        
        // Check if the embedding method has changed and update if needed
        SentenceEncoder.EmbeddingMethod currentMethod = encoder.currentMethod;
        SentenceEncoder.EmbeddingMethod selectedMethod = SettingsActivity.getEmbeddingMethod(this);
        
        if (currentMethod != selectedMethod) {
            Log.d(TAG, "Embedding method changed from " + currentMethod + " to " + selectedMethod);
            
            // Update the embedding method
            encoder.setEmbeddingMethod(selectedMethod);
            encoder.initialize();
        }
    }
    
    private void loadNotes() {
        executor.execute(() -> {
            List<TextNote> notes = noteDao.getAllNotes();
            handler.post(() -> adapter.updateNotes(notes));
        });
    }
    
    private void addNote() {
        String text = editTextNote.getText().toString().trim();
        if (TextUtils.isEmpty(text)) {
            Toast.makeText(this, "Note cannot be empty", Toast.LENGTH_SHORT).show();
            return;
        }
        
        executor.execute(() -> {
            try {
                // Create note with embedding
                float[] embedding = encoder.encode(text);
                TextNote note = new TextNote(text, embedding);
                
                if (embedding != null) {
                    Log.d(TAG, "Created note with embedding of size: " + embedding.length);
                } else {
                    Log.w(TAG, "Created note but embedding generation failed");
                }
                
                noteDao.insert(note);
                handler.post(() -> {
                    editTextNote.setText("");
                    loadNotes();
                    Toast.makeText(NotesActivity.this, "Note added", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                Log.e(TAG, "Error adding note with embedding", e);
                handler.post(() -> {
                    Toast.makeText(NotesActivity.this, "Error adding note: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    
    private void updateNote() {
        if (currentNote == null) return;
        
        String text = editTextNote.getText().toString().trim();
        if (TextUtils.isEmpty(text)) {
            Toast.makeText(this, "Note cannot be empty", Toast.LENGTH_SHORT).show();
            return;
        }
        
        executor.execute(() -> {
            try {
                // Update note text and regenerate embedding
                currentNote.setText(text);
                
                // Generate new embedding for updated text
                float[] embedding = encoder.encode(text);
                currentNote.setEmbedding(embedding);
                
                if (embedding != null) {
                    Log.d(TAG, "Updated note with new embedding of size: " + embedding.length);
                } else {
                    Log.w(TAG, "Updated note but embedding generation failed");
                }
                
                noteDao.update(currentNote);
                handler.post(() -> {
                    editTextNote.setText("");
                    buttonAdd.setEnabled(true);
                    buttonUpdate.setEnabled(false);
                    buttonCancel.setEnabled(false);
                    currentNote = null;
                    loadNotes();
                    Toast.makeText(NotesActivity.this, "Note updated", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                Log.e(TAG, "Error updating note with embedding", e);
                handler.post(() -> {
                    Toast.makeText(NotesActivity.this, "Error updating note: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    
    private void deleteNote(TextNote note) {
        executor.execute(() -> {
            noteDao.delete(note);
            handler.post(() -> {
                loadNotes();
                Toast.makeText(NotesActivity.this, "Note deleted", Toast.LENGTH_SHORT).show();
                
                // If the deleted note was being edited, clear the edit state
                if (currentNote != null && currentNote.getId() == note.getId()) {
                    cancelEdit();
                }
            });
        });
    }
    
    private void togglePinStatus(TextNote note) {
        executor.execute(() -> {
            try {
                // Toggle the pinned status
                boolean newPinnedStatus = !note.isPinned();
                note.setPinned(newPinnedStatus);
                
                // Update the note in the database
                noteDao.update(note);
                
                // Refresh the UI
                handler.post(() -> {
                    loadNotes();
                    String message = newPinnedStatus ? "Note pinned" : "Note unpinned";
                    Toast.makeText(NotesActivity.this, message, Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                Log.e(TAG, "Error toggling pin status", e);
                handler.post(() -> {
                    Toast.makeText(NotesActivity.this, "Error updating note: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    
    private void cancelEdit() {
        editTextNote.setText("");
        buttonAdd.setEnabled(true);
        buttonUpdate.setEnabled(false);
        buttonCancel.setEnabled(false);
        currentNote = null;
    }
    
    @Override
    public void onEditClick(TextNote note) {
        currentNote = note;
        editTextNote.setText(note.getText());
        buttonAdd.setEnabled(false);
        buttonUpdate.setEnabled(true);
        buttonCancel.setEnabled(true);
    }
    
    @Override
    public void onDeleteClick(TextNote note) {
        deleteNote(note);
    }
    
    @Override
    public void onPinClick(TextNote note) {
        togglePinStatus(note);
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (encoder != null) {
            encoder.close();
        }
        executor.shutdown();
    }
} 