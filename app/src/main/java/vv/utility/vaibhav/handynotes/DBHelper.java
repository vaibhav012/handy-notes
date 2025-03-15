package vv.utility.vaibhav.handynotes;

/**
 * Created by Vaibhav on 12/2/2015.
 */

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteDatabase;
import android.widget.Toast;

import java.util.Calendar;

public class DBHelper extends SQLiteOpenHelper {

    public static final String DATABASE_NAME = "VVHandyNotes.db";
    public static final String NOTES_TABLE_NAME = "notes";
    public static final String NOTE_ID = "noteId";
    public static final String NOTE_NAME = "noteName";
    public static final String NOTE = "note";
    public static final String DATE_TIME_COLUMN = "dateTime";

    public DBHelper(Context context) {
        super(context, DATABASE_NAME, null, 1);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("create table " + NOTES_TABLE_NAME + "( " + NOTE_ID + " INTEGER PRIMARY KEY, " + NOTE_NAME + " TEXT, " + NOTE + " TEXT, " + DATE_TIME_COLUMN + " TEXT );");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + NOTES_TABLE_NAME);
    }

    public void addNote(int noteId, String name, String note) {
        SQLiteDatabase db = this.getWritableDatabase();

        Calendar calendar = Calendar.getInstance();
        String dateAndTime = "Date : "+calendar.get(Calendar.DATE)+"/"+calendar.get(Calendar.MONTH)+"/"+calendar.get(Calendar.YEAR)+
                "  Time : "+calendar.get(Calendar.HOUR)+"."+calendar.get(Calendar.MINUTE)+"."+calendar.get(Calendar.SECOND);

        ContentValues values = new ContentValues();
        values.put(NOTE_ID, noteId);
        values.put(NOTE_NAME, name);
        values.put(NOTE, note);
        values.put(DATE_TIME_COLUMN, dateAndTime);
        db.insert(NOTES_TABLE_NAME, null, values);
        db.close();
    }

    public int createNoteId() {
        SQLiteDatabase db = this.getReadableDatabase();

        int noteId = 0;

        if (getCount() > 0){
            Cursor cursor = db.rawQuery("SELECT * from notes WHERE 1", null);
            cursor.moveToFirst();
            do{
                noteId = cursor.getInt(0);
            } while (cursor.moveToNext());
        }
        noteId++;
        return noteId;
    }

    public String getNoteName(int note_id) {
        SQLiteDatabase db = this.getReadableDatabase();

        String noteName = "NULL";

        if (getCount() > 0){
            Cursor cursor = db.rawQuery("SELECT * from notes WHERE 1", null);
            cursor.moveToFirst();
            do{
                if(cursor.getInt(0) == note_id)
                    noteName = cursor.getString(1);
            } while (cursor.moveToNext());
        }
        return noteName;
    }

    public String getNote(int note_id) {
        SQLiteDatabase db = this.getReadableDatabase();

        String note = "NULL";

        if (getCount() > 0){
            Cursor cursor = db.rawQuery("SELECT * from notes WHERE 1", null);
            cursor.moveToFirst();
            do{
                if(cursor.getInt(0) == note_id)
                    note = cursor.getString(2);
            } while (cursor.moveToNext());
        }
        return note;
    }

    public String getDateTime(int note_id) {
        SQLiteDatabase db = this.getReadableDatabase();

        String dateTime = "NULL";

        if (getCount() > 0){
            Cursor cursor = db.rawQuery("SELECT * from notes WHERE 1", null);
            cursor.moveToFirst();
            do{
                if(cursor.getInt(0) == note_id)
                    dateTime = cursor.getString(3);
            } while (cursor.moveToNext());
        }
        return dateTime;
    }

    void deleteNote(int noteId){
        SQLiteDatabase db = this.getReadableDatabase();
        for(int i = noteId; i < createNoteId() - 1; i++){
            ContentValues values = new ContentValues();
            values.put(NOTE_NAME, getNoteName(i + 1));
            values.put(NOTE, getNote(i + 1));
            values.put(DATE_TIME_COLUMN, getDateTime(i + 1));
            db.update("notes", values, "noteId = " + i, null);
        }
        db.delete("notes", "noteId = " + (createNoteId() - 1), null);
    }

    void updateNote(int noteId, String noteName, String note){
        SQLiteDatabase db = this.getReadableDatabase();

        Calendar calendar = Calendar.getInstance();
        String dateAndTime = "Date : "+calendar.get(Calendar.DATE)+"/"+calendar.get(Calendar.MONTH)+"/"+calendar.get(Calendar.YEAR)+
                "  Time : "+calendar.get(Calendar.HOUR)+"."+calendar.get(Calendar.MINUTE)+"."+calendar.get(Calendar.SECOND);

        ContentValues values = new ContentValues();
        values.put(NOTE_NAME, noteName);
        values.put(NOTE, note);
        values.put(DATE_TIME_COLUMN, dateAndTime);
        db.update("notes", values, "noteId = " + noteId, null);
    }

    public int getCount(){
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT  * FROM " + NOTES_TABLE_NAME, null);
        return cursor.getCount();
    }
}