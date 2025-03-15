package vv.utility.vaibhav.handynotes;

import android.content.Intent;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

public class NoteView extends AppCompatActivity {

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
        DBHelper mydb = new DBHelper(this);

        int noteId = Integer.parseInt(intent.getStringExtra("noteId"));

        TextView noteIdd = (TextView) findViewById(R.id.noteId);
        TextView noteName = (TextView) findViewById(R.id.noteName);
        TextView noteNote = (TextView) findViewById(R.id.noteNote);
        TextView dateAndTime = (TextView) findViewById(R.id.dateAndTime);

        if(noteId == 0) {
            noteIdd.setText("W");
        }
        else
            noteIdd.setText("" + noteId);
        noteName.setText(mydb.getNoteName(noteId).toString().trim());
        noteNote.setText(mydb.getNote(noteId).toString().trim());
        dateAndTime.setText(mydb.getDateTime(noteId).toString().trim());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.action_bar_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_add_note:
                Intent intent = new Intent(getBaseContext(),Home.class);
                intent.putExtra("fromAccounts","true");
                startActivity(intent);
                return true;

            case R.id.action_account:
                Intent intent1 = new Intent(getBaseContext(),AccountActivity.class);
                startActivity(intent1);
                return true;

            case R.id.action_about_us:
                Toast.makeText(getBaseContext(),"Robogenia",Toast.LENGTH_SHORT).show();
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }
}
