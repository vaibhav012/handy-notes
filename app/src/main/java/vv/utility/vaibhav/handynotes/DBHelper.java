package vv.utility.vaibhav.handynotes;

/**
 * Created by Vaibhav on 12/2/2015.
 */

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class DBHelper extends SQLiteOpenHelper {

    public static final String DATABASE_NAME = "VVHandyNotes.db";
    public static final String NOTES_TABLE_NAME = "notes";
    public static final String NOTE_ID = "noteId";
    public static final String NOTE_NAME = "noteName";
    public static final String NOTE = "note";
    public static final String DATE_TIME_COLUMN = "dateTime";
    public static final String IS_ONLINE_COLUMN = "isOnline";
    public static final String ONLINE_ID_COLUMN = "onlineId";

    public DBHelper(Context context) {
        super(context, DATABASE_NAME, null, 2);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("create table " + NOTES_TABLE_NAME + "( " 
                + NOTE_ID + " INTEGER PRIMARY KEY, " 
                + NOTE_NAME + " TEXT, " 
                + NOTE + " TEXT, " 
                + DATE_TIME_COLUMN + " TEXT, "
                + IS_ONLINE_COLUMN + " INTEGER DEFAULT 0, "
                + ONLINE_ID_COLUMN + " TEXT );");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            try {
                db.execSQL("ALTER TABLE " + NOTES_TABLE_NAME + " ADD COLUMN " + IS_ONLINE_COLUMN + " INTEGER DEFAULT 0;");
                db.execSQL("ALTER TABLE " + NOTES_TABLE_NAME + " ADD COLUMN " + ONLINE_ID_COLUMN + " TEXT;");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void addNote(int noteId, String name, String note) {
        addNote(noteId, name, note, false, "");
    }

    public void addNote(int noteId, String name, String note, boolean isOnline, String onlineId) {
        SQLiteDatabase db = this.getWritableDatabase();

        Calendar calendar = Calendar.getInstance();
        String dateAndTime = "Date : "+calendar.get(Calendar.DATE)+"/"+calendar.get(Calendar.MONTH)+"/"+calendar.get(Calendar.YEAR)+
                "  Time : "+calendar.get(Calendar.HOUR_OF_DAY)+"."+calendar.get(Calendar.MINUTE)+"."+calendar.get(Calendar.SECOND);

        ContentValues values = new ContentValues();
        values.put(NOTE_ID, noteId);
        values.put(NOTE_NAME, name);
        values.put(NOTE, note);
        values.put(DATE_TIME_COLUMN, dateAndTime);
        values.put(IS_ONLINE_COLUMN, isOnline ? 1 : 0);
        values.put(ONLINE_ID_COLUMN, onlineId != null ? onlineId : "");
        db.insert(NOTES_TABLE_NAME, null, values);
        db.close();
    }

    public int createNoteId() {
        SQLiteDatabase db = this.getReadableDatabase();

        int noteId = 0;

        if (getCount() > 0){
            Cursor cursor = db.rawQuery("SELECT * from notes WHERE 1", null);
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    do {
                        noteId = cursor.getInt(0);
                    } while (cursor.moveToNext());
                }
                cursor.close();
            }
        }
        noteId++;
        return noteId;
    }

    public String getNoteName(int note_id) {
        SQLiteDatabase db = this.getReadableDatabase();

        String noteName = "NULL";

        if (getCount() > 0){
            Cursor cursor = db.rawQuery("SELECT * from notes WHERE 1", null);
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    do {
                        if(cursor.getInt(0) == note_id)
                            noteName = cursor.getString(1);
                    } while (cursor.moveToNext());
                }
                cursor.close();
            }
        }
        return noteName;
    }

    public String getNote(int note_id) {
        SQLiteDatabase db = this.getReadableDatabase();

        String note = "NULL";

        if (getCount() > 0){
            Cursor cursor = db.rawQuery("SELECT * from notes WHERE 1", null);
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    do {
                        if(cursor.getInt(0) == note_id)
                            note = cursor.getString(2);
                    } while (cursor.moveToNext());
                }
                cursor.close();
            }
        }
        return note;
    }

    public String getDateTime(int note_id) {
        SQLiteDatabase db = this.getReadableDatabase();

        String dateTime = "NULL";

        if (getCount() > 0){
            Cursor cursor = db.rawQuery("SELECT * from notes WHERE 1", null);
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    do {
                        if(cursor.getInt(0) == note_id)
                            dateTime = cursor.getString(3);
                    } while (cursor.moveToNext());
                }
                cursor.close();
            }
        }
        return dateTime;
    }

    public boolean isNoteOnline(int note_id) {
        SQLiteDatabase db = this.getReadableDatabase();
        boolean isOnline = false;

        try (Cursor cursor = db.rawQuery("SELECT " + IS_ONLINE_COLUMN + " FROM " + NOTES_TABLE_NAME + " WHERE " + NOTE_ID + " = ?", new String[]{String.valueOf(note_id)})) {
            if (cursor != null && cursor.moveToFirst()) {
                isOnline = cursor.getInt(0) == 1;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return isOnline;
    }

    public String getOnlineId(int note_id) {
        SQLiteDatabase db = this.getReadableDatabase();
        String onlineId = "";

        try (Cursor cursor = db.rawQuery("SELECT " + ONLINE_ID_COLUMN + " FROM " + NOTES_TABLE_NAME + " WHERE " + NOTE_ID + " = ?", new String[]{String.valueOf(note_id)})) {
            if (cursor != null && cursor.moveToFirst()) {
                onlineId = cursor.getString(0);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return onlineId != null ? onlineId : "";
    }

    public void setOnlineStatus(int noteId, boolean isOnline, String onlineId) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(IS_ONLINE_COLUMN, isOnline ? 1 : 0);
        if (onlineId != null) {
            values.put(ONLINE_ID_COLUMN, onlineId);
        }
        db.update(NOTES_TABLE_NAME, values, NOTE_ID + " = " + noteId, null);
    }

    /** Add a fetched server note once, or refresh the local copy already linked to its server UUID. */
    public void upsertOnlineNote(String onlineId, String name, String note) {
        if (onlineId == null || onlineId.trim().isEmpty()) return;
        SQLiteDatabase db = this.getWritableDatabase();
        try {
            db.beginTransaction();
            int localId = -1;
            try (Cursor cursor = db.query(NOTES_TABLE_NAME, new String[]{NOTE_ID},
                    ONLINE_ID_COLUMN + " = ?", new String[]{onlineId}, null, null, null, "1")) {
                if (cursor.moveToFirst()) localId = cursor.getInt(0);
            }

            Calendar calendar = Calendar.getInstance();
            String dateAndTime = "Date : " + calendar.get(Calendar.DATE) + "/" + calendar.get(Calendar.MONTH) + "/" + calendar.get(Calendar.YEAR)
                    + "  Time : " + calendar.get(Calendar.HOUR_OF_DAY) + "." + calendar.get(Calendar.MINUTE) + "." + calendar.get(Calendar.SECOND);
            ContentValues values = new ContentValues();
            values.put(NOTE_NAME, name == null ? "Untitled Note" : name);
            values.put(NOTE, note == null ? "" : note);
            values.put(DATE_TIME_COLUMN, dateAndTime);
            values.put(IS_ONLINE_COLUMN, 1);
            values.put(ONLINE_ID_COLUMN, onlineId);

            if (localId >= 0) {
                db.update(NOTES_TABLE_NAME, values, NOTE_ID + " = ?", new String[]{String.valueOf(localId)});
            } else {
                try (Cursor cursor = db.rawQuery("SELECT COALESCE(MAX(" + NOTE_ID + "), 0) + 1 FROM " + NOTES_TABLE_NAME, null)) {
                    localId = cursor.moveToFirst() ? cursor.getInt(0) : 1;
                }
                values.put(NOTE_ID, localId);
                db.insertOrThrow(NOTES_TABLE_NAME, null, values);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
            db.close();
        }
    }

    /** Remove online-linked local notes that were absent from a successfully fetched server list. */
    public int deleteOnlineNotesMissingFrom(List<String> serverOnlineIds) {
        SQLiteDatabase db = this.getWritableDatabase();
        try {
            db.beginTransaction();
            int deleted;
            if (serverOnlineIds == null || serverOnlineIds.isEmpty()) {
                deleted = db.delete(NOTES_TABLE_NAME, IS_ONLINE_COLUMN + " = 1", null);
            } else {
                StringBuilder placeholders = new StringBuilder();
                String[] ids = new String[serverOnlineIds.size()];
                for (int i = 0; i < serverOnlineIds.size(); i++) {
                    if (i > 0) placeholders.append(",");
                    placeholders.append("?");
                    ids[i] = serverOnlineIds.get(i);
                }
                deleted = db.delete(NOTES_TABLE_NAME,
                        IS_ONLINE_COLUMN + " = 1 AND (" + ONLINE_ID_COLUMN + " IS NULL OR TRIM(" + ONLINE_ID_COLUMN
                                + ") = '' OR " + ONLINE_ID_COLUMN + " NOT IN (" + placeholders + "))", ids);
            }
            db.setTransactionSuccessful();
            return deleted;
        } finally {
            db.endTransaction();
            db.close();
        }
    }

    public void deleteNote(int noteId){
        SQLiteDatabase db = this.getReadableDatabase();
        for(int i = noteId; i < createNoteId() - 1; i++){
            ContentValues values = new ContentValues();
            values.put(NOTE_NAME, getNoteName(i + 1));
            values.put(NOTE, getNote(i + 1));
            values.put(DATE_TIME_COLUMN, getDateTime(i + 1));
            values.put(IS_ONLINE_COLUMN, isNoteOnline(i + 1) ? 1 : 0);
            values.put(ONLINE_ID_COLUMN, getOnlineId(i + 1));
            db.update(NOTES_TABLE_NAME, values, "noteId = " + i, null);
        }
        db.delete(NOTES_TABLE_NAME, "noteId = " + (createNoteId() - 1), null);
    }

    public void updateNote(int noteId, String noteName, String note){
        SQLiteDatabase db = this.getReadableDatabase();

        Calendar calendar = Calendar.getInstance();
        String dateAndTime = "Date : "+calendar.get(Calendar.DATE)+"/"+calendar.get(Calendar.MONTH)+"/"+calendar.get(Calendar.YEAR)+
                "  Time : "+calendar.get(Calendar.HOUR_OF_DAY)+"."+calendar.get(Calendar.MINUTE)+"."+calendar.get(Calendar.SECOND);

        ContentValues values = new ContentValues();
        values.put(NOTE_NAME, noteName);
        values.put(NOTE, note);
        values.put(DATE_TIME_COLUMN, dateAndTime);
        db.update(NOTES_TABLE_NAME, values, "noteId = " + noteId, null);
    }

    public int getCount(){
        SQLiteDatabase db = this.getReadableDatabase();
        try (Cursor cursor = db.rawQuery("SELECT * FROM " + NOTES_TABLE_NAME, null)) {
            return cursor != null ? cursor.getCount() : 0;
        }
    }

    // Get all notes as a list of Note objects for export
    public List<Note> getAllNotes() {
        List<Note> notes = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        
        if (getCount() > 0) {
            Cursor cursor = db.rawQuery("SELECT * FROM " + NOTES_TABLE_NAME + " ORDER BY " + NOTE_ID, null);
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    do {
                        int noteId = cursor.getInt(0);
                        String noteName = cursor.getString(1);
                        String note = cursor.getString(2);
                        String dateTime = cursor.getString(3);
                        boolean isOnline = cursor.getColumnCount() > 4 && cursor.getInt(4) == 1;
                        String onlineId = cursor.getColumnCount() > 5 ? cursor.getString(5) : "";
                        
                        if (noteName != null && !noteName.trim().isEmpty() && !noteName.equals("NULL")) {
                            notes.add(new Note(noteId, noteName, note, dateTime, isOnline, onlineId));
                        }
                    } while (cursor.moveToNext());
                }
                cursor.close();
            }
        }
        db.close();
        return notes;
    }

    // Import notes from a list (used after parsing JSON)
    public void importNotes(List<Note> notes) {
        SQLiteDatabase db = this.getWritableDatabase();
        
        for (Note note : notes) {
            ContentValues values = new ContentValues();
            values.put(NOTE_ID, note.getNoteId());
            values.put(NOTE_NAME, note.getNoteName());
            values.put(NOTE, note.getNote());
            values.put(DATE_TIME_COLUMN, note.getDateTime());
            values.put(IS_ONLINE_COLUMN, note.isOnline() ? 1 : 0);
            values.put(ONLINE_ID_COLUMN, note.getOnlineId());
            db.insert(NOTES_TABLE_NAME, null, values);
        }
        
        db.close();
    }

    // Clear all notes (useful before import)
    public void clearAllNotes() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(NOTES_TABLE_NAME, null, null);
        db.close();
    }

    // Note data class
    public static class Note {
        private int noteId;
        private String noteName;
        private String note;
        private String dateTime;
        private boolean isOnline;
        private String onlineId;

        public Note(int noteId, String noteName, String note, String dateTime) {
            this(noteId, noteName, note, dateTime, false, "");
        }

        public Note(int noteId, String noteName, String note, String dateTime, boolean isOnline, String onlineId) {
            this.noteId = noteId;
            this.noteName = noteName;
            this.note = note;
            this.dateTime = dateTime;
            this.isOnline = isOnline;
            this.onlineId = onlineId != null ? onlineId : "";
        }

        public int getNoteId() { return noteId; }
        public String getNoteName() { return noteName; }
        public String getNote() { return note; }
        public String getDateTime() { return dateTime; }
        public boolean isOnline() { return isOnline; }
        public String getOnlineId() { return onlineId; }
    }
}
