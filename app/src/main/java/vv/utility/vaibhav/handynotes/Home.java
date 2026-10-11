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
import android.view.DragEvent;
import android.widget.Button;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import java.util.ArrayList;

public class Home extends AppCompatActivity implements CustomAdapter.TalkToActivity{

    DBHelper mydb;

    int noteIdForOptions = -1;

    ListView noteListView;
    RelativeLayout optionsLayout;
    View noNote;
    Button addNote;

    ArrayList<Integer> noteIdList;
    ArrayList<String> noteNameList;
    ArrayList<String> notePreviewList;
    private View highlightedDropRow;
    private View addNoteFooter;

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
        notePreviewList = new ArrayList<String>();

        optionsLayout = (RelativeLayout) findViewById(R.id.optionsLayout);
        noNote = findViewById(R.id.noNote);
        noteListView = (ListView) findViewById(R.id.noteListView);
        addNoteFooter = getLayoutInflater().inflate(R.layout.note_list_add_footer, noteListView, false);
        addNoteFooter.findViewById(R.id.addNoteFooterCard).setOnClickListener(v -> openNewNote());
        noteListView.addFooterView(addNoteFooter, null, false);
        noteListView.setOnDragListener((view, event) -> {
            int target = noteListView.pointToPosition((int) event.getX(), (int) event.getY());
            View nextHighlightedRow = target >= 0 ? noteListView.getChildAt(target - noteListView.getFirstVisiblePosition()) : null;
            if (event.getAction() == DragEvent.ACTION_DRAG_LOCATION && nextHighlightedRow != highlightedDropRow) {
                clearDropHighlight();
                highlightedDropRow = nextHighlightedRow;
                if (highlightedDropRow != null) {
                    highlightedDropRow.animate().scaleX(0.98f).scaleY(0.98f).alpha(0.72f).setDuration(90).start();
                    highlightedDropRow.setElevation(getResources().getDisplayMetrics().density * 8);
                }
                return true;
            }
            if (event.getAction() == DragEvent.ACTION_DRAG_ENDED) {
                clearDropHighlight();
                return true;
            }
            if (event.getAction() == DragEvent.ACTION_DROP && event.getClipData() != null) {
                int noteId = Integer.parseInt(event.getClipData().getItemAt(0).getText().toString());
                int source = noteIdList.indexOf(noteId);
                if (target >= noteIdList.size()) target = noteIdList.size() - 1;
                if (source >= 0 && target >= 0 && source != target) {
                    noteIdList.remove(source); noteNameList.remove(source); notePreviewList.remove(source);
                    noteIdList.add(target, noteId);
                    noteNameList.add(target, mydb.getNoteName(noteId));
                    String content = mydb.getNote(noteId);
                    notePreviewList.add(target, content == null ? "" : content.trim().replaceAll("\\s+", " "));
                    saveNoteOrder(noteIdList);
                    noteListView.setAdapter(new CustomAdapter(this, noteIdList, noteNameList, notePreviewList));
                }
                clearDropHighlight();
                return true;
            }
            return true;
        });
        addNote = (Button) findViewById(R.id.addNote);
        final Button deleteNote = (Button) findViewById(R.id.deleteNote);
        //noteListView.setAdapter(new CustomAdapter(this, noteIdList, noteNameList));

        refreshArrayList();

        addNote.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openNewNote();
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
                ArrayList<Integer> nextOrder = new ArrayList<>(noteIdList);
                int deletedId = noteIdForOptions;
                mydb.deleteNote(deletedId);
                nextOrder.remove(Integer.valueOf(deletedId));
                for (int i = 0; i < nextOrder.size(); i++) if (nextOrder.get(i) > deletedId) nextOrder.set(i, nextOrder.get(i) - 1);
                saveNoteOrder(nextOrder);
                refreshArrayList();
                noteIdForOptions = -1;
                optionsLayout.setVisibility(View.GONE);
                updateWidget();
            }
        });
    }

    private void clearDropHighlight() {
        for (int i = 0; i < noteListView.getChildCount(); i++) {
            noteListView.getChildAt(i).setAlpha(1f);
        }
        if (highlightedDropRow != null) {
            highlightedDropRow.animate().cancel();
            highlightedDropRow.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(100).start();
            highlightedDropRow.setElevation(0f);
            highlightedDropRow = null;
        }
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
            noteIdList.clear();
            noteNameList.clear();
            notePreviewList.clear();
            
            // Query database to get all note IDs
            SQLiteDatabase db = mydb.getReadableDatabase();
            Cursor cursor = db.rawQuery("SELECT " + DBHelper.NOTE_ID + ", " + DBHelper.NOTE_NAME + ", "
                    + DBHelper.NOTE + " FROM " + DBHelper.NOTES_TABLE_NAME + " ORDER BY " + DBHelper.NOTE_ID, null);
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    do {
                        int noteId = cursor.getInt(0);
                        String noteName = cursor.getString(1);
                        if (noteName != null) {
                            noteName = noteName.trim();
                            if (!noteName.isEmpty() && !noteName.equals("NULL")) {
                                noteIdList.add(noteId);
                                noteNameList.add(noteName);
                                String noteContent = cursor.getString(2);
                                notePreviewList.add(noteContent == null ? "" : noteContent.trim().replaceAll("\\s+", " "));
                            }
                        }
                    } while (cursor.moveToNext());
                }
                cursor.close();
            }
        } else {
            noNote.setVisibility(View.VISIBLE);
            addNote.setVisibility(View.GONE);
            noteIdList.clear();
            noteNameList.clear();
            notePreviewList.clear();
        }
        applySavedNoteOrder();
        updateAddNoteAffordance();
        noteListView.setAdapter(new CustomAdapter(this, noteIdList, noteNameList, notePreviewList));
    }

    private void updateAddNoteAffordance() {
        boolean useDock = noteIdList.size() >= 5;
        if (addNoteFooter != null) {
            android.view.ViewGroup.LayoutParams params = addNoteFooter.getLayoutParams();
            if (params != null) {
                params.height = useDock ? 0 : android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
                addNoteFooter.setLayoutParams(params);
            }
            addNoteFooter.setVisibility(useDock ? View.GONE : View.VISIBLE);
        }
        if (addNote != null) addNote.setVisibility(useDock ? View.VISIBLE : View.GONE);
    }

    private void applySavedNoteOrder() {
        String saved = getSharedPreferences("note_sort", MODE_PRIVATE).getString("ordered_ids", "");
        ArrayList<Integer> ids = new ArrayList<>();
        ArrayList<String> names = new ArrayList<>();
        ArrayList<String> previews = new ArrayList<>();
        if (!saved.isEmpty()) for (String token : saved.split(",")) {
            try {
                int id = Integer.parseInt(token), at = noteIdList.indexOf(id);
                if (at >= 0) { ids.add(id); names.add(noteNameList.get(at)); previews.add(notePreviewList.get(at)); }
            } catch (NumberFormatException ignored) { }
        }
        for (int i = 0; i < noteIdList.size(); i++) if (!ids.contains(noteIdList.get(i))) {
            ids.add(noteIdList.get(i)); names.add(noteNameList.get(i)); previews.add(notePreviewList.get(i));
        }
        noteIdList.clear(); noteIdList.addAll(ids);
        noteNameList.clear(); noteNameList.addAll(names);
        notePreviewList.clear(); notePreviewList.addAll(previews);
        saveNoteOrder(ids);
    }

    private void saveNoteOrder(ArrayList<Integer> ids) {
        StringBuilder value = new StringBuilder();
        for (int id : ids) { if (value.length() > 0) value.append(','); value.append(id); }
        getSharedPreferences("note_sort", MODE_PRIVATE).edit().putString("ordered_ids", value.toString()).apply();
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
                        java.util.ArrayList<String> serverOnlineIds = new java.util.ArrayList<>();
                        try (DBHelper db = new DBHelper(Home.this)) {
                            for (ApiService.ServerNote note : notes) {
                                db.upsertOnlineNote(note.onlineId, note.title, note.content);
                                if (note.onlineId != null && !note.onlineId.trim().isEmpty()) {
                                    serverOnlineIds.add(note.onlineId);
                                }
                            }
                            db.deleteOnlineNotesMissingFrom(serverOnlineIds);
                        }
                        refreshArrayList();
                        updateWidget();
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

    private void openNewNote() {
        Intent intent = new Intent(this, NoteView.class);
        intent.putExtra("newNote", true);
        startActivity(intent);
    }

    public void updateWidget(){
        Intent intent = new Intent(getBaseContext(), WidgetManager.class);
        intent.setAction("android.appwidget.action.APPWIDGET_UPDATE");
        int ids[] = AppWidgetManager.getInstance(getApplication()).getAppWidgetIds(new ComponentName(getApplication(), WidgetManager.class));
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS,ids);
        sendBroadcast(intent);
    }

}
