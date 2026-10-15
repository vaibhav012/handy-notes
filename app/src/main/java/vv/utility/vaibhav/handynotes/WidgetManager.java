package vv.utility.vaibhav.handynotes;

import android.app.PendingIntent;
import androidx.core.app.TaskStackBuilder;
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
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class WidgetManager extends AppWidgetProvider {
    public static String ACTION_PREV_NOTE = "vv.utility.vaibhav.handynotes.PREV_NOTE";
    public static String ACTION_NEXT_NOTE = "vv.utility.vaibhav.handynotes.NEXT_NOTE";
    public static final String PREF_DEFAULT_NOTE_ID = "widget_default_note_id";
    public static final String PREF_HIDDEN_NOTE_IDS = "widget_hidden_note_ids";

    public static boolean isNoteHidden(Context context, int noteId) {
        Set<String> hidden = PreferenceManager.getDefaultSharedPreferences(context)
                .getStringSet(PREF_HIDDEN_NOTE_IDS, java.util.Collections.emptySet());
        return hidden.contains(String.valueOf(noteId));
    }

    public static void setNoteHidden(Context context, int noteId, boolean hiddenNow) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        Set<String> hidden = new HashSet<>(prefs.getStringSet(PREF_HIDDEN_NOTE_IDS, java.util.Collections.emptySet()));
        String id = String.valueOf(noteId);
        if (hiddenNow) hidden.add(id); else hidden.remove(id);
        prefs.edit().putStringSet(PREF_HIDDEN_NOTE_IDS, hidden).apply();
    }

    public static void remapNotePreferencesAfterDeletion(Context context, int deletedNoteId) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = prefs.edit();
        int defaultNoteId = prefs.getInt(PREF_DEFAULT_NOTE_ID, -1);
        if (defaultNoteId == deletedNoteId) editor.remove(PREF_DEFAULT_NOTE_ID);
        else if (defaultNoteId > deletedNoteId) editor.putInt(PREF_DEFAULT_NOTE_ID, defaultNoteId - 1);

        for (String key : prefs.getAll().keySet()) {
            if (!key.startsWith("widget_note_")) continue;
            int selectedId = prefs.getInt(key, -1);
            if (selectedId == deletedNoteId) editor.putInt(key, -1);
            else if (selectedId > deletedNoteId) editor.putInt(key, selectedId - 1);
        }

        Set<String> remappedHidden = new HashSet<>();
        for (String id : prefs.getStringSet(PREF_HIDDEN_NOTE_IDS, java.util.Collections.emptySet())) {
            try {
                int hiddenId = Integer.parseInt(id);
                if (hiddenId < deletedNoteId) remappedHidden.add(id);
                else if (hiddenId > deletedNoteId) remappedHidden.add(String.valueOf(hiddenId - 1));
            } catch (NumberFormatException ignored) { }
        }
        editor.putStringSet(PREF_HIDDEN_NOTE_IDS, remappedHidden).apply();
    }

    @Override
    public void onUpdate(Context ctxt, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int i=0; i<appWidgetIds.length; i++) {
            try (DBHelper mydb = new DBHelper(ctxt)) {
                Intent svcIntent=new Intent(ctxt, WidgetService.class);

                // Get the selected note ID for this widget
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctxt);
                int selectedNoteId = prefs.getInt("widget_note_" + appWidgetIds[i], -1);
            
                // Get all note IDs from database
                ArrayList<Integer> allNoteIds = getAllNoteIds(ctxt, mydb);
            
                // Check if the selected note still exists, if not use first note from list
                if (selectedNoteId == -1 || !allNoteIds.contains(selectedNoteId)) {
                    if (!allNoteIds.isEmpty()) {
                        selectedNoteId = allNoteIds.get(0);
                        // Update the preference to reflect the selection
                        SharedPreferences.Editor editor = prefs.edit();
                        editor.putInt("widget_note_" + appWidgetIds[i], selectedNoteId);
                        editor.apply();
                    } else {
                        selectedNoteId = -1;
                        prefs.edit().putInt("widget_note_" + appWidgetIds[i], selectedNoteId).apply();
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
                        ArrayList<Integer> noteIds = getAllNoteIds(context, dbHelper);
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
                        newNoteId = getPreviousNoteId(context, dbHelper, currentNoteId);
                    } else {
                        newNoteId = getNextNoteId(context, dbHelper, currentNoteId);
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
        if (noteId > 0) {
            widget.setPendingIntentTemplate(R.id.widgetListView, clickPI);
        }

        if (noteId > 0 && !noteName.isEmpty()) {
            Intent openNoteIntent = new Intent(context, NoteView.class);
            openNoteIntent.putExtra("noteId", String.valueOf(noteId));
            PendingIntent openNotePI = TaskStackBuilder.create(context)
                    .addNextIntentWithParentStack(openNoteIntent)
                    .getPendingIntent(appWidgetId * 10 + 3,
                            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            widget.setOnClickPendingIntent(R.id.widgetNoteName, openNotePI);
        }
        
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

        Intent defaultNoteIntent = new Intent(context, EditWidget.class);
        defaultNoteIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        defaultNoteIntent.putExtra("openDefaultNote", true);
        PendingIntent defaultNotePI = PendingIntent.getActivity(context, appWidgetId * 10 + 4,
                defaultNoteIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        widget.setOnClickPendingIntent(R.id.openDefaultNoteButton, defaultNotePI);
        
        // Update the widget - this updates both the note name and triggers the adapter refresh
        appWidgetManager.updateAppWidget(appWidgetId, widget);
        
    }

    private int getNextNoteId(Context context, DBHelper dbHelper, int currentNoteId) {
        ArrayList<Integer> noteIds = getAllNoteIds(context, dbHelper);
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

    private int getPreviousNoteId(Context context, DBHelper dbHelper, int currentNoteId) {
        ArrayList<Integer> noteIds = getAllNoteIds(context, dbHelper);
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

    private ArrayList<Integer> getAllNoteIds(Context context, DBHelper dbHelper) {
        ArrayList<Integer> noteIds = new ArrayList<Integer>();
        Set<String> hiddenNoteIds = PreferenceManager.getDefaultSharedPreferences(context)
                .getStringSet(PREF_HIDDEN_NOTE_IDS, java.util.Collections.emptySet());
        
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor cursor = db.rawQuery("SELECT " + DBHelper.NOTE_ID + ", "
                + DBHelper.NOTE_NAME + " FROM " + DBHelper.NOTES_TABLE_NAME
                + " ORDER BY " + DBHelper.NOTE_ID, null)) {
            while (cursor.moveToNext()) {
                String noteName = cursor.getString(1);
                if (noteName != null) {
                    noteName = noteName.trim();
                    if (!noteName.isEmpty() && !noteName.equals("NULL")
                            && !hiddenNoteIds.contains(String.valueOf(cursor.getInt(0)))) {
                        noteIds.add(cursor.getInt(0));
                    }
                }
            }
        }
        
        return noteIds;
    }
}
