package com.chteuchteu.munin;

import java.util.ArrayList;
import java.util.List;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;

import com.chteuchteu.munin.obj.MuninPlugin;
import com.chteuchteu.munin.obj.MuninServer;
import com.chteuchteu.munin.obj.MuninPlugin.AlertState;
import com.chteuchteu.munin.ui.Activity_Alerts;

public class Service_Notifications extends Service {
	private MuninFoo muninFoo;
	private WakeLock mWakeLock;

	/**
	 * Simply return null, since our Service will not be communicating with
	 * any other components. It just does its work silently.
	 */
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@SuppressWarnings("deprecation")
	private void handleIntent(Intent intent) {
		// obtain the wake lock
		PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
		mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "com.chteuchteu.munin");
		mWakeLock.acquire();

		// check the global background data setting
		ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
		if (!cm.getBackgroundDataSetting()) {
			stopSelf();
			return;
		}

		NetworkInfo mWifi = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
		boolean wifiOnly = false;
		if (getPref("notifs_wifiOnly").equals("true"))
			wifiOnly = true;

		if (!wifiOnly || (wifiOnly && mWifi.isConnected())) {
			muninFoo = MuninFoo.getInstance();
			new PollTask().execute();
		}
	}

	private class PollTask extends AsyncTask<Void, Void, Void> {
		int nbCriticals;
		int nbWarnings;
		int nbServers;
		String criticalPlugins;
		String warningPlugins;

		@Override
		protected Void doInBackground(Void... params) {
			List<MuninServer> servers = new ArrayList<MuninServer>();
			String serversList = getPref("notifs_serversList");
			String[] serversToWatch = serversList.split(";");

			nbCriticals = 0;
			nbWarnings = 0;
			nbServers = 0;
			criticalPlugins = "";
			warningPlugins = "";

			for (MuninServer s: muninFoo.getOrderedServers()) {
				for (String url : serversToWatch) {
					if (s.equalsApprox(url))
						servers.add(s);
				}
			}

			for (MuninServer s: servers) {
				s.fetchPluginsStates();
			}

			for (MuninServer s: servers) {
				boolean throatingServer = false;
				for (MuninPlugin p: s.getPlugins()) {
					if (p != null) {
						if (p.getState() == AlertState.CRITICAL || p.getState() == AlertState.WARNING)
							throatingServer = true;
						if (p.getState() == AlertState.CRITICAL) {
							criticalPlugins = criticalPlugins + p.getFancyName() + ", ";
							nbCriticals++;
						}
						else if (p.getState() == AlertState.WARNING) {
							warningPlugins = warningPlugins + p.getFancyName() + ", ";
							nbWarnings++;
						}
					}
				}
				if (throatingServer)
					nbServers++;
			}

			return null;
		}

		@SuppressWarnings("deprecation")
		@Override
		protected void onPostExecute(Void result) {
			//<string name="text58"> critical / criticals /&amp;amp; / warning / warnings /on / server/ servers</string>
			String[] strings = getString(R.string.text58).split("/");

			String titreNotif = "";
			if (nbCriticals > 0 && nbWarnings > 0) {
				titreNotif = nbCriticals + "";
				if (nbCriticals == 1)
					titreNotif += strings[0];
				else
					titreNotif += strings[1];
				titreNotif += strings[2];
				titreNotif += nbWarnings;
				if (nbWarnings == 1)
					titreNotif += strings[3];
				else
					titreNotif += strings[4];
				titreNotif += strings[5];
				titreNotif += nbServers;
				if (nbServers == 1)
					titreNotif += strings[6];
				else
					titreNotif += strings[7];
				//String titreNotification = nbCriticals + " criticals & " + nbWarnings + " warnings on " + nbServers + " servers";
			} else if (nbCriticals == 0 && nbWarnings > 0) {
				titreNotif = nbWarnings + "";
				if (nbWarnings == 1)
					titreNotif += strings[3];
				else
					titreNotif += strings[4];
				titreNotif += strings[5];
				titreNotif += nbServers;
				if (nbServers == 1)
					titreNotif += strings[6];
				else
					titreNotif += strings[7];
			} else if (nbCriticals > 0 && nbWarnings == 0) {
				titreNotif = nbCriticals + "";
				if (nbCriticals == 1)
					titreNotif += strings[0];
				else
					titreNotif += strings[1];
				titreNotif += strings[5];
				titreNotif += nbServers;
				if (nbServers == 1)
					titreNotif += strings[6];
				else
					titreNotif += strings[7];
			}

			String texteNotification = "";

			if (criticalPlugins.length() > 2 && criticalPlugins.substring(criticalPlugins.length()-2).equals(", "))
				criticalPlugins = criticalPlugins.substring(0, criticalPlugins.length()-2);
			if (warningPlugins.length() > 2 && warningPlugins.substring(warningPlugins.length()-2).equals(", "))
				warningPlugins = warningPlugins.substring(0, warningPlugins.length()-2);

			if (nbCriticals > 0 && nbWarnings > 0)
				texteNotification = criticalPlugins + ", " + warningPlugins;
			else if (nbCriticals > 0)
				texteNotification = criticalPlugins;
			else
				texteNotification = warningPlugins;

			if (nbCriticals > 0 || nbWarnings > 0) {
				if (!getPref("lastNotificationText").equals(texteNotification)) {
					NotificationManager notificationManager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
					Notification notification = new Notification(R.drawable.launcher_icon_mono, "Munin for Android", System.currentTimeMillis());

					PendingIntent pendingIntent = PendingIntent.getActivity(Service_Notifications.this, 0, new Intent(Service_Notifications.this, Activity_Alerts.class), 0);
					notification.setLatestEventInfo(Service_Notifications.this, titreNotif, texteNotification, pendingIntent);

					setPref("lastNotificationText", texteNotification);

					//Enfin on ajoute notre notification et son ID à notre gestionnaire de notification
					notificationManager.notify(1234, notification);
					stopSelf();
				}
			} else {
				NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
				mNotificationManager.cancel(1234);
			}
		}
	}

	public String getPref(String key) {
		return this.getSharedPreferences("user_pref", Context.MODE_PRIVATE).getString(key, "");
	}

	public void setPref(String key, String value) {
		SharedPreferences prefs = this.getSharedPreferences("user_pref", Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = prefs.edit();
		editor.putString(key, value);
		editor.commit();
	}

	/**
	 * This is deprecated, but you have to implement it if you're planning on
	 * supporting devices with an API level lower than 5 (Android 2.0).
	 */
	@Override
	public void onStart(Intent intent, int startId) {
		handleIntent(intent);
	}

	/**
	 * This is called on 2.0+ (API level 5 or higher). Returning
	 * START_NOT_STICKY tells the system to not restart the service if it is
	 * killed because of poor resource (memory/cpu) conditions.
	 */
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		handleIntent(intent);
		return START_NOT_STICKY;
	}

	/**
	 * In onDestroy() we release our wake lock. This ensures that whenever the
	 * Service stops (killed for resources, stopSelf() called, etc.), the wake
	 * lock will be released.
	 */
	public void onDestroy() {
		super.onDestroy();
		mWakeLock.release();
	}
}