package com.corner23.android.usb_otg_manager;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
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

public class MainActivity extends Activity {

	private final static String TAG = "USB_OTG_MANAGER";
	private final static String FN_STORAGE_DRIVER = "usb_storage.ko";
	private final static String MOUNT_PATH = "/mnt/sdcard/usbstorage";
	private final static String STORAGE_DEVICE_PATH = "/dev/block/sda1";
	
	private final static String[] fsTypes = {"vfat"/*, "ntfs" */};
	private int fsType;
	
	private Context mContext = this;
	ArrayAdapter<String> adapter = null;	
	Button buttonMount = null;
	ImageView ivMountStatus = null;

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
	        		
		    		response = executeSU("lsmod");
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
			    		response = executeSU("insmod " + mContext.getFileStreamPath(FN_STORAGE_DRIVER));
			    		if (response != null) {
		        			Log.d(TAG, "Error loading kernel module :" + response);
			    			break;
			    		}
			    		driverLoaded = true;
	        		}
		    		
	        		// check mount point
	        		File mountDirectory = new File(MOUNT_PATH);
	        		if (mountDirectory.exists() && !mountDirectory.isDirectory()) {
			    		response = executeSU("rm " + MOUNT_PATH);
		        		if (response != null) {
		        			Log.d(TAG, "Error deleting file @ mount point :" + response);
		        			break;
		        		}
	        		}
	        		
	        		if (!mountDirectory.exists()) {
			    		response = executeSU("mkdir " + MOUNT_PATH);
		        		if (response != null) {
		        			Log.d(TAG, "Error creating mount point :" + response);
		        			break;
		        		}
	        		}
	        		
	        		directoryCreated = true;
	        		
	        		// do real mount
	        		response = executeSU("mount -rw -o utf8 -t " + fsTypes[fsType] + " /dev/block/sda1 " + MOUNT_PATH);
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
						executeSU("rmdir " + MOUNT_PATH);
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
	        	buttonMount.setText(R.string.str_unmount);
	        	ivMountStatus.setImageResource(R.drawable.usb_android_connected);
	        } else {
	    		buttonMount.setText(R.string.str_mount);
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
	        		
	        		response = executeSU("umount " + MOUNT_PATH);
	        		if (response != null) {
	        			Log.d(TAG, "Error umount usb storage :" + response);
	        			if (isStorageExist()) {
	        				// if there's no storage inserted, do not break here
	        				break;
	        			}
	        		}

	        		response = executeSU("rmdir " + MOUNT_PATH);
	        		if (response != null) {
	        			Log.d(TAG, "Error removing mount point :" + response);
	        			break;
	        		}
	        		
	        		// TODO: option for user
//	            	response = executeSU("rmmod " + FN_STORAGE_DRIVER);
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
	        	buttonMount.setText(R.string.str_unmount);
	        	ivMountStatus.setImageResource(R.drawable.usb_android_connected);
	        } else {
	    		buttonMount.setText(R.string.str_mount);
	        	ivMountStatus.setImageResource(R.drawable.usb_android);
	        }	 	        
		}
	}    
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        buttonMount = (Button) findViewById(R.id.btn_mount);
        buttonMount.setOnClickListener(btnMountOnClickListener);
        
        ivMountStatus = (ImageView) findViewById(R.id.iv_mount_status);
        
        if (isMounted()) {
        	buttonMount.setText(R.string.str_unmount);
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

        new CopyKernelDriverTask().execute();
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

    private List<String> executeSU(String command) throws IOException, InterruptedException {
    	return executeSU(new String[] {command});
    }

    private List<String> executeSU(String[] commands) throws IOException, InterruptedException {
    	List<String> response = null;
    	List<String> errors = null;
        Process process = null;
        DataOutputStream os = null;
        InputStreamReader osRes = null;
        InputStreamReader osErr = null;

        try {
            process = Runtime.getRuntime().exec("su");
            
            os = new DataOutputStream(process.getOutputStream());
            osRes = new InputStreamReader(process.getInputStream());
            osErr = new InputStreamReader(process.getErrorStream());
            BufferedReader readerRes = new BufferedReader(osRes);
            BufferedReader readerErr = new BufferedReader(osErr);
            
            for (String single : commands) {
                os.writeBytes(single + "\n");
                os.flush();
            }

            os.writeBytes("exit \n");
            os.flush();

            String line = readerRes.readLine();
            if (line != null) {
            	response = new LinkedList<String>();
                while (line != null) {
                    response.add(line);
                    Log.d(TAG, "R:" + line + "$");
                    line = readerRes.readLine();
                }
            }
            
            // check if any error return string. Can I use return value instead ?
            line = readerErr.readLine();
            while (line != null) {
            	String str = line.trim();
            	if (!str.equals("")) {
            		if (errors == null) {
            			errors = new LinkedList<String>();
            		}
            		errors.add(line);
                    Log.d(TAG, "E:" + line + "$");
            	}
                line = readerErr.readLine();
            }
        }
        catch (Exception ex) {
        	ex.printStackTrace();
        }
        finally {
            try {
                if (os != null) {
                    os.close();
                }
                if (osRes != null) {
                    osRes.close();
                }
                if (osErr != null) {
                	osErr.close();
                }
                process.destroy();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        
        return errors != null ? errors : response;
    }
}
