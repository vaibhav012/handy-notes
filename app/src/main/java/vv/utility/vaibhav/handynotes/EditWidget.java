package vv.utility.vaibhav.handynotes;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RelativeLayout;

public class EditWidget extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_widget);

        Button cancel = (Button) findViewById(R.id.cancel);
        Button update = (Button) findViewById(R.id.update);
        final EditText widgetText = (EditText) findViewById(R.id.widgetText);
        final DBHelper mydb = new DBHelper(this);

        widgetText.setText(mydb.getNote(0));
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        update.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mydb.updateNote(0,"Widget Note", widgetText.getText().toString().trim());
                updateWidget();
                finish();
            }
        });
    }

    public void updateWidget(){
        Intent intent = new Intent(getBaseContext(), WidgetManager.class);
        intent.setAction("android.appwidget.action.APPWIDGET_UPDATE");
        int ids[] = AppWidgetManager.getInstance(getApplication()).getAppWidgetIds(new ComponentName(getApplication(), WidgetManager.class));
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
        sendBroadcast(intent);
    }
}
