package vv.utility.vaibhav.handynotes;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.RemoteViews;

public class WidgetManager extends AppWidgetProvider {
    public static String EXTRA_WORD = "com.commonsware.android.appwidget";

    DBHelper mydb;

    @Override
    public void onUpdate(Context ctxt, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int i=0; i<appWidgetIds.length; i++) {
            Intent svcIntent=new Intent(ctxt, WidgetService.class);

            mydb = new DBHelper(ctxt);

            svcIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetIds[i]);
            svcIntent.putExtra("text", ""+mydb.getNote(0));
            svcIntent.setData(Uri.parse(svcIntent.toUri(Intent.URI_INTENT_SCHEME)));

            RemoteViews widget=new RemoteViews(ctxt.getPackageName(), R.layout.widget_layout);

            widget.setRemoteAdapter(appWidgetIds[i], R.id.widgetListView, svcIntent);

            Intent clickIntent=new Intent(ctxt,  EditWidget.class);
            PendingIntent clickPI=PendingIntent.getActivity(ctxt, 0, clickIntent, PendingIntent.FLAG_IMMUTABLE);
            widget.setPendingIntentTemplate(R.id.widgetListView, clickPI);

            appWidgetManager.updateAppWidget(appWidgetIds[i], widget);
        }

        super.onUpdate(ctxt, appWidgetManager, appWidgetIds);
        }
}