package com.example.capma.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = {TextNote.class}, version = 3, exportSchema = false)
@TypeConverters({FloatArrayConverter.class})
public abstract class AppDatabase extends RoomDatabase {
    private static final String DATABASE_NAME = "capma_db";
    private static AppDatabase instance;
    
    public abstract TextNoteDao textNoteDao();
    
    // Migration from version 1 to 2 (adding embedding column)
    private static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // Add embedding column to text_notes table
            database.execSQL("ALTER TABLE text_notes ADD COLUMN embedding BLOB");
        }
    };
    
    // Migration from version 2 to 3 (adding pinned column)
    private static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // Add pinned column to text_notes table with default value of 0 (false)
            database.execSQL("ALTER TABLE text_notes ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0");
        }
    };
    
    public static synchronized AppDatabase getInstance(Context context) {
        if (instance == null) {
            instance = Room.databaseBuilder(
                    context.getApplicationContext(),
                    AppDatabase.class,
                    DATABASE_NAME)
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .fallbackToDestructiveMigration()
                    .build();
        }
        return instance;
    }
} 