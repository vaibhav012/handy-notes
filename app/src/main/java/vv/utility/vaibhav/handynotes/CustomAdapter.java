package vv.utility.vaibhav.handynotes;

/**
 * Created by Vaibhav on 3/5/2016.
 */
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

public class CustomAdapter extends BaseAdapter{

    public interface TalkToActivity {
        public void showOptions(int noteId);
    }

    TalkToActivity talkToActivity;

    ArrayList <Integer> noteIdList;
    ArrayList <String> noteNameList;
    Context context;
    private static LayoutInflater inflater=null;
    public CustomAdapter(Home mainActivity, ArrayList <Integer> noteIdL, ArrayList <String> noteNameL) {
        // TODO Auto-generated constructor stub
        noteIdList = noteIdL;
        noteNameList = noteNameL;
        context=mainActivity;
        inflater = ( LayoutInflater )context.
                getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        talkToActivity = (TalkToActivity) mainActivity;
    }
    @Override
    public int getCount() {
        // TODO Auto-generated method stub
        return noteIdList.size();
    }

    @Override
    public Object getItem(int position) {
        // TODO Auto-generated method stub
        return position;
    }

    @Override
    public long getItemId(int position) {
        // TODO Auto-generated method stub
        return position;
    }

    public class Holder
    {
        TextView noteId;
        TextView noteName;
    }
    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        // TODO Auto-generated method stub
        Holder holder=new Holder();
        View rowView;
        rowView = inflater.inflate(R.layout.one_note_layout, null);
        holder.noteId=(TextView) rowView.findViewById(R.id.noteId);
        holder.noteName=(TextView) rowView.findViewById(R.id.noteName);
        holder.noteId.setText(noteIdList.get(position).toString());
        holder.noteName.setText(noteNameList.get(position).toString());
        rowView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                // TODO Auto-generated method stub
                Intent intent = new Intent(context, NoteView.class);
                intent.putExtra("noteId", noteIdList.get(position).toString());
                context.startActivity(intent);
            }
        });
        rowView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                //Toast.makeText(context, "You Clicked " + noteIdList.get(position).toString(), Toast.LENGTH_LONG).show();
                talkToActivity.showOptions(noteIdList.get(position));
                return true;
            }
        });
        return rowView;
    }
}
