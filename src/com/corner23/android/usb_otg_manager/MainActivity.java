package com.corner23.android.usb_otg_manager;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

public class MainActivity extends Activity {
	
	public final static String ACTION_SE_USB_DEVICE_DETACHED = "com.sonyericsson.hardware.action.USB_OTG_DEVICE_DISCONNECTED";
	public final static String ACTION_SE_USB_DEVICE_ATTACHED = "com.sonyericsson.hardware.action.USB_OTG_DEVICE_CONNECTED";
	public final static String MOUNT_PATH = "/mnt/sdcard/usbstorage";

	private final static String TAG = "USB_OTG_MANAGER";
	private final static String FN_STORAGE_DRIVER = "usb_storage.ko";
	private final static String STORAGE_DEVICE_PATH = "/dev/block/sda1";
	
	private final static String[] fsTypes = {"vfat"/*, "ntfs" */};
	private int fsType;
	
	private final static boolean bIsArcS = android.os.Build.MODEL.equals("LT18i");
	
	private Context mContext = this;
	ArrayAdapter<String> adapter = null;	
	TextView tvMountStatus = null;
	ImageView ivMountStatus = null;

	private final BroadcastReceiver mOtgReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent != null) {
				String action = intent.getAction();
				
				if (action.equals(ACTION_SE_USB_DEVICE_DETACHED) ||
					action.equals(UsbManager.ACTION_USB_DEVICE_DETACHED)) {
					MainActivity.this.finish();
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
	
    private class MountStorageTask extends AsyncTask<Void, Void, Void> {

    	private ProgressDialog dialogMounting = null;
    	private boolean driverLoaded = false;
    	private boolean directoryCreated = false;
    	private boolean deviceMounted = false;
    	
		@Override
		protected void onPreExecute() {
	    	dialogMounting = ProgressDialog.show(mContext, "", getString(R.string.str_mounting), false);
		}
		
		@Override
		protected Void doInBackground(Void... params) {
	    	do {
		    	try {
	        		List<String> response = null;
	        		
	        		if (bIsArcS) {
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
				    			break;
				    		}
				    		driverLoaded = true;
		        		}
	        		} else {
	        			driverLoaded = true;
	        		}
		    		
	        		// check mount point
	        		File mountDirectory = new File(MOUNT_PATH);
	        		if (mountDirectory.exists() && !mountDirectory.isDirectory()) {
			    		response = Root.executeSU("rm " + MOUNT_PATH);
		        		if (response != null) {
		        			Log.d(TAG, "Error deleting file @ mount point :" + response);
		        			break;
		        		}
	        		}
	        		
	        		if (!mountDirectory.exists()) {
			    		response = Root.executeSU("mkdir " + MOUNT_PATH);
		        		if (response != null) {
		        			Log.d(TAG, "Error creating mount point :" + response);
		        			break;
		        		}
	        		}
	        		
	        		directoryCreated = true;
	        		
	        		// if STORAGE_DEVICE_PATH does not exist, wait for it
                    File deviceFile = new File(STORAGE_DEVICE_PATH);
                    int count = 0;
                    while (!deviceFile.exists() && count < 5) {
                        Thread.sleep(1000);
                        count++;
                    }
	        		
	        		// do real mount
	        		response = Root.executeSU("mount -rw -o utf8 -t " + fsTypes[fsType] + " " + STORAGE_DEVICE_PATH + " " + MOUNT_PATH);
	        		if (response != null) {
	        			Log.d(TAG, "Error mounting usb storage :" + response);
	        			break;
	        		}
	        		
	        		deviceMounted = true;
	    		} catch (IOException e) {
	    			e.printStackTrace();
	    		} catch (InterruptedException e) {
	    			e.printStackTrace();
	    		}
	    	} while (false);
	    	
			return null;
		}
		
		@Override
		protected void onPostExecute(Void result) {
			if (dialogMounting != null) {
				dialogMounting.dismiss();
			}
			
			if (!deviceMounted) {
				int msgId = R.string.str_err_mount;
				
				if (!driverLoaded) {
					msgId = R.string.str_err_module;
				} else if (!directoryCreated) {
					msgId = R.string.str_err_mountpoint;
				}
				
				if (directoryCreated) {
					try {
						Root.executeSU("rmdir " + MOUNT_PATH);
					} catch (IOException e) {
						e.printStackTrace();
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
	        	new AlertDialog.Builder(mContext)
		    		.setMessage(msgId)
		    		.setNeutralButton(android.R.string.ok, null)
		        	.show();
			}
			
	        if (isMounted()) {
	        	tvMountStatus.setText(getResources().getString(R.string.str_mounted, MOUNT_PATH));
	        	ivMountStatus.setImageResource(R.drawable.usb_android_connected);
	        } else {
	        	tvMountStatus.setText(R.string.str_unmounted);
	        	ivMountStatus.setImageResource(R.drawable.usb_android);
	        }	        
		}
	}
    
    private class UnmountStorageTask extends AsyncTask<Void, Void, Void> {

    	private ProgressDialog dialogUnmounting = null;
    	private boolean deviceUmounted = false;
    	
		@Override
		protected void onPreExecute() {
	    	dialogUnmounting = ProgressDialog.show(mContext, "", getString(R.string.str_unmounting), false);
		}
		
		@Override
		protected Void doInBackground(Void... params) {
	    	do {
	        	try {
	        		List<String> response = null;
	        		
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
//	            	response = Root.executeSU("rmmod " + FN_STORAGE_DRIVER);
//	        		if (response != null) {
//	        			Log.d(TAG, "Error disabling kernel module :" + response);
//	        			break;
//	        		}
	            	deviceUmounted = true;
	        		
	    		} catch (IOException e) {
	    			e.printStackTrace();
	    		} catch (InterruptedException e) {
	    			e.printStackTrace();
	    		}
	    	} while (false);
	    	
			return null;
		}
		
		@Override
		protected void onPostExecute(Void result) {
			if (dialogUnmounting != null) {
				dialogUnmounting.dismiss();
			}
			
	        if (!deviceUmounted) {
	        	new AlertDialog.Builder(mContext)
		    		.setMessage(R.string.str_err_unmount)
		    		.setNeutralButton(android.R.string.ok, null)
		        	.show();
	        }
	        
	        if (isMounted()) {
	        	tvMountStatus.setText(getResources().getString(R.string.str_mounted, MOUNT_PATH));
	        	ivMountStatus.setImageResource(R.drawable.usb_android_connected);
	        } else {
	        	tvMountStatus.setText(R.string.str_unmounted);
	        	ivMountStatus.setImageResource(R.drawable.usb_android);
	        }	 	        
		}
	}    
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        tvMountStatus = (TextView) findViewById(R.id.tv_mountstatus);
        
        ivMountStatus = (ImageView) findViewById(R.id.iv_mount_status);
        ivMountStatus.setOnClickListener(btnMountOnClickListener);
        
        if (isMounted()) {
        	tvMountStatus.setText(getResources().getString(R.string.str_mounted, MOUNT_PATH));
        	ivMountStatus.setImageResource(R.drawable.usb_android_connected);
        }
        
        adapter = new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item, fsTypes);
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

		IntentFilter filter = new IntentFilter();  
		filter.addAction(ACTION_SE_USB_DEVICE_DETACHED);
		filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
		this.registerReceiver(mOtgReceiver, filter);
		
        if (bIsArcS) {
        	new CopyKernelDriverTask().execute();
        }        
    }
    
    @Override
	protected void onDestroy() {
		super.onDestroy();
		this.unregisterReceiver(mOtgReceiver);
	}

	//
    // Utility function
    //
    private boolean isStorageExist() {
    	return new File(STORAGE_DEVICE_PATH).exists();
    }
    
    private boolean isMounted() {
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
