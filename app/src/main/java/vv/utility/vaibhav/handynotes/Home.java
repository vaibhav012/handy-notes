package vv.utility.vaibhav.handynotes;

import android.app.ProgressDialog;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Build;
import android.preference.PreferenceManager;
import androidx.appcompat.app.AppCompatActivity;
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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.lang.String;

public class Home extends AppCompatActivity implements CustomAdapter.TalkToActivity{

    DBHelper mydb;

    int noteIdForOptions = -1;

    ListView noteListView;
    RelativeLayout optionsLayout;
    TextView noNote;
    Button addNote;
    LinearLayout addNoteLayout;

    ArrayList<Integer> noteIdList;
    ArrayList<String> noteNameList;

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
        noNote = (TextView) findViewById(R.id.noNote);
        addNoteLayout = (LinearLayout) findViewById(R.id.addNoteLayout);
        noteListView = (ListView) findViewById(R.id.noteListView);
        addNote = (Button) findViewById(R.id.addNote);
        final Button addNoteButton = (Button) findViewById(R.id.addNoteButton);
        final RelativeLayout widgetNoteButton = (RelativeLayout) findViewById(R.id.widgetNoteButton);
        final EditText addNoteName = (EditText) findViewById(R.id.addNoteName);
        final EditText addNoteNote = (EditText) findViewById(R.id.addNoteNote);
        final Button optionsCancel = (Button) findViewById(R.id.optionsCancel);
        final Button addToWidget = (Button) findViewById(R.id.addToWidget);
        final Button deleteNote = (Button) findViewById(R.id.deleteNote);
        //noteListView.setAdapter(new CustomAdapter(this, noteIdList, noteNameList));

        if(mydb.getCount() == 0)
            mydb.addNote(0, "Widget Note", "Click to edit Widget Note");

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
                }
            }
        });

        addNote.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAddNoteLayout();
            }
        });

        optionsCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                noteIdForOptions = -1;
                optionsLayout.setVisibility(View.GONE);
            }
        });

        addToWidget.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mydb.updateNote(0,"Widget Note",mydb.getNote(noteIdForOptions));
                noteIdForOptions = -1;
                optionsLayout.setVisibility(View.GONE);
                updateWidget();
            }
        });

        deleteNote.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mydb.deleteNote(noteIdForOptions);
                refreshArrayList();
                noteIdForOptions = -1;
                optionsLayout.setVisibility(View.GONE);
            }
        });

        widgetNoteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getBaseContext(), NoteView.class);
                intent.putExtra("noteId", "0");
                startActivity(intent);
            }
        });
    }

    @Override
    public void showOptions(int noteId) {
        noteIdForOptions = noteId;
        optionsLayout.setVisibility(View.VISIBLE);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.action_bar_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
//        switch (item.getItemId()) {
//            case R.id.action_add_note:
//                showAddNoteLayout();
//                return true;
//
//            case R.id.action_account:
//                Intent intent = new Intent(getBaseContext(),AccountActivity.class);
//                startActivity(intent);
//                return true;
//
//            case R.id.action_about_us:
//                Toast.makeText(getBaseContext(),"Robogenia",Toast.LENGTH_SHORT).show();
//                return true;
//
//            default:
//                return super.onOptionsItemSelected(item);
//        }
                return super.onOptionsItemSelected(item);
    }

    public void refreshArrayList(){
        if(mydb.createNoteId() > 1) {
            noNote.setVisibility(View.GONE);
            noteListView.setAdapter(null);
            noteIdList.clear();
            noteNameList.clear();
            for (int i = 1; i < mydb.createNoteId(); i++) {
                if (!mydb.getNoteName(i).trim().isEmpty() && (mydb.getNoteName(i).trim() != "NULL")) {
                    noteIdList.add(i);
                    noteNameList.add(mydb.getNoteName(i).trim());
                }
            }
        }
        else {
            noNote.setVisibility(View.VISIBLE);
            noteListView.setAdapter(null);
            noteIdList.clear();
            noteNameList.clear();
        }
        noteListView.setAdapter(new CustomAdapter(this, noteIdList, noteNameList));
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