package com.corner23.android.usb_otg_manager;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.usb.UsbManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

public class Main extends Activity {
	
	public final static String ACTION_SE_USB_DEVICE_DETACHED = "com.sonyericsson.hardware.action.USB_OTG_DEVICE_DISCONNECTED";
	public final static String ACTION_SE_USB_DEVICE_ATTACHED = "com.sonyericsson.hardware.action.USB_OTG_DEVICE_CONNECTED";
	public final static String MOUNT_PATH = "/mnt/sdcard/usbstorage";

	public final static String PREFS_NAME = "otg_mgr_settings";
	public final static String PREFS_CLOSE_ON_MOUNT = "prefs_close_on_mount";
	public final static String PREFS_READ_ONLY = "prefs_read_only";

	private final static String TAG = "USB_OTG_MANAGER";
	private final static String FN_STORAGE_DRIVER = "usb_storage.ko";
	private final static String STORAGE_DEVICE_PATH = "/dev/block/sda1";
	
	private final static String[] fsTypes = {"vfat"/*, "ntfs" */};
	private int fsType;
	
	private final static boolean bIsXperiaSeries = android.os.Build.MODEL.equals("LT18i") || // arc s
													android.os.Build.MODEL.equals("LT15i") || // arc
													android.os.Build.MODEL.equals("SK17i") || // mini pro
													android.os.Build.MODEL.equals("ST15i") || // mini
													android.os.Build.MODEL.equals("MT15i") || // neo
													android.os.Build.MODEL.equals("MT11i") || // neo v
													android.os.Build.MODEL.equals("Z1i");   // [;ay
	
	private final static int STATE_SUCCESS = 0;
	private final static int STATE_ERROR_MOUNT = -1;
	private final static int STATE_ERROR_MODULE = -2;
	private final static int STATE_ERROR_MOUNTPOINT = -3;
	
	private Context mContext = this;
	ArrayAdapter<String> adapter = null;	
	TextView tvMountStatus = null;
	ImageView ivMountStatus = null;
	CheckBox cbCloseOnMount = null;
	CheckBox cbReadOnly = null;
	
	NotificationManager notificationManager = null;
	SharedPreferences mPrefs = null;
	
	boolean bCloseOnMount = false;
	boolean bReadOnly = false;
	
	// inner broadcast receiver for closing self when removing usb storage
	private final BroadcastReceiver mOtgReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent != null) {
				String action = intent.getAction();
				
				if (action.equals(ACTION_SE_USB_DEVICE_DETACHED) ||
					action.equals(UsbManager.ACTION_USB_DEVICE_DETACHED)) {
					Main.this.finish();
				}
			}
		}
	};

	private final OnClickListener btnMountOnClickListener = new OnClickListener() {

		@Override
		public void onClick(View v) {
			if (isMounted()) {
				new UnmountStorageTask().execute();
			} else {
				new MountStorageTask().execute();
			}
		}
	};
	
	// this is only for ArcS
	private class CopyKernelDriverTask extends AsyncTask<Void, Void, Void> {

		private ProgressDialog dialogCopyingModule = null;
		private boolean doCopy = false;
		
		@Override
		protected void onPreExecute() {
	    	try {
	    		openFileInput(FN_STORAGE_DRIVER);
	    		Log.d(TAG, "driver file exist, skip copy process..");
	    	} catch (FileNotFoundException e) {
	    		doCopy = true;
				dialogCopyingModule = ProgressDialog.show(mContext, "", getString(R.string.str_initial), false);
	    	}
		}

		@Override
		protected Void doInBackground(Void... params) {
			if (doCopy) {
	            try {
	        		Log.d(TAG, "driver file not found, copy..");
	            	writeToStream(getAssets().open(FN_STORAGE_DRIVER), openFileOutput(FN_STORAGE_DRIVER, 0));
	            } catch (Exception e) {
	            	e.printStackTrace();
	            }    		
	    	}
			return null;
		}
		

		@Override
		protected void onPostExecute(Void result) {
			if (dialogCopyingModule != null) {
				dialogCopyingModule.dismiss();
			}
		}
	}
	
	private int doMount() {
		int ret = STATE_ERROR_MOUNT;
		boolean driverLoaded = false;
		List<String> response = null;
				
    	do {
	    	try {
        		if (bIsXperiaSeries) {
		    		response = Root.executeSU("lsmod");
			    	if (response != null) {
			    		for (String r : response) {
			    			if (r.contains("usb_storage")) {
			    				Log.d(TAG, "kernel module already loaded");
			    				driverLoaded = true;
			    			}
			    		}
			    	}	    	
	        		
	        		// load kernel module if needed
	        		if (!driverLoaded) {
			    		response = Root.executeSU("insmod " + mContext.getFileStreamPath(FN_STORAGE_DRIVER));
			    		if (response != null) {
		        			Log.d(TAG, "Error loading kernel module :" + response);
			        		ret = STATE_ERROR_MODULE;
			    			break;
			    		}
	        		}
        		}
	    		
        		// check mount point
        		File mountDirectory = new File(MOUNT_PATH);
        		if (mountDirectory.exists() && !mountDirectory.isDirectory()) {
		    		response = Root.executeSU("rm " + MOUNT_PATH);
	        		if (response != null) {
	        			Log.d(TAG, "Error deleting file @ mount point :" + response);
	        			ret = STATE_ERROR_MOUNTPOINT;
	        			break;
	        		}
        		}
        		
        		if (!mountDirectory.exists()) {
		    		response = Root.executeSU("mkdir " + MOUNT_PATH);
	        		if (response != null) {
	        			Log.d(TAG, "Error creating mount point :" + response);
	        			ret = STATE_ERROR_MOUNTPOINT;
	        			break;
	        		}
        		}
        		
        		// if STORAGE_DEVICE_PATH does not exist, wait for it
                File deviceFile = new File(STORAGE_DEVICE_PATH);
                int count = 0;
                while (!deviceFile.exists() && count < 5) {
                    Thread.sleep(1000);
                    count++;
                }
        		
        		// do real mount
        		response = Root.executeSU("mount -r " + (bReadOnly ? "" : "-w") + " -o utf8 -t " + fsTypes[fsType] + " " + STORAGE_DEVICE_PATH + " " + MOUNT_PATH);
        		if (response != null) {
        			Log.d(TAG, "Error mounting usb storage :" + response);
					Root.executeSU("rmdir " + MOUNT_PATH);
        			break;
        		}
        		
        		ret = STATE_SUCCESS;
    		} catch (IOException e) {
    			e.printStackTrace();
    		} catch (InterruptedException e) {
    			e.printStackTrace();
    		}
    	} while (false);
    	
		return ret;
	}
	
	public static boolean doUnmount() {
		List<String> response = null;
		boolean bSuccess = false;
		
    	do {
        	try {
        		response = Root.executeSU("umount " + MOUNT_PATH);
        		if (response != null) {
        			Log.d(TAG, "Error umount usb storage :" + response);
        			if (isStorageExist()) {
        				// if there's no storage inserted, do not break here
        				break;
        			}
        		}

        		response = Root.executeSU("rmdir " + MOUNT_PATH);
        		if (response != null) {
        			Log.d(TAG, "Error removing mount point :" + response);
        			break;
        		}
        		
        		// TODO: option for user
//            	response = Root.executeSU("rmmod " + FN_STORAGE_DRIVER);
//        		if (response != null) {
//        			Log.d(TAG, "Error disabling kernel module :" + response);
//        			break;
//        		}
        		bSuccess = true;
        		
    		} catch (IOException e) {
    			e.printStackTrace();
    		} catch (InterruptedException e) {
    			e.printStackTrace();
    		}
    	} while (false);
    	
		return bSuccess;
	}
	
	private void updateUI() {
        if (isMounted()) {
        	tvMountStatus.setText(getResources().getString(R.string.str_mounted_path, MOUNT_PATH));
        	ivMountStatus.setImageResource(R.drawable.usb_android_connected);
        } else {
        	tvMountStatus.setText(R.string.str_unmounted);
        	ivMountStatus.setImageResource(R.drawable.usb_android);
        }	
    }

	private void showNotification() {
		Intent notifyIntent = new Intent(mContext, UnmountService.class);
		// notifyIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent appIntent = PendingIntent.getService(mContext, 0, notifyIntent, 0);

        Notification notification = new Notification();
        notification.icon = R.drawable.notification;
        notification.tickerText = getResources().getString(R.string.str_mounted_notify);
        // notification.defaults = Notification.DEFAULT_ALL;
        notification.flags |= Notification.FLAG_NO_CLEAR;
        notification.setLatestEventInfo(mContext, getResources().getString(R.string.app_name), 
        		getResources().getString(R.string.str_unmount) + " " + MOUNT_PATH, appIntent);
        notificationManager.notify(0, notification);        
	}
	
	private class MountStorageTask extends AsyncTask<Void, Void, Integer> {

    	private ProgressDialog dialogMounting = null;
    	
		@Override
		protected void onPreExecute() {
	    	dialogMounting = ProgressDialog.show(mContext, "", getString(R.string.str_mounting), false);
		}
		
		@Override
		protected Integer doInBackground(Void... params) {
			return doMount();
		}
		
		@Override
		protected void onPostExecute(Integer result) {
			if (dialogMounting != null) {
				dialogMounting.dismiss();
			}
			
			if (result == STATE_SUCCESS) {
				showNotification();
			} else {
				int msgId = R.string.str_err_mount;
				
				if (result == STATE_ERROR_MODULE) {
					msgId = R.string.str_err_module;
				} else if (result == STATE_ERROR_MOUNTPOINT) {
					msgId = R.string.str_err_mountpoint;
				}
				
	        	new AlertDialog.Builder(mContext)
		    		.setMessage(msgId)
		    		.setNeutralButton(android.R.string.ok, null)
		        	.show();
			}
			
			if (bCloseOnMount) {
				finish();
			} else {
				updateUI();			
			}
		}
	}
    
    private class UnmountStorageTask extends AsyncTask<Void, Void, Boolean> {

    	private ProgressDialog dialogUnmounting = null;
    	
		@Override
		protected void onPreExecute() {
	    	dialogUnmounting = ProgressDialog.show(mContext, "", getString(R.string.str_unmounting), false);
		}
		
		@Override
		protected Boolean doInBackground(Void... params) {
			return doUnmount();
		}
		
		@Override
		protected void onPostExecute(Boolean success) {
			if (dialogUnmounting != null) {
				dialogUnmounting.dismiss();
			}
			
	        if (success) {
				notificationManager.cancelAll();
	        } else {
	        	new AlertDialog.Builder(mContext)
		    		.setMessage(R.string.str_err_unmount)
		    		.setNeutralButton(android.R.string.ok, null)
		        	.show();
	        }
	        
			updateUI();
		}
	}    
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
		notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		
		mPrefs = getSharedPreferences(PREFS_NAME, 0);
		bCloseOnMount = mPrefs.getBoolean(PREFS_CLOSE_ON_MOUNT, false);
		bReadOnly = mPrefs.getBoolean(PREFS_READ_ONLY, false);
		        
        setContentView(R.layout.main);

        cbCloseOnMount = (CheckBox) findViewById(R.id.chkbox_hide);
        cbCloseOnMount.setChecked(bCloseOnMount);
        cbCloseOnMount.setOnCheckedChangeListener(new OnCheckedChangeListener() {

			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				bCloseOnMount = isChecked;
				SharedPreferences.Editor editor = mPrefs.edit();
				editor.putBoolean(PREFS_CLOSE_ON_MOUNT, bCloseOnMount);
				editor.commit();
			}
        });
        
        cbReadOnly = (CheckBox) findViewById(R.id.chkbox_readonly);
        cbReadOnly.setChecked(bReadOnly);
        cbReadOnly.setOnCheckedChangeListener(new OnCheckedChangeListener() {

			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				bReadOnly = isChecked;
				SharedPreferences.Editor editor = mPrefs.edit();
				editor.putBoolean(PREFS_READ_ONLY, bReadOnly);
				editor.commit();
			}
        });

        tvMountStatus = (TextView) findViewById(R.id.tv_mountstatus);

        ivMountStatus = (ImageView) findViewById(R.id.iv_mount_status);
        ivMountStatus.setOnClickListener(btnMountOnClickListener);

        adapter = new ArrayAdapter<String>(mContext, android.R.layout.simple_spinner_item, fsTypes);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        Spinner spinner = (Spinner) findViewById(R.id.spinner_fstype);
        spinner.setAdapter(adapter);
        spinner.setOnItemSelectedListener(new Spinner.OnItemSelectedListener(){

			@Override
			public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
				fsType = position;
			}

			@Override
			public void onNothingSelected(AdapterView<?> parent) {
			}
        });

        updateUI();
        
		IntentFilter filter = new IntentFilter();  
		filter.addAction(ACTION_SE_USB_DEVICE_DETACHED);
		filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
		mContext.registerReceiver(mOtgReceiver, filter);
		
        if (bIsXperiaSeries) {
        	new CopyKernelDriverTask().execute();
        }        
    }
    
    @Override
	protected void onDestroy() {
		super.onDestroy();
		mContext.unregisterReceiver(mOtgReceiver);
	}

	//
    // Utility function
    //
    private static boolean isStorageExist() {
    	return new File(STORAGE_DEVICE_PATH).exists();
    }
    
    public static boolean isMounted() {
    	File dir = new File(MOUNT_PATH);
    	if (dir.exists() && dir.isDirectory()) {
    		return true;
    	} else if (!dir.exists()) {
    		return false;
    	}
    	
    	// path is file ?! 
    	return false;
    }

    private static void writeToStream(InputStream in, OutputStream out) throws IOException {
    	byte[] bytes = new byte[2048];
    	
    	for (int c = in.read(bytes); c != -1; c = in.read(bytes)) {
    		out.write(bytes, 0, c);
    	}
    	in.close();
    	out.close();
    }
}
