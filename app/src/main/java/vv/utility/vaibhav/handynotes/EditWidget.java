package vv.utility.vaibhav.handynotes;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.TaskStackBuilder;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public class EditWidget extends AppCompatActivity {

    private int noteId = 0;
    private int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_widget);

        Intent intent = getIntent();
        if (intent != null) {
            noteId = intent.getIntExtra("noteId", 0);
            appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        }

        if (intent != null && (noteId <= 0 || intent.getBooleanExtra("openDefaultNote", false))) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            int defaultNoteId = prefs.getInt(WidgetManager.PREF_DEFAULT_NOTE_ID, -1);
            if (defaultNoteId == -1) {
                defaultNoteId = WidgetManager.getOrCreateDefaultNote(this);
                if (defaultNoteId == -1) {
                    finish();
                    return;
                }
            }

            boolean defaultNoteExists;
            try (DBHelper db = new DBHelper(this);
                 Cursor cursor = db.getReadableDatabase().query(DBHelper.NOTES_TABLE_NAME,
                         new String[]{DBHelper.NOTE_ID}, DBHelper.NOTE_ID + " = ?",
                         new String[]{String.valueOf(defaultNoteId)}, null, null, null)) {
                defaultNoteExists = cursor.moveToFirst();
            }
            if (!defaultNoteExists) {
                prefs.edit().remove(WidgetManager.PREF_DEFAULT_NOTE_ID).apply();
                defaultNoteId = WidgetManager.getOrCreateDefaultNote(this);
                if (defaultNoteId == -1) {
                    finish();
                    return;
                }
            }

            noteId = defaultNoteId;
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                WidgetManager.setNoteHidden(this, noteId, false);
                prefs.edit().putInt("widget_note_" + appWidgetId, noteId).apply();
                updateWidget();
            }
        }

        Button cancel = findViewById(R.id.cancel);
        Button update = findViewById(R.id.update);
        final RichNoteEditor widgetText = findViewById(R.id.widgetText);
        TextView noteTitle = findViewById(R.id.widgetEditorNoteTitle);
        ProgressBar refreshSpinner = findViewById(R.id.noteRefreshSpinner);
        SeekBar noteBrowseBar = findViewById(R.id.noteBrowseBar);
        final boolean[] serverFetchInProgress = {false};
        final boolean[] userEdited = {false};
        final boolean[] loadingLocalNote = {true};
        widgetText.setOnContentChangedListener(markdown -> { if (!loadingLocalNote[0]) userEdited[0] = true; });

        noteTitle.setOnClickListener(v -> openNoteInApp());

        try (DBHelper mydb = new DBHelper(this)) {
            String title = mydb.getNoteName(noteId);
            noteTitle.setText(title == null || title.trim().isEmpty() || title.equals("NULL") ? "Untitled Note" : title.trim());
            String noteContent = mydb.getNote(noteId);
            widgetText.setContent(noteContent);
            loadingLocalNote[0] = false;

            // Refetch in background if note is online
            if (mydb.isNoteOnline(noteId)) {
                String onlineId = mydb.getOnlineId(noteId);
                if (!onlineId.isEmpty()) {
                    serverFetchInProgress[0] = true;
                    update.setEnabled(false);
                    noteBrowseBar.setEnabled(false);
                    widgetText.setEnabled(false);
                    refreshSpinner.setVisibility(View.VISIBLE);
                    ApiService.fetchOnlineNotes(this, new ApiService.ApiCallback<List<ApiService.ServerNote>>() {
                        @Override
                        public void onSuccess(List<ApiService.ServerNote> notes) {
                            serverFetchInProgress[0] = false;
                            refreshSpinner.setVisibility(View.GONE);
                            ApiService.ServerNote currentNote = null;
                            ArrayList<String> serverOnlineIds = new ArrayList<>();
                            try (DBHelper db = new DBHelper(EditWidget.this)) {
                                for (ApiService.ServerNote note : notes) {
                                    if (note.onlineId != null && !note.onlineId.trim().isEmpty()) {
                                        serverOnlineIds.add(note.onlineId);
                                        db.upsertOnlineNote(note.onlineId, note.title, note.content);
                                        if (onlineId.equals(note.onlineId)) currentNote = note;
                                    }
                                }
                                db.deleteOnlineNotesMissingFrom(serverOnlineIds);
                            }
                            updateWidget();
                            if (currentNote == null) {
                                Toast.makeText(EditWidget.this,
                                        "This note was deleted from the server.", Toast.LENGTH_LONG).show();
                                finish();
                                return;
                            }
                            if (!userEdited[0] && currentNote.content != null) {
                                widgetText.setContent(currentNote.content);
                                widgetText.requestEditorFocus();
                            }
                            if (currentNote.title != null && !currentNote.title.trim().isEmpty()) {
                                noteTitle.setText(currentNote.title.trim());
                            }
                            update.setEnabled(true);
                            noteBrowseBar.setEnabled(true);
                            widgetText.setEnabled(true);
                        }

                        @Override
                        public void onError(String errorMessage) {
                            serverFetchInProgress[0] = false;
                            refreshSpinner.setVisibility(View.GONE);
                            update.setEnabled(true);
                            noteBrowseBar.setEnabled(true);
                            widgetText.setEnabled(true);
                        }
                    });
                }
            }
        }

        widgetText.requestEditorFocus();
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);

        cancel.setOnClickListener(v -> finish());

        ArrayList<Integer> browseNoteIds = getBrowsableNoteIds();
        ArrayList<Integer> notesNeedingOnlineSync = new ArrayList<>();
        noteBrowseBar.setMax(Math.max(0, browseNoteIds.size() - 1));
        int initialIndex = browseNoteIds.indexOf(noteId);
        noteBrowseBar.setProgress(initialIndex < 0 ? 0 : initialIndex);
        noteBrowseBar.setEnabled(!serverFetchInProgress[0] && browseNoteIds.size() > 1);
        final int[] dragStartNoteId = {noteId};
        noteBrowseBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && progress >= 0 && progress < browseNoteIds.size()) {
                    userEdited[0] = false;
                    selectBrowseNote(browseNoteIds.get(progress), widgetText, noteTitle, notesNeedingOnlineSync);
                }
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) { dragStartNoteId[0] = noteId; }

            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                int selectedIndex = seekBar.getProgress();
                if (!notesNeedingOnlineSync.isEmpty()) {
                    syncQueuedNotes(notesNeedingOnlineSync);
                    notesNeedingOnlineSync.clear();
                }
                if (noteId != dragStartNoteId[0]) updateWidget();
            }
        });

        update.setOnClickListener(v -> {
            String newText = widgetText.getContent().trim();
            String noteName = "";
            boolean isOnline = false;
            String onlineId = "";

            try (DBHelper mydb = new DBHelper(EditWidget.this)) {
                noteName = mydb.getNoteName(noteId);
                isOnline = mydb.isNoteOnline(noteId);
                onlineId = mydb.getOnlineId(noteId);
            }

            if (isOnline && !onlineId.isEmpty()) {
                final String titleToSave = noteName;
                final String contentToSave = newText;
                update.setEnabled(false);
                noteBrowseBar.setEnabled(false);
                widgetText.setEnabled(false);
                ApiService.updateOnlineNote(EditWidget.this, onlineId, titleToSave, contentToSave, new ApiService.ApiCallback<Boolean>() {
                    @Override
                    public void onSuccess(Boolean result) {
                        try (DBHelper db = new DBHelper(EditWidget.this)) {
                            db.updateNote(noteId, titleToSave, contentToSave);
                        }
                        updateWidget();
                        finish();
                    }

                    @Override
                    public void onError(String errorMessage) {
                        update.setEnabled(true);
                        noteBrowseBar.setEnabled(browseNoteIds.size() > 1);
                        widgetText.setEnabled(true);
                        Toast.makeText(EditWidget.this,
                                "Server update failed; edits remain unsaved locally. " + errorMessage,
                                Toast.LENGTH_LONG).show();
                    }
                });
                return;
            } else {
                try (DBHelper db = new DBHelper(EditWidget.this)) {
                    db.updateNote(noteId, noteName, newText);
                }
            }

            updateWidget();
            finish();
        });
    }

    private ArrayList<Integer> getBrowsableNoteIds() {
        ArrayList<Integer> noteIds = new ArrayList<>();
        try (DBHelper db = new DBHelper(this);
             Cursor cursor = db.getReadableDatabase().rawQuery(
                     "SELECT " + DBHelper.NOTE_ID + " FROM " + DBHelper.NOTES_TABLE_NAME
                             + " WHERE " + DBHelper.NOTE_NAME + " IS NOT NULL AND TRIM(" + DBHelper.NOTE_NAME
                             + ") != '' AND " + DBHelper.NOTE_NAME + " != 'NULL' ORDER BY " + DBHelper.NOTE_ID,
                     null)) {
            while (cursor.moveToNext()) {
                int id = cursor.getInt(0);
                if (!WidgetManager.isNoteHidden(this, id)) noteIds.add(id);
            }
        }
        return noteIds;
    }

    private void selectBrowseNote(int destinationNoteId, RichNoteEditor widgetText, TextView noteTitle,
                                  ArrayList<Integer> notesNeedingOnlineSync) {
        if (destinationNoteId == noteId) return;
        String currentTitle;
        boolean currentIsOnline;
        try (DBHelper db = new DBHelper(this)) {
            currentTitle = db.getNoteName(noteId);
            db.updateNote(noteId, currentTitle, widgetText.getContent());
            currentIsOnline = db.isNoteOnline(noteId);
        }
        if (currentIsOnline && !notesNeedingOnlineSync.contains(noteId)) notesNeedingOnlineSync.add(noteId);

        noteId = destinationNoteId;
        try (DBHelper db = new DBHelper(this)) {
            String title = db.getNoteName(noteId);
            noteTitle.setText(title == null || title.trim().isEmpty() || title.equals("NULL")
                    ? "Untitled Note" : title.trim());
            widgetText.setContent(db.getNote(noteId));
            widgetText.requestEditorFocus();
        }
        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            PreferenceManager.getDefaultSharedPreferences(this).edit()
                    .putInt("widget_note_" + appWidgetId, noteId).apply();
        }
    }

    private void syncQueuedNotes(ArrayList<Integer> noteIds) {
        LinkedHashSet<Integer> uniqueIds = new LinkedHashSet<>(noteIds);
        for (int id : uniqueIds) {
            String onlineId;
            String title;
            String content;
            try (DBHelper db = new DBHelper(this)) {
                onlineId = db.getOnlineId(id);
                title = db.getNoteName(id);
                content = db.getNote(id);
            }
            if (onlineId == null || onlineId.isEmpty()) continue;
            ApiService.updateOnlineNote(this, onlineId, title, content,
                    new ApiService.ApiCallback<Boolean>() {
                        @Override public void onSuccess(Boolean result) { }
                        @Override public void onError(String errorMessage) {
                            android.widget.Toast.makeText(EditWidget.this,
                                    "Saved locally. Server update failed: " + errorMessage,
                                    android.widget.Toast.LENGTH_LONG).show();
                        }
                    });
        }
    }

    private void openNoteInApp() {
        Intent noteIntent = new Intent(this, NoteView.class);
        noteIntent.putExtra("noteId", String.valueOf(noteId));
        TaskStackBuilder.create(this)
                .addNextIntentWithParentStack(noteIntent)
                .startActivities();
        finish();
    }

    public void updateWidget() {
        Intent intent = new Intent(getBaseContext(), WidgetManager.class);
        intent.setAction("android.appwidget.action.APPWIDGET_UPDATE");

        int[] ids;
        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            ids = new int[]{appWidgetId};
        } else {
            ids = AppWidgetManager.getInstance(getApplication()).getAppWidgetIds(new ComponentName(getApplication(), WidgetManager.class));
        }
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
        sendBroadcast(intent);
    }
}
