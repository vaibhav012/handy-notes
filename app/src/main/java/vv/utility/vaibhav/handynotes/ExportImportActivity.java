package vv.utility.vaibhav.handynotes;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class ExportImportActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_EXPORT = 1001;
    private static final int REQUEST_CODE_IMPORT = 1002;

    private DBHelper dbHelper;
    private TextView statusText;
    private String exportJsonData; // Temporary storage for export data

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_export_import);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Export/Import Notes");
        }

        dbHelper = new DBHelper(this);
        statusText = findViewById(R.id.statusText);

        Button exportButton = findViewById(R.id.exportButton);
        Button importButton = findViewById(R.id.importButton);

        exportButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                exportNotes();
            }
        });

        importButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                importNotes();
            }
        });

        updateStatus();
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    private void updateStatus() {
        int noteCount = dbHelper.getCount();
        statusText.setText("Total notes: " + noteCount);
    }

    private void exportNotes() {
        List<DBHelper.Note> notes = dbHelper.getAllNotes();
        
        if (notes.isEmpty()) {
            Toast.makeText(this, "No notes to export", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            // Create JSON array
            JSONArray jsonArray = new JSONArray();
            for (DBHelper.Note note : notes) {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("noteId", note.getNoteId());
                jsonObject.put("noteName", note.getNoteName());
                jsonObject.put("note", note.getNote());
                jsonObject.put("dateTime", note.getDateTime());
                jsonArray.put(jsonObject);
            }

            // Create JSON object with version and notes array
            JSONObject rootObject = new JSONObject();
            rootObject.put("version", 1);
            rootObject.put("exportDate", System.currentTimeMillis());
            rootObject.put("notes", jsonArray);

            String jsonString = rootObject.toString(2); // Pretty print with indent 2

            // Store JSON string temporarily
            exportJsonData = jsonString;

            // Use Storage Access Framework to save file
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_TITLE, "handy_notes_export.json");
            startActivityForResult(intent, REQUEST_CODE_EXPORT);

        } catch (JSONException e) {
            Toast.makeText(this, "Error creating export file: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void importNotes() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        startActivityForResult(intent, REQUEST_CODE_IMPORT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode != RESULT_OK || data == null) {
            return;
        }

        Uri uri = data.getData();

        if (requestCode == REQUEST_CODE_EXPORT) {
            if (uri != null) {
                try {
                    OutputStream outputStream = getContentResolver().openOutputStream(uri);
                    if (outputStream != null && exportJsonData != null) {
                        OutputStreamWriter writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
                        writer.write(exportJsonData);
                        writer.close();
                        outputStream.close();
                        
                        Toast.makeText(this, "Notes exported successfully!", Toast.LENGTH_SHORT).show();
                        exportJsonData = null; // Clear temporary data
                    }
                } catch (IOException e) {
                    Toast.makeText(this, "Error writing file: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
        } else if (requestCode == REQUEST_CODE_IMPORT) {
            if (uri != null) {
                try {
                    InputStream inputStream = getContentResolver().openInputStream(uri);
                    if (inputStream != null) {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
                        StringBuilder stringBuilder = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            stringBuilder.append(line).append("\n");
                        }
                        reader.close();
                        inputStream.close();

                        String jsonString = stringBuilder.toString();
                        importNotesFromJson(jsonString);

                    }
                } catch (FileNotFoundException e) {
                    Toast.makeText(this, "File not found: " + e.getMessage(), Toast.LENGTH_LONG).show();
                } catch (IOException e) {
                    Toast.makeText(this, "Error reading file: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    private void importNotesFromJson(String jsonString) {
        try {
            JSONObject rootObject = new JSONObject(jsonString);
            JSONArray notesArray = rootObject.getJSONArray("notes");

            List<DBHelper.Note> notes = new java.util.ArrayList<>();
            for (int i = 0; i < notesArray.length(); i++) {
                JSONObject noteObject = notesArray.getJSONObject(i);
                int noteId = noteObject.getInt("noteId");
                String noteName = noteObject.getString("noteName");
                String note = noteObject.getString("note");
                String dateTime = noteObject.getString("dateTime");

                notes.add(new DBHelper.Note(noteId, noteName, note, dateTime));
            }

            // Clear existing notes and import new ones
            dbHelper.clearAllNotes();
            dbHelper.importNotes(notes);

            // Update widget
            updateWidget();

            Toast.makeText(this, "Imported " + notes.size() + " notes successfully!", Toast.LENGTH_SHORT).show();
            updateStatus();

        } catch (JSONException e) {
            Toast.makeText(this, "Error parsing JSON: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void updateWidget() {
        Intent intent = new Intent(getBaseContext(), WidgetManager.class);
        intent.setAction("android.appwidget.action.APPWIDGET_UPDATE");
        int ids[] = AppWidgetManager.getInstance(getApplication()).getAppWidgetIds(new ComponentName(getApplication(), WidgetManager.class));
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
        sendBroadcast(intent);
    }
}

