package com.dr8.xposedmtc.utils;

import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.text.format.Time;
import android.util.Log;

import com.luckycatlabs.sunrisesunset.SunriseSunsetCalculator;

import java.util.Calendar;
import java.util.Date;

/**
 * Created by dlocal on 1/20/2015.
 */
public class GetLocation {

    private static String TAG = "XMTC-GetLoc";
    private static double longitude;
    private static double latitude;
    private static long newstart;
    private static long newend;
    private static long[] locarray;

    public long[] getLocation(Context ctx) {
        LocationManager mlocManager = (LocationManager)ctx.getSystemService(Context.LOCATION_SERVICE);
        Location gpsloc = mlocManager.getLastKnownLocation(mlocManager.GPS_PROVIDER);
        Location netloc = mlocManager.getLastKnownLocation(mlocManager.NETWORK_PROVIDER);
        long GPSLocationTime = 0;
        long NetLocationTime = 0;

        if (gpsloc != null) { GPSLocationTime = gpsloc.getTime(); }

        if (netloc != null) { NetLocationTime = netloc.getTime(); }

        if ( 0 < GPSLocationTime - NetLocationTime ) {
            Log.d(TAG, "Retrieving LastKnownLocation info from gps");
            locarray = getCurrentLoc(gpsloc);
        } else {
            Log.d(TAG, "Retrieving LastKnownLocation info from network");
            locarray = getCurrentLoc(netloc);
        }
        return locarray;
    }

    private long[] getCurrentLoc(Location loc) {
        if (loc != null) {
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
            return new long[]{sunset.getTimeInMillis(), sunrise.getTimeInMillis()};
        } else {
            Log.d(TAG, "No LastKnownLocation, setting times manually");
            Calendar todayCalendar = Calendar.getInstance();
            todayCalendar.set(Calendar.HOUR_OF_DAY, 18);
            todayCalendar.set(Calendar.MINUTE, 0);
            todayCalendar.set(Calendar.SECOND, 0);
            long manualstart = todayCalendar.getTimeInMillis();
            todayCalendar.roll(Calendar.HOUR_OF_DAY, -12);
            todayCalendar.roll(Calendar.MINUTE, -30);
            long manualend = todayCalendar.getTimeInMillis();
            return new long[]{manualstart, manualend};
        }
    }

}
