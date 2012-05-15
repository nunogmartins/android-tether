
package og.android.tether;

import og.android.tether.data.ClientData;
import og.android.tether.system.BluetoothService;

import android.app.Service;
import android.app.Notification;
import android.bluetooth.BluetoothAdapter;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.Message;
import android.os.Build;
import android.util.Log;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;

public class TetherService extends Service {
	
	public static final String MSG_TAG = "TETHER -> TetherService";

	public static final int MANAGE_START       = 1;
	public static final int MANAGE_STARTED     = 5;
	public static final int MANAGE_STOP        = 0;
	public static final int MANAGE_STOPPED     = 6;
	
	public static final int STATE_STARTING     = 3;
	public static final int STATE_RUNNING      = 0;
	public static final int STATE_STOPPING     = 4;
	public static final int STATE_IDLE         = 11;
	public static final int STATE_RESTARTING   = 9;
	
	public static final int STATE_FAIL_EXEC    = 2;
	public static final int STATE_FAIL_LOG     = 1;
	
	public static final String INTENT_STATE 		= "og.android.tether.intent.STATE";
	public static final String INTENT_MANAGE		= "og.android.tether.intent.MANAGE";
	public static final String INTENT_TRAFFIC	= "og.android.tether.intent.TRAFFIC";
	
	public static TetherService singleton = null;
	private int serviceState = STATE_IDLE;
	private TetherApplication application = null;
	private final ServiceBinder serviceBinder;
	
	private static final Class<?>[] startForegroundSignature =
			new Class[] { int.class, Notification.class };
	private static final Class<?>[] stopForegroundSignature =
			new Class[] { boolean.class };
	
	private Method startForeground;
	private Object[] startForegroundArgs = new Object[2];
	private Method stopForeground;
	private Object[] stopForegroundArgs = new Object[1];
	
	// WifiManager
	private WifiManager wifiManager = null;
	// Bluetooth Service
	BluetoothService bluetoothService = null;
	// Original States
	private static boolean origWifiState = false;
	private static boolean origBluetoothState = false;
	// Client-Connect-Thread
	private Thread clientConnectThread = null;
	// Data counters
	private Thread trafficCounterThread = null;
	// DNS-Server-Update Thread
	private Thread dnsUpdateThread = null;	
	
	public static DataCount dataCount = null;
	
	public TetherService() {
		this.serviceBinder = new ServiceBinder();
	}
	
	public static TetherService getInstance() {
		return TetherService.singleton;
	}
	
	public int getState() {
		return this.serviceState;
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		return this.serviceBinder;
	}
	
	class ServiceBinder extends Binder {
		public ServiceBinder() {}
		TetherService getService() { return TetherService.this; }
	}
	
	@Override
	public void onCreate() {		
		Log.d(MSG_TAG, "onCreate()");
		super.onCreate();

		TetherService.singleton = this;
		this.application = (TetherApplication)getApplication();
		
		// init wifiManager
        this.wifiManager = (WifiManager)this.getSystemService(Context.WIFI_SERVICE);
        
        // Bluetooth-Service
        this.bluetoothService = BluetoothService.getInstance();
        this.bluetoothService.setApplication(this.application);
        
		try {
			startForeground = getClass().getMethod("startForeground", startForegroundSignature);
			stopForeground = getClass().getMethod("stopForeground", stopForegroundSignature);
		} catch(NoSuchMethodException e) {
			startForeground = stopForeground = null;
			Log.d(MSG_TAG, "No startForeground method.");
		}
		
		if(this.application.coretask.getProp("tether.status").equals("running")) {
			Log.d(MSG_TAG, "tether.status already running!");
			this.serviceState = STATE_RUNNING;
		}
		sendBroadcastManage(MANAGE_STARTED);
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.d(MSG_TAG, "onStartCommand(): flags: " + flags + ", startid: " + startId);		
		return START_STICKY;
	}
	
	@Override
	public void onDestroy() {
		Log.d(MSG_TAG, "onDestroy()");
		if(this.serviceState == STATE_RUNNING) 
			stopTether();
		TetherService.singleton = null;
	
		super.onDestroy();
	}
	
	private void sendBroadcastManage(int state)
	{
		Intent intent = new Intent(INTENT_MANAGE);
		intent.putExtra("state", state);
		Log.d(MSG_TAG, "SENDING MANAGE: " + state);
		sendBroadcast(intent);
		
	}
	
	private void sendBroadcastState(int state) {
		Intent intent = new Intent(INTENT_STATE);
		intent.putExtra("state", state);
		//Log.d(MSG_TAG, "SENDING STATE: " + state);
		sendBroadcast(intent);
	}
	
	private void sendBroadcastTraffic(long[] trafficCount)
	{
		Intent intent = new Intent(TetherService.INTENT_TRAFFIC);
		intent.putExtra("traffic_count", trafficCount);
		sendBroadcast(intent);
	}
	
	
	void startForegroundCompat(int id, Notification notification) {
		if(startForeground != null) {
			startForegroundArgs[0] = Integer.valueOf(id);
			startForegroundArgs[1] = notification;
			try {
				startForeground.invoke(this, startForegroundArgs);
			} catch(InvocationTargetException e) {
				Log.w(MSG_TAG, "Unable to invoke startForeground");
				
			} catch(IllegalAccessException e) {
				Log.w(MSG_TAG, "Unable to invoke startForeground");
			}		
		} else {
			setForeground(true);
			this.application.notificationManager.notify(id, notification);
		}
	}
	
	void stopForegroundCompat(int id) {
		if(stopForeground != null) {
			stopForegroundArgs[0] = Boolean.TRUE;
			try {
				stopForeground.invoke(this, stopForegroundArgs);
			} catch(InvocationTargetException e) {
				Log.w(MSG_TAG, "Unable to invoke stopForeground");
			} catch(IllegalAccessException e) {
				Log.w(MSG_TAG, "Unable to invoke stopForeground");
			}
		} else {
			this.application.notificationManager.cancel(id);
			setForeground(false);
		}
	}
	
	// Start/Stop Tethering
    public void startTether() {

    		Log.d(MSG_TAG, "startTether()");
    		sendBroadcastState(this.serviceState = STATE_STARTING);
    		
    		new Thread(new Runnable() { public void run() {
    			
    		if(((!TetherService.this.application.binariesExists()) || (TetherService.this.application.coretask.filesetOutdated())) && (TetherService.this.application.coretask.hasRootPermission()))
    			TetherService.this.application.installFiles();
    		
    	    boolean started = false;
        boolean bluetoothPref = TetherService.this.application.settings.getBoolean("bluetoothon", false);
        boolean bluetoothWifi = TetherService.this.application.settings.getBoolean("bluetoothkeepwifi", false);
        
        // Updating all configs
        TetherService.this.application.updateConfiguration();

        if (bluetoothPref) {
    		if (setBluetoothState(true) == false) {
    			Log.e(MSG_TAG, "BLUETOOTH FALSE!");
    			TetherService.this.serviceState = STATE_FAIL_LOG;
    			started = false; //???
    		}
			if (bluetoothWifi == false) {
				TetherService.this.disableWifi();
			}
        } 
        else {
        	TetherService.this.disableWifi();
        }

        // Update resolv.conf-file
        String dns[] = TetherService.this.application.coretask.updateResolvConf();     
        
    		// Starting service
    		if ((TetherService.this.serviceState != STATE_RUNNING) &&
    			(TetherService.this.serviceState != STATE_FAIL_LOG)) {
    			if(started = TetherService.this.application.coretask
    					.runRootCommand(TetherService.this.application.coretask.DATA_FILE_PATH + "/bin/tether start 1")) {  				
	    			TetherService.this.serviceState = STATE_RUNNING;        	
				// Acquire Wakelock
	    			TetherService.this.application.acquireWakeLock();						
    			} else
    				TetherService.this.serviceState = STATE_FAIL_EXEC;
    		} else
    			started = true;
    		
    		if(started) {
    			try { Thread.sleep(400); } catch(InterruptedException e) {}
    			if(!TetherService.this.application.coretask.getProp("tether.status").equals("running"))
    				TetherService.this.serviceState = STATE_FAIL_LOG;
    			
    			TetherService.this.clientConnectEnable(true);
    			TetherService.this.trafficCounterEnable(true);
    			TetherService.this.dnsUpdateEnable(dns, true);
    			
    			if (Integer.parseInt(Build.VERSION.SDK) >= Build.VERSION_CODES.ECLAIR) {
					if (bluetoothPref) {
						boolean bluetoothDiscoverable = TetherService.this.application.settings.getBoolean("bluetoothdiscoverable", false);
						if (bluetoothDiscoverable) {
							TetherService.this.makeDiscoverable();
						}
					}
				}
    		}
    		
    		Log.d(MSG_TAG, "Service started: " + started + ", state: " + TetherService.this.serviceState);
    		sendBroadcastState(TetherService.this.serviceState);
    		
    		if (!started || TetherService.this.serviceState != STATE_RUNNING) {
    		    TetherService.this.enableWifi();
    		    TetherApplication.singleton.checkLaunched();
    		}
    		
    		}}).start();

    		String message;
    		switch(TetherService.this.serviceState) {
    		case TetherService.STATE_FAIL_EXEC :
    			message = getString(R.string.main_activity_start_unable);
    			break;
    		case TetherService.STATE_FAIL_LOG :
    			message = getString(R.string.main_activity_start_errors);
    			break;
    		default :
    			message = getString(R.string.global_application_tethering_running);
    			break;
    		}
    		    
    		startForegroundCompat(-1, TetherService.this.application.getStartNotification(message));
    }
    
    public void stopTether() {
    		Log.d(MSG_TAG, "stopTether()");
    		sendBroadcastState(this.serviceState = STATE_STOPPING);
    		
    		new Thread(new Runnable() { public void run() {
    			
		// Disabling polling-threads
    		TetherService.this.trafficCounterEnable(false);
    		TetherService.this.dnsUpdateEnable(false);
    		TetherService.this.clientConnectEnable(false);
    	
    		TetherService.this.application.releaseWakeLock();

        boolean bluetoothPref = TetherService.this.application.settings.getBoolean("bluetoothon", false);
        boolean bluetoothWifi = TetherService.this.application.settings.getBoolean("bluetoothkeepwifi", false);
        
    		boolean stopped = TetherService.this.application.coretask.runRootCommand(
    				TetherService.this.application.coretask.DATA_FILE_PATH+"/bin/tether stop 1");
    		if(!stopped)
    			TetherService.this.serviceState = STATE_FAIL_EXEC;
    		else
    			TetherService.this.serviceState = STATE_IDLE;
    		
    		TetherService.this.application.notificationManager.cancelAll();
		
		// Put WiFi and Bluetooth back, if applicable.
		if (bluetoothPref && origBluetoothState == false) {
			setBluetoothState(false);
		}
		if (bluetoothPref == false || bluetoothWifi == false) {
			TetherService.this.enableWifi();
		}
		Log.d(MSG_TAG, "Service stopped: " + stopped + ", state: " + TetherService.this.serviceState);

	        
          sendBroadcastState(TetherService.this.serviceState);
          sendBroadcastManage(TetherService.MANAGE_STOPPED);
          postToFacebook();
				}}).start();
    	
    		stopForegroundCompat(-1);
    }
	
    private void postToFacebook() {
        if (!application.settings.getBoolean("facebook_connected", false))
            return;
        Log.d(MSG_TAG, "postToFacebook()");    

        if(!application.settings.getBoolean("auto_post", true)) {
            Intent postActivity = getPostActivityIntent();
            startActivity(postActivity);
        } else {
            application.FBManager.postToFacebook(application.getParamsForPost(), new OnPostCompleteListener() {
                @Override
                void onPostComplete(String result) {
                    Log.d(MSG_TAG, "onPostComplete():" + result);
                    if(result == "OAuthException") {
                        Intent postActivity = getPostActivityIntent();
                        postActivity.putExtra("authorize_post", true);
                        startActivity(postActivity);
                    }
                }
            });
        }

    }
        
    Intent getPostActivityIntent() {
        Intent postStats = new Intent(Intent.ACTION_VIEW);
        postStats.setData(Uri.parse("message://" + TetherApplication.MESSAGE_POST_STATS));
        postStats.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return postStats;
    }
    
    public void restartTether() {
    		Log.d(MSG_TAG, "restartTether()");
    		sendBroadcastState(TetherService.this.serviceState = STATE_RESTARTING);
    		
    		new Thread(new Runnable() { public void run() {
    		boolean status = TetherService.this.application.coretask.runRootCommand(
    				TetherService.this.application.coretask.DATA_FILE_PATH+"/bin/tether stop 1");
    		if(!status) TetherService.this.serviceState = STATE_FAIL_EXEC;
    		
    		TetherService.this.application.notificationManager.cancelAll();
    		TetherService.this.trafficCounterEnable(false);
    	
        boolean bluetoothPref = TetherService.this.application.settings.getBoolean("bluetoothon", false);
        boolean bluetoothWifi = TetherService.this.application.settings.getBoolean("bluetoothkeepwifi", false);

        // Updating all configs
        TetherService.this.application.updateConfiguration();       
        
        if (bluetoothPref) {
    		if (setBluetoothState(true) == false){
    			status = false;
    		}
			if (bluetoothWifi == false) {
				TetherService.this.disableWifi();
			}
        } 
        else {
        	if (origBluetoothState == false) {
        		setBluetoothState(false);
        	}
        	TetherService.this.disableWifi();
        }
        
    		// Starting service
        if (TetherService.this.serviceState != STATE_RUNNING) {
        		if(status && (status = TetherService.this.application.coretask.runRootCommand(
        				TetherService.this.application.coretask.DATA_FILE_PATH+"/bin/tether start 1"))) {
        		
        			TetherService.this.application.showStartNotification(getString(R.string.global_application_tethering_running));
        			TetherService.this.trafficCounterEnable(true);
        			TetherService.this.serviceState = STATE_RUNNING;
        		} else
        			TetherService.this.serviceState = STATE_FAIL_EXEC;
        }
        
        if(status) {
        		try { Thread.sleep(400); } catch(InterruptedException e) {}
        		if(!TetherService.this.application.coretask.getProp("tether.status").equals("running"))
        			TetherService.this.serviceState = STATE_FAIL_LOG;
        }
        Log.d(MSG_TAG, "Service restarted: " + status + ", state: " + TetherService.this.serviceState);
		sendBroadcastState(TetherService.this.serviceState);
    		}}).start();     
    		//TetherService.this.application.displayToastMessage("Open Garden Tether restarted");
    }

    private boolean setBluetoothState(boolean enabled) {
		boolean connected = false;
		if (enabled == false) {
			this.bluetoothService.stopBluetooth();
			return false;
		}
		origBluetoothState = this.bluetoothService.isBluetoothEnabled();
		if (origBluetoothState == false) {
			connected = this.bluetoothService.startBluetooth();
			if (connected == false) {
				Log.d(MSG_TAG, "Enable bluetooth failed");
			}
		} else {
			connected = true;
		}
		return connected;
	}
    
    // Wifi
    private void disableWifi() {
	    	if (this.wifiManager.isWifiEnabled()) {
	    		origWifiState = true;
	    		this.wifiManager.setWifiEnabled(false);
	    		Log.d(MSG_TAG, "Wifi disabled!");
	        	// Waiting for interface-shutdown
	    		try {
	    			Thread.sleep(5000);
	    		} catch (InterruptedException e) {
	    			// nothing
	    		}
	    	}
    }
    
    //??? should be private, but SetupActivity calls it
    public void enableWifi() {
	    	if (origWifiState) {
	        	// Waiting for interface-restart
	    		this.wifiManager.setWifiEnabled(true);
	    		try {
	    			Thread.sleep(5000);
	    		} catch (InterruptedException e) {
	    			// nothing
	    		}
	    		Log.d(MSG_TAG, "Wifi started!");
	    	}
    }

    public void restartSecuredWifi() {
    	try {
			if (this.application.coretask.isNatEnabled() && this.application.coretask.isProcessRunning("bin/dnsmasq")) {
		    	Log.d(MSG_TAG, "Restarting iptables for access-control-changes!");
				if (!this.application.coretask.runRootCommand(this.application.coretask.DATA_FILE_PATH+"/bin/tether restartsecwifi 1")) {
					this.application.displayToastMessage(getString(R.string.global_application_error_restartsecwifi));
					return;
				}
			}
		} catch (Exception e) {
			// nothing
		}
    }

    private void makeDiscoverable() {
        Log.d(MSG_TAG, "Making device discoverable ...");
        Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120);
        discoverableIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(discoverableIntent);
    }
    
   	public void clientConnectEnable(boolean enable) {
   		if (enable == true) {
			if (this.clientConnectThread == null || this.clientConnectThread.isAlive() == false) {
				this.clientConnectThread = new Thread(new ClientConnect());
				this.clientConnectThread.start();
			}
   		} else {
	    	if (this.clientConnectThread != null)
	    		this.clientConnectThread.interrupt();
   		}
   	}    
    
    class ClientConnect implements Runnable {

        private ArrayList<String> knownWhitelists = new ArrayList<String>();
        private ArrayList<String> knownLeases = new ArrayList<String>();
        private Hashtable<String, ClientData> currentLeases = new Hashtable<String, ClientData>();
        private long timestampLeasefile = -1;
        private long timestampWhitelistfile = -1;

        // @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
            	//Log.d(MSG_TAG, "Checking for new clients ... ");
            	// Notification-Type
            	int notificationType = TetherService.this.application.getNotificationType();
            	// Access-Control activated
            	boolean accessControlActive = TetherService.this.application.whitelist.exists();
		        // Checking if Access-Control is activated
		        if (accessControlActive) {
                    // Checking whitelistfile
                    long currentTimestampWhitelistFile = TetherService.this.application.coretask.getModifiedDate(TetherService.this.application.coretask.DATA_FILE_PATH + "/conf/whitelist_mac.conf");
                    if (this.timestampWhitelistfile != currentTimestampWhitelistFile) {
                        knownWhitelists = TetherService.this.application.whitelist.get();
                        this.timestampWhitelistfile = currentTimestampWhitelistFile;
                    }
		        }

                // Checking leasefile
                long currentTimestampLeaseFile = TetherService.this.application.coretask.getModifiedDate(TetherService.this.application.coretask.DATA_FILE_PATH + "/var/dnsmasq.leases");
                if (this.timestampLeasefile != currentTimestampLeaseFile) {
                    try {
                    	// Getting current dns-leases
                        this.currentLeases = TetherService.this.application.coretask.getLeases();
                        
                        // Cleaning-up knownLeases after a disconnect (dhcp-release)
                        for (String lease : this.knownLeases) {
                            if (this.currentLeases.containsKey(lease) == false) {
                            	Log.d(MSG_TAG, "Removing '"+lease+"' from known-leases!");
                                this.knownLeases.remove(lease);
                            	
                                notifyActivity();
                            	TetherService.this.application.removeClientMac(lease);
                            }
                        }
                        
                        Enumeration<String> leases = this.currentLeases.keys();
                        while (leases.hasMoreElements()) {
                            String mac = leases.nextElement();
                            Log.d(MSG_TAG, "Mac-Address: '"+mac+"' - Known Whitelist: "+knownWhitelists.contains(mac)+" - Known Lease: "+knownLeases.contains(mac));
                            if (knownLeases.contains(mac) == false) {
	                            if (knownWhitelists.contains(mac) == false) {
	                            	// AddClientData to TetherApplication-Class for AccessControlActivity
	                            	TetherService.this.application.addClientData(this.currentLeases.get(mac));
	                            	
	                            	if (accessControlActive) {
	                            		if (notificationType == 1 || notificationType == 2) {
	                            			this.sendClientMessage(this.currentLeases.get(mac),
	                            					TetherApplication.CLIENT_CONNECT_NOTAUTHORIZED);
	                            		}
	                            	}
	                            	else {
	                            		if (notificationType == 2) {
	                            			this.sendClientMessage(this.currentLeases.get(mac),
	                            					TetherApplication.CLIENT_CONNECT_ACDISABLED);
	                            		}
	                            	}
	                                this.knownLeases.add(mac);
	                            } else if (knownWhitelists.contains(mac) == true) {
	                            	// AddClientData to TetherApplication-Class for AccessControlActivity
	                            	ClientData clientData = this.currentLeases.get(mac);
	                            	clientData.setAccessAllowed(true);
	                            	TetherService.this.application.addClientData(clientData);
	                            	
	                                if (notificationType == 2) {
	                                    this.sendClientMessage(this.currentLeases.get(mac),
	                                    		TetherApplication.CLIENT_CONNECT_AUTHORIZED);
	                                    this.knownLeases.add(mac);
	                                }
	                            }
	                            notifyActivity();
                            }
                        }
                        this.timestampLeasefile = currentTimestampLeaseFile;
                    } catch (Exception e) {
                        Log.d(MSG_TAG, "Unexpected error detected - Here is what I know: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        private void notifyActivity(){
        	if (AccessControlActivity.currentInstance != null){
        		AccessControlActivity.currentInstance.clientConnectHandler.sendMessage(new Message());
        	}
        }
        
        private void sendClientMessage(ClientData clientData, int connectType) {
            Message m = new Message();
            m.obj = clientData;
            m.what = connectType;
            TetherService.this.application.clientConnectHandler.sendMessage(m);
        }

    }
 
    public void dnsUpdateEnable(boolean enable) {
    		this.dnsUpdateEnable(null, enable);
    }
    
   	public void dnsUpdateEnable(String[] dns, boolean enable) {
   		if (enable == true) {
			if (this.dnsUpdateThread == null || this.dnsUpdateThread.isAlive() == false) {
				this.dnsUpdateThread = new Thread(new DnsUpdate(dns));
				this.dnsUpdateThread.start();
			}
   		} else {
	    	if (this.dnsUpdateThread != null)
	    		this.dnsUpdateThread.interrupt();
   		}
   	}
       
    class DnsUpdate implements Runnable {

    	String[] dns;
    	
    	public DnsUpdate(String[] dns) {
    		this.dns = dns;
    	}
    	
		public void run() {
            while (!Thread.currentThread().isInterrupted()) {
            	String[] currentDns = TetherService.this.application.coretask.getCurrentDns();
            	if (this.dns == null || this.dns[0].equals(currentDns[0]) == false || this.dns[1].equals(currentDns[1]) == false) {
            		this.dns = TetherService.this.application.coretask.updateResolvConf();
            	}
                // Taking a nap
       			try {
    				Thread.sleep(10000);
    			} catch (InterruptedException e) {
    				Thread.currentThread().interrupt();
    			}
            }
		}
    }    
    
   	public void trafficCounterEnable(boolean enable) {
   		if (enable == true) {
			if (this.trafficCounterThread == null || this.trafficCounterThread.isAlive() == false) {
				this.trafficCounterThread = new Thread(new TrafficCounter());
				this.trafficCounterThread.start();
			}
   		} else {
	    	if (this.trafficCounterThread != null)
	    		this.trafficCounterThread.interrupt();
   		}
   	}
   	
   	class TrafficCounter implements Runnable {
   		private static final int INTERVAL = 2;  // Sample rate in seconds.
   		long previousDownload;
   		long previousUpload;
   		long lastTimeChecked;
   		public void run() {
   			this.previousDownload = this.previousUpload = 0;
   			this.lastTimeChecked = new Date().getTime();

   			String tetherNetworkDevice = TetherService.this.application.getTetherNetworkDevice();
   			
   			while (!Thread.currentThread().isInterrupted()) {
		        // Check data count
		        long [] trafficCount = TetherService.this.application.coretask.getDataTraffic(tetherNetworkDevice);
		        long currentTime = new Date().getTime();
		        float elapsedTime = (float) ((currentTime - this.lastTimeChecked) / 1000);
		        this.lastTimeChecked = currentTime;
		        long[] trafficCount2 = new long[4];
		      
		        trafficCount2[0] = trafficCount[0];
		        trafficCount2[1] = trafficCount[1];
		        trafficCount2[2] = (long) ((trafficCount[0] - this.previousUpload)*8/elapsedTime);
		        trafficCount2[3] = (long) ((trafficCount[1] - this.previousDownload)*8/elapsedTime);
		        
				this.previousUpload = trafficCount[0];
				this.previousDownload = trafficCount[1];
				
				sendBroadcastTraffic(trafficCount2);
				
                try {
                    Thread.sleep(INTERVAL * 1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
   			}
   			DataCount dataCount = new DataCount();
   			dataCount.totalDownload = previousDownload;
   			dataCount.totalUpload = previousUpload;
   			TetherService.this.dataCount = dataCount;   			
   		}
   	}
   	
   	public class DataCount {
   		// Total data uploaded
   		public long totalUpload;
   		// Total data downloaded
   		public long totalDownload;
   		// Current upload rate
   		public long uploadRate;
   		// Current download rate
   		public long downloadRate;
   	}
   	
}

	