package og.android.tether;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class AlarmReceiver extends BroadcastReceiver {
    
    private static String TAG = "AlarmReceiver";
    
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "onReceive(): " + context + " " + intent);
        if (intent.getAction().equals(TetherApplication.MESSAGE_REPORT_STATS)) {
            TetherApplication.singleton.reportStats(-2);
        }
    }
}
