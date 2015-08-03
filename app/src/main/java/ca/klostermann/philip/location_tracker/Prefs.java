package ca.klostermann.philip.location_tracker;

import android.content.Context;
import android.content.SharedPreferences;

public final class Prefs {
	private static final String TAG = "Prefs";

	public static String ENDPOINT = "endpoint";
	public static String ENABLED = "enabled";
	public static String UPDATE_FREQ = "update_freq";
	public static String USER_ID = "account_id";
	public static String USER_EMAIL = "account_email";
	public static String USER_PASSWORD = "account_password";

    public static SharedPreferences get(final Context context) {
        return context.getSharedPreferences("ca.klostermann.philip.location_tracker",
			Context.MODE_PRIVATE);
    }

	public static String getPref(final Context context, String pref,
	String def) {
		SharedPreferences prefs = Prefs.get(context);
		String val = prefs.getString(pref, def);

		if (val == null || val.equals("") || val.equals("null"))
			return def;
		else
			return val;
	}

	public static void putPref(final Context context, String pref,
	String val) {
		SharedPreferences prefs = Prefs.get(context);
		SharedPreferences.Editor editor = prefs.edit();

		editor.putString(pref, val);
		editor.commit();
	}

	public static String getEndpoint(final Context context) {
		return Prefs.getPref(context, ENDPOINT, null);
	}

	public static String getUpdateFreq(final Context context) {
		return Prefs.getPref(context, UPDATE_FREQ, "30m");
	}

	public static boolean getEnabled(final Context context) {
		String e = Prefs.getPref(context, ENABLED, "false");
		return e.equals("true");
	}

	public static String getUserId(final Context context) {
		return Prefs.getPref(context, USER_ID, null);
	}

	public static String getUserEmail(final Context context) {
		return Prefs.getPref(context, USER_EMAIL, null);
	}

	public static String getUserPassword(final Context context) {
		return Prefs.getPref(context, USER_PASSWORD, null);
	}


	public static void putUpdateFreq(final Context context, String freq) {
		Prefs.putPref(context, UPDATE_FREQ, freq);
	}

	public static void putEndpoint(final Context context, String endpoint) {
		Prefs.putPref(context, ENDPOINT, endpoint);
	}
	
	public static void putEnabled(final Context context, boolean enabled) {
		Prefs.putPref(context, ENABLED, (enabled ? "true" : "false"));
	}

	public static void putUserId(final Context context, String id) {
		Prefs.putPref(context, USER_ID, id);
	}

	public static void putUserEmail(final Context context, String email) {
		Prefs.putPref(context, USER_EMAIL, email);
	}

	public static void putUserPassword(final Context context, String password) {
		Prefs.putPref(context, USER_PASSWORD, password);
	}
}
