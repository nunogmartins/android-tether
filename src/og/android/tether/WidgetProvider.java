package og.android.tether;

import og.android.tether.R;

import android.content.Context;
import android.content.Intent;
import android.appwidget.AppWidgetProvider;
import android.appwidget.AppWidgetManager;
import android.app.PendingIntent;
import android.widget.RemoteViews;
import android.view.View;

public class WidgetProvider extends AppWidgetProvider {

    Context ctx;

    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        final int N = appWidgetIds.length;

        // Perform this loop procedure for each App Widget that belongs to this provider
        for (int i=0; i<N; i++) {
            int appWidgetId = appWidgetIds[i];

            // Create an Intent to launch ExampleActivity
            Intent intent = new Intent(context, MainActivity.class);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);            
            PendingIntent pendingIntent = PendingIntent.getActivity(context, 2, intent, Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NEW_TASK);

            // Get the layout for the App Widget and attach an on-click listener
            // to the button
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.appwidget_provider_layout);
            views.setOnClickPendingIntent(R.id.button, pendingIntent);

            // Tell the AppWidgetManager to perform an update on the current app widget
            appWidgetManager.updateAppWidget(appWidgetId, views);
        }

        ctx = context;
    }

    public void launchApp(View v)
    {
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_LAUNCHER);

        ctx.startActivity(i);
    }
}
