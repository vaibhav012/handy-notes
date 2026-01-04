package vv.utility.vaibhav.handynotes;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RelativeLayout;

public class EditWidget extends Activity {

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

        Button cancel = (Button) findViewById(R.id.cancel);
        Button update = (Button) findViewById(R.id.update);
        Button openAppButton = (Button) findViewById(R.id.openAppButton);
        final EditText widgetText = (EditText) findViewById(R.id.widgetText);
        final DBHelper mydb = new DBHelper(this);

        // Load the note content
        String noteContent = mydb.getNote(noteId);
        widgetText.setText(noteContent);
        
        // Auto-focus the EditText and show keyboard
        widgetText.requestFocus();
        widgetText.setSelection(widgetText.getText().length()); // Place cursor at end
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);

        openAppButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(EditWidget.this, Home.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                finish();
            }
        });

        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        update.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String noteName = noteId == 0 ? "Widget Note" : mydb.getNoteName(noteId);
                mydb.updateNote(noteId, noteName, widgetText.getText().toString().trim());
                updateWidget();
                finish();
            }
        });
    }

    public void updateWidget(){
        Intent intent = new Intent(getBaseContext(), WidgetManager.class);
        intent.setAction("android.appwidget.action.APPWIDGET_UPDATE");
        
        // Update only the specific widget if we have its ID, otherwise update all
        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            int ids[] = {appWidgetId};
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
        } else {
            int ids[] = AppWidgetManager.getInstance(getApplication()).getAppWidgetIds(new ComponentName(getApplication(), WidgetManager.class));
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
        }
        sendBroadcast(intent);
    }
}
