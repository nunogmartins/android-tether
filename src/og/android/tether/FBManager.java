package og.android.tether;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;

import com.facebook.android.DialogError;
import com.facebook.android.Facebook;
import com.facebook.android.Facebook.DialogListener;
import com.facebook.android.FacebookError;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;

import org.json.JSONException;
import org.json.JSONObject;

public class FBManager {
    
    private static final String TAG = "****ConnectActivity";
    private static final String FACEBOOK_APP_ID = "295077497220197";
    
    public static final String MESSAGE_FB_CONNECTED = "og.android.tether.FB_CONNECTED";
    
    private Facebook mFacebook;
    
    FBManager() {
        mFacebook = new Facebook(FACEBOOK_APP_ID);
    }
    
    public void connectToFacebook(final Activity activity) {
        new Thread(new Runnable() {
            public void run() {
                Log.d(TAG, "connectToFacebook()");
                Looper.prepare();
                mFacebook.authorize(activity, new String[] {"publish_stream", "offline_access"},
                        new FacebookConnectListener(activity));
                Looper.loop();
            }
        }).start();
    }
    
    public void postToFacebookWithAuthorize(final Activity activity, final Bundle params, final OnPostCompleteListener listener) {
        new Thread(new Runnable() {
            public void run() {
                Log.d(TAG, "postToFacebookWithAuthorize()");
                Looper.prepare();
                mFacebook.authorize(activity, new String[] {"publish_stream", "offline_access"},
                        new FacebookPostListener(activity, params, listener));
                Looper.loop();
            }
        }).start();
    }
    
    public void postToFacebook(final Bundle params, final OnPostCompleteListener listener) {
        new Thread(new Runnable() {
            public void run() {
                Looper.prepare();
                Log.d(TAG, "postToFacebook()");
                String result = postToFacebook(params);
                if(listener != null)
                    listener.onPostComplete(result);
                Looper.loop(); // ???
            }
        }).start();
    }
    
    private String postToFacebook(Bundle params) {
        String result = null;
        try {
            result = FBManager.this.mFacebook.request("me/feed", params, "POST");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        Log.d(TAG, "Facebook Post Result: " + result);
            
        try {
            JSONObject resultInfo = new JSONObject(result);
            if(resultInfo.has("id")) {
                result = "ok";
            } else if(resultInfo.has("error")) {
                result = "error";
                resultInfo = resultInfo.getJSONObject("error");
                if(resultInfo.getString("type").equals("OAuthException")) {
                    result = "OAuthException";
                }
            } else {
                result = "unknown";
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        
        return result;
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
                Intent fbConnected = new Intent(MESSAGE_FB_CONNECTED)
                    .putExtra("access_token", values.getString("access_token"));
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
        private OnPostCompleteListener mListener = null;
        
        FacebookPostListener(Activity activity) {
            mActivity = activity;
        }
        
        FacebookPostListener(Activity activity, Bundle bundle) {
            mActivity = activity;
            mBundle = bundle;
        }
        
        FacebookPostListener(Activity activity, Bundle bundle, OnPostCompleteListener listener) {
            mActivity = activity;
            mBundle = bundle;
            mListener = listener;
        }
        
        @Override
        public void onComplete(Bundle values) {
            Log.d(TAG, "onComplete() " + values);
            if (values.getString("access_token") != null) {
                String result = postToFacebook(mBundle);
                if(mListener != null)
                    mListener.onPostComplete(result);
                ((TetherApplication)mActivity.getApplication()).preferenceEditor
                    .putString("fb_access_token", values.getString("access_token")).commit();
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
    
    void authorizeCallback(int requestCode, int resultCode, Intent data) {
        mFacebook.authorizeCallback(requestCode, resultCode, data);
    }
    
}

abstract class OnPostCompleteListener {
    abstract void onPostComplete(String result);
        
}
