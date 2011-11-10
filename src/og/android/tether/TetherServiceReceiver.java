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
		//Log.d(MSG_TAG, "onReceive state: " + intentArg.getIntExtra("state", 999));
		
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
				if(TetherService.singleton != null)
					TetherService.singleton.startTether();
				break;
			case TetherService.MANAGE_STOP :
				if(TetherService.singleton != null)
					TetherService.singleton.stopTether();
				break;
			case TetherService.MANAGE_STOPPED :
				if(TetherService.singleton != null)
					TetherService.singleton.stopSelf();
				break;
			default : break;
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