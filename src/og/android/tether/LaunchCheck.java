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
    public static final String EXTRA_LAUNCH_CHECK = "launched";
    private Context mContext;
    
    LaunchCheck(Context context) {
        mContext = context;
    }
    
    synchronized void runCheck() {
        new Thread(new Runnable() {
                public void run() {
                    try {
                        String text = readInputStream(httpGetInputStream(CHECK_URL));
                        if (text != null && text.contains(CHECK_KEY)) {
                            Log.d(TAG, "Update true");
                            Intent launchDialog = new Intent(Intent.ACTION_VIEW)
                                .setData(Uri.parse("message://" + MESSAGE_LAUNCH_CHECK))
                                .putExtra(EXTRA_LAUNCH_CHECK, true)
                                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NEW_TASK);
                            mContext.startActivity(launchDialog);
                        } else
                            Log.d(TAG, "Update false");
                    } catch(Exception e) {
                        Log.d(TAG, "Error", e);
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
            Log.e(TAG, "httpGet failed: no response.");
        }
        else if (response.getStatusLine().getStatusCode() != 200) {
            Log.e(TAG, "httpGet failed: " + response.getStatusLine().getStatusCode());
        } else {
            Log.d(TAG, "Response code: " + response.getStatusLine().getStatusCode());
        }
        
        return content;
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
    
}