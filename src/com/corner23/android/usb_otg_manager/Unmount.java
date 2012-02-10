package com.corner23.android.usb_otg_manager;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

public class Unmount extends Service {

	private Context mContext = this;
    
    private void handleCommand(Intent intent) {
    	Log.d("HAHHAHAAAA", "FUCKING DEAD!");
    	
    	boolean success = MainActivity.doUnmount();
    	NotificationManager notificationManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        Notification notification = new Notification();
        notification.icon = R.drawable.notification;
        notification.defaults = Notification.DEFAULT_ALL;
        
    	if (success) {
    		notificationManager.cancelAll();
            notification.setLatestEventInfo(mContext, getResources().getString(R.string.app_name), 
            		getResources().getString(R.string.str_unmounted_notify), null);
    	} else {
            notification.setLatestEventInfo(mContext, getResources().getString(R.string.app_name), 
            		getResources().getString(R.string.str_err_unmount), null);
    	}
    	
        notificationManager.notify(0, notification);
    }
    	
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public void onStart(Intent intent, int startId) {
	    handleCommand(intent);
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		handleCommand(intent);
		return START_STICKY;
	}	
}
