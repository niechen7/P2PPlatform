package com.luanfei.p2pplatform.client.android;

import java.io.BufferedOutputStream;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URL;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class DownloadActivity extends ActionBarActivity {
		
	final static private int MSG_TITLE_GOT_SIZE = 51;
	final static private int MSG_TITLE_ERROR = 50;
	final static private int MSG_TITLE_DATA_GOT = 52;
	final static private int MSG_TITLE_DOWNLOADED = 53;
	final static private int MSG_TITLE_BYTES_COMBINED = 54;
	final static private int MSG_TITLE_COMBINE_DONE = 55;
	
	final static private String MSG_KEY_TITLE = "title";
	final static private String MSG_KEY_FILE_SIZE = "size";
	final static private String MSG_KEY_PART_INDEX = "part_index";
	final static private String MSG_KEY_ERROR_MSG = "err_msg";
	final static private String MSG_KEY_BYTES_GOT = "bytes_got";
	final static private String MSG_KEY_TOTAL_BYTES_GOT = "total_bytes_got";
	final static private String MSG_KEY_SPENT_TIME = "spent_time";
	final static private String MSG_KEY_TOTAL_SPENT_TIME = "total_time";
	final static private String MSG_KEY_COMBINED_BYTES = "combined_bytes";
	
	final static private String fileSavedDir = Environment.getExternalStorageDirectory().getAbsolutePath() 
			                                   + "/" + RegisterActivity.fileDir + "/";
	
	final static private Integer[] threadCountArr = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
	
	private EditText etDownloadUrl;
	private Spinner spDownloadThreadCount;
	
	private LinearLayout downloadInfoLayout;
	private TextView downloadFileInfo;
	private Button downloadBtn;
	
	private LinearLayout combineFileInfo;
	private TextView combineFileTitle;
	private TextView combineFileResult;
	private ProgressBar combineFilePg;
	
	private String downloadFileUrl;
	private String downloadFileName;
	private int downloadFileBytes;
	private double downloadFileMB;
	
	private int threadCount = 5;
	
	private volatile boolean downloadPaused = false;
	
	View[] downloadInfoViews;
	DownloadPartInfo[] downloadPartInfos;
	boolean[] partsFinished;
	
	private ArrayAdapter<Integer> spDTCAdp;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_download);
		ActionBar actionBar = getSupportActionBar();
	    actionBar.setDisplayHomeAsUpEnabled(false);	 
	    
	    downloadInfoLayout = (LinearLayout)this.findViewById(R.id.download_info);
	    downloadInfoLayout.setVisibility(View.GONE);
	    
	    downloadFileInfo = (TextView)this.findViewById(R.id.download_file_info);	    
	    etDownloadUrl = (EditText)this.findViewById(R.id.download_url);
	    downloadBtn = (Button)this.findViewById(R.id.download);
	    
	    combineFileInfo = (LinearLayout)this.findViewById(R.id.combine_file_info);
	    combineFileTitle = (TextView)this.findViewById(R.id.combine_file_title);
	    combineFileResult = (TextView)this.findViewById(R.id.combine_file_result);
	    combineFilePg = (ProgressBar)this.findViewById(R.id.combine_file_pg);
	    combineFileInfo.setVisibility(View.GONE);
	    
	    spDownloadThreadCount = (Spinner)this.findViewById(R.id.download_thread_count);
	    spDTCAdp = new ArrayAdapter<Integer>(this, android.R.layout.simple_spinner_item, DownloadActivity.threadCountArr);
	    spDownloadThreadCount.setAdapter(spDTCAdp);
	    spDownloadThreadCount.setSelection(4);
	}
	
	public void download(View view) {
		downloadBtn.setEnabled(false);
		threadCount = (Integer)this.spDownloadThreadCount.getSelectedItem();
		partsFinished = new boolean[threadCount];
		System.out.println(this.threadCount);
		downloadFileUrl = etDownloadUrl.getText().toString();
		if(downloadFileUrl == null || "".equals(downloadFileUrl.trim())) {	
			downloadFileUrl = etDownloadUrl.getHint().toString();
		} else {
			downloadFileUrl = downloadFileUrl.trim();
		}
		downloadFileName = getFileNameFromUrl(downloadFileUrl);
		System.out.println(downloadFileName);
		new Thread(new DownloadService(downloadFileUrl)).start();
	}
	
	Handler servicesHandler = new Handler() {
		public void handleMessage(Message msg) {
			try {
				int msgTitle = msg.getData().getInt(MSG_KEY_TITLE);
				if(MSG_TITLE_GOT_SIZE == msgTitle) {
					onSizeGot(msg.getData());
				} else if(MSG_TITLE_ERROR == msgTitle) {
					onErrorOccurred(msg.getData());
				} else if(MSG_TITLE_DATA_GOT == msgTitle) {
					onDataGot(msg.getData());
				} else if(MSG_TITLE_DOWNLOADED == msgTitle) {
					onDownloaded(msg.getData());
				} else if(MSG_TITLE_BYTES_COMBINED == msgTitle) {
					onBytesCombined(msg.getData());
				} else if(MSG_TITLE_COMBINE_DONE == msgTitle) {
					onCombineDone(msg.getData());
				}
			} catch(Exception e) {
				System.out.println(e.getMessage());
				e.printStackTrace();
			}
		}
	};
	
	private void onBytesCombined(Bundle bundle) {
		int combinedBytes = bundle.getInt(MSG_KEY_COMBINED_BYTES);
		combineFilePg.setProgress(combinedBytes);
		combineFileTitle.setText("Combining all parts to a file, progress: " + combinedBytes + "/" + downloadFileBytes + " bytes.");
	}
	
	private void onCombineDone(Bundle bundle) {
		combineFilePg.setProgress(combineFilePg.getMax());
		combineFileTitle.setText("Combining all parts to a file finished. " + downloadFileBytes + "/" + downloadFileBytes + " bytes.");
		downloadBtn.setEnabled(true);
		combineFileResult.setClickable(true);
	}
	
	private void onSizeGot(Bundle bundle) {
		int size = bundle.getInt(MSG_KEY_FILE_SIZE);
		if(size != 0) {
			genDownloadInfoView();
			downloadInfoLayout.setVisibility(View.VISIBLE);
			downloadFileBytes = size;
			downloadFileMB = this.roundDouble(size / 1024.0 / 1024.0, 2);
			downloadFileInfo.setText("Downloading file " + downloadFileName + ", size is " + downloadFileMB + " MB (" + size + " KB)");
			initDownloadPartInfos();
			startDownloadThreads();
		} else {
			downloadBtn.setEnabled(true);
		}
	}
	
	private void initDownloadPartInfos() {
		downloadPartInfos = new DownloadPartInfo[threadCount];
		int eachSize = downloadFileBytes / downloadPartInfos.length;
		int remainder = downloadFileBytes % downloadPartInfos.length;
		
		for(int i = 0; i < downloadPartInfos.length; i++) {
			DownloadPartInfo info = new DownloadPartInfo();
			info.fromByte = eachSize * i;
			if(i < downloadPartInfos.length - 1) {
				info.partSize = eachSize;
				info.toByte = eachSize * (i + 1) - 1;
			} else {
				info.partSize = eachSize + remainder;
				info.toByte = downloadFileBytes - 1;
			}
			downloadPartInfos[i] = info;
			((TextView)downloadInfoViews[i].findViewById(R.id.download_part_title)).setText("Part " + (i + 1) + " size: " + info.partSize + " KB");
			ProgressBar partPg = (ProgressBar)downloadInfoViews[i].findViewById(R.id.download_part_pg);
			partPg.setMax(info.partSize);
			partPg.setProgress(0);
			((TextView)downloadInfoViews[i].findViewById(R.id.download_part_speed)).setText("speed: 0 KB/s");
			((TextView)downloadInfoViews[i].findViewById(R.id.download_part_downloaded)).setText("finished: 0/" + info.partSize + " KB");
		}
	}
	
	private void startDownloadThreads() {
		for(int i = 0; i < downloadPartInfos.length; i++) {
			new Thread(new DownloadService(downloadFileUrl, i, downloadPartInfos[i].fromByte, downloadPartInfos[i].toByte, fileSavedDir + downloadFileName)).start();
		}
	}
	
	private void genDownloadInfoView() {
		downloadInfoLayout.removeAllViews();
		downloadInfoViews = new View[threadCount];
		for(int i = 0; i < downloadInfoViews.length; i++) {
			View downloadView = this.getLayoutInflater().inflate(R.layout.downloads_list, null);
			downloadInfoViews[i] = downloadView;
			downloadInfoLayout.addView(downloadView);	
		}
		
	}
	
	private void onErrorOccurred(Bundle bundle) {
		String errMsg = bundle.getString(MSG_KEY_ERROR_MSG);
		Toast.makeText(this, errMsg, Toast.LENGTH_LONG).show();
		this.downloadPaused = true;
		downloadBtn.setEnabled(true);
	}
	
	private void onDataGot(Bundle bundle) {
		int bytesGot =bundle.getInt(MSG_KEY_BYTES_GOT);
		long spentTime = bundle.getLong(MSG_KEY_SPENT_TIME);
		int totalBytesGot = bundle.getInt(MSG_KEY_TOTAL_BYTES_GOT);
		int index = bundle.getInt(MSG_KEY_PART_INDEX);
		double speed = this.roundDouble((bytesGot / (spentTime / 1000.0) / 1000.0), 2);
		((TextView)downloadInfoViews[index].findViewById(R.id.download_part_speed)).setText("speed: " + speed + " KB/s");
		((TextView)downloadInfoViews[index].findViewById(R.id.download_part_downloaded)).setText("finished: " + totalBytesGot + "/" + downloadPartInfos[index].partSize + " KB");
		((ProgressBar)downloadInfoViews[index].findViewById(R.id.download_part_pg)).setProgress(totalBytesGot);
	}
	
	private void onDownloaded(Bundle bundle) {
		long totalSpentTime = bundle.getLong(MSG_KEY_TOTAL_SPENT_TIME);
		int index = bundle.getInt(MSG_KEY_PART_INDEX);
		((TextView)downloadInfoViews[index].findViewById(R.id.download_part_downloaded)).setText("all finished, spent " + (totalSpentTime / 1000) + " s.");
		ProgressBar partPg = (ProgressBar)downloadInfoViews[index].findViewById(R.id.download_part_pg);
		partPg.setProgress(partPg.getMax());
		partsFinished[index] = true;
		if(this.isAllFinished()) {
			combineFile();
		}
	}
	
	private void combineFile() {
		combineFileInfo.setVisibility(View.VISIBLE);
		combineFileTitle.setText("Combining all parts to a file...");
		combineFilePg.setMax(downloadFileBytes);
		combineFilePg.setProgress(0);
		combineFileResult.setText(downloadFileName);
		combineFileResult.setClickable(false);
		new Thread(new CombineFileService()).start();
	}
	
	private class CombineFileService implements Runnable {
		public void run() {
			DataOutputStream dos = null;
			DataInputStream dis = null;
			try {
				dos = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(fileSavedDir + downloadFileName, false)));
				long lastTime = System.currentTimeMillis();
				int combinedBytes = 0;
				for(int i = 0; i < threadCount; i++) {
					dis = new DataInputStream(new BufferedInputStream(new FileInputStream(fileSavedDir + downloadFileName + ".part" + i)));
					int getLength = 0;
					byte[] buffer = new byte[1024 * 1024];
					while((getLength = dis.read(buffer, 0, buffer.length)) > 0) {
						dos.write(buffer, 0, getLength);
						dos.flush();
						combinedBytes += getLength;
						long spent = System.currentTimeMillis() - lastTime;
						if(spent > 1000) {
							notifyCombinedBytes(combinedBytes);
						    lastTime = System.currentTimeMillis();
						}
					}
					dis.close();
					File file = new File(fileSavedDir + downloadFileName + ".part" + i);
					file.delete();
				}
				notifyCombineDone();
			} catch(Exception e) {
				System.out.println(e);
				notifyErrorOccurred(e.getMessage());
			} finally {
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
		
		private void notifyCombinedBytes(int combinedBytes) {
			Bundle bundle = new Bundle();
			bundle.putInt(MSG_KEY_TITLE, MSG_TITLE_BYTES_COMBINED);
			bundle.putInt(MSG_KEY_COMBINED_BYTES, combinedBytes);
			notifyServicesHandler(bundle);
		}
		
		private void notifyCombineDone() {
			Bundle bundle = new Bundle();
			bundle.putInt(MSG_KEY_TITLE, MSG_TITLE_COMBINE_DONE);
			notifyServicesHandler(bundle);
		}
		
		private void notifyServicesHandler(Bundle bundle) {
			Message handlerMsg = new Message();
			handlerMsg.setData(bundle);
			servicesHandler.sendMessage(handlerMsg);
		} 
	}
	
	private class DownloadService implements Runnable {
		
		private String textUrl;
		private int index;
		private int fromByte;
		private int toByte;
		private String filePath;
		boolean initDownload = false;
		
		private DownloadService(String textUrl) {
			this.textUrl = textUrl;
			initDownload = true;
		}
		
		private DownloadService(String textUrl, int index, int fromByte, int toByte, String filePath) {
			this.textUrl = textUrl;
			this.index = index;
			this.fromByte = fromByte;
			this.toByte = toByte;
			this.filePath = filePath;
		}
		
		public void run() {
			HttpURLConnection conn = null;
			DataOutputStream dos = null;
			DataInputStream dis = null;
			
			try {
				URL url = new URL(textUrl);
				conn = (HttpURLConnection)url.openConnection();
				conn.setConnectTimeout(10000);
				conn.setReadTimeout(60 * 1000);
				conn.setRequestProperty("User-Agent","Mozilla/5.0");
				if(!initDownload) {
					conn.setRequestProperty("RANGE", "bytes=" + fromByte + "-" + toByte);
				}
				HttpURLConnection.setFollowRedirects(true);
				conn.connect();
				System.out.println(conn.getHeaderFields());
				if(conn.getResponseCode() != HttpURLConnection.HTTP_OK && conn.getResponseCode() != HttpURLConnection.HTTP_PARTIAL) {
					throw new Exception("Connecting failed, error code is : " + conn.getResponseCode());
				}
				if(initDownload) {
					notifySizeGot(conn.getContentLength());
				} else {
					String fileName = this.filePath + ".part" + index;				
					dis = new DataInputStream(conn.getInputStream());
					dos = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(fileName, false)));
					byte[] buffer = new byte[1024];
					int getLength = 0;
					long startTime = System.currentTimeMillis();
					long lastTime = System.currentTimeMillis();
					int lastGotBytes = 0;
					int totalGotBytes = 0;
					while(!downloadPaused && (getLength = dis.read(buffer, 0, buffer.length)) > 0) {
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
				}
			} catch(Exception e) {
				System.out.println(e);
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
			if(!initDownload) {
				bundle.putInt(MSG_KEY_PART_INDEX, index);
			}
			Message handlerMsg = new Message();
			handlerMsg.setData(bundle);
			servicesHandler.sendMessage(handlerMsg);
		} 
    }
	
	private String getFileNameFromUrl(String textUrl) {
		String result = null;
		if(textUrl != null && textUrl.length() > 0) {
			int end = textUrl.indexOf("?");
			if(end < 1) {
				end = textUrl.length();
			}
			int start = textUrl.lastIndexOf("/");
			if(start < 0) {
				start = -1;
			}
			result = textUrl.substring(start + 1, end);
		}
		return result;
	}
	
	private double roundDouble(double d, int scale) {
		BigDecimal temp = new BigDecimal(d);
		return temp.setScale(scale, BigDecimal.ROUND_HALF_UP).doubleValue();
	}
	
	private class DownloadPartInfo {
		private int partSize;
		private int fromByte;
		private int toByte;
	}
	
	private boolean isAllFinished() {
		boolean result = true;
		for(boolean b : partsFinished) {
			result = result && b;
		}
		return result;
	}
	
	public void openGetFile(View view) {
		Intent it = new Intent(Intent.ACTION_VIEW);
		Uri uri = Uri.parse(fileSavedDir + this.downloadFileName);
		it.setDataAndType(uri, "video/*");
		startActivity(it);
	}
	
}
