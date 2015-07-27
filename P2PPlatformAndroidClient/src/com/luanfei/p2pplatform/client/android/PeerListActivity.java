package com.luanfei.p2pplatform.client.android;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

import com.luanfei.p2pplatform.client.android.util.UDPSendingMessage;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.method.ScrollingMovementMethod;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;

public class PeerListActivity extends Activity {
	
	private List<Map<String, String>> peerMapList = new ArrayList<Map<String, String>>();
	
	private TextView peers;
	private TextView logs;
	private EditText talk;
	private ListView peerListView;
	
	private String IMEICode = null;
	private String innerIP = null;
	private String outerIP = null;
	
	private PeerListViewAdapter listViewAdpter;
	
	private Queue<UDPSendingMessage> toSendQueue = new LinkedBlockingQueue<UDPSendingMessage>();
	
	boolean keepListening = true;
	
	private int currentPeer = 0;
	
	DatagramSocket socket = null;
	
    String fileName;
    String filePath;
    float fileSize;
    
    String selectedIP;
    int selectedPort;
    
    Map<String, Integer> peerPositionMap = new HashMap<String, Integer>();
    Map<String, Integer> peerStatusMap = new HashMap<String, Integer>(); 

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		IMEICode = this.getIntent().getStringExtra("IMEI");
		innerIP = this.getIntent().getStringExtra("innerIP");
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_peerlist);
		peers = (TextView)this.findViewById(R.id.peers);
		talk = (EditText)this.findViewById(R.id.talk);
		logs = (TextView)this.findViewById(R.id.logs);
		logs.setMovementMethod(ScrollingMovementMethod.getInstance());  
		if(!initPeers()) {
			return;
		}
		peerListView = (ListView)findViewById(R.id.peers_list);
		listViewAdpter = new PeerListViewAdapter(PeerListActivity.this, R.layout.peer_list);
		peerListView.setAdapter(listViewAdpter);
		peerListView.setOnItemClickListener(new PeerItemClickListener());	
	}
	
	@Override
	protected void onStart() {
		super.onStart();
		new Thread(new UDPService()).start();
	}
	
	private boolean initPeers() {
		Properties peerProp = new Properties();
		try {
			FileInputStream stream = this.openFileInput("peers.cfg");
			peerProp.load(stream);
			peers.setText("Peers(" + peerProp.size() + ")");
		} catch(Exception e) {
			logs.setText(e.getMessage());
			return false;
		}
		Iterator<Entry<Object, Object>> it = peerProp.entrySet().iterator();
		while (it.hasNext()) {  
            Entry<Object, Object> entry = it.next(); 
            String peerName = entry.getKey().toString();
            String peerInfo = entry.getValue().toString();
            Map<String, String> peerInfoMap = new HashMap<String, String>();
            String[] temp = peerInfo.split(",");
            peerInfoMap.put("name", peerName);
            peerInfoMap.put("ip", temp[0]);
            peerInfoMap.put("port", temp[1]);
            peerInfoMap.put("innerIP", temp[2]);
            peerMapList.add(peerInfoMap);
            if(this.IMEICode.equals(peerName)) {
            	this.outerIP = temp[0];
            }
        }  
		return true;
	}
	
	private class PeerListViewAdapter extends BaseAdapter {
    	private LayoutInflater customInflater;
    	private int layoutID;
    	
    	private int selectedItemIndex = -1; 
    	
    	private PeerListViewAdapter(Context context, int layoutID) {
    		this.customInflater = LayoutInflater.from(context);
    		this.layoutID = layoutID;
    	}
        
    	@Override
    	public int getCount() {
    		return peerMapList.size();
    	}
    	
    	@Override
    	public Object getItem(int position) {
    		return peerMapList.get(position);
    	}
    	
    	@Override
    	public long getItemId(int position) {
    		return position;
    	}
    	
    	@Override
    	public View getView(int position, View convertView, ViewGroup parent) {
            final Map<String, String> peerMap = (Map<String, String>)peerMapList.get(position);
            TextView peerInfoView;
            TextView peerStatusView;
            if(convertView == null) {
                convertView = customInflater.inflate(layoutID, null);          		 			
            } 
            peerInfoView = (TextView)convertView.findViewById(R.id.peer_info);
            peerStatusView = (TextView)convertView.findViewById(R.id.peer_status);
            String prefix = "";
            String ip;
            String port;
            if(peerMap.get("name").equals(IMEICode)) {
            	prefix = "MYSELF-";
            	ip = peerMap.get("innerIP");
            	port = RegisterActivity.LOCAL_PORT + "";
            } else if(peerMap.get("ip").equals(outerIP)) {
            	ip = peerMap.get("innerIP");
            	port = RegisterActivity.LOCAL_PORT + "";
            	prefix = "Same-WIFI-";
            } else {
            	ip = peerMap.get("ip");
            	port = peerMap.get("port");
            }
            peerInfoView.setText(prefix + "IMEI:" + peerMap.get("name") + " ip:" + peerMap.get("ip") + " port:" + peerMap.get("port") + " inner IP:" + peerMap.get("innerIP"));          
            if(position == selectedItemIndex) {
            	peerInfoView.setTextColor(Color.BLUE);
            	peerStatusView.setTextColor(Color.BLUE);
            	peerStatusView.setText("status: connecting...");
            }else {
            	peerInfoView.setTextColor(Color.BLACK);
            	peerStatusView.setTextColor(Color.BLACK);
            	peerStatusView.setText("status: unknow, touch to say hello.");
            }
            peerPositionMap.put(ip + "_" + port, position);
    		return convertView;
    	}
    	
    	public void setSelectedItemIndex(int selectedItemIndex) {   
            this.selectedItemIndex = selectedItemIndex;   
       }
    }
	
	private final class PeerItemClickListener implements OnItemClickListener {
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        	listViewAdpter.setSelectedItemIndex(position);
        	listViewAdpter.notifyDataSetInvalidated();
        	currentPeer = position;
        	Map<String, String> selectedPeerMap = peerMapList.get(position);
        	if(selectedPeerMap.get("ip").equals(outerIP)) {
        		selectedIP = selectedPeerMap.get("innerIP");
        		selectedPort = RegisterActivity.LOCAL_PORT;
        		toSendQueue.add(new UDPSendingMessage(RegisterActivity.SAY_HELLO, selectedIP, selectedPort));
        	} else {
        		selectedIP = selectedPeerMap.get("ip");
        		selectedPort = Integer.parseInt(selectedPeerMap.get("port"));
        		toSendQueue.add(new UDPSendingMessage(RegisterActivity.SAY_HELLO, selectedIP, selectedPort));
        		String msg = RegisterActivity.BRIDGE_CODE + selectedPeerMap.get("ip") + "," + selectedPeerMap.get("port");
            	toSendQueue.add(new UDPSendingMessage(msg, RegisterActivity.UDP_SERVER_IP, RegisterActivity.SERVER_GET_PORT));
        	}
        		
        }
    }
	
	private class UDPService implements Runnable {
		
		public void run() {
			
			try {
				socket = new DatagramSocket(RegisterActivity.LOCAL_PORT);
			} catch(SocketException e) {
				System.out.println(e.getMessage());
				return;
			}
			
			byte[] getBytes = new byte[1024];
	        DatagramPacket getPacket = new DatagramPacket(getBytes, getBytes.length);
	        String msg = null;
	        String clientIP = null;
	        int clientPort = 0;
	        
	        InetAddress destination = null;
	        byte[] sendBytes = null;
	        DatagramPacket sendPacket = null;
	        
	        while(keepListening) {
	        	boolean hasData = true;
	        	try {
	        		socket.setSoTimeout(100);
	        	} catch(SocketException e) {
	        		System.out.println(e.getMessage());
	        		return;
	        	}
	        	try {
	        		socket.receive(getPacket);
	        	} catch(IOException e) {
	        		//System.out.println(e + e.getMessage());
	        		hasData = false;
	        	}
	        	if(hasData) {
	        		System.out.println(getPacket.getLength());
	        		msg = new String(getBytes, 0, getPacket.getLength());
	        		System.out.println(msg);
	        		clientIP = getPacket.getAddress().getHostAddress();
	        		System.out.println(clientIP);
	        		clientPort = getPacket.getPort();
	        		System.out.println(clientPort);
	        		noticeHandler("got: " + msg + " fromIP: " + clientIP + " fromPort: " + clientPort, msg, clientIP, clientPort);
	        		handleMessage(msg, clientIP, clientPort);
	        	}
	        	
	        	if(!toSendQueue.isEmpty()) {
	        		UDPSendingMessage sendMsg = toSendQueue.remove();
	        		try {  
	                    destination = InetAddress.getByName(sendMsg.getToIP());
	                } catch (UnknownHostException e) {
	                	System.out.println(e.getMessage());
	                }
	        		sendBytes = sendMsg.getMessage().getBytes();
	        		sendPacket = new DatagramPacket(sendBytes, sendBytes.length, destination, sendMsg.getToPort());
	                try {
	                	socket.send(sendPacket);
	                	noticeHandler("sent: " + sendMsg.getMessage() + " toIP: " + sendMsg.getToIP() + " toPort: " + sendMsg.getToPort(), "", "", 0);
	                	if(sendMsg.getMessage().length() > 1 && RegisterActivity.UNREGISTER_CODE.equals(sendMsg.getMessage().substring(0, 2))) {
	                		System.exit(0);
	                	}
	                } catch(IOException e) { 
	                	System.out.println(e.getMessage());
	                }
	        	}	
	        }
	        socket.close();
		}
		
		private void handleMessage(String msg, String fromIP, int fromPort) {
			if(msg.length() < 2) {
				return;
			}
			if(RegisterActivity.CALL_PEER_CODE.equals(msg.substring(0, 2))) {
				msg = msg.substring(2);
				int index = msg.indexOf(",");
				String toIP = msg.substring(0, index);
				int toPort = Integer.parseInt(msg.substring(index + 1));
				toSendQueue.add(new UDPSendingMessage(RegisterActivity.SAY_HELLO, toIP, toPort));
			} else if(RegisterActivity.SAY_HELLO.equals(msg)) {
				toSendQueue.add(new UDPSendingMessage(RegisterActivity.SAY_HELLO_BACK, fromIP, fromPort));
			}
		}
		
		private void noticeHandler(String note, String msg, String fromIP, int fromPort) {
			Message hm = new Message();
			Bundle bundle = new Bundle();
			bundle.putString("note", note);
			bundle.putString("msg", msg);
			bundle.putString("ip", fromIP);
			bundle.putInt("port", fromPort);
			hm.setData(bundle);
			UDPServiceHandler.sendMessage(hm);
		}
	}
	
	Handler UDPServiceHandler = new Handler() {
		public void handleMessage(Message msg) {
			String note = msg.getData().getString("note");
			logs.setText(note + "\n\n" + logs.getText());
			String udpMsg = msg.getData().getString("msg");
			String ip = msg.getData().getString("ip");
			int port = msg.getData().getInt("port");
			if(udpMsg != null && !"".equals(udpMsg)) {
				onMessageGot(udpMsg, ip, port);
			}
			super.handleMessage(msg);
		}
	};
	
	private void onMessageGot(String msg, String fromIP, int fromPort) {
		if(RegisterActivity.SAY_HELLO_BACK.equals(msg) || RegisterActivity.SAY_HELLO.equals(msg)) {
			int position = peerPositionMap.get(fromIP + "_" + fromPort);
			if(position > -1) {
				TextView peerStatus = (TextView)peerListView.getChildAt(position).findViewById(R.id.peer_status);
				if(peerStatus != null) {
					peerStatus.setText("status: connected");
				}
			}
		}
	}
	
	@Override
	protected void onDestroy() {
		keepListening = false;
		super.onDestroy();
		//System.exit(0);
	}
	
	public void sendTalk(View view) {
		Map<String, String> selectedPeerMap = peerMapList.get(this.currentPeer);
		String ip;
		int port;
		if(this.outerIP.equals(selectedPeerMap.get("ip"))) {
			ip = selectedPeerMap.get("innerIP");
			port = RegisterActivity.LOCAL_PORT;
		} else {
			ip = selectedPeerMap.get("ip");
			port = Integer.parseInt(selectedPeerMap.get("port"));
		}
		toSendQueue.add(new UDPSendingMessage(talk.getText().toString(), ip, port));
		talk.setText("");
	}
	
	public void refresh(View view) {
		keepListening = false;
		try {
			socket.close();
		} catch(Exception e) {
			e.printStackTrace();
		}
		Intent intent = new Intent();
		intent.setClass(PeerListActivity.this, RegisterActivity.class);
		startActivity(intent);
		PeerListActivity.this.finish();
	}
	
	public void sendFile(View view) {
		Intent fileSelector = new Intent(Intent.ACTION_PICK,android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI);
		startActivityForResult(fileSelector, 1);
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {  
        if(resultCode == Activity.RESULT_OK) {  
        	Uri uri = data.getData();  
            Cursor cursor = this.getContentResolver().query(uri, null, null, null, null);  
            cursor.moveToFirst();  
            fileName = cursor.getString(2);
            filePath = cursor.getString(1);
            fileSize = (float)cursor.getInt(3) / 1024;
            String tmp = "Preparing sending file " + fileName + " to selected peer, path is " + filePath + ", size is " + fileSize + "KB.";
            logs.setText(tmp + "\n\n" + logs.getText());
            toSendQueue.add(new UDPSendingMessage(RegisterActivity.REQUEST_SEND_FILE_CODE + fileName + "," + fileSize, selectedIP, selectedPort));
        }  
    }
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		switch (keyCode) {
		case KeyEvent.KEYCODE_BACK:
			AlertDialog.Builder build = new AlertDialog.Builder(this);
			build.setTitle("Exit").setMessage("are you sure to exit?").setPositiveButton("Yes", new DialogInterface.OnClickListener() {
						
						@Override
						public void onClick(DialogInterface dialog, int which) {
							toSendQueue.add(new UDPSendingMessage("03" + PeerListActivity.this.IMEICode, RegisterActivity.UDP_SERVER_IP, RegisterActivity.SERVER_GET_PORT));
							//keepListening = false;
							//System.exit(0);						
						}
					}).setNegativeButton("No", new DialogInterface.OnClickListener() {						
						@Override
						public void onClick(DialogInterface dialog, int which) {							
						}
					}).show();
			break;
		default:
			break;
		}
		return false;	
	}
}
