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
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.Toast;

public class ConnectActivity extends Activity {
    
    private static final String TAG = "ConnectActivity";
    
    private SharedPreferences mPrefs;
    private SharedPreferences.Editor mPrefsEdit;
    private Button mConnectFacebook;
    private EditText mPostEditor;
    private CheckBox mAutoPost;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate()");
        super.onCreate(savedInstanceState);
        
        setContentView(R.layout.connectview);
        
        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        mPrefsEdit = mPrefs.edit();
        
        String message;
        if((message = mPrefs.getString("post_message", null)) == null) {
            mPrefsEdit.putString("post_message", message = getString(R.string.post_text));
            mPrefsEdit.commit();
        }
        
        mPostEditor = (EditText) findViewById(R.id.postEditor);
        mPostEditor.setText(message);
        
        mAutoPost = (CheckBox) findViewById(R.id.autoPostCheck);
        mAutoPost.setChecked(mPrefs.getBoolean("auto_post", true));
        mAutoPost.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mPrefsEdit.putBoolean("auto_post", isChecked);
                mPrefsEdit.commit();
            }
        });
        
        mConnectFacebook = (Button) findViewById(R.id.connectFacebook);
        if(mPrefs.getBoolean("facebook_connected", false)) {
            mConnectFacebook.setText(getString(R.string.facebook_connected));
            ConnectActivity.this.mConnectFacebook.setCompoundDrawablesWithIntrinsicBounds(R.drawable.connect_facebook, 0, drawable.checkbox_on_background, 0);
        }
        mConnectFacebook.setOnClickListener(new OnClickListener() {
            public void onClick(View view) {
                Log.d(TAG, "onClick() " + view);
                if(mPrefs.getBoolean("facebook_connected", false)) {
                    mConnectFacebook.setText(getString(R.string.connect_facebook));
                    ConnectActivity.this.mConnectFacebook.setCompoundDrawablesWithIntrinsicBounds(R.drawable.connect_facebook, 0, drawable.ic_menu_add, 0);
                    mPrefsEdit.putBoolean("facebook_connected", false).commit();
                    Toast.makeText(getApplicationContext(), "Facebook Disconnected.", Toast.LENGTH_LONG).show();
                } else {
                    ((TetherApplication)getApplication()).statFBConnectRequest();
                    if (((TetherApplication)getApplication()).FBManager == null)
                        ((TetherApplication)getApplication()).FBManager = new FBManager((TetherApplication)getApplication());
                    ((TetherApplication)getApplication()).FBManager.connectToFacebook(ConnectActivity.this);
                }
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
                ConnectActivity.this.mPrefsEdit.putBoolean("facebook_checked", true);
                ConnectActivity.this.mPrefsEdit.putString("fb_access_token", intent.getStringExtra("access_token"));
                ConnectActivity.this.mPrefsEdit.commit();
            }
        }
    };
    
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(TAG, "onActivityResult(): " + requestCode + " " + resultCode + " " + data);
        super.onActivityResult(requestCode, resultCode, data);

        if (((TetherApplication)getApplication()).FBManager != null)        
            ((TetherApplication)getApplication()).FBManager.authorizeCallback(requestCode, resultCode, data);
    }
    
    @Override
    public void onResume() {
        Log.d(TAG, "onResume()");
        super.onResume();
        IntentFilter i = new IntentFilter(FBManager.MESSAGE_FB_CONNECTED);
        registerReceiver(mReceiver, i);
        ((TetherApplication)getApplication()).statConnectActivity();   
    }
    
    @Override
    public void onPause() {
        Log.d(TAG, "onPause()");
        if (((TetherApplication)getApplication()).FBManager != null)
            ((TetherApplication)getApplication()).FBManager.destroyDialog();
        mPrefsEdit.putString("post_message", mPostEditor.getText().toString());
        if (((CheckBox)findViewById(R.id.autoPostCheck)).isChecked())
            mPrefsEdit.putBoolean("auto_post", true);
        mPrefsEdit.commit();
        super.onPause();
    }
    
    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy()");
        super.onDestroy();
        try {
            unregisterReceiver(mReceiver);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Failed unregisterReceiver", e);
        }
    }

}
