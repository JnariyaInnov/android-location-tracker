package ca.klostermann.philip.location_tracker;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.location.Location;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.PowerManager;
import android.os.RemoteException;
import android.provider.Settings;
import android.util.Log;

import com.firebase.client.AuthData;
import com.firebase.client.Firebase;
import com.firebase.client.FirebaseError;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;

import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import org.json.JSONException;
import org.json.JSONObject;


public class TrackerService extends Service {
	private static final String TAG = "LocationTracker/Service";

	public static TrackerService service;

	private NotificationManager nm;
	private Notification notification;
	private static boolean isRunning = false;

	private String freqString;
	private int freqSeconds;
	private String endpoint;

	private static volatile PowerManager.WakeLock wakeLock;
	private PendingIntent mLocationIntent;

	private GoogleApiClient mGoogleApiClient;
	private LocationListener mLocationListener;
	private Firebase mFirebaseRef;
	private String mUserId;
	private Location mLastReportedLocation;

	ArrayList<LogMessage> mLogRing = new ArrayList<>();
	ArrayList<Messenger> mClients = new ArrayList<>();
	final Messenger mMessenger = new Messenger(new IncomingHandler());

	static final int MSG_REGISTER_CLIENT = 1;
	static final int MSG_UNREGISTER_CLIENT = 2;
	static final int MSG_LOG = 3;
	static final int MSG_LOG_RING = 4;

	static final String LOGFILE_NAME = "TrackerService.log";
	static final int MAX_RING_SIZE = 250;

	@Override
	public IBinder onBind(Intent intent) {
		return mMessenger.getBinder();
	}

	@Override
	public void onCreate() {
		super.onCreate();

		// Check whether Google Play Services is installed
		int resp = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
		if(resp != ConnectionResult.SUCCESS){
			logText("Google Play Services not found. Please install to use this app.");
			stopSelf();
			return;
		}

		TrackerService.service = this;

		endpoint = Prefs.getEndpoint(this);
		if (endpoint == null || endpoint.equals("")) {
			logText("Invalid endpoint, stopping service");
			stopSelf();
			return;
		}

		freqSeconds = 0;
		freqString = Prefs.getUpdateFreq(this);
		if (freqString != null && !freqString.equals("")) {
			try {
				Pattern p = Pattern.compile("(\\d+)(m|h|s)");
				Matcher m = p.matcher(freqString);
				m.find();
				freqSeconds = Integer.parseInt(m.group(1));
				if (m.group(2).equals("h")) {
					freqSeconds *= (60 * 60);
				} else if (m.group(2).equals("m")) {
					freqSeconds *= 60;
				}
			}
			catch (Exception e) {
				Log.d(TAG, e.toString());
			}
		}

		if (freqSeconds < 1) {
			logText("Invalid frequency (" + freqSeconds + "), stopping " +
					"service");
			stopSelf();
			return;
		}

		// load saved log messages from disk
		ArrayList<LogMessage> logs = loadLogsFromDisk();
		if(logs != null) {
			Log.d(TAG, "Loaded " + logs.size() + " logs from disk");

			if(logs.size() > MAX_RING_SIZE) {
				mLogRing.addAll(logs.subList(logs.size() - MAX_RING_SIZE, logs.size() - 1));
			} else {
				mLogRing.addAll(logs);
			}

			if(logs.size() > (2 * MAX_RING_SIZE)) {
				cleanLogFile(MAX_RING_SIZE);
			}
		}

		Firebase.setAndroidContext(this);

		// Authenticate user
		String email = Prefs.getUserEmail(this);
		String password = Prefs.getUserPassword(this);
		if(email == null || email.equals("")
				|| password == null || password.equals("")) {
			logText("No email/password found, stopping service");
			stopSelf();
			return;
		}

		showNotification();
		isRunning = true;

		logText("Service authenticating...");
		mFirebaseRef = new Firebase(Prefs.getEndpoint(this));
		mFirebaseRef.authWithPassword(email, password, new Firebase.AuthResultHandler() {
			@Override
			public void onAuthenticated(AuthData authData) {
				logText("Successfully authenticated");
				mUserId = authData.getUid();

				// set this device's info in Firebase
				mFirebaseRef.child("devices/" + mUserId + "/" + getDeviceId()).setValue(getDeviceInfo());

				// mGoogleApiClient.connect() will callback to this
				mLocationListener = new LocationListener();
				mGoogleApiClient = buildGoogleApiClient();
				mGoogleApiClient.connect();

				/* we're not registered yet, so this will just log to our ring buffer,
	 			* but as soon as the client connects we send the log buffer anyway */
				logText("Service started, update frequency " + freqString);
			}

			@Override
			public void onAuthenticationError(FirebaseError firebaseError) {
				logText("Authentication failed, please check email/password, stopping service");
				stopSelf();
			}
		});
	}

	@Override
	public void onDestroy() {
		super.onDestroy();

		/* kill persistent notification */
		if(nm != null) {
			nm.cancelAll();
		}

		if(mGoogleApiClient != null && mLocationIntent != null) {
			LocationServices.FusedLocationApi.removeLocationUpdates(
					mGoogleApiClient, mLocationIntent);
		}
		isRunning = false;
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		return START_STICKY;
	}

	public static boolean isRunning() {
		return isRunning;
	}

	private synchronized GoogleApiClient buildGoogleApiClient() {
		return new GoogleApiClient.Builder(this)
				.addConnectionCallbacks(mLocationListener)
				.addOnConnectionFailedListener(mLocationListener)
				.addApi(LocationServices.API)
				.build();
	}

	private LocationRequest createLocationRequest() {
		return new LocationRequest()
				.setInterval(freqSeconds * 1000)
				.setFastestInterval(5000)
				.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
	}

	private void showNotification() {
		nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
		notification = new Notification(R.mipmap.service_icon,
				"Location Tracker Started", System.currentTimeMillis());
		PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
				new Intent(this, MainActivity.class), 0);
		notification.setLatestEventInfo(this, "Location Tracker",
				"Service started", contentIntent);
		notification.flags = Notification.FLAG_ONGOING_EVENT;
		nm.notify(1, notification);
	}

	private void updateNotification(String text) {
		if (nm != null) {
			PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
					new Intent(this, MainActivity.class), 0);
			notification.setLatestEventInfo(this, "Location Tracker", text,
					contentIntent);
			notification.when = System.currentTimeMillis();
			nm.notify(1, notification);
		}
	}

	private String getDeviceId() {
		return  Settings.Secure.getString(this.getContentResolver(), Settings.Secure.ANDROID_ID);
	}

	private Map<String, String> getDeviceInfo() {
		Map<String, String> info = new HashMap<>();
		info.put("deviceId", getDeviceId());
		info.put("brand", Build.BRAND);
		info.put("device", Build.DEVICE);
		info.put("hardware", Build.HARDWARE);
		info.put("id", Build.ID);
		info.put("manufacturer", Build.MANUFACTURER);
		info.put("model", Build.MODEL);
		info.put("product", Build.PRODUCT);

		return info;
	}

	private boolean saveLogToDisk(LogMessage logMessage) {
		JSONObject jsonObj = new JSONObject();
		try {
			jsonObj.put("date", String.valueOf(logMessage.date.getTime()));
			jsonObj.put("message", logMessage.message);
		} catch (JSONException e) {
			Log.e(TAG, "Saving Log to disk failed, cannot create JSON object: " + e);
			return false;
		}

		OutputStreamWriter osw;
		try {
			osw = new OutputStreamWriter(openFileOutput(LOGFILE_NAME, Context.MODE_APPEND));
			osw.write(jsonObj.toString() + "\n");
			osw.close();
		} catch (FileNotFoundException e) {
			Log.e(TAG, "Saving Log to disk failed, file not found: " + e);
			return false;
		} catch (IOException e) {
			Log.e(TAG, "Saving Log to disk failed, IO Exception: " + e);
			return false;
		}

		return true;
	}

	private ArrayList<LogMessage> loadLogsFromDisk() {
		ArrayList<LogMessage> logs = new ArrayList<>();

		ArrayList<String> jsonLogMessages = new ArrayList<>();
		InputStream inputStream;
		try {
			inputStream = openFileInput(LOGFILE_NAME);

			if ( inputStream != null ) {
				InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
				BufferedReader bufferedReader = new BufferedReader(inputStreamReader);

				String line;
				while ((line = bufferedReader.readLine()) != null ) {
					jsonLogMessages.add(line);
				}
				inputStream.close();
			}
		} catch (FileNotFoundException e) {
			Log.e(TAG, "Reading Logs from disk failed, file not found: " + e);
			return null;
		} catch (IOException e) {
			Log.e(TAG, "Reading Logs from disk failed, IO Exception: " + e);
			return null;
		}

		try {
			for(int i = jsonLogMessages.size() - 1; i >= 0; i--) {
				JSONObject jsonObject = new JSONObject(jsonLogMessages.get(i));
				Date d = new Date();
				d.setTime(jsonObject.getLong("date"));
				LogMessage lm = new LogMessage(d, jsonObject.getString("message"));
				logs.add(lm);
			}
		} catch (JSONException e) {
			Log.e(TAG, "Reading logs from disk failed, unable to parse JSON object: " + e);
			return null;
		}

		Collections.reverse(logs);
		return logs;
	}

	private boolean cleanLogFile(int numLogsToKeep) {
		ArrayList<LogMessage> logs = loadLogsFromDisk();
		if(logs == null || logs.size() <= 0) {
			Log.e(TAG, "Cleaning log failed, unable to load log file.");
			return false;
		}
		if(!deleteFile(LOGFILE_NAME)) {
			Log.e(TAG, "Cleaning log failed, unable to delete log file.");
			return false;
		}

		List<LogMessage> recentLogs = logs.subList(logs.size() - numLogsToKeep - 1, logs.size() - 1);

		for(int i = 0; i < recentLogs.size(); i++) {
			if(!saveLogToDisk(recentLogs.get(i))) {
				Log.e(TAG, "Cleaning log failed, error trying to write to new log file");
				return false;
			}
		}
		return true;
	}

	public void logText(String log) {
		LogMessage lm = new LogMessage(new Date(), log);
		mLogRing.add(lm);
		saveLogToDisk(lm);
		if (mLogRing.size() > MAX_RING_SIZE)
			mLogRing.remove(0);

		updateNotification(log);

		for (int i = mClients.size() - 1; i >= 0; i--) {
			try {
				Bundle b = new Bundle();
				b.putString("log", log);
				Message msg = Message.obtain(null, MSG_LOG);
				msg.setData(b);
				mClients.get(i).send(msg);
			}
			catch (RemoteException e) {
				/* client is dead, how did this happen */
				mClients.remove(i);
			}
		}
	}

	public void sendLocation(Location location) {
		/* Wake up */
		if (wakeLock == null) {
			PowerManager pm = (PowerManager)this.getSystemService(
					Context.POWER_SERVICE);

			/* we don't need the screen on */
			wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
					"locationtracker");
			wakeLock.setReferenceCounted(true);
		}

		if (!wakeLock.isHeld()) {
			wakeLock.acquire();
		}

		if(location == null) {
			return;
		}

		Log.d(TAG, "Location update received");

		if(mLastReportedLocation != null) {
			float accuracy = Math.max(location.getAccuracy(), mLastReportedLocation.getAccuracy());
			if(mLastReportedLocation.distanceTo(location) < accuracy) {
				Log.d(TAG, "Location has not changed enough. Not sending...");
				return;
			}
		}


		Map<String,String> postMap = new HashMap<>();
		postMap.put("time", String.valueOf(location.getTime()));
		postMap.put("latitude", String.valueOf(location.getLatitude()));
		postMap.put("longitude", String.valueOf(location.getLongitude()));
		postMap.put("speed", String.valueOf(location.getSpeed()));
		postMap.put("altitude", String.valueOf(location.getAltitude()));
		postMap.put("accuracy", String.valueOf(location.getAccuracy()));
		postMap.put("provider", String.valueOf(location.getProvider()));

		//Use timestamp of today's date at midnight as key
		GregorianCalendar d = new GregorianCalendar();
		d.setTimeInMillis(location.getTime());
		d.set(Calendar.HOUR, 0);
		d.set(Calendar.HOUR_OF_DAY, 0);
		d.set(Calendar.MINUTE, 0);
		d.set(Calendar.SECOND, 0);
		d.set(Calendar.MILLISECOND, 0);
		String dateKey = String.valueOf(d.getTimeInMillis());

		logText("Location " +
				(new DecimalFormat("#.######").format(location.getLatitude())) +
				", " +
				(new DecimalFormat("#.######").format(location.getLongitude())));

		try {
			mFirebaseRef.child("locations/" + mUserId + "/" + getDeviceId() + "/" + dateKey)
					.push()
					.setValue(postMap);
			mLastReportedLocation = location;
		} catch(Exception e) {
			Log.e(TAG, "Posting to Firebase failed: " + e.toString());
			logText("Failed to send location data.");
		}
	}

	class LocationListener implements
			ConnectionCallbacks,
			OnConnectionFailedListener {
		@Override
		public void onConnected(Bundle connectionHint) {
			LocationRequest locationRequest = createLocationRequest();
			Intent intent = new Intent(service, LocationReceiver.class);
			mLocationIntent = PendingIntent.getBroadcast(
					getApplicationContext(),
					14872,
					intent,
					PendingIntent.FLAG_CANCEL_CURRENT);

			// Register for automatic location updates
			LocationServices.FusedLocationApi.requestLocationUpdates(
					mGoogleApiClient, locationRequest, mLocationIntent);
		}

		@Override
		public void onConnectionSuspended(int i) {
			Log.w(TAG, "Location connection suspended " + i);
			mGoogleApiClient.connect();
		}

		@Override
		public void onConnectionFailed(ConnectionResult connectionResult) {
			Log.e(TAG, "Location connection failed" + connectionResult);
			logText("No Location found");
		}
	}

	class IncomingHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case MSG_REGISTER_CLIENT:
				mClients.add(msg.replyTo);

				/* respond with our log ring to show what we've been up to */
				try {
					Message replyMsg = Message.obtain(null, MSG_LOG_RING);
					replyMsg.obj = mLogRing;
					msg.replyTo.send(replyMsg);
				}
				catch (RemoteException e) {
					Log.e(TAG, e.getMessage());
				}
				break;
			case MSG_UNREGISTER_CLIENT:
				mClients.remove(msg.replyTo);
				break;

			default:
				super.handleMessage(msg);
			}
		}
	}

}
