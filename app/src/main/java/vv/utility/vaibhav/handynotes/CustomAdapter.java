package vv.utility.vaibhav.handynotes;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.MotionEvent;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;

public class CustomAdapter extends BaseAdapter {

    public interface TalkToActivity {
        void showOptions(int noteId);
    }

    private final TalkToActivity talkToActivity;
    private final ArrayList<Integer> noteIdList;
    private final ArrayList<String> noteNameList;
    private final ArrayList<String> notePreviewList;
    private final Context context;
    private final LayoutInflater inflater;

    public CustomAdapter(Home mainActivity, ArrayList<Integer> noteIdL, ArrayList<String> noteNameL,
                         ArrayList<String> notePreviewL) {
        this.noteIdList = noteIdL;
        this.noteNameList = noteNameL;
        this.notePreviewList = notePreviewL;
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
        TextView noteName;
        TextView notePreview;
        ImageView onlineCloudBadge;
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.one_note_layout, parent, false);
            holder = new ViewHolder();
            holder.noteName = convertView.findViewById(R.id.noteName);
            holder.notePreview = convertView.findViewById(R.id.notePreview);
            holder.onlineCloudBadge = convertView.findViewById(R.id.onlineCloudBadge);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        final int currentNoteId = noteIdList.get(position);
        final String currentNoteName = noteNameList.get(position);

        holder.noteName.setText(currentNoteName);
        String preview = notePreviewList.get(position);
        holder.notePreview.setText(preview.isEmpty() ? "No additional text" : preview);

        try (DBHelper dbHelper = new DBHelper(context)) {
            boolean isOnline = dbHelper.isNoteOnline(currentNoteId);
            if (holder.onlineCloudBadge != null) {
                holder.onlineCloudBadge.setVisibility(isOnline ? View.VISIBLE : View.GONE);
            }
        }

        View noteButton = convertView.findViewById(R.id.noteButton);
        View dragHandle = convertView.findViewById(R.id.noteDragHandle);
        if (dragHandle != null) {
            dragHandle.setOnTouchListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    android.content.ClipData data = android.content.ClipData.newPlainText("noteId", String.valueOf(currentNoteId));
                    v.startDragAndDrop(data, new View.DragShadowBuilder(v), null, 0);
                    return true;
                }
                return false;
            });
        }
        if (noteButton != null) {
            noteButton.setOnClickListener(v -> {
                Intent intent = new Intent(context, NoteView.class);
                intent.putExtra("noteId", String.valueOf(currentNoteId));
                context.startActivity(intent);
            });

            noteButton.setOnLongClickListener(v -> {
                talkToActivity.showOptions(currentNoteId);
                return true;
            });
        }

        return convertView;
    }
}
