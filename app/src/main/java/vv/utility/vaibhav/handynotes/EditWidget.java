package vv.utility.vaibhav.handynotes;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.TaskStackBuilder;

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

        Button cancel = findViewById(R.id.cancel);
        Button update = findViewById(R.id.update);
        final EditText widgetText = findViewById(R.id.widgetText);
        TextView noteTitle = findViewById(R.id.widgetEditorNoteTitle);
        ProgressBar refreshSpinner = findViewById(R.id.noteRefreshSpinner);
        final boolean[] userEdited = {false};
        final boolean[] loadingLocalNote = {true};
        widgetText.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!loadingLocalNote[0]) userEdited[0] = true;
            }
            @Override public void afterTextChanged(android.text.Editable s) { }
        });

        noteTitle.setOnClickListener(v -> openNoteInApp());

        try (DBHelper mydb = new DBHelper(this)) {
            String title = mydb.getNoteName(noteId);
            noteTitle.setText(title == null || title.trim().isEmpty() || title.equals("NULL") ? "Untitled Note" : title.trim());
            String noteContent = mydb.getNote(noteId);
            widgetText.setText(noteContent);
            loadingLocalNote[0] = false;

            // Refetch in background if note is online
            if (mydb.isNoteOnline(noteId)) {
                String onlineId = mydb.getOnlineId(noteId);
                if (!onlineId.isEmpty()) {
                    refreshSpinner.setVisibility(android.view.View.VISIBLE);
                    ApiService.fetchOnlineNotes(this, new ApiService.ApiCallback<java.util.List<ApiService.ServerNote>>() {
                        @Override
                        public void onSuccess(java.util.List<ApiService.ServerNote> notes) {
                            refreshSpinner.setVisibility(android.view.View.GONE);
                            ApiService.ServerNote currentNote = null;
                            java.util.ArrayList<String> serverOnlineIds = new java.util.ArrayList<>();
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
                                android.widget.Toast.makeText(EditWidget.this,
                                        "This note was deleted from the server.", android.widget.Toast.LENGTH_LONG).show();
                                finish();
                                return;
                            }
                            if (!userEdited[0] && currentNote.content != null) {
                                widgetText.setText(currentNote.content);
                                widgetText.setSelection(widgetText.getText().length());
                            }
                            if (currentNote.title != null && !currentNote.title.trim().isEmpty()) {
                                noteTitle.setText(currentNote.title.trim());
                            }
                        }

                        @Override
                        public void onError(String errorMessage) {
                            refreshSpinner.setVisibility(android.view.View.GONE);
                        }
                    });
                }
            }
        }

        widgetText.requestFocus();
        widgetText.setSelection(widgetText.getText().length());
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);

        cancel.setOnClickListener(v -> finish());

        update.setOnClickListener(v -> {
            String newText = widgetText.getText().toString().trim();
            String noteName = "";
            boolean isOnline = false;
            String onlineId = "";

            try (DBHelper mydb = new DBHelper(EditWidget.this)) {
                noteName = mydb.getNoteName(noteId);
                mydb.updateNote(noteId, noteName, newText);
                isOnline = mydb.isNoteOnline(noteId);
                onlineId = mydb.getOnlineId(noteId);
            }

            if (isOnline && !onlineId.isEmpty()) {
                ApiService.updateOnlineNote(EditWidget.this, onlineId, noteName, newText, new ApiService.ApiCallback<Boolean>() {
                    @Override
                    public void onSuccess(Boolean result) {
                        // Synced with backend
                    }

                    @Override
                    public void onError(String errorMessage) {
                        android.widget.Toast.makeText(EditWidget.this,
                                "Saved locally. Server update failed: " + errorMessage,
                                android.widget.Toast.LENGTH_LONG).show();
                    }
                });
            }

            updateWidget();
            finish();
        });
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
