package com.example.capma.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.capma.R;
import com.example.capma.data.TextNote;

import java.util.List;

public class NoteAdapter extends RecyclerView.Adapter<NoteAdapter.NoteViewHolder> {
    
    private List<TextNote> notes;
    private final OnNoteClickListener listener;
    
    public interface OnNoteClickListener {
        void onEditClick(TextNote note);
        void onDeleteClick(TextNote note);
        void onPinClick(TextNote note);
    }
    
    public NoteAdapter(List<TextNote> notes, OnNoteClickListener listener) {
        this.notes = notes;
        this.listener = listener;
    }
    
    public void updateNotes(List<TextNote> notes) {
        this.notes = notes;
        notifyDataSetChanged();
    }
    
    @NonNull
    @Override
    public NoteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_note, parent, false);
        return new NoteViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull NoteViewHolder holder, int position) {
        TextNote note = notes.get(position);
        holder.textViewNote.setText(note.getText());
        
        // Set the pin icon based on the note's pinned status
        if (note.isPinned()) {
            holder.buttonPin.setImageResource(R.drawable.ic_pin);
            holder.buttonPin.setContentDescription("Unpin note");
        } else {
            holder.buttonPin.setImageResource(R.drawable.ic_unpin);
            holder.buttonPin.setContentDescription("Pin note");
        }
        
        holder.buttonPin.setOnClickListener(v -> listener.onPinClick(note));
        holder.buttonEdit.setOnClickListener(v -> listener.onEditClick(note));
        holder.buttonDelete.setOnClickListener(v -> listener.onDeleteClick(note));
    }
    
    @Override
    public int getItemCount() {
        return notes != null ? notes.size() : 0;
    }
    
    static class NoteViewHolder extends RecyclerView.ViewHolder {
        TextView textViewNote;
        ImageButton buttonPin;
        ImageButton buttonEdit;
        ImageButton buttonDelete;
        
        public NoteViewHolder(@NonNull View itemView) {
            super(itemView);
            textViewNote = itemView.findViewById(R.id.textViewNote);
            buttonPin = itemView.findViewById(R.id.buttonPin);
            buttonEdit = itemView.findViewById(R.id.buttonEdit);
            buttonDelete = itemView.findViewById(R.id.buttonDelete);
        }
    }
} 