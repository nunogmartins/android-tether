/**
 *  This program is free software; you can redistribute it and/or modify it under 
 *  the terms of the GNU General Public License as published by the Free Software 
 *  Foundation; either version 3 of the License, or (at your option) any later 
 *  version.
 *  You should have received a copy of the GNU General Public License along with 
 *  this program; if not, see <http://www.gnu.org/licenses/>. 
 *  Use this application at your own risk.
 *
 *  Copyright (c) 2009 by Harald Mueller and Sofia Lemons.
 */

package og.android.tether;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Properties;

import og.android.tether.R;
import og.android.tether.data.ClientData;
import og.android.tether.system.Configuration;
import og.android.tether.system.CoreTask;
import og.android.tether.system.WebserviceTask;

import android.app.AlarmManager;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.provider.Settings.Secure;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

public class TetherApplication extends Application {

	public static final String MSG_TAG = "TETHER -> TetherApplication";

	public final String DEFAULT_PASSPHRASE = "abcdefghijklm";
	public final String DEFAULT_LANNETWORK = "192.168.2.0/24";
	public final String DEFAULT_ENCSETUP   = "wpa_supplicant";
	public final String DEFAULT_SSID		  = "OpenGarden";
	
	// Devices-Information
	public String deviceType = Configuration.DEVICE_GENERIC; 
	public String interfaceDriver = Configuration.DRIVER_WEXT; 
	
	// StartUp-Check perfomed
	public boolean startupCheckPerformed = false;
	
	//??? package private
	static final int CLIENT_CONNECT_ACDISABLED = 0;
	static final int CLIENT_CONNECT_AUTHORIZED = 1;
	static final int CLIENT_CONNECT_NOTAUTHORIZED = 2;
	

	static TetherApplication singleton;
	
	//public String tetherNetworkDevice = null;
	
	// PowerManagement
	private PowerManager powerManager = null;
	private PowerManager.WakeLock wakeLock = null;
	
	// Preferences
	public SharedPreferences settings = null;
	public SharedPreferences.Editor preferenceEditor = null;
	
    // Notification
	public NotificationManager notificationManager;
	private Notification notification;
	private int clientNotificationCount = 0;
	
	// Intents
	private PendingIntent mainIntent;
	private PendingIntent accessControlIntent;
    	
	// Client
	ArrayList<ClientData> clientDataAddList = new ArrayList<ClientData>();
	ArrayList<String> clientMacRemoveList = new ArrayList<String>();
	
	// Access-control
	boolean accessControlSupported = true;
	
	// Whitelist
	public CoreTask.Whitelist whitelist = null;
	// Supplicant
	public CoreTask.WpaSupplicant wpasupplicant = null;
	// TiWlan.conf
	public CoreTask.TiWlanConf tiwlan = null;
	// tether.conf
	public CoreTask.TetherConfig tethercfg = null;
	// dnsmasq.conf
	public CoreTask.DnsmasqConfig dnsmasqcfg = null;
	// hostapd
	public CoreTask.HostapdConfig hostapdcfg = null;
	// blue-up.sh
	public CoreTask.BluetoothConfig btcfg = null;
	
	// CoreTask
	public CoreTask coretask = null;
	
	public FBManager FBManager = null;
	
	// Update Url
	private static final String APPLICATION_PROPERTIES_URL = "https://github.com/opengarden/android-tether/raw/stable/application.properties";
	private static final String APPLICATION_DOWNLOAD_URL = "https://github.com/opengarden/android-tether/raw/stable/files";
	private static final String APPLICATION_STATS_URL = "http://opengarden.com/android-tether/stats";
	static final String FORUM_RSS_URL = "http://forum.opengarden.com/categories/wifi-tether-support/feed.rss";
	
	static final String MESSAGE_POST_STATS = "og.android.tether/POST_STATS";
	static final String MESSAGE_REPORT_STATS = "og.android.tether.REPORT_STATS";
	
	@Override
	public void onCreate() {
		Log.d(MSG_TAG, "Calling onCreate()");
		
		TetherApplication.singleton = this;
		
		//create CoreTask
		this.coretask = new CoreTask();
		this.coretask.setPath(this.getApplicationContext().getFilesDir().getParent());
		Log.d(MSG_TAG, "Current directory is "+this.coretask.DATA_FILE_PATH);
		
        // Check Homedir, or create it
        this.checkDirs(); 
        
        // Set device-information
        this.deviceType = Configuration.getDeviceType();
        this.interfaceDriver = Configuration.getWifiInterfaceDriver(this.deviceType);
        
        // Preferences
		this.settings = PreferenceManager.getDefaultSharedPreferences(this);
		
        // preferenceEditor
        this.preferenceEditor = settings.edit();
		       
        // Whitelist
        this.whitelist = this.coretask.new Whitelist();
        
        // Supplicant config
        this.wpasupplicant = this.coretask.new WpaSupplicant();
        
        // tiwlan.conf
        this.tiwlan = this.coretask.new TiWlanConf();
        
        // tether.cfg
        this.tethercfg = this.coretask.new TetherConfig();
        this.tethercfg.read();

	    	// dnsmasq.conf
	    	this.dnsmasqcfg = this.coretask.new DnsmasqConfig();
	    	
	    	// hostapd
	    	this.hostapdcfg = this.coretask.new HostapdConfig();
	    	
	    	// blue-up.sh
	    	this.btcfg = this.coretask.new BluetoothConfig();        
        
        // Powermanagement
        powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "TETHER_WAKE_LOCK");

        if (this.settings.getBoolean("facebook_connected", false)) {
            FBManager = new FBManager(this);
            FBManager.extendAccessTokenIfNeeded(this, null);
        }
        
        // init notificationManager
        this.notificationManager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);
    	this.notification = new Notification(R.drawable.start_notification, "Open Garden Wifi Tether", System.currentTimeMillis());
    	this.mainIntent = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), 0);
    	this.accessControlIntent = PendingIntent.getActivity(this, 1, new Intent(this, AccessControlActivity.class), 0);
    	requestStatsAlarm();
	}

	@Override
	public void onTerminate() {
		Log.d(MSG_TAG, "Calling onTerminate()");
		// Stopping Tether
		//???this.stopTether();
		// Remove all notifications
		this.notificationManager.cancelAll();
	}
	
	// ClientDataList Add
	public synchronized void addClientData(ClientData clientData) {
		this.clientDataAddList.add(clientData);
	}

	public synchronized void removeClientMac(String mac) {
		this.clientMacRemoveList.add(mac);
	}
	
	public synchronized ArrayList<ClientData> getClientDataAddList() {
		ArrayList<ClientData> tmp = this.clientDataAddList;
		this.clientDataAddList = new ArrayList<ClientData>();
		return tmp;
	}
	
	public synchronized ArrayList<String> getClientMacRemoveList() {
		ArrayList<String> tmp = this.clientMacRemoveList;
		this.clientMacRemoveList = new ArrayList<String>();
		return tmp;
	}	
	
	public synchronized void resetClientMacLists() {
		this.clientDataAddList = new ArrayList<ClientData>();
		this.clientMacRemoveList = new ArrayList<String>();
	}
	
	
	
	public void updateConfiguration() {
		
		long startStamp = System.currentTimeMillis();
		
        boolean bluetoothPref = this.settings.getBoolean("bluetoothon", false);
		boolean encEnabled = this.settings.getBoolean("encpref", false);
		boolean acEnabled = this.settings.getBoolean("acpref", false);
		String ssid = this.settings.getString("ssidpref", DEFAULT_SSID);
        String txpower = this.settings.getString("txpowerpref", "disabled");
        String lannetwork = this.settings.getString("lannetworkpref", DEFAULT_LANNETWORK);
        String wepkey = this.settings.getString("passphrasepref", DEFAULT_PASSPHRASE);
        String wepsetupMethod = this.settings.getString("encsetuppref", DEFAULT_ENCSETUP);
        String channel = this.settings.getString("channelpref", "1");
        
		// tether.conf
        String subnet = lannetwork.substring(0, lannetwork.lastIndexOf("."));
        this.tethercfg.read();
		this.tethercfg.put("device.type", deviceType);
        this.tethercfg.put("tether.mode", bluetoothPref ? "bt" : "wifi");
        this.tethercfg.put("wifi.essid", ssid);
        this.tethercfg.put("wifi.channel", channel);
		this.tethercfg.put("ip.network", lannetwork.split("/")[0]);
		this.tethercfg.put("ip.gateway", subnet + ".254");    
		if (Configuration.enableFixPersist()) {
			this.tethercfg.put("tether.fix.persist", "true");
		}
		else {
			this.tethercfg.put("tether.fix.persist", "false");
		}
		if (Configuration.enableFixRoute()) {
			this.tethercfg.put("tether.fix.route", "true");
		}
		else {
			this.tethercfg.put("tether.fix.route", "false");
		}
		
		/**
		 * TODO: Quick and ugly workaround for nexus
		 */
		if (Configuration.getDeviceType().equals(Configuration.DEVICE_NEXUSONE) &&
				Configuration.getWifiInterfaceDriver(this.deviceType).equals(Configuration.DRIVER_SOFTAP_GOG)) {			
			this.tethercfg.put("wifi.interface", "wl0.1");
		}
		else {
			this.tethercfg.put("wifi.interface", this.coretask.getProp("wifi.interface"));
		}

		this.tethercfg.put("wifi.txpower", txpower);

		// wepEncryption
		if (encEnabled) {
			if (this.interfaceDriver.startsWith("softap")) {
				this.tethercfg.put("wifi.encryption", "wpa2-psk");
			}
			else if (this.interfaceDriver.equals(Configuration.DRIVER_HOSTAP)) {
				this.tethercfg.put("wifi.encryption", "unused");
			}
			else {
				this.tethercfg.put("wifi.encryption", "wep");
			}
			// Storing wep-key
			this.tethercfg.put("wifi.encryption.key", wepkey);

			// Getting encryption-method if setup-method on auto 
			if (wepsetupMethod.equals("auto")) {
				wepsetupMethod = Configuration.getEncryptionAutoMethod(deviceType);
			}
			// Setting setup-mode
			this.tethercfg.put("wifi.setup", wepsetupMethod);
			// Prepare wpa_supplicant-config if wpa_supplicant selected
			if (wepsetupMethod.equals("wpa_supplicant")) {
				// Install wpa_supplicant.conf-template
				if (this.wpasupplicant.exists() == false) {
					this.installWpaSupplicantConfig();
				}
				
				// Update wpa_supplicant.conf
				Hashtable<String,String> values = new Hashtable<String,String>();
				values.put("ssid", "\""+this.settings.getString("ssidpref", DEFAULT_SSID)+"\"");
				values.put("wep_key0", "\""+this.settings.getString("passphrasepref", DEFAULT_PASSPHRASE)+"\"");
				this.wpasupplicant.write(values);
				
				/*
				// Make sure the ctrl_interface (directory) exists
				File ctrlInterfaceDir = new File("/data/data/og.android.tether/var/wpa_supplicant");
				if (ctrlInterfaceDir.exists() == false) {
					if (ctrlInterfaceDir.mkdirs() == false) {
						Log.e(MSG_TAG, "Unable to create ctrl-interface (directory) for wpa_supplicant!");
					}
				}*/
			}
        }
		else {
			this.tethercfg.put("wifi.encryption", "open");
			this.tethercfg.put("wifi.encryption.key", "none");
			
			// Make sure to remove wpa_supplicant.conf
			if (this.wpasupplicant.exists()) {
				this.wpasupplicant.remove();
			}			
		}
		
		// determine driver wpa_supplicant
		this.tethercfg.put("wifi.driver", Configuration.getWifiInterfaceDriver(deviceType));
		
		// writing config-file
		if (this.tethercfg.write() == false) {
			Log.e(MSG_TAG, "Unable to update tether.conf!");
		}
		
		// dnsmasq.conf
		this.dnsmasqcfg.set(lannetwork);
		if (this.dnsmasqcfg.write() == false) {
			Log.e(MSG_TAG, "Unable to update dnsmasq.conf!");
		}
		
		// hostapd.conf
		if (this.interfaceDriver.equals(Configuration.DRIVER_HOSTAP)) {
			this.installHostapdConfig();
			this.hostapdcfg.read();
			
			// Update the hostapd-configuration in case we have Motorola Droid X
			if (this.deviceType.equals(Configuration.DEVICE_DROIDX)) {
				this.hostapdcfg.put("ssid", ssid);
				this.hostapdcfg.put("channel", channel);
				if (encEnabled) {
					this.hostapdcfg.put("wpa", ""+2);
					this.hostapdcfg.put("wpa_pairwise", "CCMP");
					this.hostapdcfg.put("rsn_pairwise", "CCMP");
					this.hostapdcfg.put("wpa_passphrase", wepkey);
				}
			}
			// Update the hostapd-configuration in case we have ZTE Blade
			else if (this.deviceType.equals(Configuration.DEVICE_BLADE)) {
				this.hostapdcfg.put("ssid", ssid);
				this.hostapdcfg.put("channel_num", channel);
				if (encEnabled) {
					this.hostapdcfg.put("wpa", ""+2);
					this.hostapdcfg.put("wpa_key_mgmt", "WPA-PSK");
					this.hostapdcfg.put("wpa_pairwise", "CCMP");
					this.hostapdcfg.put("wpa_passphrase", wepkey);
				}				
			}
			
			if (this.hostapdcfg.write() == false) {
				Log.e(MSG_TAG, "Unable to update hostapd.conf!");
			}
			
			/*
			// Make sure the ctrl_interface (directory) exists
			File ctrlInterfaceDir = new File("/data/data/og.android.tether/var/hostapd");
			if (ctrlInterfaceDir.exists() == false) {
				if (ctrlInterfaceDir.mkdirs() == false) {
					Log.e(MSG_TAG, "Unable to create ctrl-interface (directory) for hostapd!");
				}
			}*/
		}
		
		// blue-up.sh
		this.btcfg.set(lannetwork);
		if (this.btcfg.write() == false) {
			Log.e(MSG_TAG, "Unable to update blue-up.sh!");
		}
		
		// whitelist
		if (acEnabled) {
			if (this.whitelist.exists() == false) {
				try {
					this.whitelist.touch();
				} catch (IOException e) {
					Log.e(MSG_TAG, "Unable to update whitelist-file!");
					e.printStackTrace();
				}
			}
		}
		else {
			if (this.whitelist.exists()) {
				this.whitelist.remove();
			}
		}
		
		/*
		 * TODO
		 * Need to find a better method to identify if the used device is a
		 * HTC Dream aka T-Mobile G1
		 */
		if (deviceType.equals(Configuration.DEVICE_DREAM)) {
			Hashtable<String,String> values = new Hashtable<String,String>();
			values.put("dot11DesiredSSID", this.settings.getString("ssidpref", DEFAULT_SSID));
			values.put("dot11DesiredChannel", this.settings.getString("channelpref", "1"));
			this.tiwlan.write(values);
		}
		
		Log.d(MSG_TAG, "Creation of configuration-files took ==> "+(System.currentTimeMillis()-startStamp)+" milliseconds.");
	}
	

    
    public String getTetherNetworkDevice() {
    		boolean bluetoothPref = this.settings.getBoolean("bluetoothon", false);
        if (bluetoothPref)
			return "bnep";
		else {
			/**
			 * TODO: Quick and ugly workaround for nexus
			 */
			if (Configuration.getDeviceType().equals(Configuration.DEVICE_NEXUSONE) &&
					Configuration.getWifiInterfaceDriver(this.deviceType).equals(Configuration.DRIVER_SOFTAP_GOG)) {
				return "wl0.1";
			}
			else {
				return this.coretask.getProp("wifi.interface");
			}
		}
    }
    
    // gets user preference on whether wakelock should be disabled during tethering
    public boolean isWakeLockDisabled(){
		return this.settings.getBoolean("wakelockpref", true);
	} 
	
    // gets user preference on whether sync should be disabled during tethering
    public boolean isSyncDisabled(){
		return this.settings.getBoolean("syncpref", false);
	}
    
    // gets user preference on whether sync should be disabled during tethering
    public boolean isUpdatecDisabled(){
		return this.settings.getBoolean("updatepref", false);
	}
    
    // get preferences on whether donate-dialog should be displayed
    public boolean showDonationDialog() {
    	return this.settings.getBoolean("donatepref", true);
    }

   
    
    // WakeLock
	public void releaseWakeLock() {
		try {
			if(this.wakeLock != null && this.wakeLock.isHeld()) {
				Log.d(MSG_TAG, "Trying to release WakeLock NOW!");
				this.wakeLock.release();
			}
		} catch (Exception ex) {
			Log.d(MSG_TAG, "Ups ... an exception happend while trying to release WakeLock - Here is what I know: "+ex.getMessage());
		}
	}
    
	public void acquireWakeLock() {
		try {
			if (this.isWakeLockDisabled() == false) {
				Log.d(MSG_TAG, "Trying to acquire WakeLock NOW!");
				this.wakeLock.acquire();
			}
		} catch (Exception ex) {
			Log.d(MSG_TAG, "Ups ... an exception happend while trying to acquire WakeLock - Here is what I know: "+ex.getMessage());
		}
	}
    
    public int getNotificationType() {
		return Integer.parseInt(this.settings.getString("notificationpref", "2"));
    }
    
    // Notification
    public void showStartNotification(String message) {

		notification.flags = Notification.FLAG_ONGOING_EVENT;
		notification.setLatestEventInfo(this, getString(R.string.global_application_name), message, this.mainIntent);
    		this.notificationManager.notify(-1, this.notification);
    }

    public Notification getStartNotification(String message) {
		notification.flags = Notification.FLAG_ONGOING_EVENT;
		notification.setLatestEventInfo(this, getString(R.string.global_application_name), message, this.mainIntent);
    		return notification;
    }
    
    Handler clientConnectHandler = new Handler() {
 	   public void handleMessage(Message msg) {
 		    ClientData clientData = (ClientData)msg.obj;
 		   TetherApplication.this.showClientConnectNotification(clientData, msg.what);
 	   }
    };
    
    public void showClientConnectNotification(ClientData clientData, int authType) {
    	int notificationIcon = R.drawable.secmedium;
    	String notificationString = "";
    	switch (authType) {
	    	case CLIENT_CONNECT_ACDISABLED :
	    		notificationIcon = R.drawable.secmedium;
	    		notificationString = getString(R.string.global_application_accesscontrol_disabled);
	    		break;
	    	case CLIENT_CONNECT_AUTHORIZED :
	    		notificationIcon = R.drawable.sechigh;
	    		notificationString = getString(R.string.global_application_accesscontrol_authorized);
	    		break;
	    	case CLIENT_CONNECT_NOTAUTHORIZED :
	    		notificationIcon = R.drawable.seclow;
	    		notificationString = getString(R.string.global_application_accesscontrol_authorized);
    	}
		Log.d(MSG_TAG, "New (" + notificationString + ") client connected ==> "+clientData.getClientName()+" - "+clientData.getMacAddress());
 	   	Notification clientConnectNotification = new Notification(notificationIcon, getString(R.string.global_application_name), System.currentTimeMillis());
 	   	clientConnectNotification.tickerText = clientData.getClientName()+" ("+clientData.getMacAddress()+")";
 	   	if (!this.settings.getString("notifyring", "").equals(""))
 	   		clientConnectNotification.sound = Uri.parse(this.settings.getString("notifyring", ""));

 	   	if(this.settings.getBoolean("notifyvibrate", true))
 	   		clientConnectNotification.vibrate = new long[] {100, 200, 100, 200};

 	   	if (this.accessControlSupported) 
 	   		clientConnectNotification.setLatestEventInfo(this, getString(R.string.global_application_name)+" - " + notificationString, clientData.getClientName()+" ("+clientData.getMacAddress()+") "+getString(R.string.global_application_connected)+" ...", this.accessControlIntent);
 	   	else 
 	   		clientConnectNotification.setLatestEventInfo(this, getString(R.string.global_application_name)+" - " + notificationString, clientData.getClientName()+" ("+clientData.getMacAddress()+") "+getString(R.string.global_application_connected)+" ...", this.mainIntent);
 	   	
 	   	clientConnectNotification.flags = Notification.FLAG_AUTO_CANCEL;
 	   	this.notificationManager.notify(this.clientNotificationCount, clientConnectNotification);
 	   	this.clientNotificationCount++;
    }    
    
    public boolean binariesExists() {
    	File file = new File(this.coretask.DATA_FILE_PATH+"/bin/tether");
    	return file.exists();
    }
    
    public void installWpaSupplicantConfig() {
    	this.copyFile(this.coretask.DATA_FILE_PATH+"/conf/wpa_supplicant.conf", "0644", R.raw.wpa_supplicant_conf);
    }
    
    public void installHostapdConfig() {
    	if (this.deviceType.equals(Configuration.DEVICE_DROIDX)) {
    		this.copyFile(this.coretask.DATA_FILE_PATH+"/conf/hostapd.conf", "0644", R.raw.hostapd_conf_droidx);
    	}
    	else if (this.deviceType.equals(Configuration.DEVICE_BLADE)) {
    		this.copyFile(this.coretask.DATA_FILE_PATH+"/conf/hostapd.conf", "0644", R.raw.hostapd_conf_blade);
    	}
    }
    
    Handler displayMessageHandler = new Handler(){
        public void handleMessage(Message msg) {
       		if (msg.obj != null) {
       			TetherApplication.this.displayToastMessage((String)msg.obj);
       		}
        	super.handleMessage(msg);
        }
    };

    public void installFiles() {
    	new Thread(new Runnable(){
			public void run(){
				String message = null;
				// tether
		    	if (message == null) {
			    	message = TetherApplication.this.copyFile(TetherApplication.this.coretask.DATA_FILE_PATH+"/bin/tether", "0755", R.raw.tether);
		    	}
		    	// dnsmasq
		    	if (message == null) {
			    	message = TetherApplication.this.copyFile(TetherApplication.this.coretask.DATA_FILE_PATH+"/bin/dnsmasq", "0755", R.raw.dnsmasq);
		    	}
		    	// iptables
		    	if (message == null) {
			    	message = TetherApplication.this.copyFile(TetherApplication.this.coretask.DATA_FILE_PATH+"/bin/iptables", "0755", R.raw.iptables);
		    	}
		    	// ifconfig
		    	if (message == null) {
			    	message = TetherApplication.this.copyFile(TetherApplication.this.coretask.DATA_FILE_PATH+"/bin/ifconfig", "0755", R.raw.ifconfig);
		    	}	
		    	// iwconfig
		    	if (message == null) {
			    	message = TetherApplication.this.copyFile(TetherApplication.this.coretask.DATA_FILE_PATH+"/bin/iwconfig", "0755", R.raw.iwconfig);
		    	}
		    	// ultra_bcm_config
		    	if (message == null) {
			    	message = TetherApplication.this.copyFile(TetherApplication.this.coretask.DATA_FILE_PATH+"/bin/ultra_bcm_config", "0755", R.raw.ultra_bcm_config);
		    	}
		    	//pand
		    	if (message == null) {
			    	message = TetherApplication.this.copyFile(TetherApplication.this.coretask.DATA_FILE_PATH+"/bin/pand", "0755", R.raw.pand);
		    	}
		    	// blue-up.sh
				if (message == null) {
					message = TetherApplication.this.copyFile(TetherApplication.this.coretask.DATA_FILE_PATH+"/bin/blue-up.sh", "0755", R.raw.blue_up_sh);
				}
				// blue-down.sh
				if (message == null) {
					message = TetherApplication.this.copyFile(TetherApplication.this.coretask.DATA_FILE_PATH+"/bin/blue-down.sh", "0755", R.raw.blue_down_sh);
				}		
				
				/**
				 * Installing fix-scripts if needed
				 */
				if (Configuration.enableFixPersist()) {	
					// fixpersist.sh
					if (message == null) {
						message = TetherApplication.this.copyFile(TetherApplication.this.coretask.DATA_FILE_PATH+"/bin/fixpersist.sh", "0755", R.raw.fixpersist_sh);
					}				
				}
				if (Configuration.enableFixRoute()) {
					// fixroute.sh
					if (message == null) {
						message = TetherApplication.this.copyFile(TetherApplication.this.coretask.DATA_FILE_PATH+"/bin/fixroute.sh", "0755", R.raw.fixroute_sh);
					}
				}
				
		    	// dnsmasq.conf
				if (message == null) {
					message = TetherApplication.this.copyFile(TetherApplication.this.coretask.DATA_FILE_PATH+"/conf/dnsmasq.conf", "0644", R.raw.dnsmasq_conf);
					TetherApplication.this.coretask.updateDnsmasqFilepath();
				}
		    	// tiwlan.ini
				if (message == null) {
					TetherApplication.this.copyFile(TetherApplication.this.coretask.DATA_FILE_PATH+"/conf/tiwlan.ini", "0644", R.raw.tiwlan_ini);
				}
				// edify script
				if (message == null) {
					TetherApplication.this.copyFile(TetherApplication.this.coretask.DATA_FILE_PATH+"/conf/tether.edify", "0644", R.raw.tether_edify);
				}
				// tether.cfg
				if (message == null) {
					TetherApplication.this.copyFile(TetherApplication.this.coretask.DATA_FILE_PATH+"/conf/tether.conf", "0644", R.raw.tether_conf);
				}
				
				// wpa_supplicant drops privileges, we need to make files readable.
				TetherApplication.this.coretask.chmod(TetherApplication.this.coretask.DATA_FILE_PATH+"/conf/", "0755");

				if (message == null) {
			    	message = getString(R.string.global_application_installed);
				}
				
				// Sending message
				Message msg = new Message();
				msg.obj = message;
				TetherApplication.this.displayMessageHandler.sendMessage(msg);
			}
		}).start();
    }
    
    public static Field getDeclaredField(String className, String fieldName) {
        try {
            return Class.forName(className).getDeclaredField(fieldName);
        } catch (SecurityException e) {
            Log.d(MSG_TAG, "getDeclaredField failed", e);
        } catch (NoSuchFieldException e) {
            Log.d(MSG_TAG, "getDeclaredField failed", e);
        } catch (ClassNotFoundException e) {
            Log.d(MSG_TAG, "getDeclaredField failed", e);
        }
        return null;
    }
    
    public boolean isProviderSupported(String checkProvider) {
        List<String> providers;
        // isProviderEnabled should throws a IllegalArgumentException if provider is not supported
        // But in sdk 1.1 the exception is catched by isProviderEnabled itself.
        // Therefore check out the list of providers instead (which indeed does not
        // report a provider it does not exist in the device) Undocumented is that
        // this call can throw a SecurityException
        try {
            LocationManager lm = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
            providers = lm.getAllProviders();
        } catch (Throwable e) {
            return false;
        }
        
        // scan the list for the specified provider
        for (String provider : providers) {
            if (checkProvider.equals(provider)) {
                return true;
            }
        }
        
        // not supported
        return false;
    }

    private boolean isPackageInstalled(String packageName) {
		PackageManager packageManager = getPackageManager();
		boolean installed = false;
		try {
			packageManager.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES);
			installed = true;
		} catch(PackageManager.NameNotFoundException e) {}
		return installed;
    }

    public void reportStats(int status) {
        final HashMap<String,Object> h = new HashMap<String,Object>();
        h.put("aid", Settings.Secure.getString(getContentResolver(), Secure.ANDROID_ID));
        h.put("aver", Build.VERSION.RELEASE);
        h.put("asdk", Build.VERSION.SDK_INT);
        h.put("mdl", Build.MODEL);
        Field mfr = getDeclaredField("android.os.Build", "MANUFACTURER");
        if (mfr != null) {
            try {
                h.put("mfr", mfr.get(null));
            } catch (IllegalArgumentException e) {
            } catch (IllegalAccessException e) {
            }
        }
        TelephonyManager tm = (TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE);
        h.put("mno", tm.getNetworkOperatorName());
        h.put("imei", tm.getDeviceId());
        LocationManager lm = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
        if (isProviderSupported(LocationManager.PASSIVE_PROVIDER)) {
            Location l = lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER);
            if (l != null) {
                h.put("loc", String.format("%s,%s", l.getLatitude(), l.getLongitude()));
            }
        }
        h.put("c2dm", settings.getBoolean("c2dm_registered", false)); 
        h.put("inst", settings.getLong("install_timestamp", -1));
        h.put("tver", getVersionNumber());
        h.put("root", coretask.hasRootPermission());
        //h.put("suok", coretask.rootWorks());
        h.put("nflt", coretask.isNetfilterSupported());
        h.put("actl", coretask.isAccessControlSupported());
        h.put("tpow", isTransmitPowerSupported());
        h.put("blth", Configuration.hasKernelFeature("CONFIG_BT_BNEP="));
        h.put("sfap", Configuration.hasKernelFeature("CONFIG_BCM4329_SOFTAP="));
        h.put("dtyp", deviceType);
        h.put("idrv", interfaceDriver);
        h.put("bin", binariesExists());
        h.put("stat", status);
        //h.put("dur", duration);
        String tetherNetworkDevice = TetherApplication.this.getTetherNetworkDevice();
        long [] trafficCount = TetherApplication.this.coretask.getDataTraffic(tetherNetworkDevice);
        h.put("bup", trafficCount[0]);
        h.put("bdwn", trafficCount[1]);
        h.put("ffox", isPackageInstalled("org.mozilla.firefox"));
		try {
	        Field f = BluetoothSocket.class.getDeclaredField("TYPE_EL2CAP");
	        f.setAccessible(true);
	        h.put("ertm", f.get(BluetoothSocket.class));
		} catch (Exception e) {
			h.put("ertm", false);
		}
		h.put("fbon", settings.getBoolean("facebook_connected", false));
		h.put("coac", settings.getInt("connect_activities", 0));
		h.put("fbcr", settings.getInt("fb_connect_requests", 0));
		h.put("fbco", settings.getInt("fb_connects", 0));
		h.put("fbok", settings.getInt("fb_posts_ok", 0));
		h.put("fber", settings.getInt("fb_posts_error", 0));
		h.put("fbau", settings.getBoolean("auto_post", false));
		h.put("widg", settings.getInt("widget_clicks", 0));
		h.put("comc", settings.getInt("community_clicks", 0));
		h.put("rssc", settings.getInt("rss_clicks", 0));

		if(status == -2) {
		    Log.d(MSG_TAG, "Reporting stats: " + h.toString());
		    WebserviceTask.report(APPLICATION_STATS_URL, h);
		    Log.d(MSG_TAG, "Reporting of stats complete");		    
		} else {
		    new Thread(new Runnable(){
		        public void run(){
		            Looper.prepare();
		            Log.d(MSG_TAG, "Reporting stats: " + h.toString());
		            WebserviceTask.report(APPLICATION_STATS_URL, h);
		            Log.d(MSG_TAG, "Reporting of stats complete");
		            Looper.loop();
		        }
		    }).start();
		}
    }

    public void statFBPostOk() {
        this.preferenceEditor.putInt("fb_posts_ok",
                this.settings.getInt("fb_posts_ok", 0) + 1)
                    .commit();
    }
    
    public void statFBPostError() {
        this.preferenceEditor.putInt("fb_posts_error",
                this.settings.getInt("fb_posts_error", 0) + 1)
                    .commit();
    }
    
    public void statConnectActivity() {
        this.preferenceEditor.putInt("connect_activities",
                this.settings.getInt("connect_activities", 0) + 1)
                    .commit();
    }

    public void statFBConnectRequest() {
        this.preferenceEditor.putInt("fb_connect_requests",
                this.settings.getInt("fb_connect_requests", 0) + 1)
                    .commit();       
    }
    
    public void statFBConnectOk() {
        this.preferenceEditor.putInt("fb_connects",
                this.settings.getInt("fb_connects", 0) + 1)
                    .commit();       
    }
    
    public void statCommunityClicks() {
        this.preferenceEditor.putInt("community_clicks",
                this.settings.getInt("community_clicks", 0) + 1)
                    .commit();
    }
    
    public void statRSSClicks() {
        this.preferenceEditor.putInt("rss_clicks",
                this.settings.getInt("rss_clicks", 0) + 1)
                    .commit();
    }
    
    /*
     * Update checking. We go to a predefined URL and fetch a properties style file containing
     * information on the update. These properties are:
     * 
     * versionCode: An integer, version of the new update, as defined in the manifest. Nothing will
     *              happen unless the update properties version is higher than currently installed.
     * fileName: A string, URL of new update apk. If not supplied then download buttons
     *           will not be shown, but instead just a message and an OK button.
     * message: A string. A yellow-highlighted message to show to the user. Eg for important
     *          info on the update. Optional.
     * title: A string, title of the update dialog. Defaults to "Update available".
     * 
     * Only "versionCode" is mandatory.
     */
    public void checkForUpdate() {
    	if (this.isUpdatecDisabled()) {
    		Log.d(MSG_TAG, "Update-checks are disabled!");	
    		return;
    	}
    	new Thread(new Runnable(){
			public void run(){
				Looper.prepare();
				// Getting Properties
				Properties updateProperties = WebserviceTask.queryForProperty(APPLICATION_PROPERTIES_URL);
				if (updateProperties != null && updateProperties.containsKey("versionCode")) {
				  
					int availableVersion = Integer.parseInt(updateProperties.getProperty("versionCode"));
					int installedVersion = TetherApplication.this.getVersionNumber();
					String fileName = updateProperties.getProperty("fileName", "");
					String updateMessage = updateProperties.getProperty("message", "");
					String updateTitle = updateProperties.getProperty("title", "Update available");
					if (availableVersion != installedVersion) {
						Log.d(MSG_TAG, "Installed version '"+installedVersion+"' and available version '"+availableVersion+"' do not match!");
						MainActivity.currentInstance.openUpdateDialog(APPLICATION_DOWNLOAD_URL+fileName,
						    fileName, updateMessage, updateTitle);
					}
				}
				Looper.loop();
			}
    	}).start();
    }
   
    public void downloadUpdate(final String downloadFileUrl, final String fileName) {
    	new Thread(new Runnable(){
			public void run(){
				Message msg = Message.obtain();
            	msg.what = MainActivity.MESSAGE_DOWNLOAD_STARTING;
            	msg.obj = "Downloading update...";
            	MainActivity.currentInstance.viewUpdateHandler.sendMessage(msg);
				WebserviceTask.downloadUpdateFile(downloadFileUrl, fileName);
				Intent intent = new Intent(Intent.ACTION_VIEW); 
			    intent.setDataAndType(android.net.Uri.fromFile(new File(WebserviceTask.DOWNLOAD_FILEPATH+"/"+fileName)),"application/vnd.android.package-archive"); 
			    MainActivity.currentInstance.startActivity(intent);
			}
    	}).start();
    }
    
    private String copyFile(String filename, String permission, int ressource) {
    	String result = this.copyFile(filename, ressource);
    	if (result != null) {
    		return result;
    	}
    	if (this.coretask.chmod(filename, permission) != true) {
    		result = "Can't change file-permission for '"+filename+"'!";
    	}
    	return result;
    }
    
    private String copyFile(String filename, int ressource) {
    	File outFile = new File(filename);
    	Log.d(MSG_TAG, "Copying file '"+filename+"' ...");
    	InputStream is = this.getResources().openRawResource(ressource);
    	byte buf[] = new byte[1024];
        int len;
        try {
        	OutputStream out = new FileOutputStream(outFile);
        	while((len = is.read(buf))>0) {
				out.write(buf,0,len);
			}
        	out.close();
        	is.close();
		} catch (IOException e) {
			return "Couldn't install file - "+filename+"!";
		}
		return null;
    }
    
    private void checkDirs() {
    	File dir = new File(this.coretask.DATA_FILE_PATH);
    	if (dir.exists() == false) {
    			this.displayToastMessage("Application data-dir does not exist!");
    	}
    	else {
    		//String[] dirs = { "/bin", "/var", "/conf", "/library" };
    		String[] dirs = { "/bin", "/var", "/conf" };
    		for (String dirname : dirs) {
    			dir = new File(this.coretask.DATA_FILE_PATH + dirname);
    	    	if (dir.exists() == false) {
    	    		if (!dir.mkdir()) {
    	    			this.displayToastMessage("Couldn't create " + dirname + " directory!");
    	    		}
    	    	}
    	    	else {
    	    		Log.d(MSG_TAG, "Directory '"+dir.getAbsolutePath()+"' already exists!");
    	    	}
    		}
    	}
    }

    
    // Display Toast-Message
	public void displayToastMessage(String message) {
		Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
	}
    
    public int getVersionNumber() {
    	int version = -1;
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            version = pi.versionCode;
        } catch (Exception e) {
            Log.e(MSG_TAG, "Package name not found", e);
        }
        return version;
    }
    
    public String getVersionName() {
    	String version = "?";
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            version = pi.versionName;
        } catch (Exception e) {
            Log.e(MSG_TAG, "Package name not found", e);
        }
        return version;
    }

    /*
     * This method checks if changing the transmit-power is supported
     */
    public boolean isTransmitPowerSupported() {
    	// Only supported for the nexusone 
    	if (Configuration.getWifiInterfaceDriver(deviceType).equals(Configuration.DRIVER_WEXT)) {
    		return true;
    	}
    	return false;
    }    
    
    public Bundle getParamsForPost() {
        Bundle params = new Bundle();
        String text = settings.getString("post_message", getString(R.string.post_text));
        text = text.replaceFirst("#MB", MainActivity.formatCountForPost(TetherService.dataCount.totalDownload));
        params.putString("message", text);
        params.putString("link", "http://bit.ly/og_facebook");
        params.putString("caption", "opengarden.com");
        params.putString("picture", "http://www.opengarden.com/og_fb.jpg");
        params.putString("actions", "[{\"name\":\"View\",\"link\":\"http://bit.ly/og_facebook\"}]" );
        params.putString("access_token", settings.getString("fb_access_token", ""));
        return params;
    }
 
    void requestStatsAlarm() {
        ((AlarmManager)getSystemService(ALARM_SERVICE))
            .setInexactRepeating(AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + 1000 * 3,
                AlarmManager.INTERVAL_DAY,
                PendingIntent.getBroadcast(this, 0,
                        new Intent(this, AlarmReceiver.class).setAction(MESSAGE_REPORT_STATS),
                        0));
        Log.d(MSG_TAG, "Alarm Requested");
    }
    
    void checkLaunched(final boolean startupCheck) {
        Log.d(MSG_TAG, "checkLaunched()");
        try {
            new LaunchCheck(new LaunchCheck.Callback() {
                public void onResult(LaunchCheck.Callback.Result result) {
                    if (startupCheck) {
                        if (MainActivity.currentInstance != null) {
                            Looper.prepare();
                            MainActivity.currentInstance.openNotRootDialog(result == Result.TRUE);
                            Looper.loop();
                            return;
                        }
                    }
                    if (result == Result.TRUE) {
                        Intent launchDialog = new Intent(Intent.ACTION_VIEW)
                            .setData(Uri.parse("message://" + LaunchCheck.MESSAGE_LAUNCH_CHECK))
                            .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(launchDialog);
                        
                    }
                }
            }).runCheck();
        } catch (Exception e) {
            Log.d(MSG_TAG, "", e);
        }
    }
}
