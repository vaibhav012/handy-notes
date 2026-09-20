package vv.utility.vaibhav.handynotes;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;

public class CustomAdapter extends BaseAdapter {

    public interface TalkToActivity {
        void showOptions(int noteId);
    }

    private final TalkToActivity talkToActivity;
    private final ArrayList<Integer> noteIdList;
    private final ArrayList<String> noteNameList;
    private final Context context;
    private final LayoutInflater inflater;

    public CustomAdapter(Home mainActivity, ArrayList<Integer> noteIdL, ArrayList<String> noteNameL) {
        this.noteIdList = noteIdL;
        this.noteNameList = noteNameL;
        this.context = mainActivity;
        this.inflater = LayoutInflater.from(context);
        this.talkToActivity = (TalkToActivity) mainActivity;
    }

    @Override
    public int getCount() {
        return noteIdList.size();
    }

    @Override
    public Object getItem(int position) {
        return noteIdList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    private static class ViewHolder {
        TextView noteId;
        TextView noteName;
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.one_note_layout, parent, false);
            holder = new ViewHolder();
            holder.noteId = convertView.findViewById(R.id.noteId);
            holder.noteName = convertView.findViewById(R.id.noteName);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        final int currentNoteId = noteIdList.get(position);
        final String currentNoteName = noteNameList.get(position);

        holder.noteId.setText(String.valueOf(currentNoteId));
        holder.noteName.setText(currentNoteName);

        View noteButton = convertView.findViewById(R.id.noteButton);
        if (noteButton != null) {
            noteButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(context, NoteView.class);
                    intent.putExtra("noteId", String.valueOf(currentNoteId));
                    context.startActivity(intent);
                }
            });

            noteButton.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    talkToActivity.showOptions(currentNoteId);
                    return true;
                }
            });
        }

        return convertView;
    }
}
