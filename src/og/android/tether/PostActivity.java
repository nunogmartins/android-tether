package og.android.tether;

import android.R.drawable;
import android.app.Activity;
import android.content.Context;
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

import og.android.tether.OnPostCompleteListener;

public class PostActivity extends Activity {
    
    private static final String TAG = "PostActivity";
    
    private SharedPreferences mPrefs;
    private SharedPreferences.Editor mPrefsEdit;
    
    private InputMethodManager mInputManager;
    private EditText mPostEditor;
    private Button mPostButton;
    private CheckBox mCheckFacebook;
    
    private Bundle mParams = null;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate()");
        super.onCreate(savedInstanceState);
        
        setContentView(R.layout.postview);
        mInputManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        mPrefsEdit = mPrefs.edit();
        
        mCheckFacebook = (CheckBox) findViewById(R.id.facebookCheck);
        mCheckFacebook.setChecked(mPrefs.getBoolean("facebook_checked", false));
        
        mPostButton = (Button) findViewById(R.id.postButton);
        mPostButton.setOnClickListener(new OnClickListener() {
            public void onClick(View view) {
                Log.d(TAG, "onClick() " + view);
                mInputManager.toggleSoftInput(0, 0);
                if(mCheckFacebook.isChecked()) {
                    mPrefsEdit.putBoolean("facebook_checked", true).commit();
                    mParams.putString("message", mPostEditor.getText().toString());
                    postToFacebook(mParams);
                } else {
                    mPrefsEdit.putBoolean("facebook_checked", false).commit();
                    finish();
                }
            }
        });
        mPostEditor = (EditText) findViewById(R.id.postEditor);
        
        Log.d(TAG, "onCreate() intent: " + getIntent());
        if(getIntent().getData().getPath().equals("/POST_STATS")) {
            
            mParams = ((TetherApplication)getApplication()).getParamsForPost();
            mPostEditor.setText(mParams.getString("message"));
            
            ViewGroup.LayoutParams layoutParams = mPostEditor.getLayoutParams();
            layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            mPostEditor.setLayoutParams(layoutParams);
            mPostEditor.setOnFocusChangeListener(new OnFocusChangeListener() {
                public void onFocusChange(View v, boolean hasFocus) {
                    Log.d(TAG, "onFocusChange:"+v+" "+hasFocus);
                    
                    if(hasFocus) {
                       // mInputManager.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);            
                    }
                }
            });
            mPostEditor.requestFocus();
            
            if(getIntent().getBooleanExtra("authorize_post", false)) {
                postToFacebookWithAuthorize(mParams);
                return;
            }
            if(mPrefs.getBoolean("auto_post", true)) {
                postToFacebook(mParams);
                return;
            }
        }
        
    }
        
    void postToFacebook(Bundle params) {
        ((TetherApplication)getApplication()).FBManager
            .postToFacebook(params, new OnPostCompleteListener() {
                @Override
                void onPostComplete(String result) {
                    Log.d(TAG, "onPostComplete()");
                    PostActivity.this.finish();
                }
            });
    }
    
    void postToFacebookWithAuthorize(Bundle params) {
        ((TetherApplication)getApplication()).FBManager
            .postToFacebookWithAuthorize(PostActivity.this, params, new OnPostCompleteListener() {
                @Override
                void onPostComplete(String result) {
                    Log.d(TAG, "onPostComplete()");
                    PostActivity.this.finish();
                }
            });
    }
    
    @Override
    public void onResume() {
        Log.d(TAG, "onResume()");
        mInputManager.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
        super.onResume();
    }
    
    @Override
    public void onPause() {
        Log.d(TAG, "onPause()");
        mInputManager.toggleSoftInput(0, 0);
        if (((TetherApplication)getApplication()).FBManager != null)
            ((TetherApplication)getApplication()).FBManager.destroyDialog();
        super.onPause();
    }

}
