package vv.utility.vaibhav.handynotes;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

public class WidgetViewsFactory implements RemoteViewsService.RemoteViewsFactory {
    private String[] items;
    private Context ctxt=null;

    public WidgetViewsFactory(Context ctxt, Intent intent) {
        this.ctxt=ctxt;
        String noteText = intent.getStringExtra("text");
        if (noteText != null && !noteText.isEmpty()) {
            // Display full text as a single item
            items = new String[]{noteText};
        } else {
            items = new String[]{""};
        }
    }

    @Override
    public void onCreate() {
    // no-op
    }

    @Override
    public void onDestroy() {
    // no-op
    }

    @Override
    public int getCount() {
    return(items.length);
    }

    @Override
    public RemoteViews getViewAt(int position) {
        RemoteViews row=new RemoteViews(ctxt.getPackageName(), R.layout.widget_row);

        row.setTextViewText(android.R.id.text1, items[position]);

        Intent i=new Intent();
        Bundle extras=new Bundle();

        extras.putString("noteText", items[position]);
        i.putExtras(extras);
        row.setOnClickFillInIntent(android.R.id.text1, i);

        return(row);
    }

    @Override
    public RemoteViews getLoadingView() {
        return(null);
    }

    @Override
    public int getViewTypeCount() {
    return(1);
    }

    @Override
    public long getItemId(int position) {
    return(position);
    }

    @Override
    public boolean hasStableIds() {
    return(true);
    }

    @Override
    public void onDataSetChanged() {
        // Keep the body snapshot paired with the title in this widget update.
        // Edits and navigation publish a new adapter with a unique URI.
    }
}
