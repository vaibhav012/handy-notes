package vv.utility.vaibhav.handynotes;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NoteView extends AppCompatActivity {

    private int noteId = 0;
    private DBHelper mydb;
    private EditText noteNote;
    private EditText noteName;
    private SwitchCompat onlineSyncSwitch;
    private TextView syncStatusText;
    private String originalNoteText;
    private String originalNoteName;
    private boolean isUpdatingSwitch = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_note_view);

        Toolbar myToolbar = (Toolbar) findViewById(R.id.note_view_toolbar);
        setSupportActionBar(myToolbar);

        ActionBar ab = getSupportActionBar();
        if (ab != null) {
            ab.setDisplayHomeAsUpEnabled(true);
        }

        Intent intent = getIntent();
        mydb = new DBHelper(this);

        noteId = Integer.parseInt(intent.getStringExtra("noteId"));

        noteName = (EditText) findViewById(R.id.noteName);
        noteNote = (EditText) findViewById(R.id.noteNote);
        TextView dateAndTime = (TextView) findViewById(R.id.dateAndTime);
        onlineSyncSwitch = (SwitchCompat) findViewById(R.id.onlineSyncSwitch);
        syncStatusText = (TextView) findViewById(R.id.syncStatusText);

        originalNoteName = mydb.getNoteName(noteId).trim();
        noteName.setText(originalNoteName);
        
        originalNoteText = mydb.getNote(noteId).trim();
        noteNote.setText(originalNoteText);
        dateAndTime.setText(formatDateTime(mydb.getDateTime(noteId).trim()));

        // Set online sync status
        boolean isOnline = mydb.isNoteOnline(noteId);
        String onlineId = mydb.getOnlineId(noteId);

        isUpdatingSwitch = true;
        onlineSyncSwitch.setChecked(isOnline);
        isUpdatingSwitch = false;

        if (isOnline) {
            syncStatusText.setVisibility(View.VISIBLE);
            syncStatusText.setText("Online mode active");
            // Background refetch if online
            if (!onlineId.isEmpty()) {
                refetchOnlineNote(onlineId);
            }
        }

        onlineSyncSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isUpdatingSwitch) return;

            if (isChecked) {
                SupabaseAuthManager.getSignedInEmail(NoteView.this, new SupabaseAuthManager.Callback() {
                    @Override
                    public void onSuccess(String email) {
                        if (email != null && !email.isEmpty()) {
                            activateOnlineSync();
                        } else {
                            isUpdatingSwitch = true;
                            onlineSyncSwitch.setChecked(false);
                            isUpdatingSwitch = false;
                            Toast.makeText(NoteView.this, "Please sign in to enable online sync", Toast.LENGTH_SHORT).show();
                            AccountDialog.show(NoteView.this, null);
                        }
                    }

                    @Override
                    public void onError(String message) {
                        isUpdatingSwitch = true;
                        onlineSyncSwitch.setChecked(false);
                        isUpdatingSwitch = false;
                        Toast.makeText(NoteView.this, "Please sign in to enable online sync", Toast.LENGTH_SHORT).show();
                        AccountDialog.show(NoteView.this, null);
                    }
                });
            } else {
                deactivateOnlineSync();
            }
        });

        noteNote.requestFocus();
        noteNote.setSelection(noteNote.getText().length());
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);

        Button saveButton = (Button) findViewById(R.id.saveButton);
        saveButton.setOnClickListener(v -> saveNote());

        Button cancelButton = (Button) findViewById(R.id.cancelButton);
        cancelButton.setOnClickListener(v -> finish());
    }

    private void refetchOnlineNote(final String onlineId) {
        syncStatusText.setVisibility(View.VISIBLE);
        syncStatusText.setText("Refetching note from server...");

        ApiService.fetchOnlineNote(this, onlineId, new ApiService.ApiCallback<ApiService.NoteSyncResult>() {
            @Override
            public void onSuccess(ApiService.NoteSyncResult result) {
                boolean hasChanges = false;
                if (result.title != null && !result.title.isEmpty() && !result.title.equals(originalNoteName)) {
                    originalNoteName = result.title;
                    noteName.setText(result.title);
                    hasChanges = true;
                }
                if (result.content != null && !result.content.equals(originalNoteText)) {
                    originalNoteText = result.content;
                    noteNote.setText(result.content);
                    hasChanges = true;
                }

                if (hasChanges) {
                    mydb.updateNote(noteId, originalNoteName, originalNoteText);
                    syncStatusText.setText("Refetched and updated from server");
                    updateWidget();
                } else {
                    syncStatusText.setText("Up to date with server");
                }
            }

            @Override
            public void onError(String errorMessage) {
                syncStatusText.setText("Offline (Server fetch error)");
            }
        });
    }

    private void activateOnlineSync() {
        final String currentTitle = noteName.getText().toString().trim();
        final String currentContent = noteNote.getText().toString().trim();
        String existingOnlineId = mydb.getOnlineId(noteId);

        syncStatusText.setVisibility(View.VISIBLE);
        syncStatusText.setText("Registering note on server...");

        if (existingOnlineId.isEmpty()) {
            ApiService.createOnlineNote(this, noteId, currentTitle, currentContent, new ApiService.ApiCallback<ApiService.NoteSyncResult>() {
                @Override
                public void onSuccess(ApiService.NoteSyncResult result) {
                    mydb.setOnlineStatus(noteId, true, result.onlineId);
                    syncStatusText.setText("Online sync enabled (Server ID: " + result.onlineId + ")");
                    Toast.makeText(NoteView.this, "Note activated for online sync", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onError(String errorMessage) {
                    mydb.setOnlineStatus(noteId, false, "");
                    isUpdatingSwitch = true;
                    onlineSyncSwitch.setChecked(false);
                    isUpdatingSwitch = false;
                    syncStatusText.setText("Failed to activate online mode");
                    Toast.makeText(NoteView.this, "Error: " + errorMessage, Toast.LENGTH_LONG).show();
                }
            });
        } else {
            mydb.setOnlineStatus(noteId, true, existingOnlineId);
            syncStatusText.setText("Online sync enabled");
            Toast.makeText(this, "Note activated for online sync", Toast.LENGTH_SHORT).show();
        }
    }

    private void deactivateOnlineSync() {
        String existingOnlineId = mydb.getOnlineId(noteId);
        mydb.setOnlineStatus(noteId, false, existingOnlineId);
        syncStatusText.setVisibility(View.VISIBLE);
        syncStatusText.setText("Offline mode enabled");
        Toast.makeText(this, "Note saved for offline only", Toast.LENGTH_SHORT).show();
    }

    private void saveNote() {
        String noteText = noteNote.getText().toString().trim();
        String newNoteName = noteName.getText().toString().trim();
        
        if (newNoteName.isEmpty()) {
            newNoteName = originalNoteName;
            noteName.setText(newNoteName);
        }
        
        mydb.updateNote(noteId, newNoteName, noteText);
        originalNoteText = noteText;
        originalNoteName = newNoteName;

        if (mydb.isNoteOnline(noteId)) {
            String onlineId = mydb.getOnlineId(noteId);
            if (!onlineId.isEmpty()) {
                syncStatusText.setVisibility(View.VISIBLE);
                syncStatusText.setText("Updating note on server...");
                ApiService.updateOnlineNote(this, onlineId, newNoteName, noteText, new ApiService.ApiCallback<Boolean>() {
                    @Override
                    public void onSuccess(Boolean result) {
                        Toast.makeText(NoteView.this, "Saved locally and on server", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onError(String errorMessage) {
                        Toast.makeText(NoteView.this, "Saved locally (Server update error)", Toast.LENGTH_SHORT).show();
                    }
                });
            } else {
                activateOnlineSync();
            }
        } else {
            Toast.makeText(this, "Note saved locally", Toast.LENGTH_SHORT).show();
        }
        
        updateWidget();
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
            Pattern pattern = Pattern.compile("Date : (\\d+)/(\\d+)/(\\d+)\\s+Time : (\\d+)\\.(\\d+)\\.(\\d+)");
            Matcher matcher = pattern.matcher(dateTimeString);
            
            if (matcher.find()) {
                int day = Integer.parseInt(matcher.group(1));
                int month = Integer.parseInt(matcher.group(2));
                int hour = Integer.parseInt(matcher.group(4));
                int minute = Integer.parseInt(matcher.group(5));
                
                String[] monthNames = {"Jan", "Feb", "Mar", "Apr", "May", "Jun",
                                      "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
                String monthName = monthNames[month];
                
                if (hour > 23) {
                    hour = hour % 24;
                }
                
                String ordinal = getOrdinalSuffix(day);
                String timeString = String.format(Locale.getDefault(), "%02d:%02d", hour, minute);
                
                return "Last Modified: " + day + ordinal + " " + monthName + ", " + timeString;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
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
        String currentText = noteNote.getText().toString().trim();
        String currentName = noteName.getText().toString().trim();
        if (!currentText.equals(originalNoteText) || !currentName.equals(originalNoteName)) {
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
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
