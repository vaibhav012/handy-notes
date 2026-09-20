package vv.utility.vaibhav.handynotes;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.RemoteViews;

import java.util.ArrayList;
import java.util.UUID;

public class WidgetManager extends AppWidgetProvider {
    public static String ACTION_PREV_NOTE = "vv.utility.vaibhav.handynotes.PREV_NOTE";
    public static String ACTION_NEXT_NOTE = "vv.utility.vaibhav.handynotes.NEXT_NOTE";

    @Override
    public void onUpdate(Context ctxt, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int i=0; i<appWidgetIds.length; i++) {
            try (DBHelper mydb = new DBHelper(ctxt)) {
                Intent svcIntent=new Intent(ctxt, WidgetService.class);

                // Get the selected note ID for this widget
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctxt);
                int selectedNoteId = prefs.getInt("widget_note_" + appWidgetIds[i], -1);
            
                // Get all note IDs from database
                ArrayList<Integer> allNoteIds = getAllNoteIds(mydb);
            
                // Check if the selected note still exists, if not use first note from list
                if (selectedNoteId == -1 || !allNoteIds.contains(selectedNoteId)) {
                    if (!allNoteIds.isEmpty()) {
                        selectedNoteId = allNoteIds.get(0);
                        // Update the preference to reflect the selection
                        SharedPreferences.Editor editor = prefs.edit();
                        editor.putInt("widget_note_" + appWidgetIds[i], selectedNoteId);
                        editor.apply();
                    } else {
                        // No notes available, skip this widget
                        continue;
                    }
                }
            
                svcIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetIds[i]);
                svcIntent.putExtra("noteId", selectedNoteId);

                setupWidget(ctxt, appWidgetManager, appWidgetIds[i], selectedNoteId, svcIntent);
            }
        }

        super.onUpdate(ctxt, appWidgetManager, appWidgetIds);
        }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        
        if (ACTION_PREV_NOTE.equals(intent.getAction()) || ACTION_NEXT_NOTE.equals(intent.getAction())) {
            int appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
            
            // Fallback: try to extract widget ID from URI if not in extras
            if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID && intent.getData() != null) {
                try {
                    String uriString = intent.getData().toString();
                    String[] parts = uriString.split("/");
                    if (parts.length > 0) {
                        appWidgetId = Integer.parseInt(parts[parts.length - 1]);
                    }
                } catch (Exception e) {
                    // Ignore parsing errors
                }
            }
            
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                try (DBHelper dbHelper = new DBHelper(context)) {
                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
                    int currentNoteId = prefs.getInt("widget_note_" + appWidgetId, -1);
                
                    // If no note is set, get first note from list
                    if (currentNoteId == -1) {
                        ArrayList<Integer> noteIds = getAllNoteIds(dbHelper);
                        if (!noteIds.isEmpty()) {
                            currentNoteId = noteIds.get(0);
                            SharedPreferences.Editor editor = prefs.edit();
                            editor.putInt("widget_note_" + appWidgetId, currentNoteId);
                            editor.commit();
                        } else {
                            // No notes available, skip update
                            return;
                        }
                    }
                
                    int newNoteId;
                    if (ACTION_PREV_NOTE.equals(intent.getAction())) {
                        newNoteId = getPreviousNoteId(dbHelper, currentNoteId);
                    } else {
                        newNoteId = getNextNoteId(dbHelper, currentNoteId);
                    }
                
                    // Only update if we have a valid note ID
                    if (newNoteId != -1) {
                        // Save the new note ID (use commit() for synchronous write to ensure it's saved before widget update)
                        SharedPreferences.Editor editor = prefs.edit();
                        editor.putInt("widget_note_" + appWidgetId, newNoteId);
                        editor.commit();
                    
                        // Update the widget directly for faster response
                        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
                        updateWidgetDirectly(context, appWidgetManager, appWidgetId, newNoteId);
                    }
                }
            }
        }
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        // Clean up preferences when widgets are deleted
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = prefs.edit();
        for (int appWidgetId : appWidgetIds) {
            editor.remove("widget_note_" + appWidgetId);
        }
        editor.commit();
        super.onDeleted(context, appWidgetIds);
    }
    
    private void updateWidgetDirectly(Context context, AppWidgetManager appWidgetManager, int appWidgetId, int noteId) {
        try {
            // Ensure SharedPreferences is updated before creating the Intent
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt("widget_note_" + appWidgetId, noteId);
            editor.commit();
            
            Intent svcIntent = new Intent(context, WidgetService.class);
            svcIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            svcIntent.putExtra("noteId", noteId);
            
            setupWidget(context, appWidgetManager, appWidgetId, noteId, svcIntent);
        } catch (Exception e) {
            Log.e("WidgetManager", "Error updating widget: " + e.getMessage());
            // If direct update fails, fall back to full update
            int[] appWidgetIds = {appWidgetId};
            onUpdate(context, appWidgetManager, appWidgetIds);
        }
    }

    private void setupWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId, int noteId, Intent svcIntent) {
        RemoteViews widget = new RemoteViews(context.getPackageName(), R.layout.widget_layout);

        Log.d("WidgetManager", "Setting up widget for noteId: " + noteId);

        // Read title and body together so this update represents one snapshot.
        String noteName = "";
        String noteText = "";
        try (DBHelper dbHelper = new DBHelper(context);
             Cursor cursor = dbHelper.getReadableDatabase().rawQuery(
                     "SELECT " + DBHelper.NOTE_NAME + ", " + DBHelper.NOTE
                             + " FROM " + DBHelper.NOTES_TABLE_NAME
                             + " WHERE " + DBHelper.NOTE_ID + " = ?",
                     new String[]{String.valueOf(noteId)})) {
            if (cursor.moveToFirst()) {
                noteName = cursor.getString(0);
                noteText = cursor.getString(1);
            }
        }
        if (noteName == null || noteName.equals("NULL") || noteName.trim().isEmpty()) {
            noteName = "";
        } else {
            noteName = noteName.trim();
        }
        
        if (noteText == null || noteText.equals("NULL")) {
            noteText = "";
        }
        
        // Update the Intent with the latest note text to ensure consistency
        svcIntent.putExtra("text", noteText);
        svcIntent.putExtra("noteId", noteId);

        Log.d("WidgetManager", "Setting up widget for noteId: " + noteName);
        
        // Set the note name in the widget header
        widget.setTextViewText(R.id.widgetNoteName, noteName);
        
        // Every update owns a snapshot, even when rapid taps revisit a note.
        String uniqueUri = "widget://" + appWidgetId + "/" + noteId + "/" + UUID.randomUUID();
        svcIntent.setData(Uri.parse(uniqueUri));
        
        widget.setRemoteAdapter(appWidgetId, R.id.widgetListView, svcIntent);
        
        // Set up click intent for the note content
        Intent clickIntent = new Intent(context, EditWidget.class);
        clickIntent.putExtra("noteId", noteId);
        clickIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        // Use unique request code per widget to avoid conflicts between multiple widgets
        PendingIntent clickPI = PendingIntent.getActivity(context, appWidgetId * 10, clickIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        widget.setPendingIntentTemplate(R.id.widgetListView, clickPI);
        
        // Set up click intent for previous note button
        Intent prevIntent = new Intent(context, WidgetManager.class);
        prevIntent.setAction(ACTION_PREV_NOTE);
        prevIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        prevIntent.setData(Uri.parse("widget://prev/" + appWidgetId));
        PendingIntent prevPI = PendingIntent.getBroadcast(context, appWidgetId * 10 + 1, prevIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        widget.setOnClickPendingIntent(R.id.prevNoteButton, prevPI);
        
        // Set up click intent for next note button
        Intent nextIntent = new Intent(context, WidgetManager.class);
        nextIntent.setAction(ACTION_NEXT_NOTE);
        nextIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        nextIntent.setData(Uri.parse("widget://next/" + appWidgetId));
        PendingIntent nextPI = PendingIntent.getBroadcast(context, appWidgetId * 10 + 2, nextIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        widget.setOnClickPendingIntent(R.id.nextNoteButton, nextPI);
        
        // Update the widget - this updates both the note name and triggers the adapter refresh
        appWidgetManager.updateAppWidget(appWidgetId, widget);
        
    }

    private int getNextNoteId(DBHelper dbHelper, int currentNoteId) {
        ArrayList<Integer> noteIds = getAllNoteIds(dbHelper);
        if (noteIds.isEmpty()) {
            return -1;
        }
        
        int currentIndex = noteIds.indexOf(currentNoteId);
        if (currentIndex == -1) {
            return noteIds.get(0);
        }
        
        // Move to next note, wrap around if at the end
        int nextIndex = (currentIndex + 1) % noteIds.size();
        return noteIds.get(nextIndex);
    }

    private int getPreviousNoteId(DBHelper dbHelper, int currentNoteId) {
        ArrayList<Integer> noteIds = getAllNoteIds(dbHelper);
        if (noteIds.isEmpty()) {
            return -1;
        }
        
        int currentIndex = noteIds.indexOf(currentNoteId);
        if (currentIndex == -1) {
            return noteIds.get(0);
        }
        
        // Move to previous note, wrap around if at the beginning
        int prevIndex = (currentIndex - 1 + noteIds.size()) % noteIds.size();
        return noteIds.get(prevIndex);
    }

    private ArrayList<Integer> getAllNoteIds(DBHelper dbHelper) {
        ArrayList<Integer> noteIds = new ArrayList<Integer>();
        
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor cursor = db.rawQuery("SELECT " + DBHelper.NOTE_ID + ", "
                + DBHelper.NOTE_NAME + " FROM " + DBHelper.NOTES_TABLE_NAME
                + " ORDER BY " + DBHelper.NOTE_ID, null)) {
            while (cursor.moveToNext()) {
                String noteName = cursor.getString(1);
                if (noteName != null) {
                    noteName = noteName.trim();
                    if (!noteName.isEmpty() && !noteName.equals("NULL")) {
                        noteIds.add(cursor.getInt(0));
                    }
                }
            }
        }
        
        return noteIds;
    }
}
