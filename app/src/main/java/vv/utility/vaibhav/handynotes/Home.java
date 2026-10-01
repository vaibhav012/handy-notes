package vv.utility.vaibhav.handynotes;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import androidx.appcompat.app.AppCompatActivity;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import androidx.appcompat.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

public class Home extends AppCompatActivity implements CustomAdapter.TalkToActivity{

    DBHelper mydb;

    int noteIdForOptions = -1;

    ListView noteListView;
    RelativeLayout optionsLayout;
    View noNote;
    Button addNote;
    LinearLayout addNoteLayout;

    ArrayList<Integer> noteIdList;
    ArrayList<String> noteNameList;

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh the note list when returning to activity
        if (mydb != null) {
            refreshArrayList();
            syncServerNotes();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        Toolbar myToolbar = (Toolbar) findViewById(R.id.home_toolbar);
        setSupportActionBar(myToolbar);

        mydb = new DBHelper(this);

        noteIdList = new ArrayList<Integer>();
        noteNameList = new ArrayList<String>();

        optionsLayout = (RelativeLayout) findViewById(R.id.optionsLayout);
        noNote = findViewById(R.id.noNote);
        addNoteLayout = (LinearLayout) findViewById(R.id.addNoteLayout);
        noteListView = (ListView) findViewById(R.id.noteListView);
        addNote = (Button) findViewById(R.id.addNote);
        final Button addNoteButton = (Button) findViewById(R.id.addNoteButton);
        final EditText addNoteName = (EditText) findViewById(R.id.addNoteName);
        final EditText addNoteNote = (EditText) findViewById(R.id.addNoteNote);
        final Button deleteNote = (Button) findViewById(R.id.deleteNote);
        //noteListView.setAdapter(new CustomAdapter(this, noteIdList, noteNameList));

        refreshArrayList();

        addNoteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addNoteLayout.setVisibility(View.GONE);
                addNote.setVisibility(View.VISIBLE);
                int noteId = mydb.createNoteId();
                String noteName = addNoteName.getText().toString().trim();
                String note = addNoteNote.getText().toString().trim();
                if(note.isEmpty()){
                    Toast.makeText(getBaseContext(),"Cannot add empty note",Toast.LENGTH_SHORT).show();
                }
                else {
                    if (noteName.isEmpty()) {
                        noteName = note;
                    }
                    mydb.addNote(noteId, noteName, note);
                    noteIdList.add(noteId);
                    noteNameList.add(noteName);
                    refreshArrayList();
                    addNoteNote.getText().clear();
                    addNoteName.getText().clear();
                    updateWidget();
                }
            }
        });

        addNote.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAddNoteLayout();
            }
        });

        deleteNote.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final boolean wasOnline = mydb.isNoteOnline(noteIdForOptions);
                final String onlineId = mydb.getOnlineId(noteIdForOptions);
                if (wasOnline && !onlineId.isEmpty()) {
                    ApiService.deleteOnlineNote(Home.this, onlineId, new ApiService.ApiCallback<Boolean>() {
                        @Override
                        public void onSuccess(Boolean deleted) { }

                        @Override
                        public void onError(String errorMessage) {
                            Toast.makeText(Home.this,
                                    "Deleted locally, but the server copy could not be deleted: " + errorMessage,
                                    Toast.LENGTH_LONG).show();
                        }
                    });
                }
                mydb.deleteNote(noteIdForOptions);
                refreshArrayList();
                noteIdForOptions = -1;
                optionsLayout.setVisibility(View.GONE);
                updateWidget();
            }
        });
    }

    @Override
    public void showOptions(int noteId) {
        noteIdForOptions = noteId;
        optionsLayout.setVisibility(View.VISIBLE);
    }

    @Override
    public void onBackPressed() {
        if (optionsLayout.getVisibility() == View.VISIBLE) {
            noteIdForOptions = -1;
            optionsLayout.setVisibility(View.GONE);
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
        int id = item.getItemId();
        
        if (id == R.id.action_export_import) {
            Intent intent = new Intent(getBaseContext(), ExportImportActivity.class);
            startActivity(intent);
            return true;
        }

        if (id == R.id.action_server_settings) {
            ServerSettingsDialog.show(this);
            return true;
        }

        if (id == R.id.action_account) {
            AccountDialog.show(this, this::syncServerNotes);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void refreshArrayList(){
        // Get all notes from database
        if (mydb.getCount() > 0) {
            noNote.setVisibility(View.GONE);
            noteListView.setAdapter(null);
            noteIdList.clear();
            noteNameList.clear();
            
            // Query database to get all note IDs
            SQLiteDatabase db = mydb.getReadableDatabase();
            Cursor cursor = db.rawQuery("SELECT " + DBHelper.NOTE_ID + " FROM " + DBHelper.NOTES_TABLE_NAME + " ORDER BY " + DBHelper.NOTE_ID, null);
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    do {
                        int noteId = cursor.getInt(0);
                        String noteName = mydb.getNoteName(noteId);
                        if (noteName != null) {
                            noteName = noteName.trim();
                            if (!noteName.isEmpty() && !noteName.equals("NULL")) {
                                noteIdList.add(noteId);
                                noteNameList.add(noteName);
                            }
                        }
                    } while (cursor.moveToNext());
                }
                cursor.close();
            }
        } else {
            noNote.setVisibility(View.VISIBLE);
            noteListView.setAdapter(null);
            noteIdList.clear();
            noteNameList.clear();
        }
        noteListView.setAdapter(new CustomAdapter(this, noteIdList, noteNameList));
    }

    private void syncServerNotes() {
        if (!ApiConfig.isSupabaseConfigured(this)) return;
        SupabaseAuthManager.getSignedInEmail(this, new SupabaseAuthManager.Callback() {
            @Override
            public void onSuccess(String email) {
                if (email == null || email.isEmpty() || isFinishing()) return;
                ApiService.fetchOnlineNotes(Home.this, new ApiService.ApiCallback<java.util.List<ApiService.ServerNote>>() {
                    @Override
                    public void onSuccess(java.util.List<ApiService.ServerNote> notes) {
                        try (DBHelper db = new DBHelper(Home.this)) {
                            for (ApiService.ServerNote note : notes) {
                                db.upsertOnlineNote(note.onlineId, note.title, note.content);
                            }
                        }
                        refreshArrayList();
                    }

                    @Override
                    public void onError(String errorMessage) {
                        if (!errorMessage.contains("Sign in to your Supabase account") && !isFinishing()) {
                            Toast.makeText(Home.this, "Could not refresh server notes: " + errorMessage,
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }

            @Override
            public void onError(String errorMessage) {
                // Local notes remain available while Supabase is unreachable.
            }
        });
    }

    public void showAddNoteLayout(){
        if(addNote.getVisibility() == View.VISIBLE) {
            addNoteLayout.setVisibility(View.VISIBLE);
            addNote.setVisibility(View.GONE);
        }
        else {
            addNoteLayout.setVisibility(View.GONE);
            addNote.setVisibility(View.VISIBLE);
        }
    }

    public void updateWidget(){
        Intent intent = new Intent(getBaseContext(), WidgetManager.class);
        intent.setAction("android.appwidget.action.APPWIDGET_UPDATE");
        int ids[] = AppWidgetManager.getInstance(getApplication()).getAppWidgetIds(new ComponentName(getApplication(), WidgetManager.class));
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS,ids);
        sendBroadcast(intent);
    }

}
