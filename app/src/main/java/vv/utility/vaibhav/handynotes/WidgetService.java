package vv.utility.vaibhav.handynotes;

import android.content.Intent;
import android.widget.RemoteViewsService;

public class WidgetService extends RemoteViewsService {
  @Override
  public RemoteViewsFactory onGetViewFactory(Intent intent) {
    return(new WidgetViewsFactory(this.getApplicationContext(), intent));
  }
}