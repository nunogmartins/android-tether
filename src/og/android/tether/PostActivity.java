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
import android.view.View.OnFocusChangeListener;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TableLayout.LayoutParams;
import android.widget.Toast;

import og.android.tether.OnPostCompleteListener;

public class PostActivity extends Activity {
    
    private static final String TAG = "PostActivity";
    
    private SharedPreferences mPrefs;
    private SharedPreferences.Editor mPrefsEdit;
    
    private InputMethodManager mInputManager;
    private EditText mPostEditor;
    private Button mPostButton;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate()");
        super.onCreate(savedInstanceState);
        
        setContentView(R.layout.postview);
        mInputManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        mPrefsEdit = mPrefs.edit();
        
        mPostEditor = (EditText) findViewById(R.id.postEditor);
        
        Log.d(TAG, "getIntent():" + getIntent());
        if(getIntent().getAction().equals(TetherApplication.MESSAGE_POST_STATS)) {
            String text = mPrefs.getString("post_message", getString(R.string.post_text));
            text = text.replaceFirst("\\$X", getIntent().getStringExtra("message"));
            mPostEditor.setText(text);
            
            ViewGroup.LayoutParams params = mPostEditor.getLayoutParams();
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            mPostEditor.setLayoutParams(params);
            mPostEditor.setOnFocusChangeListener(new OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    Log.d(TAG, "onFocusChange:"+v+" "+hasFocus);
                    
                    if(hasFocus) {
                       // mInputManager.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);            
                    }
                }
            });
            mPostEditor.requestFocus();
        }
        
        mPostButton = (Button) findViewById(R.id.postButton);

        mPostButton.setOnClickListener(new OnClickListener() {
            public void onClick(View view) {
                Log.d(TAG, "onClick() " + view);
                mInputManager.toggleSoftInput(0, 0);
                Bundle params = new Bundle();
                params.putString("message", mPostEditor.getText().toString());
                params.putString("link", "http://www.opengarden.com");
                ((TetherApplication)getApplication()).FBManager
                    .postToFacebook(PostActivity.this, params, new OnPostCompleteListener() {
                        @Override
                        void onPostComplete(String result) {
                            Log.d("!!!", "onPostComplete()");
                            //startActivity(new Intent(this, MainActivity.class).addFlags(Intent.))
                            finish();
                        }
                    });
                
            }
        });
    }
        
    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "BroadcastReceiver::onReceive " + intent.getAction() + " " + intent);
            String action = intent.getAction();
            if (action.equals(FBManager.MESSAGE_FB_CONNECTED)) {

            }
        }
    };
    
    @Override
    public void onResume() {
        Log.d(TAG, "onResume()");
        mInputManager.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
        super.onResume();
        IntentFilter i = new IntentFilter();
        registerReceiver(mReceiver, i);
    }
    
    @Override
    public void onPause() {
        Log.d(TAG, "onPause()");
        mInputManager.toggleSoftInput(0, 0);
        super.onPause();
        try {
            unregisterReceiver(mReceiver);
        } catch(IllegalArgumentException e) {
            Log.e(TAG, "Failed unregisterReceiver", e);
        }
    }
}
