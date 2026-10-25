package vv.utility.vaibhav.handynotes;

import android.app.DownloadManager;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NoteView extends AppCompatActivity {

    private int noteId = 0;
    private DBHelper mydb;
    private RichNoteEditor noteNote;
    private EditText noteName;
    private SwitchCompat onlineSyncSwitch;
    private TextView syncStatusText;
    private String originalNoteText;
    private String originalNoteName;
    private boolean isUpdatingSwitch = false;
    private boolean isNewNote = false;
    private static final int REQUEST_PICK_ATTACHMENT = 4102;
    private LinearLayout attachmentList;
    private TextView attachmentStatus;

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

        isNewNote = intent.getBooleanExtra("newNote", false);
        noteId = isNewNote ? mydb.createNoteId() : Integer.parseInt(intent.getStringExtra("noteId"));

        noteName = (EditText) findViewById(R.id.noteName);
        noteNote = (RichNoteEditor) findViewById(R.id.noteNote);
        TextView dateAndTime = (TextView) findViewById(R.id.dateAndTime);
        onlineSyncSwitch = (SwitchCompat) findViewById(R.id.onlineSyncSwitch);
        syncStatusText = (TextView) findViewById(R.id.syncStatusText);
        attachmentList = findViewById(R.id.attachmentList);
        attachmentStatus = findViewById(R.id.attachmentStatus);
        findViewById(R.id.addAttachmentButton).setOnClickListener(v -> pickAttachment());

        originalNoteName = isNewNote ? "" : mydb.getNoteName(noteId).trim();
        noteName.setText(originalNoteName);
        
        originalNoteText = isNewNote ? "" : mydb.getNote(noteId).trim();
        noteNote.setContent(originalNoteText);
        dateAndTime.setText(isNewNote ? "New note" : formatDateTime(mydb.getDateTime(noteId).trim()));

        // Set online sync status
        boolean isOnline = !isNewNote && mydb.isNoteOnline(noteId);
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
                refreshAttachments(onlineId);
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

        noteNote.requestEditorFocus();
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
                    noteNote.setContent(result.content);
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
        final String currentContent = noteNote.getContent().trim();
        String existingOnlineId = mydb.getOnlineId(noteId);

        syncStatusText.setVisibility(View.VISIBLE);
        syncStatusText.setText("Registering note on server...");

        if (existingOnlineId.isEmpty()) {
            ApiService.createOnlineNote(this, noteId, currentTitle, currentContent, new ApiService.ApiCallback<ApiService.NoteSyncResult>() {
                @Override
                public void onSuccess(ApiService.NoteSyncResult result) {
                    mydb.setOnlineStatus(noteId, true, result.onlineId);
                    syncStatusText.setText("Online sync enabled (Server ID: " + result.onlineId + ")");
                    attachmentStatus.setText("Loading files…");
                    refreshAttachments(result.onlineId);
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
            refreshAttachments(existingOnlineId);
            Toast.makeText(this, "Note activated for online sync", Toast.LENGTH_SHORT).show();
        }
    }

    private void pickAttachment() {
        if (!mydb.isNoteOnline(noteId)) {
            Toast.makeText(this, "Enable Online Sync before attaching files", Toast.LENGTH_SHORT).show();
            return;
        }
        if (isNewNote) {
            onlineSyncSwitch.setVisibility(View.GONE);
            findViewById(R.id.attachmentsPanel).setVisibility(View.GONE);
        }
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, REQUEST_PICK_ATTACHMENT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_PICK_ATTACHMENT || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        String onlineId = mydb.getOnlineId(noteId);
        if (onlineId.isEmpty()) {
            Toast.makeText(this, "This note is not linked to the server yet", Toast.LENGTH_LONG).show();
            return;
        }
        Uri fileUri = data.getData();
        attachmentStatus.setText("Uploading file…");
        findViewById(R.id.addAttachmentButton).setEnabled(false);
        ApiService.uploadNoteAttachment(this, onlineId, fileUri, new ApiService.ApiCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
                findViewById(R.id.addAttachmentButton).setEnabled(true);
                Toast.makeText(NoteView.this, "File uploaded", Toast.LENGTH_SHORT).show();
                refreshAttachments(onlineId);
            }

            @Override
            public void onError(String errorMessage) {
                findViewById(R.id.addAttachmentButton).setEnabled(true);
                attachmentStatus.setText(errorMessage);
                Toast.makeText(NoteView.this, errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void refreshAttachments(String onlineId) {
        if (onlineId == null || onlineId.isEmpty()) {
            attachmentStatus.setText("Enable Online Sync to attach files");
            return;
        }
        attachmentStatus.setText("Loading files…");
        ApiService.fetchNoteAttachments(this, onlineId, new ApiService.ApiCallback<java.util.List<ApiService.NoteAttachment>>() {
            @Override
            public void onSuccess(java.util.List<ApiService.NoteAttachment> attachments) {
                attachmentList.removeAllViews();
                if (attachments.isEmpty()) {
                    attachmentStatus.setText("No files attached");
                    return;
                }
                attachmentStatus.setText(attachments.size() + (attachments.size() == 1 ? " file" : " files"));
                for (ApiService.NoteAttachment attachment : attachments) addAttachmentRow(onlineId, attachment);
            }

            @Override
            public void onError(String errorMessage) {
                attachmentStatus.setText(errorMessage);
            }
        });
    }

    private void addAttachmentRow(String onlineId, ApiService.NoteAttachment attachment) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(0, 2, 0, 2);

        if (attachment.thumbnailUrl != null && !attachment.thumbnailUrl.isEmpty()) {
            ImageView preview = new ImageView(this);
            preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
            preview.setContentDescription("Preview of " + attachment.fileName);
            row.addView(preview, new LinearLayout.LayoutParams(dp(42), dp(42)));
            ApiService.loadAttachmentPreview(attachment.thumbnailUrl, dp(42), new ApiService.ApiCallback<android.graphics.Bitmap>() {
                @Override public void onSuccess(android.graphics.Bitmap bitmap) {
                    if (preview.getParent() != null) preview.setImageBitmap(bitmap);
                }
                @Override public void onError(String errorMessage) { preview.setVisibility(View.GONE); }
            });
        } else {
            TextView fileBadge = new TextView(this);
            fileBadge.setText("FILE");
            fileBadge.setTextColor(getColor(R.color.on_surface_variant));
            fileBadge.setTextSize(9);
            fileBadge.setGravity(android.view.Gravity.CENTER);
            fileBadge.setBackgroundResource(R.drawable.rounded_edittext);
            row.addView(fileBadge, new LinearLayout.LayoutParams(dp(42), dp(42)));
        }

        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setPadding(dp(8), 0, 0, 0);
        row.addView(details, new LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView name = new TextView(this);
        name.setText(attachment.fileName + " · " + formatFileSize(attachment.sizeBytes));
        name.setTextColor(getColor(R.color.on_surface));
        name.setTextSize(12);
        name.setMaxLines(1);
        name.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        details.addView(name);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        details.addView(actions);
        actions.addView(makeAttachmentAction("Open", () -> openAttachment(attachment)));
        actions.addView(makeAttachmentAction("Download", () -> downloadAttachment(attachment)));
        actions.addView(makeAttachmentAction("Remove", () ->
                ApiService.deleteNoteAttachment(NoteView.this, onlineId, attachment.id,
                        new ApiService.ApiCallback<Boolean>() {
                            @Override public void onSuccess(Boolean result) { refreshAttachments(onlineId); }
                            @Override public void onError(String errorMessage) {
                                Toast.makeText(NoteView.this, errorMessage, Toast.LENGTH_LONG).show();
                            }
                        })));
        attachmentList.addView(row);
    }

    private TextView makeAttachmentAction(String label, Runnable action) {
        TextView button = new TextView(this);
        button.setText(label);
        button.setTextColor(getColor(R.color.primary));
        button.setTextSize(11);
        button.setGravity(android.view.Gravity.CENTER);
        button.setPadding(dp(5), 0, dp(5), 0);
        button.setMinHeight(dp(24));
        button.setBackgroundResource(android.R.drawable.list_selector_background);
        button.setOnClickListener(v -> action.run());
        return button;
    }

    private void openAttachment(ApiService.NoteAttachment attachment) {
        if (attachment.downloadUrl == null || attachment.downloadUrl.isEmpty()) {
            Toast.makeText(this, "A download link is not available. Refresh the file list.", Toast.LENGTH_LONG).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(attachment.downloadUrl));
        try {
            startActivity(intent);
        } catch (android.content.ActivityNotFoundException e) {
            Toast.makeText(this, "No app can open this file", Toast.LENGTH_SHORT).show();
        }
    }

    private String formatFileSize(long sizeBytes) {
        if (sizeBytes < 1024) return sizeBytes + " B";
        if (sizeBytes < 1024 * 1024) return String.format(Locale.getDefault(), "%.1f KB", sizeBytes / 1024.0);
        return String.format(Locale.getDefault(), "%.1f MB", sizeBytes / (1024.0 * 1024.0));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void downloadAttachment(ApiService.NoteAttachment attachment) {
        if (attachment.downloadUrl == null || attachment.downloadUrl.isEmpty()) {
            Toast.makeText(this, "A download link is not available. Refresh the file list.", Toast.LENGTH_LONG).show();
            return;
        }
        DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        String downloadName = attachment.fileName == null ? "attachment" : attachment.fileName
                .replaceAll("[\\\\/\\u0000-\\u001f\\u007f]", "_");
        if (downloadName.trim().isEmpty() || downloadName.equals(".") || downloadName.equals("..")) {
            downloadName = "attachment";
        }
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(attachment.downloadUrl))
                .setTitle(downloadName)
                .setDescription("Downloading note attachment")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, downloadName);
        if (attachment.contentType != null && !attachment.contentType.isEmpty()) {
            request.setMimeType(attachment.contentType);
        }
        manager.enqueue(request);
        Toast.makeText(this, "Download started", Toast.LENGTH_SHORT).show();
    }

    private void deactivateOnlineSync() {
        String existingOnlineId = mydb.getOnlineId(noteId);
        mydb.setOnlineStatus(noteId, false, existingOnlineId);
        syncStatusText.setVisibility(View.VISIBLE);
        syncStatusText.setText("Offline mode enabled");
        Toast.makeText(this, "Note saved for offline only", Toast.LENGTH_SHORT).show();
    }

    private void saveNote() {
        String noteText = noteNote.getContent().trim();
        String newNoteName = noteName.getText().toString().trim();

        if (isNewNote && noteText.isEmpty() && newNoteName.isEmpty()) {
            Toast.makeText(this, "Write a note or add a title before saving", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (newNoteName.isEmpty()) {
            newNoteName = originalNoteName;
            noteName.setText(newNoteName);
        }
        
        if (isNewNote) {
            if (newNoteName.isEmpty()) newNoteName = noteText.isEmpty() ? "Untitled Note" : noteText;
            mydb.addNote(noteId, newNoteName, noteText);
            isNewNote = false;
        } else {
            mydb.updateNote(noteId, newNoteName, noteText);
        }
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
                        Toast.makeText(NoteView.this, "Saved locally. Server update failed: " + errorMessage, Toast.LENGTH_LONG).show();
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
        String currentText = noteNote.getContent().trim();
        String currentName = noteName.getText().toString().trim();
        if (!currentText.equals(originalNoteText) || !currentName.equals(originalNoteName)) {
            finish();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
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


