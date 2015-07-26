package com.luanfei.p2pplatform.client.android.util;

public class UDPSendingMessage {
	
	private String message;
	private String toIP;
	private int toPort;
	
	public UDPSendingMessage(String message, String toIP, int toPort) {
		this.message = message;
		this.toIP = toIP;
		this.toPort = toPort;
	}
	
	public String getMessage() {
		return this.message;
	}
	
	public String getToIP() {
		return this.toIP;
	}
	
	public int getToPort() {
		return this.toPort;
	}

}
