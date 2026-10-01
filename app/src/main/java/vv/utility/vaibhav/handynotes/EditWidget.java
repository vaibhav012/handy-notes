package vv.utility.vaibhav.handynotes;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;

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
        Button openAppButton = findViewById(R.id.openAppButton);
        final EditText widgetText = findViewById(R.id.widgetText);

        try (DBHelper mydb = new DBHelper(this)) {
            String noteContent = mydb.getNote(noteId);
            widgetText.setText(noteContent);

            // Refetch in background if note is online
            if (mydb.isNoteOnline(noteId)) {
                String onlineId = mydb.getOnlineId(noteId);
                if (!onlineId.isEmpty()) {
                    ApiService.fetchOnlineNote(this, onlineId, new ApiService.ApiCallback<ApiService.NoteSyncResult>() {
                        @Override
                        public void onSuccess(ApiService.NoteSyncResult result) {
                            if (result.content != null && !result.content.isEmpty()) {
                                try (DBHelper db = new DBHelper(EditWidget.this)) {
                                    String title = result.title != null && !result.title.isEmpty() ? result.title : db.getNoteName(noteId);
                                    db.updateNote(noteId, title, result.content);
                                }
                                widgetText.setText(result.content);
                                updateWidget();
                            }
                        }

                        @Override
                        public void onError(String errorMessage) {
                            // Keep offline content if fetch fails
                        }
                    });
                }
            }
        }

        widgetText.requestFocus();
        widgetText.setSelection(widgetText.getText().length());
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);

        openAppButton.setOnClickListener(v -> {
            Intent noteIntent = new Intent(EditWidget.this, NoteView.class);
            noteIntent.putExtra("noteId", String.valueOf(noteId));
            startActivity(noteIntent);
            finish();
        });

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
                        // Offline save
                    }
                });
            }

            updateWidget();
            finish();
        });
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
