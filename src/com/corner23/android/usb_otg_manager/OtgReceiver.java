package com.corner23.android.usb_otg_manager;

import java.io.IOException;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbManager;

public class OtgReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		if (intent != null) {
			String action = intent.getAction();
			
			if (action.equals(MainActivity.ACTION_SE_USB_DEVICE_ATTACHED) ||
				action.equals(UsbManager.ACTION_USB_DEVICE_ATTACHED)) {
				Intent i = new Intent(context, MainActivity.class);
				i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
				context.startActivity(i);
			} else if (action.equals(MainActivity.ACTION_SE_USB_DEVICE_DETACHED) ||
					   action.equals(UsbManager.ACTION_USB_DEVICE_DETACHED)) {
				try {
					Root.executeSU(new String[] {"umount " + MainActivity.MOUNT_PATH, "rmdir " + MainActivity.MOUNT_PATH });
					
					((NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE)).cancelAll();
				} catch (IOException e) {
					e.printStackTrace();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}		
	}	
}
