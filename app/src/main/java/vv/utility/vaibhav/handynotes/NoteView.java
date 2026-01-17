package vv.utility.vaibhav.handynotes;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import androidx.appcompat.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NoteView extends AppCompatActivity {

    private int noteId = 0;
    private DBHelper mydb;
    private EditText noteNote;
    private EditText noteName;
    private String originalNoteText;
    private String originalNoteName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_note_view);

        Toolbar myToolbar = (Toolbar) findViewById(R.id.note_view_toolbar);
        setSupportActionBar(myToolbar);

        // Get a support ActionBar corresponding to this toolbar
        ActionBar ab = getSupportActionBar();
        // Enable the Up button
        ab.setDisplayHomeAsUpEnabled(true);

        Intent intent = getIntent();
        mydb = new DBHelper(this);

        noteId = Integer.parseInt(intent.getStringExtra("noteId"));

        noteName = (EditText) findViewById(R.id.noteName);
        noteNote = (EditText) findViewById(R.id.noteNote);
        TextView dateAndTime = (TextView) findViewById(R.id.dateAndTime);

        originalNoteName = mydb.getNoteName(noteId).toString().trim();
        noteName.setText(originalNoteName);
        
        originalNoteText = mydb.getNote(noteId).toString().trim();
        noteNote.setText(originalNoteText);
        dateAndTime.setText(formatDateTime(mydb.getDateTime(noteId).toString().trim()));

        // Auto-focus the EditText and show keyboard
        noteNote.requestFocus();
        noteNote.setSelection(noteNote.getText().length()); // Place cursor at end
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);

        // Set up save button
        Button saveButton = (Button) findViewById(R.id.saveButton);
        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveNote();
            }
        });

        // Set up cancel button
        Button cancelButton = (Button) findViewById(R.id.cancelButton);
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }

    private void saveNote() {
        String noteText = noteNote.getText().toString().trim();
        String newNoteName = noteName.getText().toString().trim();
        
        // Ensure name is not empty
        if (newNoteName.isEmpty()) {
            newNoteName = originalNoteName;
            noteName.setText(newNoteName);
        }
        
        mydb.updateNote(noteId, newNoteName, noteText);
        originalNoteText = noteText;
        originalNoteName = newNoteName;
        
        // Update widget if this is the widget note or if any widget is showing this note
        updateWidget();
        
        Toast.makeText(this, "Note saved", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void updateWidget() {
        Intent intent = new Intent(getBaseContext(), WidgetManager.class);
        intent.setAction("android.appwidget.action.APPWIDGET_UPDATE");
        int ids[] = AppWidgetManager.getInstance(getApplication()).getAppWidgetIds(new ComponentName(getApplication(), WidgetManager.class));
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
        sendBroadcast(intent);
    }

    private String formatDateTime(String dateTimeString) {
        try {
            // Parse format: "Date : 27/1/2025  Time : 4.30.45" or "Date : 27/0/2025  Time : 16.30.45"
            Pattern pattern = Pattern.compile("Date : (\\d+)/(\\d+)/(\\d+)\\s+Time : (\\d+)\\.(\\d+)\\.(\\d+)");
            Matcher matcher = pattern.matcher(dateTimeString);
            
            if (matcher.find()) {
                int day = Integer.parseInt(matcher.group(1));
                int month = Integer.parseInt(matcher.group(2)); // Calendar.MONTH is 0-based (0=Jan, 11=Dec)
                int year = Integer.parseInt(matcher.group(3));
                int hour = Integer.parseInt(matcher.group(4));
                int minute = Integer.parseInt(matcher.group(5));
                
                // Get month name (month is 0-based: 0=Jan, 11=Dec)
                String[] monthNames = {"Jan", "Feb", "Mar", "Apr", "May", "Jun",
                                      "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
                String monthName = monthNames[month];
                
                // Handle old 12-hour format (0-11) - assume it's already in 24-hour if > 11
                // For old entries with 0-11, we can't determine AM/PM, so just display as-is
                // New entries will have 0-23 (24-hour format)
                if (hour > 23) {
                    hour = hour % 24; // Safety check
                }
                
                // Get ordinal suffix
                String ordinal = getOrdinalSuffix(day);
                
                // Format hour:minute (24-hour format)
                String timeString = String.format(Locale.getDefault(), "%02d:%02d", hour, minute);
                
                return "Last Modified: " + day + ordinal + " " + monthName + ", " + timeString;
            }
        } catch (Exception e) {
            // If parsing fails, return original or a default message
            e.printStackTrace();
        }
        
        // Fallback to original format or default message
        return dateTimeString.isEmpty() ? "Last Modified: Unknown" : "Last Modified: " + dateTimeString;
    }

    private String getOrdinalSuffix(int day) {
        if (day >= 11 && day <= 13) {
            return "th";
        }
        switch (day % 10) {
            case 1: return "st";
            case 2: return "nd";
            case 3: return "rd";
            default: return "th";
        }
    }

    @Override
    public void onBackPressed() {
        // Check if note or name has been modified
        String currentText = noteNote.getText().toString().trim();
        String currentName = noteName.getText().toString().trim();
        if (!currentText.equals(originalNoteText) || !currentName.equals(originalNoteName)) {
            // Note has been modified, just finish (user can use cancel button if they don't want to save)
            finish();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.action_bar_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            // Handle toolbar back button
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
