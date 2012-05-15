package og.android.tether;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

public class LaunchCheck {
    public static final String TAG = "TETHER -> LaunchCheck";
    public static final String CHECK_URL = "http://opengarden.com/update_check";
    public static final String CHECK_KEY = "launched";
    public static final String MESHCLIENT_GOOGLE_PLAY_URL = "market://details?id=com.opengarden.meshclient";
    public static final String MESSAGE_LAUNCH_CHECK = "og.android.meshclient/LAUNCH_CHECK";
    
    private final Callback mCallback;
        
    LaunchCheck(Callback callback) {
        if (callback == null)
            mCallback = new Callback() { public void onResult(Result result) {} };
        else
            mCallback = callback;
    }
    
    synchronized void runCheck() {
        new Thread(new Runnable() {
                public void run() {
                    String text = null;
                    InputStream inputStream = null;
                    try {
                        inputStream = httpGetInputStream(CHECK_URL);
                        text = readInputStream(inputStream);
                    } catch(Exception e) {
                        Log.d(TAG, "runCheck() error", e);
                    } finally {
                        if (inputStream == null) {
                            Log.d(TAG, "Launch unknown");
                            mCallback.onResult(Callback.Result.UNKNOWN);
                        } else if (text != null && text.contains(CHECK_KEY)) {
                            Log.d(TAG, "Launch true: " + text);
                            mCallback.onResult(Callback.Result.TRUE);
                        } else {
                            Log.d(TAG, "Launch false");
                            mCallback.onResult(Callback.Result.FALSE);
                        }
                    }
                }
        }).start();
    }
    
    InputStream httpGetInputStream(String url) {
        HttpResponse response = null;
        InputStream content = null;
        try {
            response = new DefaultHttpClient().execute(new HttpGet(CHECK_URL));
            content = response.getEntity().getContent();
        } catch (ClientProtocolException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } 
        if (response == null) {
            Log.e(TAG, "httpGet failed, no response");
            return null;
        } else {
            Log.d(TAG, "Response code: " + response.getStatusLine().getStatusCode());
            return content;
        }
    }
    
    String readInputStream(InputStream inputStream) {
        if (inputStream == null)
            return null;
        StringBuilder stringBuilder = new StringBuilder();
        try {
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            String lineSeparator = System.getProperty("line.separator");
            
            while ((line = bufferedReader.readLine()) != null) {
                stringBuilder.append(line).append(lineSeparator);
            }
            bufferedReader.close();
            
        } catch (IOException e) {
            Log.d(TAG, "readInputStream()", e);
        }
        
        return stringBuilder.toString();
    }
    
    interface Callback {
        enum Result { TRUE, FALSE, UNKNOWN }
        void onResult(Result result);
    }
    
}