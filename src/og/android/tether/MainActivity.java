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

import android.R.drawable;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.app.AlertDialog.Builder;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.Html;
import android.text.Spanned;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SubMenu;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.animation.Animation;
import android.view.animation.BounceInterpolator;
import android.view.animation.ScaleAnimation;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TableRow;
import android.widget.TextView;

import com.google.android.c2dm.C2DMessaging;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import org.miscwidgets.widget.Panel;
import org.miscwidgets.widget.Panel.OnPanelListener;


public class MainActivity extends Activity {
	
	private TetherApplication application = null;
	private ProgressDialog progressDialog;

	private ImageView startBtn = null;
	private OnClickListener startBtnListener = null;
	private ImageView stopBtn = null;
	private OnClickListener stopBtnListener = null;
	private CompoundButton lockBtn = null;
	private OnCheckedChangeListener lockBtnListener = null;
	private TextView radioModeLabel = null;
	private ImageView radioModeImage = null;
	private TextView progressTitle = null;
	private TextView progressText = null;
	private ProgressBar progressBar = null;
	private RelativeLayout downloadUpdateLayout = null;
	private RelativeLayout batteryTemperatureLayout = null;
	private CheckBox lockButtonCheckbox = null;
	
	private RelativeLayout trafficRow = null;
	private TextView downloadText = null;
	private TextView uploadText = null;
	private TextView downloadRateText = null;
	private TextView uploadRateText = null;
	private TextView batteryTemperature = null;
	
	private TableRow startTblRow = null;
	private TableRow stopTblRow = null;
	
	private ScaleAnimation animation = null;
	
	private RSSReader rssReader = null;	
	private ListView rssView = null;
	private ArrayAdapter<Spanned> rssAdapter = null;
	private JSONArray jsonRssArray = null;
	private Panel rssPanel = null;
	private TextView communityText = null;
	
	private static int ID_DIALOG_STARTING = 0;
	private static int ID_DIALOG_STOPPING = 1;
	
	public static final int MESSAGE_CHECK_LOG = 1;
	public static final int MESSAGE_CANT_START_TETHER = 2;
	public static final int MESSAGE_DOWNLOAD_STARTING = 3;
	public static final int MESSAGE_DOWNLOAD_PROGRESS = 4;
	public static final int MESSAGE_DOWNLOAD_COMPLETE = 5;
	public static final int MESSAGE_DOWNLOAD_BLUETOOTH_COMPLETE = 6;
	public static final int MESSAGE_DOWNLOAD_BLUETOOTH_FAILED = 7;
	public static final int MESSAGE_TRAFFIC_START = 8;
	public static final int MESSAGE_TRAFFIC_COUNT = 9;
	public static final int MESSAGE_TRAFFIC_RATE = 10;
	public static final int MESSAGE_TRAFFIC_END = 11;
	
	public static final String MSG_TAG = "TETHER -> MainActivity";
	public static MainActivity currentInstance = null;
	
    private static void setCurrent(MainActivity current){
    	MainActivity.currentInstance = current;
    }
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	Log.d(MSG_TAG, "Calling onCreate()");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        // Init Application
        this.application = (TetherApplication)this.getApplication();
        MainActivity.setCurrent(this);
        
        // Init Table-Rows
        this.startTblRow = (TableRow)findViewById(R.id.startRow);
        this.stopTblRow = (TableRow)findViewById(R.id.stopRow);
        this.radioModeImage = (ImageView)findViewById(R.id.radioModeImage);
        this.progressBar = (ProgressBar)findViewById(R.id.progressBar);
        this.progressText = (TextView)findViewById(R.id.progressText);
        this.progressTitle = (TextView)findViewById(R.id.progressTitle);
        this.downloadUpdateLayout = (RelativeLayout)findViewById(R.id.layoutDownloadUpdate);
        this.batteryTemperatureLayout = (RelativeLayout)findViewById(R.id.layoutBatteryTemp);
        this.lockButtonCheckbox = (CheckBox)findViewById(R.id.lockButton);
        
        this.trafficRow = (RelativeLayout)findViewById(R.id.trafficRow);
        this.downloadText = (TextView)findViewById(R.id.trafficDown);
        this.uploadText = (TextView)findViewById(R.id.trafficUp);
        this.downloadRateText = (TextView)findViewById(R.id.trafficDownRate);
        this.uploadRateText = (TextView)findViewById(R.id.trafficUpRate);
        this.batteryTemperature = (TextView)findViewById(R.id.batteryTempText);
        
        // Define animation
        animation = new ScaleAnimation(
                0.9f, 1, 0.9f, 1, // From x, to x, from y, to y
                ScaleAnimation.RELATIVE_TO_SELF, 0.5f,
                ScaleAnimation.RELATIVE_TO_SELF, 0.5f);
        animation.setDuration(600);
        animation.setFillAfter(true); 
        animation.setStartOffset(0);
        animation.setRepeatCount(1);
        animation.setRepeatMode(Animation.REVERSE);

        if (this.application.startupCheckPerformed == false) {
            if (this.application.settings.getLong("install_timestamp", -1) == -1) {
                long t = System.currentTimeMillis()/1000;
                this.application.preferenceEditor.putLong("install_timestamp", t);
            }
        }

        this.application.reportStats(-1);

        // Startup-Check
        if (this.application.startupCheckPerformed == false) {
	        this.application.startupCheckPerformed = true;
	    	
	    	String regId = C2DMessaging.getRegistrationId(this);
	    	boolean registered = this.application.settings.getBoolean("c2dm_registered", false);
	    	if (!registered || regId == null || "".equals(regId)) {
	    		Log.d(MSG_TAG, "C2DM Registering");
	    		C2DMessaging.register(this, DeviceRegistrar.SENDER_ID);
	    	} else {
	    		Log.d(MSG_TAG, "C2DM already registered");
	    	}
	    	
	    	// Check if required kernel-features are enabled
	    	if (!this.application.coretask.isNetfilterSupported()) {
	    		this.openNoNetfilterDialog();
	    		this.application.accessControlSupported = false;
	    		this.application.whitelist.remove();
	    	}
	    	else {
	    		// Check if access-control-feature is supported by kernel
	    		if (!this.application.coretask.isAccessControlSupported()) {
	    			if (this.application.settings.getBoolean("warning_noaccesscontrol_displayed", false) == false) {
	    				this.openNoAccessControlDialog();
	    				this.application.preferenceEditor.putBoolean("warning_noaccesscontrol_displayed", true);
	    				this.application.preferenceEditor.commit();
	    			}
	    			this.application.accessControlSupported = false;
	    			this.application.whitelist.remove();
	    		}
	    	}
	    		
        	// Check root-permission, files
	    	if (!this.application.coretask.hasRootPermission() && this.application.launchCheckResult != null)
	    	        openNotRootDialog(TetherApplication.singleton.launchCheckResult == LaunchCheck.Callback.Result.TRUE);

	    	// Check if binaries need to be updated
	    	if (this.application.binariesExists() == false || this.application.coretask.filesetOutdated()) {
	        	if (this.application.coretask.hasRootPermission()) {
	        		this.application.installFiles();
	        	}
	        }
	    	
	        // Open donate-dialog
			this.openDonateDialog();
        
			// Check for updates
			this.application.checkForUpdate();
        }
       
        this.rssReader = new RSSReader(getApplicationContext(), TetherApplication.FORUM_RSS_URL);
        this.rssView = (ListView)findViewById(R.id.RSSView);
        this.rssAdapter = new ArrayAdapter<Spanned>(this, R.layout.rss_item);
        this.rssView.setAdapter(this.rssAdapter);
        this.rssView.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Log.d(MSG_TAG, parent + ":" + view + ":" + position + ":" + id);
                Intent viewRssLink;
                try {
                    MainActivity.this.application.statRSSClicks();
                    viewRssLink = new Intent(Intent.ACTION_VIEW)
                        .setData(Uri.parse(MainActivity.this.jsonRssArray
                                .getJSONObject(position).getString("link")));
                    startActivity(viewRssLink);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
        
        this.rssPanel = (Panel) findViewById(R.id.RSSPanel);
        this.rssPanel.setInterpolator(new BounceInterpolator());
        this.rssPanel.setOnPanelListener(new OnPanelListener() {
            public void onPanelClosed(Panel panel) {
                hideCommunityText(true);
                MainActivity.this.application.preferenceEditor.putBoolean("rss_closed", true).commit();
            }
            public void onPanelOpened(Panel panel) {
                hideCommunityText(false);
                MainActivity.this.application.preferenceEditor.putBoolean("rss_closed", false).commit();
            }
        });
        
        this.communityText = (TextView) findViewById(R.id.communityHeader);
        this.communityText.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                MainActivity.this.rssPanel.setOpen(!MainActivity.this.rssPanel.isOpen(), true);
            }
        });
        hideCommunityText(!this.rssPanel.isOpen());
        
        // Start Button
        this.startBtn = (ImageView) findViewById(R.id.startTetherBtn);
        this.startBtnListener = new OnClickListener() {
			public void onClick(View v) {
				Log.d(MSG_TAG, "StartBtn pressed ...");
				new Thread(new Runnable(){
					public void run() {
						Intent intent = new Intent(TetherService.INTENT_MANAGE);
						intent.putExtra("state", TetherService.MANAGE_START);
						Log.d(MSG_TAG, "SENDING MANAGE: " + intent);
						MainActivity.this.sendBroadcast(intent);  
					}
				}).start();
			}
		};
		
		this.startBtn.setOnClickListener(this.startBtnListener);

		// Stop Button
		this.stopBtn = (ImageView) findViewById(R.id.stopTetherBtn);
		this.stopBtnListener = new OnClickListener() {
			public void onClick(View v) {
				Log.d(MSG_TAG, "StopBtn pressed ...");
				if (MainActivity.this.lockBtn.isChecked()) {
					Log.d(MSG_TAG, "Tether was locked ...");
					MainActivity.this.application.displayToastMessage(getString(R.string.main_activity_locked));
					return;
				}
				
				new Thread(new Runnable() {
					public void run() {
						Intent intent = new Intent(TetherService.INTENT_MANAGE);
						intent.setAction(TetherService.INTENT_MANAGE);
						intent.putExtra("state", TetherService.MANAGE_STOP);
						Log.d(MSG_TAG, "Sending Intent: " + intent);
						MainActivity.this.sendBroadcast(intent);
					}
				}).start();
			}
		};
		this.stopBtn.setOnClickListener(this.stopBtnListener);

		this.lockBtn = (CompoundButton) findViewById(R.id.lockButton);
		this.lockBtnListener = new OnCheckedChangeListener() {
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				Log.d(MSG_TAG, "LockBtn pressed ...");
			}
		};
		this.lockBtn.setOnCheckedChangeListener(this.lockBtnListener);

		// Toggles between start and stop screen
		this.toggleStartStop();
		
		
		
    }
    
    @Override
	public boolean onTrackballEvent(MotionEvent event){
		if (event.getAction() == MotionEvent.ACTION_DOWN){
			Log.d(MSG_TAG, "Trackball pressed ...");
			String tetherStatus = this.application.coretask.getProp("tether.status");
            if (!tetherStatus.equals("running")){
				new AlertDialog.Builder(this)
				.setMessage(getString(R.string.main_activity_trackball_pressed_start))  
			    .setPositiveButton(getString(R.string.main_activity_confirm), new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						Log.d(MSG_TAG, "Trackball press confirmed ...");
						MainActivity.currentInstance.startBtnListener.onClick(MainActivity.currentInstance.startBtn);
					}
				}) 
			    .setNegativeButton(getString(R.string.main_activity_cancel), null)  
			    .show();
			}
            else{
    			if (MainActivity.this.lockBtn.isChecked()){
    				Log.d(MSG_TAG, "Tether was locked ...");
    				MainActivity.this.application.displayToastMessage(getString(R.string.main_activity_locked));
    				return false;
    			}
				new AlertDialog.Builder(this)
				.setMessage(getString(R.string.main_activity_trackball_pressed_stop))  
			    .setPositiveButton(getString(R.string.main_activity_confirm), new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						Log.d(MSG_TAG, "Trackball press confirmed ...");
						MainActivity.currentInstance.stopBtnListener.onClick(MainActivity.currentInstance.startBtn);
					}
				})
			    .setNegativeButton(getString(R.string.main_activity_cancel), null)  
			    .show();
            }
		}
		return true;
	}
    
	public void onStop() {
    	Log.d(MSG_TAG, "Calling onStop()");
		super.onStop();
	}

	public void onDestroy() {
		Log.d(MSG_TAG, "Calling onDestroy()");
		super.onDestroy(); 
	}
	
	public void onPause() {
	    Log.d(MSG_TAG, "Calling onPause()");
	    try {
	        unregisterReceiver(this.intentReceiver);
	    } catch (Exception ex) {;} 
	    super.onPause();  	
	}
	
	protected void onNewIntent(Intent intent) {
	    Log.d(MSG_TAG, "onNewIntent(): " + intent);
	    setIntent(intent);
	}
	
	public void onResume() {
		Log.d(MSG_TAG, "Calling onResume()");
		
		try {
		    if (getIntent().getData().getPath().equals("/LAUNCH_CHECK")) {
		        setIntent(null);
		        openLaunchedDialog();
		    }
		} catch (Exception e) {}
		
		this.showRadioMode();
		super.onResume();
		this.intentFilter = new IntentFilter();
		
		// Check, if the battery-temperature should be displayed
		if(this.application.settings.getString("batterytemppref", "celsius").equals("disabled") == false) {
	        // create the IntentFilter that will be used to listen
	        // to battery status broadcasts
	        this.intentFilter.addAction(Intent.ACTION_BATTERY_CHANGED);	        
	        this.batteryTemperatureLayout.setVisibility(View.VISIBLE);
		} else {			
			this.batteryTemperatureLayout.setVisibility(View.INVISIBLE);
		}
		
		this.intentFilter.addAction(TetherService.INTENT_TRAFFIC);
		this.intentFilter.addAction(TetherService.INTENT_STATE);
		this.intentFilter.addAction(RSSReader.MESSAGE_JSON_RSS);
		this.intentFilter.addAction(TetherApplication.MESSAGE_POST_STATS);
        registerReceiver(this.intentReceiver, this.intentFilter);
        this.toggleStartStop();
        
		// Check, if the lockbutton should be displayed
		if (this.stopTblRow.getVisibility() == View.VISIBLE &&
				this.application.settings.getBoolean("lockscreenpref", true) == false) {
			this.lockButtonCheckbox.setVisibility(View.VISIBLE);
		}
		else {
			this.lockButtonCheckbox.setVisibility(View.GONE);
		}

		this.rssPanel.setOpen(!this.application.settings.getBoolean("rss_closed", false), true);
		hideCommunityText(!this.rssPanel.isOpen());

        this.rssReader.readRSS(); 
	}
	
	private static final int MENU_SETUP = 0;
	private static final int MENU_LOG = 1;
	private static final int MENU_ABOUT = 2;
	private static final int MENU_ACCESS = 3;
	private static final int MENU_CONNECT = 4;
	private static final int MENU_COMMUNITY = 5;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	boolean supRetVal = super.onCreateOptionsMenu(menu);
    	SubMenu setup = menu.addSubMenu(0, MENU_SETUP, 0, getString(R.string.main_activity_settings));
    	setup.setIcon(drawable.ic_menu_preferences);
    	if (this.application.accessControlSupported) { 
    		SubMenu accessctr = menu.addSubMenu(0, MENU_ACCESS, 0, getString(R.string.main_activity_accesscontrol));
    		accessctr.setIcon(drawable.ic_menu_manage);   
    	}
    	SubMenu connect = menu.addSubMenu(0, MENU_CONNECT, 0, getString(R.string.main_activity_connect));
    	connect.setIcon(drawable.ic_menu_add);
    	SubMenu log = menu.addSubMenu(0, MENU_LOG, 0, getString(R.string.main_activity_showlog));
    	log.setIcon(drawable.ic_menu_agenda);
    	SubMenu community = menu.addSubMenu(0, MENU_COMMUNITY, 0, getString(R.string.main_activity_community));
    	community.setIcon(drawable.ic_menu_myplaces);
    	SubMenu about = menu.addSubMenu(0, MENU_ABOUT, 0, getString(R.string.main_activity_about));
    	about.setIcon(drawable.ic_menu_info_details);
    	return supRetVal;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
    	boolean supRetVal = super.onOptionsItemSelected(menuItem);
    	Log.d(MSG_TAG, "Menuitem:getId  -  "+menuItem.getItemId()); 
    	switch (menuItem.getItemId()) {
	    	case MENU_SETUP :
		        startActivityForResult(new Intent(
		        		MainActivity.this, SetupActivity.class), 0);
		        break;
	    	case MENU_LOG :
		        startActivityForResult(new Intent(
		        		MainActivity.this, LogActivity.class), 0);
		        break;
	    	case MENU_ABOUT :
	    		this.openAboutDialog();
	    		break;
	    	case MENU_ACCESS :
		        startActivityForResult(new Intent(
		        		MainActivity.this, AccessControlActivity.class), 0);   	
		        break;
	    	case MENU_CONNECT :
	    	    startActivity(new Intent(MainActivity.this, ConnectActivity.class));
	    	    break;
	    	case MENU_COMMUNITY :
	    	    this.application.statCommunityClicks();
	    	    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.communityUrl))));
                break;	    	    
    	}
    	return supRetVal;
    }    

    @Override
    protected Dialog onCreateDialog(int id) {
    	if (id == ID_DIALOG_STARTING) {
	    	progressDialog = new ProgressDialog(this);
	    	progressDialog.setTitle(getString(R.string.main_activity_start));
	    	progressDialog.setMessage(getString(R.string.main_activity_start_summary));
	    	progressDialog.setIndeterminate(false);
	    	progressDialog.setCancelable(true);
	        return progressDialog;
    	}
    	else if (id == ID_DIALOG_STOPPING) {
	    	progressDialog = new ProgressDialog(this);
	    	progressDialog.setTitle(getString(R.string.main_activity_stop));
	    	progressDialog.setMessage(getString(R.string.main_activity_stop_summary));
	    	progressDialog.setIndeterminate(false);
	    	progressDialog.setCancelable(true);
	        return progressDialog;  		
    	}
    	return null;
    }

    /**
     *Listens for intent broadcasts; Needed for the temperature-display, traffic count, and service state
     */
     private IntentFilter intentFilter;
     private BroadcastReceiver intentReceiver;
     public Handler viewUpdateHandler;
     
     public MainActivity() {
    	
     intentReceiver = new BroadcastReceiver() {
         @Override
         public void onReceive(Context context, Intent intent) {
             Log.d(MSG_TAG, "onReceive() " + intent);
             String action = intent.getAction();
             if (action.equals(Intent.ACTION_BATTERY_CHANGED)) {
	            	 int temp = (intent.getIntExtra("temperature", 0));
	            	 int celsius = (int)((temp+5)/10);
	            	 int fahrenheit = (int)(((temp/10)/0.555)+32+0.5);
	            	 Log.d(MSG_TAG, "Temp ==> "+temp+" -- Celsius ==> "+celsius+" -- Fahrenheit ==> "+fahrenheit);
	            	 if (MainActivity.this.application.settings.getString("batterytemppref", "celsius").equals("celsius")) {
	            		 batteryTemperature.setText("" + celsius + getString(R.string.main_activity_temperatureunit_celsius));
	            	 } else {
            		 batteryTemperature.setText("" + fahrenheit + getString(R.string.main_activity_temperatureunit_fahrenheit));
	            	 }
             } else {
            	 	//Log.d(MSG_TAG, "INTENT RECEIVED: "+intent.getAction());
            	 	if(action.equals(TetherService.INTENT_TRAFFIC))
            	 		updateTrafficDisplay(intent.getLongArrayExtra("traffic_count"));
            	 	if(action.equals(TetherService.INTENT_STATE)) {
            	 		Log.d(MSG_TAG, "STATE RECEIVED: "+intent.getIntExtra("state", 999));
            	 		try {
            	 		switch(intent.getIntExtra("state", TetherService.STATE_IDLE)) {
            	 		case TetherService.STATE_RESTARTING :
            	 			try { MainActivity.this.dismissDialog(MainActivity.ID_DIALOG_STOPPING); }
            	 			catch(Exception e) {}
            	 			break;
            	 		case TetherService.STATE_STARTING :
            	 			MainActivity.this.showDialog(MainActivity.ID_DIALOG_STARTING);
            	 			break;
            	 		case TetherService.STATE_RUNNING :
            	 			try { MainActivity.this.dismissDialog(MainActivity.ID_DIALOG_STARTING); }
            	 			catch(Exception e) {}
            	 			MainActivity.this.toggleStartStop();
            	 			break;
            	 		case TetherService.STATE_STOPPING :
            	 			MainActivity.this.showDialog(MainActivity.ID_DIALOG_STOPPING);
            	 			break;
            	 		case TetherService.STATE_IDLE :
            	 			try { MainActivity.this.dismissDialog(MainActivity.ID_DIALOG_STOPPING); }
            	 			catch(Exception e) {}
            	 			MainActivity.this.toggleStartStop();
            	 			break;
            	 		case TetherService.STATE_FAIL_LOG :
            	 			try { MainActivity.this.dismissDialog(MainActivity.ID_DIALOG_STARTING); }
            	 			catch(Exception e) {}
            	 			MainActivity.this.application.displayToastMessage(getString(R.string.main_activity_start_errors));
            	 			MainActivity.this.toggleStartStop();
            	 			break;
            	 		case TetherService.STATE_FAIL_EXEC :
            	 			try { MainActivity.this.dismissDialog(MainActivity.ID_DIALOG_STARTING); }
            	 			catch(Exception e) {}
            	 			MainActivity.this.application.displayToastMessage(getString(R.string.main_activity_start_unable));
            	 			MainActivity.this.toggleStartStop();
            	 			break;
            	 		}
            	 		
            	 		} catch(Exception e) { }
            	 		finally {
            	 			// MainActivity.this.toggleStartStop();
            	 		}
            	 				
            	 	} else if (action.equals(RSSReader.MESSAGE_JSON_RSS)) {
            	 	    updateRSSView(intent.getStringExtra(RSSReader.EXTRA_JSON_RSS));
            	 	}
         	}

         }
     };

     // ??? deprecated?
     viewUpdateHandler = new Handler(){
        public void handleMessage(Message msg) {
        	Log.d(MSG_TAG, "MESSAGE: "+msg);
        	switch(msg.what) {
        	case MESSAGE_CHECK_LOG :
        		Log.d(MSG_TAG, "Error detected. Check log.");
        		MainActivity.this.application.displayToastMessage(getString(R.string.main_activity_start_errors));
            	MainActivity.this.toggleStartStop();
            	break;
        	case MESSAGE_CANT_START_TETHER :
        		Log.d(MSG_TAG, "Unable to start tethering!");
        		MainActivity.this.application.displayToastMessage(getString(R.string.main_activity_start_unable));
            	MainActivity.this.toggleStartStop();
            	break;
        	case MESSAGE_TRAFFIC_START :
        		MainActivity.this.trafficRow.setVisibility(View.VISIBLE);
        		break;
        	case MESSAGE_TRAFFIC_COUNT :
        		MainActivity.this.trafficRow.setVisibility(View.VISIBLE);
        		//???
	        	long uploadTraffic = ((TetherService.DataCount)msg.obj).totalUpload;
	        	long downloadTraffic = ((TetherService.DataCount)msg.obj).totalDownload;
	        	long uploadRate = ((TetherService.DataCount)msg.obj).uploadRate;
	        	long downloadRate = ((TetherService.DataCount)msg.obj).downloadRate;

	        	// Set rates to 0 if values are negative
	        	if (uploadRate < 0)
	        		uploadRate = 0;
	        	if (downloadRate < 0)
	        		downloadRate = 0;
	        	
        		MainActivity.this.uploadText.setText(MainActivity.this.formatCount(uploadTraffic, false));
        		MainActivity.this.downloadText.setText(MainActivity.this.formatCount(downloadTraffic, false));
        		MainActivity.this.downloadText.invalidate();
        		MainActivity.this.uploadText.invalidate();

        		MainActivity.this.uploadRateText.setText(MainActivity.this.formatCount(uploadRate, true));
        		MainActivity.this.downloadRateText.setText(MainActivity.this.formatCount(downloadRate, true));
        		MainActivity.this.downloadRateText.invalidate();
        		MainActivity.this.uploadRateText.invalidate();
        		break;
        	case MESSAGE_TRAFFIC_END :
        		MainActivity.this.trafficRow.setVisibility(View.INVISIBLE);
        		break;
        	case MESSAGE_DOWNLOAD_STARTING :
        		Log.d(MSG_TAG, "Start progress bar");
        		MainActivity.this.progressBar.setIndeterminate(true);
        		MainActivity.this.progressTitle.setText((String)msg.obj);
        		MainActivity.this.progressText.setText("Starting...");
        		MainActivity.this.downloadUpdateLayout.setVisibility(View.VISIBLE);
        		break;
        	case MESSAGE_DOWNLOAD_PROGRESS :
        		MainActivity.this.progressBar.setIndeterminate(false);
        		MainActivity.this.progressText.setText(msg.arg1 + "k /" + msg.arg2 + "k");
        		MainActivity.this.progressBar.setProgress(msg.arg1*100/msg.arg2);
        		break;
        	case MESSAGE_DOWNLOAD_COMPLETE :
        		Log.d(MSG_TAG, "Finished download.");
        		MainActivity.this.progressText.setText("");
        		MainActivity.this.progressTitle.setText("");
        		MainActivity.this.downloadUpdateLayout.setVisibility(View.GONE);
        		break;
        	case MESSAGE_DOWNLOAD_BLUETOOTH_COMPLETE :
        		Log.d(MSG_TAG, "Finished bluetooth download.");
        		MainActivity.this.startBtn.setClickable(true);
        		MainActivity.this.radioModeLabel.setText("Bluetooth");
        		break;
        	case MESSAGE_DOWNLOAD_BLUETOOTH_FAILED :
        		Log.d(MSG_TAG, "FAILED bluetooth download.");
        		MainActivity.this.startBtn.setClickable(true);
        		MainActivity.this.application.preferenceEditor.putBoolean("bluetoothon", false);
        		MainActivity.this.application.preferenceEditor.commit();
        		// TODO: More detailed popup info.
        		MainActivity.this.application.displayToastMessage("No bluetooth module for your kernel! Please report your kernel version.");
        	default:
        		MainActivity.this.toggleStartStop();
        	}
        	super.handleMessage(msg);
        }
   };

   } // constructor
     
     private synchronized void updateRSSView(String JSONrss) {
         Log.d(MSG_TAG, "Intent JSONRSS: " + JSONrss);
         try {
             this.rssAdapter.clear();
             this.rssAdapter.notifyDataSetChanged();
             this.jsonRssArray = new JSONArray(JSONrss);
             for(int i = 0; i < jsonRssArray.length(); i++) {
                 JSONObject jsonRssItem = jsonRssArray.getJSONObject(i);
                 this.rssAdapter.add(Html.fromHtml(
                     jsonRssItem.getString("title") + " - <i>" +
                     jsonRssItem.getString("creator") + "</i>" ));
             }
             if (jsonRssArray.length() > 0 && !this.application.settings.getBoolean("rss_closed", false))
                 this.rssPanel.setOpen(true, true);
         } catch (JSONException e) {
             e.printStackTrace();
         }
     }
     
   private void updateTrafficDisplay(long[] trafficData) {
	   
		MainActivity.this.trafficRow.setVisibility(View.VISIBLE);
		
		long uploadTraffic		= trafficData[0];
		long downloadTraffic 	= trafficData[1];
		long uploadRate 			= trafficData[2];
		long downloadRate 		= trafficData[3];
		
		// Set rates to 0 if values are negative
		if (uploadRate < 0)
			uploadRate = 0;
		if (downloadRate < 0)
			downloadRate = 0;
		
		MainActivity.this.uploadText.setText(MainActivity.this.formatCount(uploadTraffic, false));
		MainActivity.this.downloadText.setText(MainActivity.this.formatCount(downloadTraffic, false));
		MainActivity.this.downloadText.invalidate();
		MainActivity.this.uploadText.invalidate();
		
		MainActivity.this.uploadRateText.setText(MainActivity.this.formatCount(uploadRate, true));
		MainActivity.this.downloadRateText.setText(MainActivity.this.formatCount(downloadRate, true));
		MainActivity.this.downloadRateText.invalidate();
		MainActivity.this.uploadRateText.invalidate();
   }
   
   private void toggleStartStop() {
    
	   if((TetherService.singleton != null) &&
			   ((TetherService.singleton.getState() == TetherService.STATE_RUNNING) ||
			   (TetherService.singleton.getState() == TetherService.STATE_FAIL_LOG) ||
			   (TetherService.singleton.getState() == TetherService.STATE_STOPPING))) {
		Log.d(MSG_TAG, "TOGGLE: RUNNING");
    		this.startTblRow.setVisibility(View.GONE);
    		this.stopTblRow.setVisibility(View.VISIBLE);
    		// Animation
    		if (this.animation != null)
    			this.stopBtn.startAnimation(this.animation);

            // Checking, if "wired tether" is currently running
            String tetherMode = this.application.coretask.getProp("tether.mode");
            String tetherStatus = this.application.coretask.getProp("tether.status");
            if (tetherStatus.equals("running")) {
            	if (!(tetherMode.equals("wifi") == true || tetherMode.equals("bt") == true)) {
            		MainActivity.this.application.displayToastMessage(getString(R.string.main_activity_start_wiredtethering_running));
            	}
            }
            
            // Checking, if cyanogens usb-tether is currently running
            tetherStatus = this.application.coretask.getProp("tethering.enabled");
            if  (tetherStatus.equals("1")) {
            	MainActivity.this.application.displayToastMessage(getString(R.string.main_activity_start_usbtethering_running));
            }

    		this.application.showStartNotification(getString(R.string.global_application_tethering_running));
    		
			// Check, if the lockbutton should be displayed
			if (MainActivity.this.application.settings.getBoolean("lockscreenpref", true) == false) {
				MainActivity.this.lockButtonCheckbox.setVisibility(View.VISIBLE);
			}
    	}
    	else if ((TetherService.singleton == null) ||
    			(TetherService.singleton.getState() == TetherService.STATE_IDLE) ||
    			(TetherService.singleton.getState() == TetherService.STATE_STARTING) ||
    			(TetherService.singleton.getState() == TetherService.STATE_RESTARTING) ||
    			(TetherService.singleton.getState() == TetherService.STATE_FAIL_EXEC)) {
    		Log.d(MSG_TAG, "TOGGLE: STOPPED");
    		this.startTblRow.setVisibility(View.VISIBLE);
    		this.stopTblRow.setVisibility(View.GONE);
    		this.trafficRow.setVisibility(View.INVISIBLE);
    		//??? this.application.trafficCounterEnable(false);
    		// Animation
    		if (this.animation != null)
    			this.startBtn.startAnimation(this.animation);
    		// Notification
        	this.application.notificationManager.cancelAll();
        	
			// Check, if the lockbutton should be displayed
			MainActivity.this.lockButtonCheckbox.setVisibility(View.GONE);
    	} else {
    		Log.d(MSG_TAG, "TOGGLE: UNKNOWN");
    		this.startTblRow.setVisibility(View.VISIBLE);
    		this.stopTblRow.setVisibility(View.VISIBLE);
    		MainActivity.this.application.displayToastMessage(getString(R.string.main_activity_start_unknownstate));
    	}
    	this.showRadioMode();
    	System.gc();
    }
   
	static String formatCount(long count, boolean rate) {
		// Converts the supplied argument into a string.
		// 'rate' indicates whether is a total bytes, or bits per sec.
		// Under 2Mb, returns "xxx.xKb"
		// Over 2Mb, returns "xxx.xxMb"
		if (count < 1e6 * 2)
			return ((float)((int)(count*10/1024))/10 + (rate ? "kbps" : "kB"));
		return ((float)((int)(count*100/1024/1024))/100 + (rate ? "mbps" : "MB"));
	}
  
    static String formatCountForPost(long count) {
        if (count < 1e6 * 2)
            return ((float)((int)(count*10/1024))/10 + (" kilobytes"));
        return ((float)((int)(count*100/1024/1024))/100 + (" megabytes"));
    }
    
   	private void openNoNetfilterDialog() {
		LayoutInflater li = LayoutInflater.from(this);
        View view = li.inflate(R.layout.nonetfilterview, null); 
		new AlertDialog.Builder(MainActivity.this)
        .setTitle(getString(R.string.main_activity_nonetfilter))
        .setView(view)
        .setNegativeButton(getString(R.string.main_activity_exit), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                        Log.d(MSG_TAG, "Close pressed");
                        MainActivity.this.finish();
                }
        })
        .setNeutralButton(getString(R.string.main_activity_ignore), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    Log.d(MSG_TAG, "Override pressed");
                    MainActivity.this.application.displayToastMessage("Ignoring, note that this application will NOT work correctly.");
                }
        })
        .show();
   	}
   	
   	private void openNoAccessControlDialog() {
		LayoutInflater li = LayoutInflater.from(this);
        View view = li.inflate(R.layout.noaccesscontrolview, null); 
		new AlertDialog.Builder(MainActivity.this)
        .setTitle(getString(R.string.main_activity_noaccesscontrol))
        .setView(view)
        .setNeutralButton(getString(R.string.main_activity_ok), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    Log.d(MSG_TAG, "OK pressed");
                    MainActivity.this.application.displayToastMessage(getString(R.string.main_activity_accesscontrol_disabled));
                }
        })
        .show();
   	}
   	
   	synchronized void openNotRootDialog(final boolean launched) {
   	    if (this.application.settings.getBoolean("notrootdialogshown", false))
   	        return;
   	    
		LayoutInflater li = LayoutInflater.from(this);
        View view = li.inflate(R.layout.norootview, null); 
		AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this)
        .setTitle(getString(R.string.main_activity_notroot))
        .setView(view);
		
		if (launched) {
            ((TextView)view.findViewById(R.id.noroottext1))
                .setText(getString(R.string.dialog_launched_text));
            ((TextView)view.findViewById(R.id.noroottext2))
                .setText("");
		    builder.setPositiveButton(getString(R.string.main_activity_yes), new DialogInterface.OnClickListener() {
		        public void onClick(DialogInterface dialog, int whichButton) {
		            startGooglePlayMeshclient();
		        }
		    })
	        .setNeutralButton(getString(R.string.main_activity_ignore), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    Log.d(MSG_TAG, "Ignore pressed");
                    MainActivity.this.application.installFiles();
                    MainActivity.this.application.displayToastMessage("Ignoring, note that this application will NOT work correctly.");
                }
	        });
		} else {
		    builder.setNegativeButton(getString(R.string.main_activity_exit), new DialogInterface.OnClickListener() {
		        public void onClick(DialogInterface dialog, int whichButton) {
		            Log.d(MSG_TAG, "Exit pressed");
		            MainActivity.this.finish();
		        }
		    })
		    .setNeutralButton(getString(R.string.main_activity_ignore), new DialogInterface.OnClickListener() {
		        public void onClick(DialogInterface dialog, int whichButton) {
		            Log.d(MSG_TAG, "Ignore pressed");
		            MainActivity.this.application.installFiles();
		            MainActivity.this.application.displayToastMessage("Ignoring, note that this application will NOT work correctly.");
		        }
		    });
		}
        builder.show();
        this.application.settings.edit().putBoolean("notrootdialogshown", true).commit();
   	}
   
   	private void openAboutDialog() {
		LayoutInflater li = LayoutInflater.from(this);
        View view = li.inflate(R.layout.aboutview, null); 
        TextView versionName = (TextView)view.findViewById(R.id.versionName);
        versionName.setText(this.application.getVersionName());        
		new AlertDialog.Builder(MainActivity.this)
        .setTitle(getString(R.string.main_activity_about))
        .setView(view)
        .setNeutralButton(getString(R.string.main_activity_donate), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                        Log.d(MSG_TAG, "Donate pressed");
                        // Disable donate-dialog for later startups
                        MainActivity.this.application.preferenceEditor.putBoolean("donatepref", false);
                        MainActivity.this.application.preferenceEditor.commit();
    					Uri uri = Uri.parse(getString(R.string.paypalUrl));
    					startActivity(new Intent(Intent.ACTION_VIEW, uri));
                }
        })
        .setNegativeButton(getString(R.string.main_activity_close), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                        Log.d(MSG_TAG, "Close pressed");
                }
        })
        .show();  		
   	}
   	
   	private void openDonateDialog() {
   		if (this.application.showDonationDialog()) {
   			// Creating Layout
			LayoutInflater li = LayoutInflater.from(this);
	        View view = li.inflate(R.layout.donateview, null); 
	        new AlertDialog.Builder(MainActivity.this)
	        .setTitle(getString(R.string.main_activity_donate))
	        .setView(view)
	        .setNeutralButton(getString(R.string.main_activity_close), new DialogInterface.OnClickListener() {
	                public void onClick(DialogInterface dialog, int whichButton) {
	                        Log.d(MSG_TAG, "Close pressed");
	                }
	        })
	        .setNegativeButton(getString(R.string.main_activity_donate), new DialogInterface.OnClickListener() {
	                public void onClick(DialogInterface dialog, int whichButton) {
	                        Log.d(MSG_TAG, "Donate pressed");
                            // Disable donate-dialog for later startups
                            MainActivity.this.application.preferenceEditor.putBoolean("donatepref", false);
                            MainActivity.this.application.preferenceEditor.commit();
	    					Uri uri = Uri.parse(getString(R.string.paypalUrl));
	    					startActivity(new Intent(Intent.ACTION_VIEW, uri));
	                }
	        })
	        .show();
   		}
   	}

  	private void showRadioMode() {
  		boolean usingBluetooth = this.application.settings.getBoolean("bluetoothon", false);
  		if (usingBluetooth) {
  			this.radioModeImage.setImageResource(R.drawable.bluetooth);
  		} else {
  			this.radioModeImage.setImageResource(R.drawable.wifi);
  		}
  	}
	
   	public void openUpdateDialog(final String downloadFileUrl, final String fileName, final String message,
   	    final String updateTitle) {
		LayoutInflater li = LayoutInflater.from(this);
		Builder dialog;
		View view;
		view = li.inflate(R.layout.updateview, null);
        TextView messageView = (TextView) view.findViewById(R.id.updateMessage);
        TextView updateNowText = (TextView) view.findViewById(R.id.updateNowText);
        if (fileName.length() == 0)  // No filename, hide 'download now?' string
          updateNowText.setVisibility(View.GONE);
        messageView.setText(message);
        dialog = new AlertDialog.Builder(MainActivity.this)
        .setTitle(updateTitle)
        .setView(view);
        
        if (fileName.length() > 0) {
          // Display Yes/No for if a filename is available.
          dialog.setNeutralButton(getString(R.string.main_activity_no), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                Log.d(MSG_TAG, "No pressed");
            }
          });
          dialog.setNegativeButton(getString(R.string.main_activity_yes), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                Log.d(MSG_TAG, "Yes pressed");
                MainActivity.this.application.downloadUpdate(downloadFileUrl, fileName);
            }
          });          
        } else
          dialog.setNeutralButton(getString(R.string.main_activity_ok), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                Log.d(MSG_TAG, "Ok pressed");
            }
          });

        dialog.show();
   	}

   	public Dialog openLaunchedDialog() {
        Dialog dialog = new AlertDialog.Builder(this)
        .setMessage(R.string.dialog_launched_text)
        .setTitle(getString(R.string.dialog_launched_title))
        .setIcon(drawable.ic_dialog_info)
        .setCancelable(false)
        .setOnKeyListener(new DialogInterface.OnKeyListener() {
            public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_BACK)
                    MainActivity.this.finish();
                if(keyCode < KeyEvent.KEYCODE_DPAD_UP || keyCode > KeyEvent.KEYCODE_DPAD_CENTER)
                    return true;
                else
                    return false;
            }
        })
        .setPositiveButton(getString(R.string.main_activity_yes), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                startGooglePlayMeshclient();
            }
        })
        .setNegativeButton(getString(R.string.main_activity_no), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                
            }
        })
        .create();
        dialog.show();
        return dialog;
   	}
   	
   	void startGooglePlayMeshclient() {
   	    Log.d(MSG_TAG, "startGooglePlayMeshclient()");
        Intent meshclientInstall = new Intent(Intent.ACTION_VIEW)
            .setData(Uri.parse(LaunchCheck.MESHCLIENT_GOOGLE_PLAY_URL));
        startActivity(meshclientInstall);
   	}
   	
   	private void hideCommunityText(boolean hide) {
   	    if(hide) {
   	        MainActivity.this.communityText.setText(
   	                String.format("%" + this.communityText.getText().length() + "s", " "));
   	    } else
   	        MainActivity.this.communityText.setText(R.string.community_header);
   	}
}
