package com.luanfei.p2pplatform.server;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

public class UDPGettingService {
	
	public static int GET_PORT = 5454;
	public static int SEND_PORT = 5455;
	
	private Map<String, String> peers = new HashMap<String, String>();
	
	public void start() {
		
		DatagramSocket getter = null;
		try {
			getter = new DatagramSocket(GET_PORT);
		} catch(SocketException e) {
			System.out.println(e.getMessage());
			return;
		}
		
		byte[] gottenBytes = new byte[1024];
        DatagramPacket packet = new DatagramPacket(gottenBytes, gottenBytes.length);
        String msg = null;
        String clientIP = null;
        int clientPort = 0;
        while(true) {
        	try {
        	    getter.receive(packet);
        	} catch(IOException e) {
        		System.out.println(e.getMessage());
        		break;
        	}
        	
        	if(packet.getLength() > 0) {
        		msg = new String(gottenBytes, 0, packet.getLength());
        		System.out.println(msg);
        		clientIP = packet.getAddress().getHostAddress();
        		System.out.println(clientIP);
        		clientPort = packet.getPort();
        		System.out.println(clientPort);
        		handleMessage(msg, clientIP, clientPort);
        	}
        }
	}
	
	private void handleMessage(String msg, String clientIP, int clientPort) {
		String msgType = msg.substring(0, 2);
		if("01".equals(msgType)) {
			handleRegister(msg.substring(2), clientIP, clientPort);
		} else if("02".equals(msgType)) {
			handleBridge(msg.substring(2), clientIP, clientPort);
		} else if("03".equals(msgType)) {
			handleUnRegister(msg.substring(2), clientIP, clientPort);
		}
	}
	
	private void handleRegister(String peerName, String peerIP, int peerPort) {
		String[] temp = peerName.split(",");		
		peers.put(temp[0], peerIP + "," + peerPort + "," + temp[1]);
		new Thread(new SendUDPThread("10" + mapToStr(peers), peerIP, peerPort)).start();
	}
	
	private void handleBridge(String toPeerInfo, String fromIP, int fromPort) {
		int index = toPeerInfo.indexOf(",");
		String toPeerIP = toPeerInfo.substring(0, index);
		int toPeerPort = Integer.parseInt(toPeerInfo.substring(index + 1));
		new Thread(new SendUDPThread("11" + fromIP + "," + fromPort, toPeerIP, toPeerPort)).start();
	}
	
	private void handleUnRegister(String peerName, String peerIP, int peerPort) {
		peers.remove(peerName);
		new Thread(new SendUDPThread("12", peerIP, peerPort)).start();
	}
	
	private class SendUDPThread implements Runnable {
		private String UDPMessage;
		private String sendToIP;
		private int sendToPort;
		
		private SendUDPThread(String UDPMessage, String sendToIP, int sendToPort) {
			this.UDPMessage = UDPMessage;
			this.sendToIP = sendToIP;
			this.sendToPort = sendToPort;
		}
		
		public void run() {
			DatagramSocket sender = null;
			try {
				sender = new DatagramSocket(SEND_PORT);
			} catch(SocketException e) {
				System.out.println(e.getMessage());
				return;
			}
			
			byte[] sendBytes = this.UDPMessage.getBytes();
            InetAddress destination = null ;  
            try {  
                destination = InetAddress.getByName(this.sendToIP);
            } catch (UnknownHostException e) {  
                System.out.println(e.getMessage());
                sender.close();
                return;  
            }
            
            DatagramPacket packet = new DatagramPacket(sendBytes, sendBytes.length, destination, sendToPort);
            try {
            	sender.send(packet);
            	System.out.println("sent:" + this.UDPMessage);
            } catch(Exception e) {
            	System.out.println(e.getMessage());
            } finally {
            	sender.close();
            }
		}
	}
	
	private String mapToStr(Map<String, String> map) {
		StringBuffer buf = new StringBuffer();
		for(String key : map.keySet()) {
			buf.append(";" + key + ":" + map.get(key));
		}
		return buf.toString().substring(1);
	}
	
	public static void main(String[] args) {
		UDPGettingService service = new UDPGettingService();		
		service.start();
	}
}
