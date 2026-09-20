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

        // Get note ID and widget ID from intent
        Intent intent = getIntent();
        if (intent != null) {
            noteId = intent.getIntExtra("noteId", 0);
            appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        }

        Button cancel = findViewById(R.id.cancel);
        Button update = findViewById(R.id.update);
        Button openAppButton = findViewById(R.id.openAppButton);
        final EditText widgetText = findViewById(R.id.widgetText);

        // Load the note content
        try (DBHelper mydb = new DBHelper(this)) {
            String noteContent = mydb.getNote(noteId);
            widgetText.setText(noteContent);
        }

        // Auto-focus the EditText and show keyboard
        widgetText.requestFocus();
        widgetText.setSelection(widgetText.getText().length()); // Place cursor at end
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);

        openAppButton.setOnClickListener(v -> {
            Intent mainIntent = new Intent(EditWidget.this, Home.class);
            mainIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(mainIntent);
            finish();
        });

        cancel.setOnClickListener(v -> finish());

        update.setOnClickListener(v -> {
            try (DBHelper mydb = new DBHelper(EditWidget.this)) {
                String noteName = mydb.getNoteName(noteId);
                mydb.updateNote(noteId, noteName, widgetText.getText().toString().trim());
            }
            updateWidget();
            finish();
        });
    }

    public void updateWidget() {
        Intent intent = new Intent(getBaseContext(), WidgetManager.class);
        intent.setAction("android.appwidget.action.APPWIDGET_UPDATE");

        // Update only the specific widget if we have its ID, otherwise update all
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
