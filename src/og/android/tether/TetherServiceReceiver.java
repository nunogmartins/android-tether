package og.android.tether;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ComponentName;
import android.content.Intent;
import android.util.Log;

public class TetherServiceReceiver extends BroadcastReceiver {
	
	static final String MSG_TAG = "TETHER -> ServiceReceiver";

	@Override
	public void onReceive(Context contextArg, Intent intentArg) {
		Log.d(MSG_TAG, "onReceive:" + intentArg + " state:" + intentArg.getIntExtra("state", -9));
		
		if(intentArg.getAction().equals(TetherService.INTENT_MANAGE)) {		
			switch(intentArg.getIntExtra("state", TetherService.MANAGE_START))
			{
			case TetherService.MANAGE_START :
				if(TetherService.singleton == null)
					contextArg.startService(new Intent(contextArg, TetherService.class));
				else
					sendBroadcastManage(contextArg, TetherService.MANAGE_STARTED);
				break;
			case TetherService.MANAGE_STARTED :
				if( (TetherService.singleton != null) &&
					(TetherService.singleton.getState() != TetherService.STATE_STARTING) &&
					(TetherService.singleton.getState() != TetherService.STATE_RESTARTING) )
					TetherService.singleton.startTether();
				break;
			case TetherService.MANAGE_STOP :
				if( (TetherService.singleton != null) &&
					(TetherService.singleton.getState() != TetherService.STATE_STOPPING) )
					TetherService.singleton.stopTether();
				break;
			case TetherService.MANAGE_STOPPED :
				if(TetherService.singleton != null)
					TetherService.singleton.stopSelf();
				break;
			default : break;
			} 
		} else if(intentArg.getAction().equals(TetherService.INTENT_STATE)) {
		    int serviceState = intentArg.getIntExtra("state", -9);
		    switch(serviceState) {
		    case TetherService.STATE_RUNNING :
		    case TetherService.STATE_FAIL_EXEC :
		    case TetherService.STATE_FAIL_LOG :
		    case TetherService.STATE_IDLE :
		        TetherApplication.singleton.reportStats(serviceState);
		        break;
		    }
		}
		
	}
	
	void sendBroadcastManage(Context context, int state)
	{
		Intent intent = new Intent(TetherService.INTENT_MANAGE);
		//intent.setComponent(new ComponentName("og.android.tether", "og.android.tether.TetherService"));
		intent.putExtra("state", state);
		Log.d(MSG_TAG, "SENDING MANAGE: " + state);
		context.sendBroadcast(intent);

	}
	 
}