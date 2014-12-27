package com.dr8.xposedmtc.services;

import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;

import com.dr8.xposedmtc.activities.PrefsActivity;
import com.dr8.xposedmtc.R;
import com.luckycatlabs.sunrisesunset.SunriseSunsetCalculator;

import android.app.Notification;
import android.app.Notification.Builder;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.text.format.Time;
import android.util.Log;

public class SunriseService extends Service {

	public SharedPreferences prefs;
	public Context ctx;
	private static String TAG = "XMTC-Sunrise";
	private static double longitude;
	private static double latitude;
	private static int notifID = 1;
	private static String lastupdate;
	private static NotificationManager nm;
	private static long currentstart;
	private static long currentend;

	@Override
	public IBinder onBind(Intent arg0) {
		return null;
	}

	public void onDestroy() {
	}

	@Override
	public void onCreate()
	{
		super.onCreate();
		ctx = getApplicationContext();

		nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

		Builder builder = new Notification.Builder(ctx);
		Intent notificationIntent = new Intent(this, PrefsActivity.class);
		PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
		builder.setContentIntent(pendingIntent)
		.setSmallIcon(R.drawable.ic_xposedmtc)
		.setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.ic_xposedmtc))
		.setWhen(System.currentTimeMillis())
		.setAutoCancel(false)
		.setTicker(getText(R.string.servicestart))
		.setContentTitle(getText(R.string.app_name))
		.setContentText(getText(R.string.servicetext));
		Notification n = builder.build();
		n.flags = Notification.FLAG_ONGOING_EVENT;
		startForeground(notifID, n);
	}

	@Override
	public void onStart(Intent intent, int startid)
	{
		prefs = getSharedPreferences("com.dr8.xposedmtc_preferences", MODE_MULTI_PROCESS);

		Log.d(TAG, "onStart");
		LocationManager mlocManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
		LocationListener mlocListener = new MyLocationListener();
		if (prefs.getBoolean("debug", false)) {
			mlocManager.requestLocationUpdates( LocationManager.GPS_PROVIDER, 0, 0, mlocListener);
		} else {
			mlocManager.requestLocationUpdates( LocationManager.GPS_PROVIDER, 18000000, 80467, mlocListener);	
		}

	}

	public void updateNotif(String update) {
		Builder builder = new Notification.Builder(ctx);
		Intent notificationIntent = new Intent(this, PrefsActivity.class);
		PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
		builder.setContentIntent(pendingIntent)
		.setSmallIcon(R.drawable.ic_xposedmtc)
		.setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.ic_xposedmtc))
		.setWhen(System.currentTimeMillis())
		.setAutoCancel(false)
		.setContentTitle(getText(R.string.app_name))
		.setContentText(update);
		Notification newn = builder.build();
		newn.flags = Notification.FLAG_ONGOING_EVENT;
		nm.notify(notifID, newn);
	}

	public boolean checkTime(){
		currentstart = prefs.getLong("dimmerstart", 0);
		currentend = prefs.getLong("dimmerend", 0);

		// build now time, midnight, and 11:59p
		Calendar todayCalendar = Calendar.getInstance();

		Calendar midnightCalendar = Calendar.getInstance();
		midnightCalendar.set(Calendar.HOUR_OF_DAY, 0);
		midnightCalendar.set(Calendar.MINUTE, 0);
		midnightCalendar.set(Calendar.SECOND, 0);

		Calendar endofnightCalendar = Calendar.getInstance();
		endofnightCalendar.set(Calendar.HOUR_OF_DAY, 23);
		endofnightCalendar.set(Calendar.MINUTE, 59);
		endofnightCalendar.set(Calendar.SECOND, 59);

		// build starttime from current millis, then change to today date with pref hour/min
		Calendar startCalendarTime = Calendar.getInstance();
		startCalendarTime.setTimeInMillis(currentstart);
		startCalendarTime.setLenient(true);
		int starthour = startCalendarTime.get(Calendar.HOUR_OF_DAY);
		startCalendarTime.roll(Calendar.MINUTE, -1); // subtract a minute, due to intent frequency
		int startmin = startCalendarTime.get(Calendar.MINUTE);
		startCalendarTime.set(todayCalendar.get(Calendar.YEAR), todayCalendar.get(Calendar.MONTH), todayCalendar.get(Calendar.DAY_OF_MONTH), starthour, startmin);

		// build endtime from current millis, then change to today date with pref hour/min
		Calendar endCalendarTime = Calendar.getInstance();
		endCalendarTime.setTimeInMillis(currentend);
		endCalendarTime.setLenient(true);
		int endhour = endCalendarTime.get(Calendar.HOUR_OF_DAY);
		endCalendarTime.roll(Calendar.MINUTE, -1); // subtract a minute, due to intent frequency
		int endmin = endCalendarTime.get(Calendar.MINUTE);
		endCalendarTime.set(todayCalendar.get(Calendar.YEAR), todayCalendar.get(Calendar.MONTH), todayCalendar.get(Calendar.DAY_OF_MONTH), endhour, endmin);


		if ((todayCalendar.after(midnightCalendar) && todayCalendar.before(endCalendarTime)) || (todayCalendar.after(startCalendarTime) && todayCalendar.before(endofnightCalendar)) || todayCalendar.equals(midnightCalendar)) {
			Log.d(TAG, "currently dimmed, hold off on updating");
			return false;
		} else {
			Log.d(TAG, "currently not dimmed, we can update");
			return true;
		}
	}

	public class MyLocationListener implements LocationListener
	{
		@Override
		public void onLocationChanged(Location loc)	{		
			if (checkTime()) {
				latitude = loc.getLatitude();
				Log.d(TAG, "latitude changed to " + latitude);
				longitude = loc.getLongitude();
				Log.d(TAG, "longitude changed to " + longitude);
				Log.d(TAG, "current TZ is " + Time.getCurrentTimezone());
				com.luckycatlabs.sunrisesunset.dto.Location currentloc = new com.luckycatlabs.sunrisesunset.dto.Location(latitude, longitude);
				SunriseSunsetCalculator sscalc = new SunriseSunsetCalculator(currentloc, Time.getCurrentTimezone());
				Calendar sunrise = sscalc.getOfficialSunriseCalendarForDate(Calendar.getInstance());
				Calendar sunset = sscalc.getOfficialSunsetCalendarForDate(Calendar.getInstance());
				Date sunsetdate = sunset.getTime();
				Date sunrisedate = sunrise.getTime();
				Log.d(TAG, "setting dimmerstart to " + sunsetdate.toString() + " and dimmerend to " + sunrisedate.toString());
				prefs.edit().putLong("dimmerstart", sunset.getTimeInMillis()).commit();
				prefs.edit().putLong("dimmerend", sunrise.getTimeInMillis()).commit();

				DateFormat sunrisedf = android.text.format.DateFormat.getTimeFormat(ctx);
				DateFormat sunsetdf = android.text.format.DateFormat.getTimeFormat(ctx);
				lastupdate = "Sunset: " + sunsetdf.format(sunsetdate) + "\t\tSunrise: " + sunrisedf.format(sunrisedate);
				updateNotif(lastupdate);
			} else {
				return;
			}
		}

		@Override
		public void onProviderDisabled(String provider)	{
		}

		@Override
		public void onProviderEnabled(String provider)	{
		}

		@Override
		public void onStatusChanged(String provider, int status, Bundle extras)	{
		}
	}
}