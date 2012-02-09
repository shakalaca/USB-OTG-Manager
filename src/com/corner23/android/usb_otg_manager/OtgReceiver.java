package com.corner23.android.usb_otg_manager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class OtgReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		if (intent != null) {
			String action = intent.getAction();
			
			if (action.equals("com.sonyericsson.hardware.action.USB_OTG_DEVICE_CONNECTED")) {
				Intent i = new Intent(context, MainActivity.class);
				i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
				context.startActivity(i);
			}
		}		
	}	
}
