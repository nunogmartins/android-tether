package og.android.tether;

import java.util.ArrayList;
import java.util.List;

import og.android.tether.system.WebserviceTask;

import org.apache.http.HttpResponse;
import org.apache.http.message.BasicNameValuePair;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Configuration;
import android.preference.PreferenceManager;
import android.provider.Settings.Secure;
import android.util.Log;

public class DeviceRegistrar {
    public static final String STATUS_EXTRA = "Status";
    public static final int REGISTERED_STATUS = 1;
    public static final int AUTH_ERROR_STATUS = 2;
    public static final int UNREGISTERED_STATUS = 3;
    public static final int ERROR_STATUS = 4;

    private static final String TAG = "DeviceRegistrar";
    static final String SENDER_ID = "c2dm@opengarden.com";

    static final String BASE_URL = "https://opengarden.com/c2dm";
    private static final String REGISTER_PATH = BASE_URL + "/register";
    private static final String UNREGISTER_PATH = BASE_URL + "/unregister";
    static final String RECEIVED_PATH = BASE_URL + "/received";
    static final String REACT_PATH = BASE_URL + "/react";

    public static SharedPreferences prefs(final Context context) {
    	return PreferenceManager.getDefaultSharedPreferences(context);
    }

    public static void registerWithServer(final Context context,
          final String deviceRegistrationID) {
        new Thread(new Runnable() {
            public void run() {
                try {
                    HttpResponse res = makeRequest(context, deviceRegistrationID, REGISTER_PATH);
                    if (res.getStatusLine().getStatusCode() == 200) {
                        Log.i(TAG, "Registration with server complete");
                        Editor editor = prefs(context).edit();
                        editor.putBoolean("c2dm_registered", true);
                        editor.commit();
                    } else {
                        Log.w(TAG, "Registration error " +
                                String.valueOf(res.getStatusLine().getStatusCode()));
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Registration error " + e.getMessage());
                }
            }
        }).start();
    }

    public static void unregisterWithServer(final Context context,
            final String deviceRegistrationID) {
        new Thread(new Runnable() {
            public void run() {
                try {
                    HttpResponse res = makeRequest(context, deviceRegistrationID, UNREGISTER_PATH);
                    if (res.getStatusLine().getStatusCode() == 200) {
                        Log.i(TAG, "Unregistration with server complete");
                        Editor editor = prefs(context).edit();
                        editor.putBoolean("c2dm_registered", false);
                        editor.commit();
                    } else {
                        Log.w(TAG, "Unregistration error " +
                                String.valueOf(res.getStatusLine().getStatusCode()));
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Unregistration error " + e.getMessage());
                }
            }
        }).start();
    }

    private static HttpResponse makeRequest(Context context, String deviceRegistrationID,
            String urlPath) throws Exception {
        List<BasicNameValuePair> params = new ArrayList<BasicNameValuePair>();
        params.add(new BasicNameValuePair("c2dm_id", deviceRegistrationID));

        String aid = Secure.getString(context.getContentResolver(), Secure.ANDROID_ID);
        if (aid != null) {
            params.add(new BasicNameValuePair("aid", aid));
        }

        // TODO: Allow device name to be configured
        params.add(new BasicNameValuePair("device_type", isTablet(context) ? "Tablet" : "Phone"));

        return WebserviceTask.makeRequest(urlPath, params);
    }

    static boolean isTablet (Context context) {
        // TODO: This hacky stuff goes away when we allow users to target devices
        int xlargeBit = 4; // Configuration.SCREENLAYOUT_SIZE_XLARGE;  // upgrade to HC SDK to get this
        Configuration config = context.getResources().getConfiguration();
        return (config.screenLayout & xlargeBit) == xlargeBit;
    }
}
