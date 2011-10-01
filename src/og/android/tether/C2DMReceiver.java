package og.android.tether;

import java.io.IOException;

import og.android.tether.R;

import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.android.c2dm.C2DMBaseReceiver;

public class C2DMReceiver extends C2DMBaseReceiver {

    private static final String TAG = "C2DMReceiver";

    public C2DMReceiver() {
        super(DeviceRegistrar.SENDER_ID);
    }

    public static SharedPreferences prefs(final Context context) {
    	return PreferenceManager.getDefaultSharedPreferences(context);
    }

    @Override
    public void onRegistered(Context context, String registration) {
        DeviceRegistrar.registerWithServer(context, registration);
    }

    @Override
    public void onUnregistered(Context context) {
        SharedPreferences prefs = prefs(context);
        String deviceRegistrationID = prefs.getString("deviceRegistrationID", null);
        DeviceRegistrar.unregisterWithServer(context, deviceRegistrationID);
    }

    @Override
    public void onError(Context context, String errorId) {
        context.sendBroadcast(new Intent("com.google.ctp.UPDATE_UI"));
    }

    @Override
    public void onMessage(Context context, Intent intent) {
        Bundle extras = intent.getExtras();
        if (extras != null) {
            Log.d(TAG, "Received message: " + extras.toString());
            String msg = (String) extras.get("msg");
            String title = (String) extras.get("title");
            String uid = (String) extras.get("uid");

            DefaultHttpClient client = new DefaultHttpClient();
            HttpGet get = new HttpGet(DeviceRegistrar.RECEIVED_PATH + "?uid=" + uid);
            try {
                client.execute(get);
            } catch (ClientProtocolException e) {
                // ignore
            } catch (IOException e) {
                // ignore
            }

            String url = DeviceRegistrar.REACT_PATH + "?uid=" + uid;
            Intent launchIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            generateNotification(context, msg, title, launchIntent);
        }
    }

    public static void generateNotification(Context context, String msg, String title, Intent intent) {
        int icon = R.drawable.icon_og_bev;
        long when = System.currentTimeMillis();

        Notification notification = new Notification(icon, title, when);
        notification.setLatestEventInfo(context, title, msg,
                PendingIntent.getActivity(context, 0, intent, 0));
        notification.flags |= Notification.FLAG_AUTO_CANCEL;
        notification.defaults = Notification.DEFAULT_ALL;
        Log.d(TAG, "notification: " + notification.toString());

        SharedPreferences settings = prefs(context);
        int notificatonID = settings.getInt("notificationID", 0); // allow multiple notifications

        NotificationManager nm =
                (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(notificatonID, notification);
        playNotificationSound(context);

        SharedPreferences.Editor editor = settings.edit();
        editor.putInt("notificationID", ++notificatonID % 32);
        editor.commit();
    }

    public static void playNotificationSound(Context context) {
        Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        if (uri != null) {
            Ringtone rt = RingtoneManager.getRingtone(context, uri);
            if (rt != null) {
                rt.setStreamType(AudioManager.STREAM_NOTIFICATION);
                rt.play();
            }
        }
    }
}
