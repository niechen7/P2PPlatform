package com.luanfei.p2pplatform.client.android;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
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
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.StatFs;
import android.support.v7.app.ActionBarActivity;
import android.text.method.ScrollingMovementMethod;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;

public class PeerListActivity extends ActionBarActivity {
	
	private List<Map<String, String>> peerMapList = new ArrayList<Map<String, String>>();
	
	private TextView peers;
	private TextView logs;
	private EditText talk;
	private ListView peerListView;
	private Button sendBtn;
	private Button sendFileBtn;
	private ProgressBar sendFilePg;
	private TextView sendFileInfo;
	private TextView sendFileSpeed;
	
	private ProgressBar getFilePg;
	private TextView getFileInfo;
	private TextView getFileSpeed;
	private TextView getFileOpen;
	
	private String IMEICode = null;
	private String innerIP = null;
	private String outerIP = null;
	
	private PeerListViewAdapter listViewAdpter;
	
	private Queue<UDPSendingMessage> toSendQueue = new LinkedBlockingQueue<UDPSendingMessage>();
	
	boolean keepListening = true;
	
	private int currentPeer = 0;
	
	DatagramSocket socket = null;
	
	byte[] sendFileBuffer = null;
	
	int blockSize = 1024 * 20;
	
	int tcpSocketPort = 5456;
	
    String sentFileName;
    String sentFilePath;
    long sentFileSize;
    int sendFileMaxBlockId;
    int sendFileId = -1;
    int lastSentBlockId = 0;
    long startSendTime = 0;
    String sendFileToIP;
    int sendFileToPort;
    long previousSentTime = 0;
    boolean sendFileToInnerPeer = false;
    
    String getFileName;
    long getFileSize;
    int lastGotBlockId = 0;
    int previousGotBlockId = 0;
    int getFileMaxBlockId;
    String getFileFromIP;
    int getFileFromPort;
    int getFileId = -1;
    long startGetTime = 0;
    long previousGetTime = 0;
    String getFilePath;
    boolean getFileFromInnerPeer = false;
    
    byte[] getBuffer = null;
    
    String selectedIP;
    int selectedPort;
    boolean selectedInnerPeer = false;
    
    boolean isFileTransfer = false;
    
    Map<String, Integer> peerPositionMap = new HashMap<String, Integer>();
    Map<String, Integer> peerStatusMap = new HashMap<String, Integer>(); 
    
    int logsLineNum = 0;
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		try {
		System.out.println("onCreate...");
		SharedPreferences settings = getSharedPreferences("registerInfo", Activity.MODE_PRIVATE);
		IMEICode = settings.getString("IMEI", "uu");
		innerIP = settings.getString("innerIP", "0.0.0.0");
		System.out.println(IMEICode);
		System.out.println(innerIP);
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_peerlist);
		peers = (TextView)this.findViewById(R.id.peers);
		talk = (EditText)this.findViewById(R.id.talk);
		logs = (TextView)this.findViewById(R.id.logs);
		sendBtn = (Button)this.findViewById(R.id.send_talk);
		sendFileBtn = (Button)this.findViewById(R.id.send_file);
		sendBtn.setEnabled(false);
		sendFileBtn.setEnabled(false);
		logs.setMovementMethod(ScrollingMovementMethod.getInstance()); 
		sendFilePg = (ProgressBar)this.findViewById(R.id.sendFilePg);
		sendFilePg.setVisibility(View.GONE);
		sendFileInfo = (TextView)this.findViewById(R.id.sendFileInfo);
		sendFileSpeed = (TextView)this.findViewById(R.id.sendFileSpeed);
		sendFileInfo.setVisibility(View.GONE);
		sendFileSpeed.setVisibility(View.GONE);
		
		getFilePg = (ProgressBar)this.findViewById(R.id.getFilePg);
		getFilePg.setVisibility(View.GONE);
		getFileInfo = (TextView)this.findViewById(R.id.getFileInfo);
		getFileSpeed = (TextView)this.findViewById(R.id.getFileSpeed);
		getFileInfo.setVisibility(View.GONE);
		getFileSpeed.setVisibility(View.GONE);
		
		getFileOpen = (TextView)this.findViewById(R.id.getFileOpen);
		getFileOpen.setVisibility(View.GONE);
		
		if(!initPeers()) {
			return;
		}
		peerListView = (ListView)findViewById(R.id.peers_list);
		listViewAdpter = new PeerListViewAdapter(PeerListActivity.this, R.layout.peer_list);
		peerListView.setAdapter(listViewAdpter);
		peerListView.setOnItemClickListener(new PeerItemClickListener());
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
	    // Inflate the menu items for use in the action bar
	    MenuInflater inflater = getMenuInflater();
	    inflater.inflate(R.menu.peerlist_activity_actions, menu);
	    return super.onCreateOptionsMenu(menu);
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
	    // Handle presses on the action bar items
	    switch (item.getItemId()) {
	        case R.id.action_download:
	        	//keepListening = false;
	    		//try {
	    		//	socket.close();
	    		//} catch(Exception e) {
	    		//	e.printStackTrace();
	    		//}
	    		Intent intent = new Intent();
	    		intent.setClass(PeerListActivity.this, DownloadActivity.class);
	    		startActivity(intent);
	    		//PeerListActivity.this.finish();
	            //return true;
	        default:
	            return super.onOptionsItemSelected(item);
	    }
	}
	
	@Override
	protected void onStart() {
		System.out.println("onStart()...");
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
            } else {
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
        	sendBtn.setEnabled(false);
    		sendFileBtn.setEnabled(false);
        	listViewAdpter.setSelectedItemIndex(position);
        	listViewAdpter.notifyDataSetInvalidated();
        	currentPeer = position;
        	Map<String, String> selectedPeerMap = peerMapList.get(position);
        	if(selectedPeerMap.get("ip").equals(outerIP)) {
        		selectedInnerPeer = true;
        		selectedIP = selectedPeerMap.get("innerIP");
        		selectedPort = RegisterActivity.LOCAL_PORT;
        		toSendQueue.add(new UDPSendingMessage(RegisterActivity.SAY_HELLO, selectedIP, selectedPort));
        	} else {
        		selectedInnerPeer = false;
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
			DataOutputStream fos = null;
			try {
			
			try {
				socket = new DatagramSocket(RegisterActivity.LOCAL_PORT);
			} catch(SocketException e) {
				System.out.println(e.getMessage());
				return;
			}
			
			byte[] getBytes = new byte[blockSize];
	        DatagramPacket getPacket = new DatagramPacket(getBytes, getBytes.length);
	        String msg = null;
	        String clientIP = null;
	        int clientPort = 0;
	        
	        InetAddress destination = null;
	        byte[] sendBytes = null;
	        DatagramPacket sendPacket = null;
	        
	        byte[] prefix1 = null;
	        byte[] prefix2 = null;
	        while(keepListening) {
	        	boolean hasData = true;
	        	boolean isFileContent = false;
	        	try {
	        		socket.setSoTimeout(10);
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
	        		clientIP = getPacket.getAddress().getHostAddress();
	        		clientPort = getPacket.getPort();
	        		if(getPacket.getLength() > 8) {
	        			prefix1 = new byte[4];
	        			prefix2 = new byte[4];
	        			System.arraycopy(getBytes, 0, prefix1, 0, 4);
	        			System.arraycopy(getBytes, 4, prefix2, 0, 4);
	        			if(bytes2Int(prefix1) == getFileId && clientIP.equals(getFileFromIP) && clientPort == getFileFromPort) {
	        				isFileContent = true; 
	        			}
	        		}
	        		if(isFileContent) {
	        			//lastGotBlockId = bytes2Int(prefix2);
	        			if(lastGotBlockId == 0) {
	        				fos = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(getFilePath)));
	        			}
	        			if(bytes2Int(prefix2) - lastGotBlockId == 1) {
	        			    fos.write(getBytes, 8, getPacket.getLength() - 8);
	        			    fos.flush();
	        			    lastGotBlockId++;
	        			    noticeHandler("Got part " + lastGotBlockId + " of file " + getFileName + " from " + clientIP, RegisterActivity.GOT_FILE_PART + lastGotBlockId, clientIP, clientPort);
	        			}
	        			toSendQueue.add(new UDPSendingMessage(RegisterActivity.GOT_FILE_PART_CONFIRM + lastGotBlockId, clientIP, clientPort));
	        			if(lastGotBlockId == getFileMaxBlockId) {
	        				fos.close();
	        			}
	        		} else {
		        		msg = new String(getBytes, 0, getPacket.getLength());		        		
		        		noticeHandler("got: " + msg + " fromIP: " + clientIP + " fromPort: " + clientPort, msg, clientIP, clientPort);
		        		handleMessage(msg, clientIP, clientPort);
		            }
	        	}
	        	
	        	if(!toSendQueue.isEmpty()) {
	        		UDPSendingMessage sendMsg = toSendQueue.remove();
	        		try {  
	                    destination = InetAddress.getByName(sendMsg.getToIP());
	                } catch (UnknownHostException e) {
	                	System.out.println(e.getMessage());
	                }
	        		String temp;
	        		if(RegisterActivity.SEND_FILE_SIGN.equals(sendMsg.getMessage())) {
	        			sendBytes = sendFileBuffer;
	        			temp = "part " + (lastSentBlockId + 1) + " of file " + sentFileName;
	        		} else {
	        		    sendBytes = sendMsg.getMessage().getBytes();
	        		    temp = sendMsg.getMessage();
	        		}
	        		sendPacket = new DatagramPacket(sendBytes, sendBytes.length, destination, sendMsg.getToPort());
	                try {
	                	socket.send(sendPacket);
	                	noticeHandler("sent: " + temp + " toIP: " + sendMsg.getToIP() + " toPort: " + sendMsg.getToPort(), "", "", 0);
	                	if(sendMsg.getMessage().length() > 1 && RegisterActivity.UNREGISTER_CODE.equals(sendMsg.getMessage().substring(0, 2))) {
	                		System.exit(0);
	                	}
	                } catch(IOException e) { 
	                	System.out.println(e.getMessage());
	                }
	        	}	
	        }
	        socket.close();
			} catch(Exception e) {
				e.printStackTrace();
			} finally {
				try {
					fos.close();
				} catch(Exception e){}
			}
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
			System.out.println("Handling message.........");
			String note = msg.getData().getString("note");
			if(logsLineNum > 10) {
				logsLineNum = 0;
				logs.setText(note);
			} else {
			    logs.setText(note + "\n\n" + logs.getText());
			    logsLineNum++;
			}
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
		System.out.println("onMessageGot........." + msg);
		final String tempFromIP = fromIP;
		final int tempFromPort = fromPort;
		if(RegisterActivity.SAY_HELLO_BACK.equals(msg) || RegisterActivity.SAY_HELLO.equals(msg)) {
			int position = peerPositionMap.get(fromIP + "_" + fromPort);
			if(position > -1) {
				TextView peerStatus = (TextView)peerListView.getChildAt(position).findViewById(R.id.peer_status);
				if(peerStatus != null) {
					peerStatus.setText("status: connected");
					sendBtn.setEnabled(true);
					if(!isFileTransfer) {
					    sendFileBtn.setEnabled(true);
					}
				}
			}
		} else if(msg.length() > 2 && RegisterActivity.REQUEST_SEND_FILE_CODE.equals(msg.substring(0, 2))) {
			msg = msg.substring(2);
			String[] temp = msg.split(",");
			getFileName = temp[0];
			getFilePath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + RegisterActivity.fileDir + "/" + getFileName;
			getFileSize = Long.parseLong(temp[1]);
			this.getFileMaxBlockId = maxBlockNum(this.getFileSize, blockSize - 8);
			getFileId = Integer.parseInt(temp[2]);
			this.getFileFromInnerPeer = "true".equalsIgnoreCase(temp[3]);
			this.lastGotBlockId = 0;
			previousGotBlockId = 0;
			getFileFromIP = fromIP;
			getFileFromPort = fromPort;
			if(this.getAvailableBytes() > getFileSize) {
				AlertDialog.Builder build = new AlertDialog.Builder(this);
				build.setTitle("File Send Request").setMessage("Do you agree to get the file:" + getFileName + "(" + getFileSize + "bytes) from " + fromIP + "?").setPositiveButton("Yes", new DialogInterface.OnClickListener() {
				    @Override
					public void onClick(DialogInterface dialog, int which) {
				    	toSendQueue.add(new UDPSendingMessage(RegisterActivity.AGREE_SEND_FILE_CODE + ":User agree to get the file " + getFileName + "(" + getFileSize + "bytes).", tempFromIP, tempFromPort));
				    	isFileTransfer = true;
				    	sendFileBtn.setEnabled(false);
						getFileOpen.setVisibility(View.GONE);
				    	getFileInfo.setVisibility(View.VISIBLE);
						getFileInfo.setText("Getting file " + getFileName + "... size: 0/" + (getFileSize / 1000) + " kb");
						getFileSpeed.setVisibility(View.VISIBLE);
						getFileSpeed.setText("Speed: 0 kb/s");
						getFilePg.setVisibility(View.VISIBLE);
						getFilePg.setMax(getFileMaxBlockId);
						getFilePg.setProgress(0);
						startGetTime = System.currentTimeMillis();
						previousGetTime = startGetTime;
						if(getFileFromInnerPeer) {
							new Thread(new TcpGetFileService()).start();
						}
				    	
					}}).setNegativeButton("No", new DialogInterface.OnClickListener() {						
					    @Override
						public void onClick(DialogInterface dialog, int which) {
					    	toSendQueue.add(new UDPSendingMessage(RegisterActivity.REFUSE_SEND_FILE_CODE + ":User refused to get the file " + getFileName + "(" + getFileSize + "bytes).", tempFromIP, tempFromPort));
						}
					}).show();
			} else {
				toSendQueue.add(new UDPSendingMessage(RegisterActivity.REFUSE_SEND_FILE_CODE + ":No enough space for file " + getFileName + "(" + getFileSize + "bytes).", fromIP, fromPort));
			}
		} else if(msg.length() > 2 && RegisterActivity.REFUSE_SEND_FILE_CODE.equals(msg.substring(0, 2))) {
			sendFileBtn.setEnabled(true);
		} else if(msg.length() > 2 && RegisterActivity.AGREE_SEND_FILE_CODE.equals(msg.substring(0, 2))) {
			this.lastSentBlockId = 0;
			sendFileBtn.setEnabled(false);
			isFileTransfer = true;
			sendFileInfo.setVisibility(View.VISIBLE);
			sendFileInfo.setText("Sending file " + sentFileName + "... size: 0/" + (this.sentFileSize / 1000) + " kb");
			sendFileSpeed.setVisibility(View.VISIBLE);
			sendFileSpeed.setText("Speed: 0 kb/s");
			sendFilePg.setVisibility(View.VISIBLE);
			sendFilePg.setMax(this.sendFileMaxBlockId);
			sendFilePg.setProgress(0);
			this.startSendTime = System.currentTimeMillis();
			this.previousSentTime = this.startSendTime;
			if(sendFileToInnerPeer) {
				new Thread(new TcpSendFileService()).start();
			} else {
				new Thread(new SendFileService()).start();
			}
		} else if(msg.length() > 2 && RegisterActivity.GOT_FILE_PART_CONFIRM.equals(msg.substring(0, 2))) {
			if(fromIP.equals(this.sendFileToIP) && fromPort == this.sendFileToPort) {
				try {
					this.sendFileSpeed.setText("Speed: " + (((this.blockSize - 8) * (Integer.parseInt(msg.substring(2)) - lastSentBlockId) / 1000) / (((float)(System.currentTimeMillis() - this.previousSentTime)) / 1000)) + " kb/s");
				} catch(Exception e) {
					e.printStackTrace();
				}
				this.previousSentTime = System.currentTimeMillis();
				this.lastSentBlockId = Integer.parseInt(msg.substring(2));
				sendFilePg.setProgress(lastSentBlockId);
		        String tmp;
				if(this.lastSentBlockId == this.sendFileMaxBlockId) {
					tmp = "Sending file " + sentFileName + " finished. size: " + (this.sentFileSize / 1000) + "/" + (this.sentFileSize / 1000) + " kb";
					sendFileBtn.setEnabled(true);
					isFileTransfer = false;
				} else {
					tmp = "Sending file " + sentFileName + "... size: " + (this.lastSentBlockId * (this.blockSize - 8) / 1000) + "/" + (this.sentFileSize / 1000) + " kb";
				}
				sendFileInfo.setText(tmp); 
			}
		} else if(msg.length() > 2 && RegisterActivity.GOT_FILE_PART.equals(msg.substring(0, 2))) {
			try {
				this.getFileSpeed.setText("Speed: " + (((this.blockSize - 8) * (lastGotBlockId - previousGotBlockId) / 1000) / (((float)(System.currentTimeMillis() - this.previousGetTime)) / 1000)) + " kb/s");
			} catch(Exception e) {
				e.printStackTrace();
			}
			previousGotBlockId = lastGotBlockId;
			this.previousGetTime = System.currentTimeMillis();
			getFilePg.setProgress(lastGotBlockId);
	        String tmp;
			if(this.lastGotBlockId == this.getFileMaxBlockId) {
				tmp = "Getting file " + getFileName + " finished. size: " + (this.getFileSize / 1000) + "/" + (this.getFileSize / 1000) + " kb";
				sendFileBtn.setEnabled(true);
				isFileTransfer = false;
			} else {
				tmp = "Getting file " + getFileName + "... size: " + (this.lastGotBlockId * (this.blockSize - 8) / 1000) + "/" + (this.getFileSize / 1000) + " kb";
			}
			getFileInfo.setText(tmp); 
			int gotPart = Integer.parseInt(msg.substring(2));
			if(gotPart == this.getFileMaxBlockId) {
				this.isFileTransfer = false;
				this.sendFileBtn.setEnabled(true);
				getFileOpen.setVisibility(View.VISIBLE);
				getFileOpen.setText(this.getFilePath);
				getFileOpen.setClickable(true);
			}
		}
	}
	
	public void openGetFile(View view) {
		Intent it = new Intent(Intent.ACTION_VIEW);
		Uri uri = Uri.parse(getFilePath);
		it.setDataAndType(uri, "video/*");
		startActivity(it);
	}
	
	@Override
	protected void onStop() {
		System.out.println("onStop...");
		super.onStop();
		//System.exit(0);
	}
	
	@Override
	protected void onDestroy() {
		System.out.println("onDestroy");
		keepListening = false;
		super.onDestroy();
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
		this.sendFileToIP = selectedIP;
		this.sendFileToPort = selectedPort;
		Intent fileSelector = new Intent(Intent.ACTION_PICK,android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI);
		startActivityForResult(fileSelector, 1);
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {  
        if(resultCode == Activity.RESULT_OK) {  
        	Uri uri = data.getData();  
            Cursor cursor = this.getContentResolver().query(uri, null, null, null, null);  
            cursor.moveToFirst();  
            sentFileName = cursor.getString(2);
            sentFilePath = cursor.getString(1);
            sentFileSize = cursor.getLong(3);
            sendFileToInnerPeer = selectedInnerPeer;
            this.sendFileMaxBlockId = this.maxBlockNum(sentFileSize, blockSize - 8);
            String tmp = "Preparing sending file " + sentFileName + " to selected peer, path is " + sentFilePath + ", size is " + sentFileSize + "Bytes.";
            logs.setText(tmp);
            
            toSendQueue.add(new UDPSendingMessage(RegisterActivity.REQUEST_SEND_FILE_CODE + sentFileName + "," + sentFileSize + "," + this.generateSendFileID() + "," + this.sendFileToInnerPeer, sendFileToIP, sendFileToPort));
            sendFileBtn.setEnabled(false);
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
	
	private long getAvailableBytes() {
		long availableBytes = 0;

        try {
		    File path = Environment.getExternalStorageDirectory();
		    System.out.println(path);
		    StatFs stat = new StatFs(path.getPath()); 
		    availableBytes = ((long)stat.getFreeBlocks()) * ((long)stat.getBlockSize());
		    System.out.println("remains " + availableBytes + " bytes.");
		    System.out.println(((double)availableBytes) / 1024 / 1024 / 1024);
		} catch(Exception e) {
			System.out.println(e.getMessage());
		}
		return availableBytes;
	}
	
	private int generateSendFileID() {
		return (++this.sendFileId) % 10;
	}
	
    private class SendFileService implements Runnable {
		
		public void run() {
			DataInputStream fis = null;
			try {
				lastSentBlockId = 0;
				//startSendTime = System.currentTimeMillis();
			    byte[] bufferBytes = new byte[blockSize - 8];
			    byte[] prefix1 = int2Bytes(sendFileId);
			    byte[] prefix2;
			    fis = new DataInputStream(new BufferedInputStream(new FileInputStream(sentFilePath)));
			    int prepareToSendBlockId = 1;
			    long lastSentTime = 0;
			    int readBytes = 0;
			    while(true) {
			    	if((prepareToSendBlockId - lastSentBlockId) == 1) {
			    		prefix2 = int2Bytes(prepareToSendBlockId);			    		
			    		readBytes = fis.read(bufferBytes);
			    		if(readBytes == -1) {
			    			break;
			    		}
			    		sendFileBuffer = new byte[8 + readBytes];
			    		System.arraycopy(prefix1, 0, sendFileBuffer, 0, prefix1.length);			    		
			    		System.arraycopy(prefix2, 0, sendFileBuffer, 4, prefix2.length);
			    		System.arraycopy(bufferBytes, 0, sendFileBuffer, 8, readBytes);
			    		toSendQueue.add(new UDPSendingMessage(RegisterActivity.SEND_FILE_SIGN, sendFileToIP, sendFileToPort));
			    		prepareToSendBlockId++;
			    		lastSentTime = System.currentTimeMillis();
			    		
			    	} else {
			    		if(System.currentTimeMillis() - lastSentTime > 300) {
			    			toSendQueue.add(new UDPSendingMessage(RegisterActivity.SEND_FILE_SIGN, sendFileToIP, sendFileToPort));
			    			lastSentTime = System.currentTimeMillis();
			    			System.out.println(toSendQueue.size());
			    		}
			    	}
			    }
			    
			} catch(Exception e) {
				e.printStackTrace();
			} finally {
				try {
					fis.close();
				} catch(Exception e) {
					
				}
			}
			
		}
    }
    
    private byte[] int2Bytes(int num) {  
        byte[] byteNum = new byte[4];  
        for (int i = 0; i < 4; i++) {  
            int offset = 32 - (i + 1) * 8;  
            byteNum[i] = (byte)((num >> offset) & 0xff);  
        }  
        return byteNum;  
    }
    
    public int bytes2Int(byte[] byteNum) {  
        int num = 0;  
        for (int i = 0; i < 4; i++) {  
            num <<= 8;  
            num |= (byteNum[i] & 0xff);  
        }  
        return num;  
    }
    
    private int maxBlockNum(long totalSize, int blockSize) {
    	long result = totalSize / blockSize;
    	if(totalSize % blockSize > 0) {
    		result++;
    	}
    	return (int)result;
    }
    
    private class TcpGetFileService implements Runnable {
		
		public void run() {
			ServerSocket server = null;
			Socket socket = null;
			DataOutputStream dos = null;
			DataInputStream dis = null;
			try {
				server = new ServerSocket(tcpSocketPort);
				socket = server.accept();
				socket.setReceiveBufferSize(1024 * 64);
				//socket.setTcpNoDelay(true);
				dis = new DataInputStream(socket.getInputStream());
				dos = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(getFilePath)));
				byte[] buffer = new byte[blockSize - 8];
				int getLength = 0;
				long lastTime = System.currentTimeMillis();
				long gotBytes = 0;
				while((getLength = dis.read(buffer, 0, buffer.length)) > 0) {
					dos.write(buffer, 0, getLength);
					//dos.flush();
					gotBytes += getLength;
					int i = gotBytes % (blockSize - 8) == 0 ? 0 : 1;
					lastGotBlockId = (int)(gotBytes / (blockSize - 8) + i);
					if(gotBytes == getFileSize || (System.currentTimeMillis() - lastTime) > 1000) {
					    sendMessageToHandler("Got part " + lastGotBlockId + " of file " + getFileName + " from " + getFileFromIP, RegisterActivity.GOT_FILE_PART + lastGotBlockId, getFileFromIP, getFileFromPort);
					    lastTime = System.currentTimeMillis();
					}
					
				}
			} catch(Exception e) {
				e.printStackTrace();
			} finally {
				try {
					dis.close();
				} catch(Exception e){}
				try {
					dos.close();
				} catch(Exception e){}
				try {
					socket.close();
				} catch(Exception e){}
				try {
					server.close();
				} catch(Exception e){}
			}
		}
		
		private void sendMessageToHandler(String note, String msg, String fromIP, int fromPort) {
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
    
private class TcpSendFileService implements Runnable {
		
		public void run() {
			Socket socket = null;
			DataOutputStream dos = null;
			DataInputStream dis = null;
			lastSentBlockId = 0;
			int sentIndex = 0;
			try {
				socket = new Socket();
				socket.setSendBufferSize(1024 * 64);
				socket.setTcpNoDelay(true);
				socket.connect(new InetSocketAddress(sendFileToIP, tcpSocketPort)); 
				dos = new DataOutputStream(socket.getOutputStream());
				dis = new DataInputStream(new BufferedInputStream(new FileInputStream(sentFilePath)));
				byte[] buffer = new byte[blockSize - 8];
				int getLength = 0;
				long lastTime = System.currentTimeMillis();
				while((getLength = dis.read(buffer, 0, buffer.length)) > 0) {
					dos.write(buffer, 0, getLength);
					dos.flush();
					sentIndex++;
					if(sentIndex == sendFileMaxBlockId || (System.currentTimeMillis() - lastTime) > 1000) {
						System.out.println("Sending message to handler.........");
					    sendMessageToHandler("Sent part " + sentIndex + " of file " + sentFileName, RegisterActivity.GOT_FILE_PART_CONFIRM + sentIndex, sendFileToIP, sendFileToPort);
					    lastTime = System.currentTimeMillis();
					}
				}
			} catch(Exception e) {
				e.printStackTrace();
			} finally {
				try {
					dis.close();
				} catch(Exception e){}
				try {
					dos.close();
				} catch(Exception e){}
				try {
					socket.close();
				} catch(Exception e){}
			}
		}
		
		private void sendMessageToHandler(String note, String msg, String fromIP, int fromPort) {
			Message hm = new Message();
			Bundle bundle = new Bundle();
			bundle.putString("note", note);
			bundle.putString("msg", msg);
			bundle.putString("ip", fromIP);
			bundle.putInt("port", fromPort);
			hm.setData(bundle);
			UDPServiceHandler.sendMessage(hm);
			System.out.println("Sent message to handler.........");
		}
    }
}
