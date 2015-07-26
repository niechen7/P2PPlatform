package com.luanfei.p2pplatform.client.android;

import android.telephony.TelephonyManager;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;  
import java.net.*;
import java.util.Properties;

public class RegisterActivity extends Activity {
	
	protected static String UDP_SERVER_IP = "180.76.145.140";
	protected static int SERVER_GET_PORT = 5454;
	protected static int SERVER_SEND_PORT = 5455;
	protected static int LOCAL_PORT = 5454;
	protected static String SAY_HELLO = "Hello";
	protected static String REGISTER_MSG_CODE = "01";
	protected static String BRIDGE_CODE = "02";
	protected static String REGISTER_FINISHED_CODE = "10";
	protected static String CALL_PEER_CODE = "11";
	
	private boolean registered = false;
	private int registerTriedTime = 0;
	
	String receiveStr = "";
    String clientIP = "";
    int clientPort = 0;
    TextView registerInfo;
    Button tryAgainBtn;
    
    String IMEICode = null;
    String innerIP = null;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		System.out.println("register onCreate()");
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_register);
		
		TelephonyManager phoneMgr = (TelephonyManager)this.getSystemService(Context.TELEPHONY_SERVICE);
		IMEICode = phoneMgr.getDeviceId();
		
		innerIP = this.getInnerIP();
		
		registerInfo = (TextView)this.findViewById(R.id.register_info);
		tryAgainBtn = (Button)this.findViewById(R.id.register_again);
		
	}
	
	@Override
	protected void onResume() {
		System.out.println("register onResume()");
		System.out.println(this.registered);
		System.out.println(this.registerTriedTime);
		super.onResume();
		register();
	}
	
	private void register() {
		tryAgainBtn.setEnabled(false);
		registerInfo.setText("Peer " + IMEICode + " is joining P2P platform..." + "inner IP address is " + innerIP + ".");
		new RegisterTask().execute(null, null);
	}
	
	public void registerAgain(View view) {
		registerTriedTime = 0;
		register();
	}
	
	private class RegisterTask extends AsyncTask<String, String, String> {
		
		@Override
		protected String doInBackground(String... paras) {
			System.out.println("doInBackground...");
			publishProgress("Starting registering...");
			registerTriedTime++;
			DatagramSocket socket = null;
			try {
				socket = new DatagramSocket(LOCAL_PORT);
			} catch(SocketException e) {
				return e.getMessage();
			}
			
            InetAddress destination = null ;  
            try {  
                destination = InetAddress.getByName(UDP_SERVER_IP);
            } catch (UnknownHostException e) {
            	socket.close();
                return e.getMessage();
            }
	        //Say hello to server's send port so that can get message from server.
            byte[] bytes = SAY_HELLO.getBytes();
            DatagramPacket packet = new DatagramPacket(bytes, bytes.length, destination, SERVER_SEND_PORT);
            try {
            	socket.send(packet);
            } catch(IOException e) {
            	socket.close();
            	return e.getMessage();
            }
            publishProgress("Said hello to server.");
            
            //Send register info to server's get port.
            bytes = (REGISTER_MSG_CODE + IMEICode + "," + innerIP).getBytes();
            packet = new DatagramPacket(bytes, bytes.length, destination, SERVER_GET_PORT);
            try {
            	socket.send(packet);
            } catch(IOException e) {
            	socket.close();
            	return e.getMessage();
            }
            publishProgress("Register message sent.");
            
            //Waiting for server's return.
            publishProgress("Waiting server's return...");
            bytes = new byte[1024];
            packet = new DatagramPacket(bytes, bytes.length);
            try {
                socket.setSoTimeout(2000);
            } catch(IOException e) {
            	socket.close();
            	return e.getMessage();
            }
            try {
                socket.receive(packet);
            } catch(IOException e) {
            	socket.close();
            	return e.getMessage();
            }
            String returnStr = new String(bytes, 0, packet.getLength());
            socket.close();
            if(returnStr.length() > 2 && REGISTER_FINISHED_CODE.equals(returnStr.substring(0, 2))) {
            	registered = true;
            	return returnStr.substring(2);
            }
			return "Got invalid message.";
		}
		
        @Override
        protected void onProgressUpdate(String... msg) {
        	registerInfo.setText(registerInfo.getText() + "\n" + msg[0]);
        }
        
        @Override
        protected void onPostExecute(String result) {
        	System.out.println("onPostExecute..." + result);
            if(registered) {
            	registerInfo.setText(registerInfo.getText() + "\n" + "Register finished, all peers - " + result);
            	if(savePeers(result)) {
            		registerInfo.setText(registerInfo.getText() + "\n" + "Saving peers succeeded, will got to peer list page...");
            		try {
            			Thread.sleep(5000);
            		} catch(InterruptedException e) {
            			e.printStackTrace();
            		}
            		Intent intent = new Intent();
            		intent.setClass(RegisterActivity.this, PeerListActivity.class);
            		intent.putExtra("IMEI", IMEICode);
            		intent.putExtra("innerIP", innerIP);
            		startActivity(intent);
            		RegisterActivity.this.finish();
            		
            	} else {
            		registerInfo.setText(registerInfo.getText() + "\n" + "Saving peers failed, please try again.");
            		tryAgainBtn.setEnabled(true);
            	}
            } else {
            	if(registerTriedTime < 3) {
            		registerInfo.setText(registerInfo.getText() + "\n" + "Register failed, will try again...");
            		new RegisterTask().execute(null, null);
            	} else {
            		System.out.println(1);
            		registerInfo.setText(registerInfo.getText() + "\n" + "Register failed, maybe the server is down, please try later.");
            		System.out.println(2);
            		tryAgainBtn.setEnabled(true);
            		System.out.println(3);
            	}
            }
        }
	}
	
	private boolean savePeers(String peers) {
		registerInfo.setText(registerInfo.getText() + "\n" + "saving peers...");
		String[] peerArray = peers.split(";");
		Properties peerProp = new Properties();
		for(String peer: peerArray) {
			int index = peer.indexOf(":");
			peerProp.put(peer.substring(0, index), peer.substring(index + 1));
		}
		try {
			FileOutputStream stream = this.openFileOutput("peers.cfg", Context.MODE_PRIVATE);
			peerProp.store(stream, "");
			return true;
		} catch(FileNotFoundException e) {
			registerInfo.setText(registerInfo.getText() + "\n" + e.getMessage());
			return false;
		} catch(IOException e) {
			registerInfo.setText(registerInfo.getText() + "\n" + e.getMessage());
			return false;
		}
	}
	
	private String getInnerIP() {
		WifiManager wifiManager = (WifiManager)getSystemService(WIFI_SERVICE);
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        int ipAddress = wifiInfo.getIpAddress();
        return String.format("%d.%d.%d.%d",
                (ipAddress & 0xff), (ipAddress >> 8 & 0xff),
                (ipAddress >> 16 & 0xff), (ipAddress >> 24 & 0xff));
	}
	
}
