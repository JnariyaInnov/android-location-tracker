package ca.klostermann.philip.location_tracker;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
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
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.PowerManager;
import android.os.RemoteException;
import android.provider.Settings;
import android.util.Log;

import com.firebase.client.Firebase;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;

import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;


public class TrackerService extends Service {
	private static final String TAG = "LocationTracker/Service";

	public static TrackerService service;

	private NotificationManager nm;
	private Notification notification;
	private static boolean isRunning = false;

	private int freqSeconds;
	private String endpoint;

	private static volatile PowerManager.WakeLock wakeLock;
	private PendingIntent mLocationIntent;

	private GoogleApiClient mGoogleApiClient;
	private LocationListener mLocationListener;
	private LocationRequest mLocationRequest;
	private Firebase mFirebaseRef;

	ArrayList<LogMessage> mLogRing = new ArrayList<>();
	ArrayList<Messenger> mClients = new ArrayList<>();
	final Messenger mMessenger = new Messenger(new IncomingHandler());

	static final int MSG_REGISTER_CLIENT = 1;
	static final int MSG_UNREGISTER_CLIENT = 2;
	static final int MSG_LOG = 3;
	static final int MSG_LOG_RING = 4;

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
		}

		TrackerService.service = this;

		endpoint = Prefs.getEndpoint(this);
		if (endpoint == null || endpoint.equals("")) {
			logText("invalid endpoint, stopping service");
			stopSelf();
		}

		freqSeconds = 0;
		String freqString = null;
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
			logText("invalid frequency (" + freqSeconds + "), stopping " +
				"service");
			stopSelf();
		}

		Firebase.setAndroidContext(this);
		if(!Firebase.getDefaultConfig().isPersistenceEnabled()) {
			Firebase.getDefaultConfig().setPersistenceEnabled(true);
		}
		mFirebaseRef = new Firebase(createFirebaseAddress());

		// mGoogleApiClient.connect() will callback to this
		mLocationListener = new LocationListener();
		buildGoogleApiClient();
		mGoogleApiClient.connect();

		showNotification(freqString);

		isRunning = true;

		/* we're not registered yet, so this will just log to our ring buffer,
		 * but as soon as the client connects we send the log buffer anyway */
		logText("service started, requesting location update every " +
			freqString);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();

		/* kill persistent notification */
		nm.cancelAll();

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

	private synchronized void buildGoogleApiClient() {
		mGoogleApiClient = new GoogleApiClient.Builder(this)
				.addConnectionCallbacks(mLocationListener)
				.addOnConnectionFailedListener(mLocationListener)
				.addApi(LocationServices.API)
				.build();
	}

	private void createLocationRequest() {
		mLocationRequest = new LocationRequest();
		mLocationRequest.setInterval(freqSeconds * 1000);
		mLocationRequest.setFastestInterval(5000);
		mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
	}

	private String createFirebaseAddress() {
		String deviceId = Settings.Secure.getString(this.getContentResolver(), Settings.Secure.ANDROID_ID);
		return endpoint.replaceAll("/$", "") + '/' + deviceId;
	}

	private void showNotification(String freqString) {
		nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
		notification = new Notification(R.mipmap.service_icon,
			"Location Tracker Started", System.currentTimeMillis());
		PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
			new Intent(this, MainActivity.class), 0);
		notification.setLatestEventInfo(this, "Location Tracker",
			"Sending location every " + freqString, contentIntent);
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

	public void logText(String log) {
		LogMessage lm = new LogMessage(new Date(), log);
		mLogRing.add(lm);
		int MAX_RING_SIZE = 15;
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

		if (!wakeLock.isHeld())
			wakeLock.acquire();

		Log.d(TAG, "Location update received");
		if(location == null) {
			Log.d(TAG, "Location has not changed");
			logText("Location has not changed");
			return;
		}

		Map<String,String> postMap = new HashMap<>();
		postMap.put("time", String.valueOf(location.getTime()));
		postMap.put("latitude", String.valueOf(location.getLatitude()));
		postMap.put("longitude", String.valueOf(location.getLongitude()));
		postMap.put("speed", String.valueOf(location.getSpeed()));
		postMap.put("altitude", String.valueOf(location.getAltitude()));
		postMap.put("accuracy", String.valueOf(location.getAccuracy()));
		postMap.put("provider", String.valueOf(location.getProvider()));

		logText("location " +
				(new DecimalFormat("#.######").format(location.getLatitude())) +
				", " +
				(new DecimalFormat("#.######").format(location.getLongitude())));

		try {
			mFirebaseRef.push().setValue(postMap);
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
			Location location = LocationServices.FusedLocationApi.getLastLocation(
					mGoogleApiClient);
			if (location != null) {
				sendLocation(location);
			} else {
				Log.e(TAG, "Location is null");
				logText("No location found");
			}

			createLocationRequest();
			Intent intent = new Intent(service, LocationReceiver.class);
			mLocationIntent = PendingIntent.getBroadcast(
					getApplicationContext(),
					14872,
					intent,
					PendingIntent.FLAG_CANCEL_CURRENT);

			LocationServices.FusedLocationApi.requestLocationUpdates(
					mGoogleApiClient, mLocationRequest, mLocationIntent);
		}

		@Override
		public void onConnectionSuspended(int i) {
			Log.w(TAG, "Location connection suspended " + i);
			logText("No Location found");
		}

		@Override
		public void onConnectionFailed(ConnectionResult connectionResult) {
			Log.e(TAG, "Location connection failed" + connectionResult);
			logText("No Location found");
		}
	};

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
