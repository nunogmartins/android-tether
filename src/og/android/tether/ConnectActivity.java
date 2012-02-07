package og.android.tether;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.facebook.android.DialogError;
import com.facebook.android.Facebook;
import com.facebook.android.Facebook.DialogListener;
import com.facebook.android.FacebookError;

public class ConnectActivity extends Activity {
    
    private static final String TAG = "****Connect";
    
    Facebook facebook;

    void post() {
        Log.d(TAG, "posting()");
        Bundle params = new Bundle();
        params.putString("message", "I like Open Garden WiFi Tether!");
        params.putString("link", "http://www.opengarden.com");
        try {
            String result = facebook.request("me/feed", params, "POST");
            Log.d(TAG, ("POST RESULT: " + result));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate()");
        super.onCreate(savedInstanceState);
        
        setContentView(R.layout.connectview);
        
        facebook = new Facebook(getString(R.string.facebookAppID));    
        facebook.authorize(this, new String[] {"publish_stream", "offline_access"}, new DialogListener() {
            
            @Override
            public void onComplete(Bundle values) {
                Log.d(TAG, "onComplete() " + values);
                
                if (values.getString("access_token") != null) {
                    //post();
                }
            }
            
            @Override
            public void onFacebookError(FacebookError error) {
                Log.d(TAG, "onFacebookError() " + error);
            }
            
            @Override
            public void onError(DialogError error) {
                Log.d(TAG, "onError() " + error);
            }
            
            @Override
            public void onCancel() {
                Log.d(TAG, "onCancel");
            }
            
        });
        
    }
    
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(TAG, "onActivityResult() " + requestCode + " " + resultCode + " " + data);
        
        facebook.authorizeCallback(requestCode, resultCode, data);
    }
}
