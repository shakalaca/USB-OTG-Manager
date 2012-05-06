package com.corner23.android.usb_otg_manager;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.Notification.Builder;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

public class UnmountService extends Service {

	private Context mContext = this;
    
    private void handleCommand(Intent intent) {
    	boolean success = Main.doUnmount();
    	PendingIntent pi = PendingIntent.getService(mContext, 0, new Intent(), 0);
    	NotificationManager notificationManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
    	
        Notification.Builder builder = new Builder(mContext)
        	.setSmallIcon(R.drawable.notification)
        	.setAutoCancel(true)
        	.setContentTitle(getResources().getString(R.string.app_name))
        	.setContentIntent(pi);
        
    	if (success) {
    		notificationManager.cancelAll();
    		builder.setContentText(getResources().getString(R.string.str_unmounted_notify));
    	} else {
    		builder.setContentText(getResources().getString(R.string.str_err_unmount));
    		builder.setDefaults(Notification.DEFAULT_ALL);
    	}
    	
        notificationManager.notify(0, builder.getNotification());
        this.stopSelf();
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
		return START_NOT_STICKY;
	}	
}
