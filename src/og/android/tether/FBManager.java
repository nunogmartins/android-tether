package og.android.tether;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;

import com.facebook.android.DialogError;
import com.facebook.android.Facebook;
import com.facebook.android.Facebook.DialogListener;
import com.facebook.android.FacebookError;

public class FBManager {
    
    private static final String TAG = "****ConnectActivity";
    private static final String FACEBOOK_APP_ID = "295077497220197";
    
    public static final String MESSAGE_FB_CONNECTED = "og.android.tether.FB_CONNECTED";
    
    public static ConnectActivity singleton;
    
    private Facebook mFacebook;
    
    FBManager() {
        mFacebook = new Facebook(FACEBOOK_APP_ID);
    }
    
    public void connectToFacebook(final Activity activity) {
        new Thread(new Runnable() {
            public void run() {
                Looper.prepare();
                mFacebook.authorize(activity, new String[] {"publish_stream", "offline_access"},
                        new FacebookConnectListener(activity));
                Looper.loop();
            }
        }).start();
    }
    
    public void postToFacebook(final Activity activity, final Bundle params) {
        new Thread(new Runnable() {
            public void run() {
                Looper.prepare();
                Log.d(TAG, "postToFacebook()");
                mFacebook.authorize(activity, new String[] {"publish_stream", "offline_access"},
                        new FacebookPostListener(activity, params));
                Looper.loop();
            }
        }).start();
    }
    
    class FacebookConnectListener implements DialogListener {

        private static final String TAG = "FBListener";
        
        private Activity mActivity;
        
        FacebookConnectListener(Activity activity) {
            mActivity = activity;
        }
        
        @Override
        public void onComplete(Bundle values) {
            Log.d(TAG, "onComplete() " + values);
            
            if (values.getString("access_token") != null) {
                Intent fbConnected = new Intent(MESSAGE_FB_CONNECTED);
                mActivity.getApplicationContext().sendBroadcast(fbConnected);
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
            Log.d(TAG, "onCancel()");
        }
        
    }

    class FacebookPostListener implements DialogListener {

        public static final String TAG = "FBListener";
        
        private Activity mActivity;
        private Bundle mBundle;
        
        FacebookPostListener(Activity activity) {
            mActivity = activity;
            Bundle params = new Bundle();
            params.putString("message", "I like Open Garden WiFi Tether.");
            params.putString("link", "http://www.opengarden.com");
            mBundle = params;
        }
        
        FacebookPostListener(Activity activity, Bundle bundle) {
            mActivity = activity;
            mBundle = bundle;
        }
        
        @Override
        public void onComplete(Bundle values) {
            Log.d(TAG, "onComplete() " + values);
            
            if (values.getString("access_token") != null) {
                post();
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
            Log.d(TAG, "onCancel()");
        }
        
        private void post() {
            Log.d(TAG, "post()");    
            try {
                Log.d(TAG, "POSTING MESSAGE:" + mBundle.getString("message"));
                String result = FBManager.this.mFacebook.request("me/feed", mBundle, "POST");
                Log.d(TAG, ("POST RESULT: " + result));
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        
    }
        
}
