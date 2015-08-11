package com.luanfei.p2pplatform.client.android;

import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URL;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

public class DownloadActivity extends ActionBarActivity {
		
	final static private int MSG_TITLE_GOT_SIZE = 51;
	final static private int MSG_TITLE_ERROR = 50;
	final static private int MSG_TITLE_DATA_GOT = 52;
	final static private int MSG_TITLE_DOWNLOADED = 53;
	
	final static private String MSG_KEY_TITLE = "title";
	final static private String MSG_KEY_FILE_SIZE = "size";
	final static private String MSG_KEY_ERROR_MSG = "err_msg";
	final static private String MSG_KEY_BYTES_GOT = "bytes_got";
	final static private String MSG_KEY_TOTAL_BYTES_GOT = "total_bytes_got";
	final static private String MSG_KEY_SPENT_TIME = "spent_time";
	final static private String MSG_KEY_TOTAL_SPENT_TIME = "total_time";
	
	private EditText etDownloadUrl;
	private ProgressBar part1Pg;
	private TextView part1Speed;
	
	private double fileMB;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_download);
		ActionBar actionBar = getSupportActionBar();
	    actionBar.setDisplayHomeAsUpEnabled(false);
	    
	    part1Pg = (ProgressBar)this.findViewById(R.id.part1Pg);
	    part1Pg.setVisibility(View.GONE);
	    part1Speed = (TextView)this.findViewById(R.id.part1Speed);
	    part1Speed.setVisibility(View.GONE);
	    
	    etDownloadUrl = (EditText)this.findViewById(R.id.download_url);
	}
	
	public void download(View view) {
		String textUrl = etDownloadUrl.getText().toString();
		if(textUrl == null || "".equals(textUrl.trim())) {	
			textUrl = etDownloadUrl.getHint().toString();
		}
		System.out.println(etDownloadUrl.getHint());
		new Thread(new DownloadService(textUrl, 1)).start();
	}
	
	Handler servicesHandler = new Handler() {
		public void handleMessage(Message msg) {
			int msgTitle = msg.getData().getInt(MSG_KEY_TITLE);
			if(MSG_TITLE_GOT_SIZE == msgTitle) {
				onSizeGot(msg.getData().getInt(MSG_KEY_FILE_SIZE));
			} else if(MSG_TITLE_ERROR == msgTitle) {
				onErrorOccurred(msg.getData().getString(MSG_KEY_ERROR_MSG));
			} else if(MSG_TITLE_DATA_GOT == msgTitle) {
				onDataGot(msg.getData().getInt(MSG_KEY_BYTES_GOT), msg.getData().getLong(MSG_KEY_SPENT_TIME), msg.getData().getInt(MSG_KEY_TOTAL_BYTES_GOT));
			}
		}
	};
	
	private void onSizeGot(int size) {
		BigDecimal fileBytes = new BigDecimal((double)size);
		BigDecimal divisor = new BigDecimal(1024.00);
		fileMB = fileBytes.divide(divisor).divide(divisor).setScale(2, BigDecimal.ROUND_HALF_UP).doubleValue();
		part1Pg.setVisibility(View.VISIBLE);
		part1Pg.setMax(size);
		part1Pg.setProgress(0);
		part1Speed.setVisibility(View.VISIBLE);
		part1Speed.setText("size: " + fileMB + " MB  speed: 0KB/s");
	}
	
	private void onErrorOccurred(String errMsg) {
		Toast.makeText(this, errMsg, Toast.LENGTH_LONG).show();
	}
	
	private void onDataGot(int bytesGot, long spentTime, int totalBytesGot) {
		
		part1Speed.setText("size: " + fileMB + " MB  speed: " + (bytesGot / 1024.0 / spentTime * 1000)  + "KB/s  got: " + (totalBytesGot / 1024.0 / 1024.0) + " MB");
		part1Pg.setProgress(totalBytesGot);
	}
	
	private class DownloadService implements Runnable {
		
		private String textUrl;
		private int id;
		
		private DownloadService(String textUrl, int id) {
			this.textUrl = textUrl;
			this.id = id;
		}
		
		public void run() {
			HttpURLConnection conn = null;
			DataOutputStream dos = null;
			DataInputStream dis = null;
			try {
				URL url = new URL(textUrl);
				conn = (HttpURLConnection)url.openConnection();
				conn.setConnectTimeout(1000);
				conn.connect();
				if(conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
					throw new Exception("Connecting failed, error code is : " + conn.getResponseCode());
				}
				notifySizeGot(conn.getContentLength());
				
				String filePath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/" 
									+ RegisterActivity.fileDir + "/" + System.currentTimeMillis() + ".flv";
				
				dis = new DataInputStream(conn.getInputStream());
				dos = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(filePath)));
				byte[] buffer = new byte[1024];
				int getLength = 0;
				long startTime = System.currentTimeMillis();
				long lastTime = System.currentTimeMillis();
				int lastGotBytes = 0;
				int totalGotBytes = 0;
				while((getLength = dis.read(buffer, 0, buffer.length)) > 0) {
					dos.write(buffer, 0, getLength);
					dos.flush();
					lastGotBytes += getLength;
					totalGotBytes += getLength;
					long spent = System.currentTimeMillis() - lastTime;
					if(spent > 1000) {
						notifyDataGot(lastGotBytes, spent, totalGotBytes);
					    lastTime = System.currentTimeMillis();
					    lastGotBytes = 0;
					}	
				}
				notifyDownloaded(System.currentTimeMillis() - startTime);
			} catch(Exception e) {
				notifyErrorOccurred(e.getMessage());
			} finally {
				try {
					conn.disconnect();
				} catch(Exception e){}
				try {
					dis.close();
				} catch(Exception e){}
				try {
					dos.close();
				} catch(Exception e){}
			}
		}
		
		private void notifyErrorOccurred(String errorMsg) {
			Bundle bundle = new Bundle();
			bundle.putInt(MSG_KEY_TITLE, MSG_TITLE_ERROR);
			bundle.putString(MSG_KEY_ERROR_MSG, errorMsg);
			notifyServicesHandler(bundle);
		}
		
		private void notifySizeGot(int size) {
			Bundle bundle = new Bundle();
			bundle.putInt(MSG_KEY_TITLE, MSG_TITLE_GOT_SIZE);
			bundle.putInt(MSG_KEY_FILE_SIZE, size);
			notifyServicesHandler(bundle);
		}
		
		private void notifyDataGot(int bytesGot, long spentTime, int totalGotBytes) {
			Bundle bundle = new Bundle();
			bundle.putInt(MSG_KEY_TITLE, MSG_TITLE_DATA_GOT);
			bundle.putInt(MSG_KEY_BYTES_GOT, bytesGot);
			bundle.putLong(MSG_KEY_SPENT_TIME, spentTime);
			bundle.putInt(MSG_KEY_TOTAL_BYTES_GOT, totalGotBytes);
			notifyServicesHandler(bundle);
		}
		
		private void notifyDownloaded(long totalSpentTime) {
			Bundle bundle = new Bundle();
			bundle.putInt(MSG_KEY_TITLE, MSG_TITLE_DOWNLOADED);
			bundle.putLong(MSG_KEY_TOTAL_SPENT_TIME, totalSpentTime);
			notifyServicesHandler(bundle);
		}
		
		private void notifyServicesHandler(Bundle bundle) {
			Message handlerMsg = new Message();
			handlerMsg.setData(bundle);
			servicesHandler.sendMessage(handlerMsg);
		} 
    }
	
}
