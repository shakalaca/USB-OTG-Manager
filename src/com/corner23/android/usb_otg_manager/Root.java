package com.corner23.android.usb_otg_manager;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.LinkedList;
import java.util.List;

//import android.util.Log;

public class Root {

    public static List<String> executeSU(String command) throws IOException, InterruptedException {
    	return executeSU(new String[] {command});
    }

    public static List<String> executeSU(String[] commands) throws IOException, InterruptedException {
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
//                    Log.d("Root", "R:" + line + "$");
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
//                    Log.d("Root", "E:" + line + "$");
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
