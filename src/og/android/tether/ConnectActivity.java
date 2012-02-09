package og.android.tether;

import android.R.drawable;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Toast;

public class ConnectActivity extends Activity {
    
    private static final String TAG = "ConnectActivity";
    
    public static ConnectActivity singleton;
    
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
        
        mConnectFacebook = (Button) findViewById(R.id.connectFacebook);
        if(mPrefs.getBoolean("facebook_connected", false)) {
            mConnectFacebook.setText(getString(R.string.facebook_connected));
            ConnectActivity.this.mConnectFacebook.setCompoundDrawablesWithIntrinsicBounds(R.drawable.connect_facebook, 0, drawable.checkbox_on_background, 0);
        }
        mConnectFacebook.setOnClickListener(new OnClickListener() {
            public void onClick(View view) {
                Log.d(TAG, "onClick() " + view);
                ((TetherApplication)getApplication()).FBManager.connectToFacebook(ConnectActivity.this);
                
            }
        });
    }
    
    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "BroadcastReceiver::onReceive " + intent.getAction() + " " + intent);
            String action = intent.getAction();
            if (action.equals(FBManager.MESSAGE_FB_CONNECTED)) {
                Toast.makeText(getApplicationContext(), "Facebook Connect Complete!", Toast.LENGTH_LONG).show();
                ConnectActivity.this.mConnectFacebook.setText(getString(R.string.facebook_connected));
                ConnectActivity.this.mConnectFacebook.setCompoundDrawablesWithIntrinsicBounds(R.drawable.connect_facebook, 0, drawable.checkbox_on_background, 0);
                ConnectActivity.this.mPrefsEdit.putBoolean("facebook_connected", true);
                ConnectActivity.this.mPrefsEdit.commit();
            }
        }
    };
    
    @Override
    public void onResume() {
        super.onResume();
        IntentFilter i = new IntentFilter(FBManager.MESSAGE_FB_CONNECTED);
        registerReceiver(mReceiver, i);
    }
    
    @Override
    public void onPause() {
        super.onPause();
        try {
            unregisterReceiver(mReceiver);
        } catch(IllegalArgumentException e) {
            Log.e(TAG, "Failed unregisterReceiver", e);
        }
    }

}
