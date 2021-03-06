package org.bodytrack.BodyTrack.Activities;

import java.io.File;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import org.bodytrack.BodyTrack.BTService;
import org.bodytrack.BodyTrack.BTStatisticTracker;
import org.bodytrack.BodyTrack.BodyTrackExceptionHandler;
import org.bodytrack.BodyTrack.DbAdapter;
import org.bodytrack.BodyTrack.IBTSvcRPC;
import org.bodytrack.BodyTrack.PreferencesAdapter;
import org.bodytrack.BodyTrack.R;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.os.RemoteException;
import android.provider.MediaStore;
import android.text.Editable;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CursorAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SimpleCursorAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

/**
 * This class defines a tabbed UI that allows the user to see the app's main
 * features. It is what is shown when the app is launched.
 */
public class HomeTabbed extends Activity /* TabActivity */implements
		OnClickListener, OnItemSelectedListener {
	public DbAdapter dbAdapter;
	public static final String TAG = "HomeTabbed";
	public static final int ACTIVITY_PREFERENCES = 1;
	public static final int ACTIVITY_NEW_PICTURE = 2;

	private final static String TEMP_PHOTO_FILE = Environment.getExternalStorageDirectory() + "/TEMP_PHOTO.JPG"; 

	public static HomeTabbed instance;

	private PreferencesAdapter prefAdapter;
	private AlertDialog prefConfigDialog;

	private BTStatisticTracker btStats;
	private CheckBox toggleAcc;
	private CheckBox toggleGyro;
	private CheckBox toggleWifi;
	private CheckBox toggleLight;
	private CheckBox toggleTemp;
	private CheckBox toggleOrnt;
	private CheckBox toggleGPS;
	private CheckBox togglePress;
	private CheckBox splitAcc;
	private TextView recordInfo;
	private TextView uploadInfo;
	private Button takePic, enableAll, disableAll, logComment;
	private Spinner gpsUpdateRatePicker;
	private Spinner sensorUpdateRatePicker;
	private LinearLayout gpsSettingsPane;
		
	private View accContainer, gyroContainer, orntContainer, lightContainer, tempContainer, pressContainer;
	
	private TextView[] logRates = new TextView[BTService.NUM_LOGGERS];
	private int[] loggerIds = new int[]{R.id.gpsSamples,R.id.accSamples,
			R.id.gyroSamples,R.id.wifiSamples,R.id.tempSamples,R.id.orntSamples, R.id.lightSamples, R.id.pressSamples};

	private IBTSvcRPC btBinder;
	
	private UploadStatusTask uploadStatusTask;
	
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		instance = this;
		Thread.setDefaultUncaughtExceptionHandler(new BodyTrackExceptionHandler());
		
		btStats = BTStatisticTracker.getInstance();
		
	    setContentView(R.layout.sensors);
	    dbAdapter = DbAdapter.getDbAdapter(getApplicationContext());
	    prefAdapter = PreferencesAdapter.getInstance(getApplicationContext());
	    
	    AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(false)
            .setTitle(R.string.pref_config)
            .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
	                dialog.dismiss();
	                Intent intent = new Intent(getApplicationContext(), BTPrefs.class);
	                startActivityForResult(intent, ACTIVITY_PREFERENCES);
                }
            });
        prefConfigDialog = builder.create();
	    
        if (!prefAdapter.prefsAreGood()){
        	prefConfigDialog.setMessage(getString(R.string.pref_need_config));
        	prefConfigDialog.show();
        }
        
        recordInfo = (TextView)findViewById(R.id.recordInfo);
        uploadInfo = (TextView)findViewById(R.id.uploadInfo);
        
        toggleAcc = (CheckBox)findViewById(R.id.toggleAcc);
		toggleGyro = (CheckBox)findViewById(R.id.toggleGyro);
		toggleWifi = (CheckBox)findViewById(R.id.toggleWifi);
		toggleLight = (CheckBox)findViewById(R.id.toggleLight);
		toggleTemp = (CheckBox)findViewById(R.id.toggleTemp);
		toggleOrnt = (CheckBox)findViewById(R.id.toggleOrnt);
		toggleGPS = (CheckBox)findViewById(R.id.toggleGPS);
		togglePress = (CheckBox)findViewById(R.id.togglePress);
		
		splitAcc = (CheckBox)findViewById(R.id.splitAcc);
		
		enableAll = (Button)findViewById(R.id.enableAll);
		disableAll = (Button)findViewById(R.id.disableAll);
		logComment = (Button)findViewById(R.id.logComment);
		
		takePic = (Button)findViewById(R.id.takePic);
		
		accContainer =  findViewById(R.id.accContainer);
		gyroContainer =  findViewById(R.id.gyroContainer);
		orntContainer =  findViewById(R.id.orntContainer);
		lightContainer =  findViewById(R.id.lightContainer);
		tempContainer =   findViewById(R.id.tempContainer);
		pressContainer = findViewById(R.id.pressContainer);
		
		accContainer.setVisibility(View.GONE);
		gyroContainer.setVisibility(View.GONE);
		lightContainer.setVisibility(View.GONE);
		tempContainer.setVisibility(View.GONE);
		orntContainer.setVisibility(View.GONE);
		splitAcc.setVisibility(View.GONE);
		pressContainer.setVisibility(View.GONE);
		
		toggleWifi.setEnabled(false);
		toggleGPS.setEnabled(false);
		
		toggleAcc.setOnClickListener(this);
		toggleGyro.setOnClickListener(this);
		toggleWifi.setOnClickListener(this);
		toggleLight.setOnClickListener(this);
		toggleTemp.setOnClickListener(this);
		toggleOrnt.setOnClickListener(this);
		toggleGPS.setOnClickListener(this);
		takePic.setOnClickListener(this);
		enableAll.setOnClickListener(this);
		disableAll.setOnClickListener(this);
		logComment.setOnClickListener(this);
		splitAcc.setOnClickListener(this);
		togglePress.setOnClickListener(this);
		
		for (int i = 0; i < logRates.length; i++){
			logRates[i] = (TextView) findViewById(loggerIds[i]);
		}
		
		gpsUpdateRatePicker = (Spinner)findViewById(R.id.GPSRatePicker);
		
		ArrayAdapter<String> adapter = new ArrayAdapter<String>(getApplicationContext(), android.R.layout.simple_spinner_item, BTService.getAllGpsDelayNames());
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		gpsUpdateRatePicker.setAdapter(adapter);
		
		gpsUpdateRatePicker.setOnItemSelectedListener(this);
		
		sensorUpdateRatePicker = (Spinner)findViewById(R.id.sensorDelaySpinner);
		
		adapter = new ArrayAdapter<String>(getApplicationContext(), android.R.layout.simple_spinner_item, BTService.getAllSensorDelayNames());
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		sensorUpdateRatePicker.setAdapter(adapter);
		
		sensorUpdateRatePicker.setOnItemSelectedListener(this);
		
		gpsSettingsPane = (LinearLayout)findViewById(R.id.gpsSettingsPane);
		gpsSettingsPane.setVisibility(View.GONE);
		
		Context ctx = getApplicationContext();
    	Intent intent = new Intent(ctx, BTService.class);
    	ctx.startService(intent);
    	ctx.bindService(intent, sc, 0);
    	
    	//SensorManager senseMan = (SensorManager)getSystemService(SENSOR_SERVICE);
    	//Toast.makeText(this, "Linear Acceleration: " + (senseMan.getDefaultSensor(10) != null ? "supported" : "unsupported"), Toast.LENGTH_LONG).show();
    	
    	//Debug.startMethodTracing("profile");
	}
	
	public void onPause() {
		super.onPause();
		if (uploadStatusTask != null) {
			uploadStatusTask.cancel(true);
		}
	}

	public void onResume(){
		super.onResume();
		updateButtons();
		btStats.out.println("Verifying db storage location");
		dbAdapter.verifyDBLocation(this);
		uploadStatusTask = new UploadStatusTask();
		uploadStatusTask.execute();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.prefs: {
			Intent intent = new Intent(getApplicationContext(), BTPrefs.class);
			startActivityForResult(intent, ACTIVITY_PREFERENCES);
			return true;
		}
		case R.id.stats: {
			Intent intent = new Intent(getApplicationContext(), Stats.class);
			startActivity(intent);
			return true;
		}
		case R.id.website: {
			Intent intent = new Intent(Intent.ACTION_VIEW,Uri.parse("http://" + prefAdapter.getHost()));
			startActivity(intent);
			return true;
		}
		}
		return false;
	}

	protected void onActivityResult(int requestCode, int resultCode,
			Intent intent) {
		super.onActivityResult(requestCode, resultCode, intent);

		switch (requestCode) {
		case ACTIVITY_PREFERENCES:
			prefAdapter.obtainUserID(this);
			break;
		case ACTIVITY_NEW_PICTURE: {
			btStats.out.println("Camera Activity finished with result: "
					+ resultCode);
			if (resultCode == RESULT_OK) {
				try {
					final File tempFile = new File(TEMP_PHOTO_FILE);
					if (!prefAdapter.noCommentsOnPhotos()){
						AlertDialog.Builder alert = new AlertDialog.Builder(this);

						alert.setTitle("Comment Your Photo");

						// Set an EditText view to get user input 
						final EditText input = new EditText(this);
						alert.setView(input);

						alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int whichButton) {
							dbAdapter.writePicture(tempFile,input.getText().toString());
						  }
						});

						alert.show();
					}
					else{
						dbAdapter.writePicture(tempFile,"");
					}
				} catch (Exception e) {
					btStats.out.println("Exception occured trying to obtain photo: " + e);
					e.printStackTrace(btStats.out);
					Toast.makeText(this, "An error occured trying to obtain picture. The picture will not be uploaded!", Toast.LENGTH_LONG).show();
				}

			}
			break;
		}
		}

	}

	public void onUserIDUpdated(boolean networkForceDisabled) {
		try {
			if (networkForceDisabled) {
				AlertDialog d = new AlertDialog.Builder(this)
						.setCancelable(false)
						.setTitle(R.string.pref_config)
						.setMessage(R.string.network_force_disabled)
						.setPositiveButton("Ok",
								new DialogInterface.OnClickListener() {
									public void onClick(DialogInterface dialog,
											int id) {
										dialog.dismiss();
									}
								}).create();
				d.show();
			} else if (!prefAdapter.prefsAreGood()) {
				prefConfigDialog.setMessage(getString(R.string.pref_invalid));
				prefConfigDialog.show();
			}
		} catch (Exception e) {
			Toast.makeText(this,
					"Exception occured in onUserIDUpdated. exception logged.",
					Toast.LENGTH_SHORT).show();
			btStats.out.println("Exception in onUserIDUpdated:");
			e.printStackTrace(btStats.out);
		}
	}

	@Override
	public void onClick(View v) {
		if (v == toggleAcc) {
			try {
				if (btBinder.isLogging(BTService.ACC_LOGGING)) {
					btBinder.stopLogging(BTService.ACC_LOGGING);
					splitAcc.setVisibility(View.GONE);
				} else {
					btBinder.startLogging(BTService.ACC_LOGGING);
					if (btBinder.canSplitAcc())
						splitAcc.setVisibility(View.VISIBLE);
				}
			} catch (RemoteException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} else if (v == splitAcc){
			try {
				btBinder.setAccSplitting(!btBinder.isSplittingAcc());
			} catch (RemoteException e) {
			}
		} else if (v == toggleGyro) {
			try {
				if (btBinder.isLogging(BTService.GYRO_LOGGING)) {
					btBinder.stopLogging(BTService.GYRO_LOGGING);
				} else {
					btBinder.startLogging(BTService.GYRO_LOGGING);
				}
			} catch (RemoteException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} else if (v == toggleWifi) {
			try {
				if (btBinder.isLogging(BTService.WIFI_LOGGING)) {
					btBinder.stopLogging(BTService.WIFI_LOGGING);
				} else {
					btBinder.startLogging(BTService.WIFI_LOGGING);
				}
			} catch (RemoteException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} else if (v == toggleLight) {
			try {
				if (btBinder.isLogging(BTService.LIGHT_LOGGING)) {
					btBinder.stopLogging(BTService.LIGHT_LOGGING);
				} else {
					btBinder.startLogging(BTService.LIGHT_LOGGING);
				}
			} catch (RemoteException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} else if (v == toggleTemp) {
			try {
				if (btBinder.isLogging(BTService.TEMP_LOGGING)) {
					btBinder.stopLogging(BTService.TEMP_LOGGING);
				} else {
					btBinder.startLogging(BTService.TEMP_LOGGING);
				}
			} catch (RemoteException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} else if (v == toggleOrnt) {
			try {
				if (btBinder.isLogging(BTService.ORNT_LOGGING)) {
					btBinder.stopLogging(BTService.ORNT_LOGGING);
				} else {
					btBinder.startLogging(BTService.ORNT_LOGGING);
				}
			} catch (RemoteException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} else if (v == toggleGPS) {
			try {
				if (btBinder.isLogging(BTService.GPS_LOGGING)) {
					btBinder.stopLogging(BTService.GPS_LOGGING);
					gpsSettingsPane.setVisibility(View.GONE);
				} else {
					btBinder.startLogging(BTService.GPS_LOGGING);
					gpsSettingsPane.setVisibility(View.VISIBLE);
				}
			} catch (RemoteException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} else if (v == togglePress){
			try {
				if (btBinder.isLogging(BTService.PRESS_LOGGING)) {
					btBinder.stopLogging(BTService.PRESS_LOGGING);
				} else {
					btBinder.startLogging(BTService.PRESS_LOGGING);
				}
			} catch (RemoteException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} else if (v == takePic) {
			try {
				Intent cameraIntent = new Intent(
						MediaStore.ACTION_IMAGE_CAPTURE);
				ContentValues values = new ContentValues();
				new File(TEMP_PHOTO_FILE).createNewFile();
				values.put(MediaStore.Images.Media.TITLE, TEMP_PHOTO_FILE);
				values.put(MediaStore.Images.Media.DESCRIPTION,
						"Image taken by BodyTrack");
				cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(new File(TEMP_PHOTO_FILE)));
				startActivityForResult(cameraIntent, ACTIVITY_NEW_PICTURE);
			} catch (Exception e) {
				Toast.makeText(this,
						"Please insert an SD card before using the camera.",
						Toast.LENGTH_SHORT).show();
			}
		} else if (v == enableAll) {
			try {
				for (int i = 0; i < BTService.NUM_LOGGERS; i++) {
					if (!btBinder.isLogging(i) && btBinder.canLog(i)) {
						btBinder.startLogging(i);
					}
				}
				updateButtons();
			} catch (Exception e) {
			}
		} else if (v == disableAll) {
			try {
				for (int i = 0; i < BTService.NUM_LOGGERS; i++) {
					if (btBinder.isLogging(i)) {
						btBinder.stopLogging(i);
					}
				}
				updateButtons();
			} catch (Exception e) {
			}
		} else if (v == logComment){
			final AlertDialog.Builder alert = new AlertDialog.Builder(this);

			alert.setTitle("Log a Comment");

			// Set an EditText view to get user input 
			final EditText input = new EditText(this);
			alert.setView(input);

			alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
			  final String comment = input.getText().toString();
			  final EditText newInput = new EditText(HomeTabbed.this);
			  dbAdapter.writeComment(comment);

			  // Diable quick comments for now
//			  new AlertDialog.Builder(HomeTabbed.this)
//			  			.setTitle("Save as quick comment?")
//			  			.setView(newInput)
//			  			.setMessage("Quick comment name:")
//			  			.setPositiveButton("Save", new DialogInterface.OnClickListener() {
//							
//							@Override
//							public void onClick(DialogInterface dialog, int which) {
//								String name = newInput.getText().toString();
//								if (!name.equals(""))
//									dbAdapter.writeQuickComment(name, comment);
//							}
//						})
//						.setNegativeButton("No", new DialogInterface.OnClickListener() {
//							
//							@Override
//							public void onClick(DialogInterface dialog, int which) {
//								btStats.out.println("Quick Comment not saved!");
//							}
//						}).show();
			  			
			}
			});

			alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
			  public void onClick(DialogInterface dialog, int whichButton) {
			    btStats.out.println("Comment canceled!");
			  }
			});
			
			boolean allowQuickComment = false;
			
			// Diable quick comments for now
//			Cursor c = dbAdapter.fetchQuickComments();
//			if (c != null){
//				allowQuickComment = c.moveToFirst();
//				c.close();
//			}
			
			if (allowQuickComment){			
				new AlertDialog.Builder(this)
					.setTitle("Log a comment")
					.setMessage("Would you like to use a quick comment?")
					.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
						
						@Override
						public void onClick(DialogInterface dialog, int which) {
							Cursor c = dbAdapter.fetchQuickComments();
							startManagingCursor(c);
							String[] from = new String[]{DbAdapter.QCOMMENT_KEY_NAME};
							int[] to = new int[]{R.id.quickCommentText};
							 final SimpleCursorAdapter quickCommentsAdapter = 
					        	    new SimpleCursorAdapter(HomeTabbed.this, R.layout.quick_comment_row, c, from, to);
							new AlertDialog.Builder(HomeTabbed.this)
									.setTitle("Quick Comment")
									.setAdapter(quickCommentsAdapter, new DialogInterface.OnClickListener() {
										@Override
										public void onClick(DialogInterface dialog, int which) {
											final long id = quickCommentsAdapter.getItemId(which);
											Button upload = new Button(HomeTabbed.this);
											upload.setText("Log");
											
											final AlertDialog dlog = new AlertDialog.Builder(HomeTabbed.this)
											.setTitle("What would you like to do?")
											.setView(upload)
											.setPositiveButton("Delete", new DialogInterface.OnClickListener() {
												
												@Override
												public void onClick(DialogInterface dialog, int which) {
													dbAdapter.deleteQuickComment(id);
												}
											})
											.setNegativeButton("Cancel", new DialogInterface.OnClickListener(){

												@Override
												public void onClick(DialogInterface dialog,int which) {
												}
												
											}).show();
											
											upload.setOnClickListener(new Button.OnClickListener(){

												@Override
												public void onClick(View v) {
													dlog.dismiss();
													boolean success = dbAdapter.logQuickComment(id);
													if (success)
														btStats.out.println("Successfully wrote quick comment " + id);
													else
														btStats.out.println("Failed to write quick comment " + id);
												}
												
											});
										}
									})
									.show();
						}
					})
					.setNegativeButton("No", new DialogInterface.OnClickListener() {
						
						@Override
						public void onClick(DialogInterface dialog, int which) {
							alert.show();
						}
					})
					.show();
			}
			else{
				alert.show();
			}
		}
	}

	@Override
	public void onItemSelected(AdapterView<?> parent, View view, int pos,
			long id) {
		if (parent == gpsUpdateRatePicker){
			try {
				btBinder.setGPSDelay(pos);
			} catch (Exception e) {
			}
		}
		else if (parent == sensorUpdateRatePicker){
			try {
				btBinder.setSensorDelay(pos);
			} catch (Exception e) {
			}
		}
	}

	@Override
	public void onNothingSelected(AdapterView<?> arg0) {
		// TODO Auto-generated method stub
	}
	
	public void onSampleRateChanged(final int loggerId, final double rate){
		runOnUiThread(new Runnable() {
			public void run(){
				double formattedRate = rate;
				String unit = "samples/s";
				if (formattedRate != 0){
					if (formattedRate < 1){
						formattedRate *= 60;
						unit = "samples/min";
						if (formattedRate < 1){
							formattedRate *= 60;
							unit = "samples/hr";
							if (formattedRate < 1){
								formattedRate *= 24;
								unit = "samples/day";
							}
						}
					}
				}
				logRates[loggerId].setText(new DecimalFormat("0.00").format(formattedRate) + " " + unit);
			}
		});
	}

	protected void updateButtons() {
		try {
			toggleAcc.setChecked(btBinder.isLogging(BTService.ACC_LOGGING));
			toggleGyro.setChecked(btBinder.isLogging(BTService.GYRO_LOGGING));
			toggleWifi.setChecked(btBinder.isLogging(BTService.WIFI_LOGGING));
			toggleLight.setChecked(btBinder.isLogging(BTService.LIGHT_LOGGING));
			toggleTemp.setChecked(btBinder.isLogging(BTService.TEMP_LOGGING));
			toggleOrnt.setChecked(btBinder.isLogging(BTService.ORNT_LOGGING));
			toggleGPS.setChecked(btBinder.isLogging(BTService.GPS_LOGGING));
			togglePress.setChecked(btBinder.isLogging(BTService.PRESS_LOGGING));
			splitAcc.setChecked(btBinder.isSplittingAcc());

			gpsSettingsPane.setVisibility(btBinder
					.isLogging(BTService.GPS_LOGGING) ? View.VISIBLE
					: View.GONE);

			accContainer
					.setVisibility(btBinder.canLog(BTService.ACC_LOGGING) ? View.VISIBLE
							: View.GONE);
			gyroContainer
					.setVisibility(btBinder.canLog(BTService.GYRO_LOGGING) ? View.VISIBLE
							: View.GONE);
			lightContainer
					.setVisibility(btBinder.canLog(BTService.LIGHT_LOGGING) ? View.VISIBLE
							: View.GONE);
			tempContainer
					.setVisibility(btBinder.canLog(BTService.TEMP_LOGGING) ? View.VISIBLE
							: View.GONE);
			orntContainer
					.setVisibility(btBinder.canLog(BTService.ORNT_LOGGING) ? View.VISIBLE
							: View.GONE);
			
			pressContainer.setVisibility(btBinder.canLog(BTService.PRESS_LOGGING) ? View.VISIBLE : View.GONE);
			
			splitAcc.setVisibility((btBinder.isLogging(BTService.ACC_LOGGING) && btBinder.canSplitAcc()) ? View.VISIBLE : View.GONE);

			toggleGPS.setEnabled(btBinder.canLog(BTService.GPS_LOGGING));
			toggleWifi.setEnabled(btBinder.canLog(BTService.WIFI_LOGGING));

			gpsUpdateRatePicker.setSelection(btBinder.getGPSDelayIndex());
			sensorUpdateRatePicker.setSelection(btBinder.getSensorDelayIndex());
		} catch (Exception e) {
		}
	}

	protected void serviceBound(IBTSvcRPC binder) {
		btBinder = binder;
		updateButtons();
	}

	private ServiceConnection sc = new ServiceConnection() {

		@Override
		public void onServiceConnected(ComponentName svc, IBinder binder) {
			btBinder = IBTSvcRPC.Stub.asInterface(binder);
			serviceBound(btBinder);
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
		}
	};

	private class UploadStatusTask extends AsyncTask<Void, String, Void> {

		@Override
		protected Void doInBackground(Void... arg) {
			while (!this.isCancelled()) {
				String record;
				String upload;
				
				// bytes/ms = kb/sec
				DecimalFormat df = new DecimalFormat("#.##");
				
				// Calculate upload kb remaining
				// NOTE: We fudge by snapping negative values to 0 due to imprecise accounting of storage vs. upload
				long kbDelta = (btStats.getTotalDataStorageBytes() - btStats.getTotalDataUploadBytes())/1024;
				if (kbDelta < 0) kbDelta = 0;
				
				if (!prefAdapter.isNetworkEnabled()) {
					record = "Recording: " + df.format(btStats.getStoreRate()) + " KB/s [" + Long.toString(kbDelta) + " KB recorded]";
					upload = "Upload paused until network is enabled";
				}
				else {
					record = "Recording: " + df.format(btStats.getStoreRate()) + " KB/s";
					upload = "Uploading: " + df.format(btStats.getUploadRate()) + " KB/s [" + Long.toString(kbDelta) + " KB remaining]";
				}
				publishProgress(record, upload);
				try {
					Thread.sleep(3000);
				} 
				catch (InterruptedException e) {
				}
			}
			return null;
		}		
		
		protected void onProgressUpdate(String... progress) {
			recordInfo.setText(progress[0]);
			uploadInfo.setText(progress[1]);
		}
	}
}
