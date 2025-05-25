package vv.utility.vaibhav.handynotes;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.widget.RemoteViews;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import java.util.ArrayList;
import java.util.List;

public class WidgetManager extends AppWidgetProvider {
    public static String EXTRA_WORD = "com.commonsware.android.appwidget";
    public static String EXTRA_SELECTED_NOTE_ID = "selected_note_id";

    DBHelper mydb;

    @Override
    public void onUpdate(Context ctxt, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int i=0; i<appWidgetIds.length; i++) {
            Intent svcIntent=new Intent(ctxt, WidgetService.class);

            mydb = new DBHelper(ctxt);

            svcIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetIds[i]);
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctxt);
            int selectedNoteId = prefs.getInt("widget_note_id_" + appWidgetIds[i], 0);
            svcIntent.putExtra("text", ""+mydb.getNote(selectedNoteId));
            svcIntent.putExtra(EXTRA_SELECTED_NOTE_ID, selectedNoteId);
            svcIntent.setData(Uri.parse(svcIntent.toUri(Intent.URI_INTENT_SCHEME)));

            RemoteViews widget=new RemoteViews(ctxt.getPackageName(), R.layout.widget_layout);

            widget.setRemoteAdapter(appWidgetIds[i], R.id.widgetListView, svcIntent);

            Intent clickIntent=new Intent(ctxt,  EditWidget.class);
            PendingIntent clickPI=PendingIntent.getActivity(ctxt, 0, clickIntent, PendingIntent.FLAG_IMMUTABLE);
            widget.setPendingIntentTemplate(R.id.widgetListView, clickPI);

            List<String> noteNames = new ArrayList<>();
            for (int j = 0; j < mydb.createNoteId(); j++) {
                String noteName = mydb.getNoteName(j);
                if (noteName != null && !noteName.equals("NULL")) {
                    noteNames.add(noteName);
                }
            }
            ArrayAdapter<String> adapter = new ArrayAdapter<>(ctxt, android.R.layout.simple_spinner_item, noteNames);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            widget.setAdapter(R.id.widget_spinner, adapter);

            Intent spinnerIntent = new Intent(ctxt, WidgetManager.class);
            spinnerIntent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
            spinnerIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, new int[]{appWidgetIds[i]});
            PendingIntent spinnerPI = PendingIntent.getBroadcast(ctxt, appWidgetIds[i], spinnerIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            widget.setOnClickPendingIntent(R.id.widget_spinner, spinnerPI);

            appWidgetManager.updateAppWidget(appWidgetIds[i], widget);
        }

        super.onUpdate(ctxt, appWidgetManager, appWidgetIds);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (intent.getAction().equals(AppWidgetManager.ACTION_APPWIDGET_UPDATE)) {
            int appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
                int selectedNoteId = prefs.getInt("widget_note_id_" + appWidgetId, 0);
                AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
                onUpdate(context, appWidgetManager, new int[]{appWidgetId});
            }
        }
    }
}