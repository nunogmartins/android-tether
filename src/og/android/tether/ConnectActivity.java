package og.android.tether;

import android.app.Activity;
import android.os.Bundle;

import com.facebook.android.DialogError;
import com.facebook.android.Facebook;
import com.facebook.android.Facebook.DialogListener;
import com.facebook.android.FacebookError;

public class ConnectActivity extends Activity {
    
    Facebook facebook;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setContentView(R.layout.connectview);
        
        facebook = new Facebook(getString(R.string.facebookAppID));    
        facebook.authorize(this, new DialogListener() {
            @Override
            public void onComplete(Bundle values) {}
            
            @Override
            public void onFacebookError(FacebookError error) {}
            
            @Override
            public void onError(DialogError error) {}
            
            @Override
            public void onCancel() {}
            
        });
        
    }
    
}
