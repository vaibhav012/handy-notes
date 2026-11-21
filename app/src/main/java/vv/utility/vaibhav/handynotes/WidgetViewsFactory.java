package vv.utility.vaibhav.handynotes;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import java.util.Collections;
import java.util.Set;

public class WidgetViewsFactory implements RemoteViewsService.RemoteViewsFactory {
    private String[] items = new String[]{""};
    private Context ctxt = null;
    private Intent intent = null;
    private int noteId = -1;
    private int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;

    public WidgetViewsFactory(Context ctxt, Intent intent) {
        this.ctxt = ctxt;
        this.intent = intent;
        updateItems();
    }

    private void updateItems() {
        if (ctxt == null) return;
        appWidgetId = intent != null ? intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID) : AppWidgetManager.INVALID_APPWIDGET_ID;
        int currentNoteId = -1;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctxt);
        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            currentNoteId = prefs.getInt("widget_note_" + appWidgetId, -1);
        }
        
        if (currentNoteId <= 0 && intent != null) {
            currentNoteId = intent.getIntExtra("noteId", -1);
        }

        Set<String> hidden = prefs.getStringSet(WidgetManager.PREF_HIDDEN_NOTE_IDS, Collections.emptySet());

        boolean exists = false;
        if (currentNoteId > 0 && !hidden.contains(String.valueOf(currentNoteId))) {
            try (DBHelper db = new DBHelper(ctxt);
                 Cursor cursor = db.getReadableDatabase().rawQuery(
                         "SELECT " + DBHelper.NOTE_ID + " FROM " + DBHelper.NOTES_TABLE_NAME
                                 + " WHERE " + DBHelper.NOTE_ID + " = ?",
                         new String[]{String.valueOf(currentNoteId)})) {
                exists = cursor.moveToFirst();
            }
        }

        if (!exists || currentNoteId <= 0) {
            currentNoteId = -1;
            try (DBHelper db = new DBHelper(ctxt);
                 Cursor cursor = db.getReadableDatabase().rawQuery(
                         "SELECT " + DBHelper.NOTE_ID + ", " + DBHelper.NOTE_NAME + " FROM "
                                 + DBHelper.NOTES_TABLE_NAME + " ORDER BY " + DBHelper.NOTE_ID, null)) {
                while (cursor.moveToNext()) {
                    String name = cursor.getString(1);
                    int id = cursor.getInt(0);
                    if (name != null && !name.trim().isEmpty() && !name.equals("NULL")
                            && !hidden.contains(String.valueOf(id))) {
                        currentNoteId = id;
                        break;
                    }
                }
            }
        }

        this.noteId = currentNoteId;

        if (this.noteId <= 0) {
            items = new String[]{"Tap here to create your Default Note."};
        } else {
            String noteText = null;
            try (DBHelper db = new DBHelper(ctxt)) {
                noteText = db.getNote(this.noteId);
            }
            if (noteText == null || noteText.equals("NULL")) {
                noteText = "";
            }
            items = new String[]{noteText};
        }
    }

    @Override
    public void onCreate() {
        updateItems();
    }

    @Override
    public void onDestroy() {
        // no-op
    }

    @Override
    public int getCount() {
        return (items.length);
    }

    @Override
    public RemoteViews getViewAt(int position) {
        RemoteViews row = new RemoteViews(ctxt.getPackageName(), R.layout.widget_row);

        if (position >= 0 && position < items.length) {
            row.setTextViewText(android.R.id.text1, RichNoteEditor.renderMarkdown(items[position]));
        } else {
            row.setTextViewText(android.R.id.text1, "");
        }

        Intent i = new Intent();
        Bundle extras = new Bundle();
        if (noteId > 0 && position >= 0 && position < items.length) {
            extras.putInt("noteId", noteId);
            extras.putInt(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            extras.putString("noteText", items[position]);
        }
        i.putExtras(extras);
        row.setOnClickFillInIntent(android.R.id.text1, i);

        return (row);
    }

    @Override
    public RemoteViews getLoadingView() {
        return (null);
    }

    @Override
    public int getViewTypeCount() {
        return (1);
    }

    @Override
    public long getItemId(int position) {
        return (position);
    }

    @Override
    public boolean hasStableIds() {
        return (true);
    }

    @Override
    public void onDataSetChanged() {
        updateItems();
    }
}
