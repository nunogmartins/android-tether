package og.android.tether;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;

import android.R.drawable;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Toast;

import com.facebook.android.DialogError;
import com.facebook.android.Facebook;
import com.facebook.android.Facebook.DialogListener;
import com.facebook.android.FacebookError;

public class ConnectActivity extends Activity {
    
    private static final String TAG = "****ConnectActivity";
    private static final String FACEBOOK_APP_ID = "295077497220197";
    
    public static ConnectActivity singleton;
    
    private Facebook mFacebook;
    private SharedPreferences mPrefs;
    private SharedPreferences.Editor mPrefsEdit;
    private Button mConnectFacebook;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate()");
        super.onCreate(savedInstanceState);
        
        setContentView(R.layout.connectview);
        
        ConnectActivity.singleton = this;
        
        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        mPrefsEdit = mPrefs.edit();
        mFacebook = new Facebook(getString(R.string.facebookAppID));
        
        mConnectFacebook = (Button) findViewById(R.id.connectFacebook);
        if(mPrefs.getBoolean("facebook_connected", false)) {
            mConnectFacebook.setText(getString(R.string.facebook_connected));
            ConnectActivity.this.mConnectFacebook.setCompoundDrawablesWithIntrinsicBounds(R.drawable.connect_facebook, 0, drawable.checkbox_on_background, 0);
        }
        mConnectFacebook.setOnClickListener(new OnClickListener() {
            public void onClick(View view) {
                Log.d(TAG, "onClick() " + view);
                mFacebook.authorize(ConnectActivity.this, new String[] {"publish_stream", "offline_access"},
                        new FacebookConnectListener());
                
            }
        });
    }
    
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(TAG, "onActivityResult() " + requestCode + " " + resultCode + " " + data);
        
        mFacebook.authorizeCallback(requestCode, resultCode, data);
    }
    
    public void postToFacebook(final Bundle params) {
        new Thread(new Runnable() {
            public void run() {
                Looper.prepare();
                post(params);
                Looper.loop();
            }
        }).start();
    }

    private void post(Bundle params) {
        Log.d(TAG, "post()");
        mFacebook.authorize(MainActivity.currentInstance, new String[] {"publish_stream", "offline_access"},
                new ConnectActivity.FacebookPostListener(params));

    }
    
    class FacebookConnectListener implements DialogListener {

        public static final String TAG = "FBListener";
                
        @Override
        public void onComplete(Bundle values) {
            Log.d(TAG, "onComplete() " + values);
            
            if (values.getString("access_token") != null) {
                Toast.makeText(getApplicationContext(), "Facebook Connect Complete!", Toast.LENGTH_LONG).show();
                ConnectActivity.this.mConnectFacebook.setText(getString(R.string.facebook_connected));
                ConnectActivity.this.mConnectFacebook.setCompoundDrawablesWithIntrinsicBounds(R.drawable.connect_facebook, 0, drawable.checkbox_on_background, 0);
                ConnectActivity.this.mPrefsEdit.putBoolean("facebook_connected", true);
                ConnectActivity.this.mPrefsEdit.commit();
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
        
        private Bundle mBundle;
        
        FacebookPostListener() {
            Bundle params = new Bundle();
            params.putString("message", "I like Open Garden WiFi Tether.");
            params.putString("link", "http://www.opengarden.com");
            mBundle = params;
        }
        
        FacebookPostListener(Bundle bundle) {
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
                String result = ConnectActivity.this.mFacebook.request("me/feed", mBundle, "POST");
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
