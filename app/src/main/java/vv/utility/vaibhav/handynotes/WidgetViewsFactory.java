package vv.utility.vaibhav.handynotes;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;
import android.widget.Toast;

public class WidgetViewsFactory implements RemoteViewsService.RemoteViewsFactory {
    private static String[] items={"sdrfgaerterthedrt " +
          "aedfaswedfqweffqw " + "aedfaswedfqweffqw " + "aedfaswedfqweffqw " + "aedfaswedfqweffqw " +
          "aedfaswedfqweffqw " + "aedfaswedfqweffqw " + "aedfaswedfqweffqw " + "aedfaswedfqweffqw " +
          "aedfaswedfqweffqw " + "aedfaswedfqweffqw " + "aedfaswedfqweffqw " + "aedfaswedfqweffqw " +
          "aedfaswedfqweffqw "};
    private Context ctxt=null;
    private int appWidgetId;

    public WidgetViewsFactory(Context ctxt, Intent intent) {
        this.ctxt=ctxt;
        appWidgetId=intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        String tryString = intent.getStringExtra("text");
        items[0] = ""+tryString;
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

        //Toast.makeText(ctxt,"ok..",Toast.LENGTH_SHORT).show();
        row.setTextViewText(android.R.id.text1, items[position]);

        Intent i=new Intent();
        Bundle extras=new Bundle();

        extras.putString(WidgetManager.EXTRA_WORD, items[position]);
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
    // no-op
}
}