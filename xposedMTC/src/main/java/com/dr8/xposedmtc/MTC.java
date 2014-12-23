package com.dr8.xposedmtc;

import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.getBooleanField;
import static de.robv.android.xposed.XposedHelpers.getIntField;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static de.robv.android.xposed.XposedHelpers.getStaticObjectField;
import static de.robv.android.xposed.XposedHelpers.setIntField;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Set;

import com.maxmpz.poweramp.player.PowerampAPI;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningTaskInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Handler;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;

import android.content.pm.PackageManager;

import android.media.AudioManager;
import android.os.Build;

import android.os.IBinder;
import android.os.UserHandle;
import android.util.Log;
import android.widget.Toast;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class MTC implements IXposedHookLoadPackage, IXposedHookZygoteInit {

    private static XSharedPreferences prefs;
    private static String targetmtc = "android.microntek.service";
    private static String TARGET_CLASS = "android.microntek.service.MicrontekServer";
    private static String radiopkg = "com.microntek.radio";
    private static String RADIO_CLASS = "com.microntek.radio.RadioService";
    private static String btpkg = "com.microntek.bluetooth";
    private static String BT_CLASS = "com.microntek.bluetooth.BTDevice";
    private static String btuipkg = "com.microntek.bluetooth.ui";
    private static String BTUI_CLASS = "com.microntek.bluetooth.ui.SearchFragment";
    private static String settingspkg = "com.android.settings";
    private static String SETTINGS_CLASS = "com.android.settings.MtcBluetoothSettings";

    private static String TAG = "XMTC";
    public static Context mCtx;
    private static Context radioCtx;
    private BroadcastReceiver mtckeyproc;
    private BroadcastReceiver muteReceiver;
    private BroadcastReceiver statusReceiver;
    private BroadcastReceiver learnReceiver;

    private static BroadcastReceiver tickReceiver;
    private static BroadcastReceiver keyReceiver;
    private static BroadcastReceiver backviewReceiver;

    private boolean runnableWaiting = false;
    private boolean isPaused;
    public static boolean DEBUG = false;
    private static AudioManager am;
    private static PackageManager pmi;
    private static String MUTE_INTENT = "com.dr8.xposedmtc.MuteRadio";
    private static String STATUS_INTENT = "com.dr8.xposedmtc.SEND_STATUS";
    private static String LEARN_INTENT = "com.dr8.xposedmtc.LEARN_PRESETS";
    private static String SAVE_PRESET = "com.dr8.xposedmtc.SAVE_PRESETS";
    public static String foregroundTaskAppName = null;

    private Runnable myRunnable;
    private Handler myHandler;

    private SharedPreferences radio_prefs;
    private android.content.SharedPreferences.Editor editor;

    private Object mIPowerManager;
    private Method mSetBacklightBrightness;

    private UserHandle mCurrentUserHandle = (UserHandle) getStaticObjectField(UserHandle.class, "CURRENT");

    public static void log(String tag, String msg) {
        Calendar c = Calendar.getInstance();
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String formattedDate = df.format(c.getTime());
        XposedBridge.log("[" + formattedDate + "] " + tag + ": " + msg);
    }

    public String getAndroidRelease() {
        return Build.VERSION.RELEASE;
    }

    public static void cmdPlayer(Context ctx, String cmd) {
        if (prefs.getString("apps_key", "com.microntek.music").equals("com.maxmpz.audioplayer")) {
            Intent intent = new Intent(PowerampAPI.ACTION_API_COMMAND);
            if (cmd.equals("play") || cmd.equals("pause")) {
                intent.putExtra(PowerampAPI.COMMAND, PowerampAPI.Commands.TOGGLE_PLAY_PAUSE);
            } else if (cmd.equals("next")) {
                intent.putExtra(PowerampAPI.COMMAND, PowerampAPI.Commands.NEXT);
            } else if (cmd.equals("prev")) {
                intent.putExtra(PowerampAPI.COMMAND, PowerampAPI.Commands.PREVIOUS);
            } else if (cmd.equals("stop")) {
                intent.putExtra(PowerampAPI.COMMAND, PowerampAPI.Commands.STOP);
            }
            if (DEBUG) log(TAG, "sending poweramp " + cmd + " intent " + intent);
            ctx.startService(intent);
        } else if (prefs.getString("apps_key", "com.microntek.music").equals("com.tbig.playerpro")) {
            Intent intent = new Intent("com.tbig.playerpro.musicservicecommand");
            if (cmd.equals("play")) {
                intent.putExtra("command", "togglepause");
            } else if (cmd.equals("next")) {
                intent.putExtra("command", "next");
            } else if (cmd.equals("prev")) {
                intent.putExtra("command", "previous");
            } else if (cmd.equals("stop")) {
                intent.putExtra("command", "stop");
            }
            if (DEBUG) log(TAG, "sending playerpro " + cmd + " intent " + intent);
            ctx.sendBroadcast(intent);
        }
    }

    public static void doTimeCompare(Object obj) {
        prefs.reload();
        long start = prefs.getLong("dimmerstart", 0);
        long end = prefs.getLong("dimmerend", 0);

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
        startCalendarTime.setTimeInMillis(start);
        startCalendarTime.setLenient(true);
        int starthour = startCalendarTime.get(Calendar.HOUR_OF_DAY);
        startCalendarTime.roll(Calendar.MINUTE, -1); // subtract a minute, due to intent frequency
        int startmin = startCalendarTime.get(Calendar.MINUTE);
        startCalendarTime.set(todayCalendar.get(Calendar.YEAR), todayCalendar.get(Calendar.MONTH), todayCalendar.get(Calendar.DAY_OF_MONTH), starthour, startmin);

        // build endtime from current millis, then change to today date with pref hour/min
        Calendar endCalendarTime = Calendar.getInstance();
        endCalendarTime.setTimeInMillis(end);
        endCalendarTime.setLenient(true);
        int endhour = endCalendarTime.get(Calendar.HOUR_OF_DAY);
        endCalendarTime.roll(Calendar.MINUTE, -1); // subtract a minute, due to intent frequency
        int endmin = endCalendarTime.get(Calendar.MINUTE);
        endCalendarTime.set(todayCalendar.get(Calendar.YEAR), todayCalendar.get(Calendar.MONTH), todayCalendar.get(Calendar.DAY_OF_MONTH), endhour, endmin);


        if ((todayCalendar.after(midnightCalendar) && todayCalendar.before(endCalendarTime)) || (todayCalendar.after(startCalendarTime) && todayCalendar.before(endofnightCalendar)) || todayCalendar.equals(midnightCalendar)) {
            if (DEBUG) log(TAG, "current time is between our start/end times");
            int value = prefs.getInt("dimmervalue", 10);
            callMethod(obj, "setBrightness", value);
            am.setParameters("cfg_backlight=" + value);
            android.provider.Settings.System.putInt(mCtx.getContentResolver(), "screen_brightness", value);
            if (prefs.getBoolean("screenfilter", false)) {
                Intent sf = new Intent("com.tonymanou.screenfilter.action.ENABLE");
                mCtx.sendBroadcast(sf);
            }
        } else {
            if (DEBUG) log(TAG, "current time is outside our start/end times");
            int nondim = prefs.getInt("nondimmedvalue", 255);
            callMethod(obj, "setBrightness", nondim);
            am.setParameters("cfg_backlight=" + nondim);
            android.provider.Settings.System.putInt(mCtx.getContentResolver(), "screen_brightness", nondim);
            if (prefs.getBoolean("screenfilter", false)) {
                Intent sf = new Intent("com.tonymanou.screenfilter.action.DISABLE");
                mCtx.sendBroadcast(sf);
            }
        }
    }

    // borrowed from ru426 regxm xposed module https://github.com/ru426/com.ru426.android.xposed.regxm
    private Object makeIPowerManager(){
        try {
            Class<?> mServiceManagerClass = Class.forName("android.os.ServiceManager");
            Class<?> mIPowerManagerClass = Class.forName("android.os.IPowerManager");
            Class<?> mIPowerManagerStubClass = Class.forName("android.os.IPowerManager$Stub");

            Method mGetService = mServiceManagerClass.getMethod("getService", new Class[]{String.class});
            Method mAsInterface = mIPowerManagerStubClass.getMethod("asInterface", new Class[]{IBinder.class});
            try{
                mSetBacklightBrightness = mIPowerManagerClass.getMethod("setBacklightBrightness", new Class[]{int.class});
            }catch(NoSuchMethodException e){
                mSetBacklightBrightness = mIPowerManagerClass.getMethod("setTemporaryScreenBrightnessSettingOverride", new Class[]{int.class});
            }

            IBinder power = (IBinder) mGetService.invoke(null, "power");
            return mAsInterface.invoke(null, power);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
        return null;
    }

    // borrowed from ru426 regxm xposed module https://github.com/ru426/com.ru426.android.xposed.regxm
    private void setBright(int brightness){
        try {
            if(mIPowerManager != null && mSetBacklightBrightness != null){
                mSetBacklightBrightness.invoke(mIPowerManager, brightness);
            }
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    private void initHandlers(final Context ctx, final String app) {
        if (!app.equals("Launcher")) {
            myRunnable = new Runnable() {
                @Override
                public void run() {
                    Intent intent1 = new Intent("com.microntek.STATUS_BAR_CHANGED");
                    intent1.putExtra("pkname", "blah");
                    intent1.putExtra("title", app);
                    ctx.sendBroadcastAsUser(intent1, mCurrentUserHandle);
                    runnableWaiting = false;
                }
            };
        } else {
            myRunnable = new Runnable() {
                @Override
                public void run() {
                    Intent intent1 = new Intent("com.microntek.STATUS_BAR_CHANGED");
                    intent1.putExtra("pkname", "blah");
                    intent1.putExtra("title", "Home");
                    ctx.sendBroadcastAsUser(intent1, mCurrentUserHandle);
                    runnableWaiting = false;
                }
            };
        }
        runnableWaiting = true;
        myHandler.postDelayed(myRunnable, 5000);
    }

    private void SendStatusBarVol(final Context ctx, String vol) {
        //get foreground app
        ActivityManager actmgr = (ActivityManager) mCtx.getSystemService(Context.ACTIVITY_SERVICE);
        RunningTaskInfo foregroundTaskInfo = actmgr.getRunningTasks(1).get(0);
        String foregroundTaskPackageName = foregroundTaskInfo.topActivity.getPackageName();
        pmi = mCtx.getPackageManager();
        PackageInfo foregroundAppPackageInfo;
        try {
            foregroundAppPackageInfo = pmi.getPackageInfo(foregroundTaskPackageName, 0);
            foregroundTaskAppName = foregroundAppPackageInfo.applicationInfo.loadLabel(pmi).toString();
        } catch (NameNotFoundException e) {
            e.printStackTrace();
        }

        Intent intent = new Intent("com.microntek.STATUS_BAR_CHANGED");
        intent.putExtra("pkname", "blah");
        intent.putExtra("title", vol);
        ctx.sendBroadcastAsUser(intent, mCurrentUserHandle);
        if (DEBUG) {
            log(TAG, "sending vol intent with app named " + foregroundTaskAppName);
            log(TAG, "runnableWaiting is " + runnableWaiting);
        }
        if (!runnableWaiting) {
            initHandlers(ctx, foregroundTaskAppName);
        } else {
            myHandler.removeCallbacks(myRunnable);
            initHandlers(ctx, foregroundTaskAppName);
        }
    }

    private int mtcGetRealVolume(int paramInt)
    {
        int i = paramInt * 100 / 30;
        if (i < 20) {
            return i + i / 2;
        }
        if (i < 50) {
            return i + 10;
        }
        return i + (100 - i) / 5;
    }

    private void changeVolume(int i, boolean bool) {

        String s;
        String prefix;
        if (bool) {
            s = "av_phone_volume=";
            prefix = "BT Vol ";
        } else {
            s = "av_volume=";
            prefix = "Vol ";
        }

        int avvol = android.provider.Settings.System.getInt(mCtx.getContentResolver(), s, 15);
        switch (i) {
            case 0: // vol down
                if (avvol > 0)
                {
                    avvol--;
                    android.provider.Settings.System.putInt(mCtx.getContentResolver(), s, avvol);
                }
                break;
            case 1: // vol up
                if (avvol < 30)
                {
                    avvol++;
                    android.provider.Settings.System.putInt(mCtx.getContentResolver(), s, avvol);
                }
                break;
        }
        am.setParameters(s + mtcGetRealVolume(avvol));
        SendStatusBarVol(mCtx, prefix + avvol);

    }

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
        if (mCurrentUserHandle == null)
            mCurrentUserHandle = (UserHandle) getStaticObjectField(UserHandle.class, "CURRENT");

        if (lpparam.packageName.equals(targetmtc)) {

            findAndHookMethod(TARGET_CLASS, lpparam.classLoader, "onCreate", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(final MethodHookParam mparam) throws Throwable {
                    prefs.reload();
                    mCtx = (Context) getObjectField(mparam.thisObject, "mContext");
                    am = (AudioManager) getObjectField(mparam.thisObject, "am");

                    if (prefs.getBoolean("loud", false)) {
                        callMethod(mparam.thisObject, "LoudSwitch");
                    }

                    if (prefs.getBoolean("dimmerswitch", false)) {
                        doTimeCompare(mparam.thisObject);

                        tickReceiver = new BroadcastReceiver(){
                            @Override
                            public void onReceive(Context context, Intent intent) {
                                if(intent.getAction().compareTo(Intent.ACTION_TIME_TICK) == 0)
                                {
                                    doTimeCompare(mparam.thisObject);
                                }
                            }
                        };
                        mCtx.registerReceiver(tickReceiver, new IntentFilter(Intent.ACTION_TIME_TICK));
                    }

                    if (prefs.getBoolean("volswitch", false)) {
                        myHandler = new Handler();
                        // set initial volume, change app label to Volume
                        String s;
                        s = "av_volume=";
                        int avvol = android.provider.Settings.System.getInt(mCtx.getContentResolver(), s, 15);
                        am.setParameters(s + mtcGetRealVolume(avvol));
                        SendStatusBarVol(mCtx, "Vol " + avvol);

                        // set initial bt volume
                        String t;
                        t = "av_phone_volume=";
                        int btvol = android.provider.Settings.System.getInt(mCtx.getContentResolver(), t, 15);
                        am.setParameters(t + mtcGetRealVolume(btvol));

                        IntentFilter intentfilter = new IntentFilter();
                        intentfilter.addAction("com.microntek.irkeyDown");
                        keyReceiver = new BroadcastReceiver() {
                            public void onReceive(Context context, Intent intent)
                            {
                                String s = intent.getAction();
                                int i = intent.getIntExtra("keyCode", -1);
                                if (s.equals("com.microntek.irkeyDown")) {
                                    boolean btLock = getBooleanField(mparam.thisObject, "btLock");
                                    switch (i) {
                                        default:
                                            return;
                                        case 27:
                                            changeVolume(0, btLock);
                                            return;
                                        case 19:
                                            changeVolume(1, btLock);
                                            return;
                                    }
                                }
                            }
                        };
                        mCtx.registerReceiver(keyReceiver, intentfilter);
                    }

                    IntentFilter bvfilter = new IntentFilter();
                    bvfilter.addAction("com.dr8.xposedmtc.BACKVIEW");
                    backviewReceiver = new BroadcastReceiver() {
                        @Override
                        public void onReceive(Context context, Intent intent) {
                            String s = intent.getAction();
                            if (s.equals("com.dr8.xposedmtc.BACKVIEW")) {
                                if (DEBUG) log(TAG, "starting backview");
                                callMethod(mparam.thisObject, "startBackView");
                            }
                        }
                    };
                    mCtx.registerReceiver(backviewReceiver, bvfilter);

                    IntentFilter mrfilter = new IntentFilter();
                    mrfilter.addAction(MUTE_INTENT);

                    muteReceiver = new BroadcastReceiver() {
                        @Override
                        public void onReceive(Context context, Intent intent) {
                            Intent killradio = new Intent("com.microntek.bootcheck");
                            String mode = intent.getStringExtra("mode");
                            String srcpkg = intent.getStringExtra("pkg");
                            killradio.putExtra("class", srcpkg);

                            if (mode.equals("music")) {
                                if (DEBUG) log(TAG, "switching to mymusic from radio");
                                mCtx.sendBroadcast(killradio);
                                am.setParameters("ctl_radio_mute=true");
                                am.setParameters("av_channel_exit=fm");
                                am.setParameters("av_channel_enter=sys");
                                callMethod(mparam.thisObject, "startMusic", "mswitch", 1);
                            } else if (mode.equals("video")) {
                                mCtx.sendBroadcast(killradio);
                                am.setParameters("ctl_radio_mute=true");
                                am.setParameters("av_channel_exit=fm");
                                am.setParameters("av_channel_enter=sys");
                                if (DEBUG) log(TAG, "switching to myvideo from radio");
                                callMethod(mparam.thisObject, "startMovie", 1);
                            } else {
                                am.setParameters("av_channel_exit=sys");
                                am.setParameters("av_channel_enter=fm");
                                am.setParameters("ctl_radio_mute=false");
                                if (!isPaused) {
                                    cmdPlayer(mCtx, "stop");
                                }
                                if (DEBUG) log(TAG, "switching to radio from mymusic/myvideo");
                                callMethod(mparam.thisObject, "startRadio", 1);
                            }
                        }
                    };
                    mCtx.registerReceiver(muteReceiver, mrfilter);

                    IntentFilter srfilter = new IntentFilter();
                    srfilter.addAction(STATUS_INTENT);

                    statusReceiver = new BroadcastReceiver() {
                        @Override
                        public void onReceive(Context context, Intent intent) {
                            isPaused = intent.getBooleanExtra("paused", false);
                        }
                    };

                    mCtx.registerReceiver(statusReceiver, srfilter);
                }
            });

            findAndHookMethod(TARGET_CLASS, lpparam.classLoader, "setBrightness", int.class, new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam mparam) throws Throwable {
                    if (mIPowerManager == null) {
                        makeIPowerManager();
                    }
                    int i = (Integer) mparam.args[0];
                    if (i < 1)
                        i = 1;
                    setBright(i);
                    return true;
                }
            });

            findAndHookMethod(TARGET_CLASS, lpparam.classLoader, "startMovie", int.class, new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam mparam) throws Throwable {
                    prefs.reload();

                    int i = (Integer) mparam.args[0];
                    pmi = mCtx.getPackageManager();

                    Intent intent = new Intent();
                    intent = pmi.getLaunchIntentForPackage(prefs.getString("video_key", "com.microntek.media"));
                    int j = 0;
                    if (i == 1)
                    {
                        boolean flag = getBooleanField(mparam.thisObject, "gps_isfront");

                        j = 0;
                        if (flag)
                        {
                            intent.putExtra("start", 1);
                            j = 1;
                        }
                    }
                    intent.addFlags(0x30230000);
                    try
                    {
                        mCtx.startActivity(intent);
                    }
                    catch (Exception exception) { }
                    setIntField(mparam.thisObject, "mtcappmode", 4);
                    return j;
                }
            });

            findAndHookMethod(TARGET_CLASS, lpparam.classLoader, "startMusic", String.class, int.class, new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam mparam) throws Throwable {
                    prefs.reload();

                    mtckeyproc = new BroadcastReceiver() {
                        public void onReceive(Context context, Intent intent)
                        {
                            String s = intent.getAction();
                            int i = intent.getIntExtra("keyCode", 0);
                            if (s.equals("com.microntek.irkeyDown"))
                                switch (i) {
                                    case 13:
                                        cmdPlayer(mCtx, "stop");
                                        return;
                                    case 14:
                                    case 24:
                                    case 46:
                                    case 62:
                                        cmdPlayer(mCtx, "next");
                                        return;
                                    case 6:
                                    case 22:
                                    case 45:
                                    case 61:
                                        cmdPlayer(mCtx, "prev");
                                        return;
                                    case 3:
                                        cmdPlayer(mCtx, "play");
                                        return;
                                }
                        }
                    };

                    setIntField(mparam.thisObject, "mtcappmode", 3);
                    pmi = mCtx.getPackageManager();
                    boolean gps_isfront = getBooleanField(mparam.thisObject, "gps_isfront");

                    Intent intent = new Intent();
                    String s = (String) mparam.args[0];
                    int i = (Integer) mparam.args[1];
                    intent = pmi.getLaunchIntentForPackage(prefs.getString("apps_key", "com.microntek.music"));
                    if (DEBUG) log(TAG, "args caught, string is " + s + " int is " + i);
                    int j;
                    if (s != null)
                        intent.putExtra("dev", s);

                    if (i != 1 || !gps_isfront) {
                        j = 0;
                    } else {
                        intent.putExtra("start", 1);
                        j = 1;
                    }
                    try
                    {
                        intent.addFlags(0x30230000);
                        if (intent != null) {
                            if (DEBUG) log(TAG, "intent is " + intent);
                            mCtx.startActivity(intent);

                            IntentFilter intentfilter = new IntentFilter();
                            intentfilter.addAction("com.microntek.irkeyDown");
                            intentfilter.addAction("com.microntek.irkeyUp");
                            mCtx.registerReceiver(mtckeyproc, intentfilter);
                            if (isPaused) {
                                cmdPlayer(mCtx, "play");
                            }
                        }
                    }
                    catch (Exception exception) { }
                    return j;
                }
            });

            if (prefs.getBoolean("volswitch", false)) {
                Object[] volobj = new Object[2];
                volobj[0] = Integer.TYPE;
                volobj[1] = XC_MethodReplacement.DO_NOTHING;
                findAndHookMethod(TARGET_CLASS, lpparam.classLoader, "MTCAdjVolume", volobj);
                findAndHookMethod(TARGET_CLASS, lpparam.classLoader, "SendStatusBarTitle", String.class, new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(MethodHookParam mparam) throws Throwable {
                        String s;
                        s = "av_volume=";
                        int avvol = android.provider.Settings.System.getInt(mCtx.getContentResolver(), s, 15);
                        SendStatusBarVol(mCtx, "Vol " + avvol);
                        return null;
                    }
                });
            }

            if (prefs.getBoolean("modeswitch", false)) {
                findAndHookMethod(TARGET_CLASS, lpparam.classLoader, "ModeSwitch", new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(MethodHookParam mparam) throws Throwable {
                        prefs.reload();
                        String s = am.getParameters("av_channel=");
                        String s1 = am.getParameters("cfg_dvd=");
                        Toast mToast = (Toast) getObjectField(mparam.thisObject, "mToast");
                        String ipod = am.getParameters("sta_ipod=");
                        Set<String> modearray = prefs.getStringSet("mode_key", null);
                        //						modearray.add("radio");
                        modearray.add("music");
                        int mtcappmode = getIntField(mparam.thisObject, "mtcappmode");
                        if (DEBUG) log(TAG, "mtcappmode is " + mtcappmode + ", current av_channel is " + s);

                        // handle startup condition of mtcappmode = 0
                        if (s.equals("fm") && mtcappmode == 0) {
                            setIntField(mparam.thisObject, "mtcappmode", 1);
                            callMethod(mparam.thisObject, "ModeSwitch");
                        } else if (s.equals("sys") && mtcappmode == 0) {
                            setIntField(mparam.thisObject, "mtcappmode", 3);
                            callMethod(mparam.thisObject, "ModeSwitch");
                        } else if (s.equals("dvd") && mtcappmode == 0) {
                            setIntField(mparam.thisObject, "mtcappmode", 2);
                            callMethod(mparam.thisObject, "ModeSwitch");
                        }

                        // radio output
                        if (s.equals("fm")) {
                            if (!s1.equals("0"))
                            {
                                if (modearray.contains("dvd")) {
                                    callMethod(mparam.thisObject, "startDVD", 1);
                                    mToast.setText("DVD");
                                    mToast.setGravity(17, 0, -100);
                                    mToast.show();
                                    if (DEBUG) log(TAG, "switch to dvd from fm");
                                    setIntField(mparam.thisObject, "mtcappmode", 2);
                                    return null;
                                } else if (modearray.contains("music")) {
                                    am.setParameters("ctl_radio_mute=true");
                                    am.setParameters("av_channel_exit=fm");
                                    am.setParameters("av_channel_enter=sys");
                                    callMethod(mparam.thisObject, "startMusic", "mswitch", 1);
                                    mToast.setText("Music");
                                    mToast.setGravity(17, 0, -100);
                                    mToast.show();
                                    if (DEBUG) log(TAG, "switch to music from fm");
                                    setIntField(mparam.thisObject, "mtcappmode", 3);
                                    return null;
                                }
                            }
                            setIntField(mparam.thisObject, "mtcappmode", 1);
                            return null;
                        }

                        // dvd output
                        if (s.equals("dvd")) {
                            if (modearray.contains("music")) {
                                am.setParameters("av_channel_exit=dvd");
                                am.setParameters("av_channel_enter=sys");
                                callMethod(mparam.thisObject, "startMusic", "mswitch", 1);
                                mToast.setText("Music");
                                mToast.setGravity(17, 0, -100);
                                mToast.show();
                                if (DEBUG) log(TAG, "switch to music from dvd");
                                setIntField(mparam.thisObject, "mtcappmode", 3);
                                return null;
                            }
                        }

                        // android output
                        if (s.equals("sys")) {
                            // 1 - radio w/ sys output -- we should never hit here
                            if (mtcappmode == 1)
                            {
                                if (!s1.equals("0")) {
                                    if (modearray.contains("dvd")) {
                                        am.setParameters("av_channel_exit=sys");
                                        callMethod(mparam.thisObject, "startDVD", 1);
                                        mToast.setText("DVD");
                                        mToast.setGravity(17, 0, -100);
                                        mToast.show();
                                        if (DEBUG) log(TAG, "switch to dvd from sys, mtc1");
                                        setIntField(mparam.thisObject, "mtcappmode", 2);
                                        return null;
                                    } else if (modearray.contains("music")) {
                                        callMethod(mparam.thisObject, "startMusic", "mswitch", 1);
                                        mToast.setText("Music");
                                        mToast.setGravity(17, 0, -100);
                                        mToast.show();
                                        if (DEBUG) log(TAG, "switch to music from sys, mtc1");
                                        setIntField(mparam.thisObject, "mtcappmode", 3);
                                        return null;
                                    }
                                }
                                setIntField(mparam.thisObject, "mtcappmode", 2);
                                return null;
                                // 2 - dvd	-- shouldn't hit here either
                            } else if (mtcappmode == 2) {
                                if (modearray.contains("music")) {
                                    callMethod(mparam.thisObject, "startMusic", "mswitch", 1);
                                    mToast.setText("Music");
                                    mToast.setGravity(17, 0, -100);
                                    mToast.show();
                                    if (DEBUG) log(TAG, "switch to music from sys, mtc2");
                                }
                                setIntField(mparam.thisObject, "mtcappmode", 3);
                                return null;
                                // 3 - music
                            } else if (mtcappmode == 3) {
                                if (modearray.contains("video")) {
                                    cmdPlayer(mCtx, "stop");
                                    callMethod(mparam.thisObject, "startMovie", 1);
                                    mToast.setText("Video");
                                    mToast.setGravity(17, 0, -100);
                                    mToast.show();
                                    if (DEBUG) log(TAG, "switch to video from sys, mtc3");
                                    setIntField(mparam.thisObject, "mtcappmode", 4);
                                    return null;
                                } else if (modearray.contains("ipod")) {
                                    cmdPlayer(mCtx, "stop");
                                    am.setParameters("av_channel_exit=sys");
                                    callMethod(mparam.thisObject, "startIpod", 1);
                                    mToast.setText("IPOD");
                                    mToast.setGravity(17, 0, -100);
                                    mToast.show();
                                    if (DEBUG) log(TAG, "switch to ipod from sys, mtc3");
                                    setIntField(mparam.thisObject, "mtcappmode", 5);
                                    return null;
                                } else if (modearray.contains("aux")) {
                                    cmdPlayer(mCtx, "stop");
                                    am.setParameters("av_channel_exit=sys");
                                    am.setParameters("av_channel_enter=line");
                                    callMethod(mparam.thisObject, "startAux", 1);
                                    mToast.setText("AV IN/AUX");
                                    mToast.setGravity(17, 0, -100);
                                    mToast.show();
                                    if (DEBUG) log(TAG, "switch to aux from sys, mtc3");
                                    setIntField(mparam.thisObject, "mtcappmode", 6);
                                    return null;
                                } else if (modearray.contains("nav")) {
                                    callMethod(mparam.thisObject, "startGPS");
                                    mToast.setText("Nav");
                                    mToast.setGravity(17, 0, -100);
                                    mToast.show();
                                    if (DEBUG) log(TAG, "switch to nav from sys, mtc3");
                                    setIntField(mparam.thisObject, "mtcappmode", 7);
                                    return null;
                                } else if (modearray.contains("dvd")) {
                                    cmdPlayer(mCtx, "stop");
                                    am.setParameters("av_channel_exit=sys");
                                    am.setParameters("av_channel_enter=dvd");
                                    callMethod(mparam.thisObject, "startDVD", 1);
                                    mToast.setText("DVD");
                                    mToast.setGravity(17, 0, -100);
                                    mToast.show();
                                    if (DEBUG) log(TAG, "switch to dvd from sys, mtc3");
                                    setIntField(mparam.thisObject, "mtcappmode", 2);
                                    return null;
                                } else if (modearray.contains("radio")) {
                                    cmdPlayer(mCtx, "stop");
                                    am.setParameters("av_channel_exit=sys");
                                    am.setParameters("av_channel_enter=fm");
                                    am.setParameters("ctl_radio_mute=false");
                                    callMethod(mparam.thisObject, "startRadio", 1);
                                    mToast.setText("Radio");
                                    mToast.setGravity(17, 0, -100);
                                    mToast.show();
                                    if (DEBUG) log(TAG, "switch to radio from sys, mtc3");
                                    setIntField(mparam.thisObject, "mtcappmode", 1);
                                    return null;
                                }
                                // 4 - ipod
                            } else if (ipod.equals("true") && mtcappmode == 4) {
                                if (modearray.contains("ipod")) {
                                    callMethod(mparam.thisObject, "startIpod", 1);
                                    mToast.setText("IPOD");
                                    mToast.setGravity(17, 0, -100);
                                    mToast.show();
                                    if (DEBUG) log(TAG, "switch to ipod from sys, mtc4");
                                    setIntField(mparam.thisObject, "mtcappmode", 5);
                                    return null;
                                } else if (modearray.contains("aux")) {
                                    cmdPlayer(mCtx, "stop");
                                    am.setParameters("av_channel_exit=sys");
                                    am.setParameters("av_channel_enter=line");
                                    callMethod(mparam.thisObject, "startAux", 1);
                                    mToast.setText("AV IN/AUX");
                                    mToast.setGravity(17, 0, -100);
                                    mToast.show();
                                    if (DEBUG) log(TAG, "switch to aux from sys, mtc4");
                                    setIntField(mparam.thisObject, "mtcappmode", 6);
                                    return null;
                                } else if (modearray.contains("nav")) {
                                    callMethod(mparam.thisObject, "startGPS");
                                    mToast.setText("Nav");
                                    mToast.setGravity(17, 0, -100);
                                    mToast.show();
                                    if (DEBUG) log(TAG, "switch to nav from sys, mtc4");
                                    setIntField(mparam.thisObject, "mtcappmode", 7);
                                    return null;
                                } else if (modearray.contains("radio")) {
                                    am.setParameters("av_channel_exit=sys");
                                    am.setParameters("av_channel_enter=fm");
                                    am.setParameters("ctl_radio_mute=false");
                                    callMethod(mparam.thisObject, "startRadio", 1);
                                    mToast.setText("Radio");
                                    mToast.setGravity(17, 0, -100);
                                    mToast.show();
                                    if (DEBUG) log(TAG, "switch to radio from sys, mtc4");
                                    setIntField(mparam.thisObject, "mtcappmode", 1);
                                    return null;
                                } else if (modearray.contains("music")) {
                                    callMethod(mparam.thisObject, "startMusic", 1);
                                    mToast.setText("Music");
                                    mToast.setGravity(17, 0, -100);
                                    mToast.show();
                                    if (DEBUG) log(TAG, "switch to music from sys, mtc4");
                                    setIntField(mparam.thisObject, "mtcappmode", 3);
                                    return null;
                                }
                                // 4 - video
                            } else if (mtcappmode == 4) {
                                if (modearray.contains("aux")) {
                                    cmdPlayer(mCtx, "stop");
                                    am.setParameters("av_channel_exit=sys");
                                    am.setParameters("av_channel_enter=line");
                                    callMethod(mparam.thisObject, "startAux", 1);
                                    mToast.setText("AV IN/AUX");
                                    mToast.setGravity(17, 0, -100);
                                    mToast.show();
                                    if (DEBUG) log(TAG, "switch to aux from sys, mtc4");
                                    setIntField(mparam.thisObject, "mtcappmode", 6);
                                    return null;
                                } else if (modearray.contains("nav")) {
                                    callMethod(mparam.thisObject, "startGPS");
                                    mToast.setText("Nav");
                                    mToast.setGravity(17, 0, -100);
                                    mToast.show();
                                    if (DEBUG) log(TAG, "switch to nav from sys, mtc4");
                                    setIntField(mparam.thisObject, "mtcappmode", 7);
                                    return null;
                                } else if (modearray.contains("radio")) {
                                    am.setParameters("av_channel_exit=sys");
                                    am.setParameters("av_channel_enter=fm");
                                    am.setParameters("ctl_radio_mute=false");
                                    callMethod(mparam.thisObject, "startRadio", 1);
                                    mToast.setText("Radio");
                                    mToast.setGravity(17, 0, -100);
                                    mToast.show();
                                    if (DEBUG) log(TAG, "switch to radio from sys, mtc4");
                                    setIntField(mparam.thisObject, "mtcappmode", 1);
                                    return null;
                                } else if (modearray.contains("dvd")) {
                                    am.setParameters("av_channel_exit=sys");
                                    callMethod(mparam.thisObject, "startDVD", 1);
                                    mToast.setText("DVD");
                                    mToast.setGravity(17, 0, -100);
                                    mToast.show();
                                    if (DEBUG) log(TAG, "switch to dvd from sys, mtc4");
                                    setIntField(mparam.thisObject, "mtcappmode", 2);
                                    return null;
                                } else if (modearray.contains("music")) {
                                    callMethod(mparam.thisObject, "startMusic", 1);
                                    mToast.setText("Music");
                                    mToast.setGravity(17, 0, -100);
                                    mToast.show();
                                    if (DEBUG) log(TAG, "switch to music from sys, mtc4");
                                    setIntField(mparam.thisObject, "mtcappmode", 3);
                                    return null;
                                }
                                setIntField(mparam.thisObject, "mtcappmode", 5);
                                return null;
                                // 5 - ipod
                            } else if (mtcappmode == 5) {
                                if (modearray.contains("aux")) {
                                    cmdPlayer(mCtx, "stop");
                                    am.setParameters("av_channel_exit=sys");
                                    am.setParameters("av_channel_enter=line");
                                    callMethod(mparam.thisObject, "startAux", 1);
                                    mToast.setText("AV IN/AUX");
                                    mToast.setGravity(17, 0, -100);
                                    mToast.show();
                                    if (DEBUG) log(TAG, "switch to aux from sys, mtc5");
                                    setIntField(mparam.thisObject, "mtcappmode", 6);
                                    return null;
                                } else if (modearray.contains("nav")) {
                                    callMethod(mparam.thisObject, "startGPS");
                                    mToast.setText("Nav");
                                    mToast.setGravity(17, 0, -100);
                                    mToast.show();
                                    if (DEBUG) log(TAG, "switch to nav from sys, mtc5");
                                    setIntField(mparam.thisObject, "mtcappmode", 7);
                                    return null;
                                } else if (modearray.contains("radio")) {
                                    am.setParameters("av_channel_exit=sys");
                                    am.setParameters("av_channel_enter=fm");
                                    am.setParameters("ctl_radio_mute=false");
                                    callMethod(mparam.thisObject, "startRadio", 1);
                                    mToast.setText("Radio");
                                    mToast.setGravity(17, 0, -100);
                                    mToast.show();
                                    if (DEBUG) log(TAG, "switch to radio from sys, mtc5");
                                    setIntField(mparam.thisObject, "mtcappmode", 1);
                                    return null;
                                } else if (modearray.contains("dvd")) {
                                    am.setParameters("av_channel_exit=sys");
                                    callMethod(mparam.thisObject, "startDVD", 1);
                                    mToast.setText("DVD");
                                    mToast.setGravity(17, 0, -100);
                                    mToast.show();
                                    if (DEBUG) log(TAG, "switch to dvd from sys, mtc5");
                                    setIntField(mparam.thisObject, "mtcappmode", 2);
                                    return null;
                                } else if (modearray.contains("music")) {
                                    callMethod(mparam.thisObject, "startMusic", 1);
                                    mToast.setText("Music");
                                    mToast.setGravity(17, 0, -100);
                                    mToast.show();
                                    if (DEBUG) log(TAG, "switch to music from sys, mtc5");
                                    setIntField(mparam.thisObject, "mtcappmode", 3);
                                    return null;
                                }
                                // 6 - aux
                            } else if (mtcappmode == 6) {
                                if (modearray.contains("nav")) {
                                    callMethod(mparam.thisObject, "startGPS");
                                    mToast.setText("NAV");
                                    mToast.setGravity(17, 0, -100);
                                    mToast.show();
                                    if (DEBUG) log(TAG, "switch to nav from sys, mtc6");
                                    setIntField(mparam.thisObject, "mtcappmode", 7);
                                    return null;
                                } else if (modearray.contains("radio")) {
                                    am.setParameters("av_channel_exit=sys");
                                    am.setParameters("av_channel_enter=fm");
                                    am.setParameters("ctl_radio_mute=false");
                                    callMethod(mparam.thisObject, "startRadio", 1);
                                    mToast.setText("Radio");
                                    mToast.setGravity(17, 0, -100);
                                    mToast.show();
                                    if (DEBUG) log(TAG, "switch to radio from sys, mtc6");
                                    setIntField(mparam.thisObject, "mtcappmode", 1);
                                    return null;
                                } else if (modearray.contains("dvd")) {
                                    am.setParameters("av_channel_exit=sys");
                                    callMethod(mparam.thisObject, "startDVD", 1);
                                    mToast.setText("DVD");
                                    mToast.setGravity(17, 0, -100);
                                    mToast.show();
                                    if (DEBUG) log(TAG, "switch to dvd from sys, mtc6");
                                    setIntField(mparam.thisObject, "mtcappmode", 2);
                                    return null;
                                } else if (modearray.contains("music")) {
                                    callMethod(mparam.thisObject, "startMusic", 1);
                                    mToast.setText("Music");
                                    mToast.setGravity(17, 0, -100);
                                    mToast.show();
                                    if (DEBUG) log(TAG, "switch to music from sys, mtc6");
                                    setIntField(mparam.thisObject, "mtcappmode", 3);
                                    return null;
                                } else if (modearray.contains("video")) {
                                    callMethod(mparam.thisObject, "startMovie", 1);
                                    mToast.setText("Video");
                                    mToast.setGravity(17, 0, -100);
                                    mToast.show();
                                    if (DEBUG) log(TAG, "switch to video from sys, mtc6");
                                    setIntField(mparam.thisObject, "mtcappmode", 4);
                                    return null;
                                } else if (modearray.contains("ipod")) {
                                    callMethod(mparam.thisObject, "startIpod", 1);
                                    mToast.setText("IPOD");
                                    mToast.setGravity(17, 0, -100);
                                    mToast.show();
                                    if (DEBUG) log(TAG, "switch to ipod from sys, mtc6");
                                    setIntField(mparam.thisObject, "mtcappmode", 5);
                                    return null;
                                }

                                // 7 - nav
                            } else if (mtcappmode == 7) {
                                if (modearray.contains("radio")) {
                                    cmdPlayer(mCtx, "stop");
                                    am.setParameters("av_channel_exit=sys");
                                    am.setParameters("av_channel_enter=fm");
                                    am.setParameters("ctl_radio_mute=false");
                                    callMethod(mparam.thisObject, "startRadio", 1);
                                    mToast.setText("Radio");
                                    mToast.setGravity(17, 0, -100);
                                    mToast.show();
                                    if (DEBUG) log(TAG, "switch to radio from sys, mtc7");
                                    setIntField(mparam.thisObject, "mtcappmode", 1);
                                    return null;
                                } else if (modearray.contains("dvd")) {
                                    am.setParameters("av_channel_exit=sys");
                                    callMethod(mparam.thisObject, "startDVD", 1);
                                    mToast.setText("DVD");
                                    mToast.setGravity(17, 0, -100);
                                    mToast.show();
                                    if (DEBUG) log(TAG, "switch to dvd from sys, mtc7");
                                    setIntField(mparam.thisObject, "mtcappmode", 2);
                                    return null;
                                } else if (modearray.contains("music")) {
                                    callMethod(mparam.thisObject, "startMusic", 1);
                                    mToast.setText("Music");
                                    mToast.setGravity(17, 0, -100);
                                    mToast.show();
                                    if (DEBUG) log(TAG, "switch to music from sys, mtc7");
                                    setIntField(mparam.thisObject, "mtcappmode", 3);
                                    return null;
                                } else if (modearray.contains("video")) {
                                    callMethod(mparam.thisObject, "startMovie", 1);
                                    mToast.setText("Video");
                                    mToast.setGravity(17, 0, -100);
                                    mToast.show();
                                    if (DEBUG) log(TAG, "switch to video from sys, mtc7");
                                    setIntField(mparam.thisObject, "mtcappmode", 4);
                                    return null;
                                } else if (modearray.contains("ipod")) {
                                    am.setParameters("av_channel_exit=sys");
                                    am.setParameters("av_channel_enter=ipod");
                                    callMethod(mparam.thisObject, "startIpod", 1);
                                    mToast.setText("IPOD");
                                    mToast.setGravity(17, 0, -100);
                                    mToast.show();
                                    if (DEBUG) log(TAG, "switch to ipod from sys, mtc7");
                                    setIntField(mparam.thisObject, "mtcappmode", 5);
                                    return null;
                                } else if (modearray.contains("aux")) {
                                    cmdPlayer(mCtx, "stop");
                                    am.setParameters("av_channel_exit=sys");
                                    am.setParameters("av_channel_enter=line");
                                    callMethod(mparam.thisObject, "startAux", 1);
                                    mToast.setText("AV IN/AUX");
                                    mToast.setGravity(17, 0, -100);
                                    mToast.show();
                                    if (DEBUG) log(TAG, "switch to aux from sys, mtc7");
                                    setIntField(mparam.thisObject, "mtcappmode", 6);
                                    return null;
                                }
                            }
                        }

                        // av channel is ipod
                        if (s.equals("ipod")) {
                            if (modearray.contains("aux")) {
                                am.setParameters("av_channel_exit=ipod");
                                am.setParameters("av_channel_enter=line");
                                callMethod(mparam.thisObject, "startAux", 1);
                                mToast.setText("AV IN/AUX");
                                mToast.setGravity(17, 0, -100);
                                mToast.show();
                                if (DEBUG) log(TAG, "switch to aux from ipod");
                                setIntField(mparam.thisObject, "mtcappmode", 6);
                                return null;
                            } else if (modearray.contains("nav")) {
                                am.setParameters("av_channel_exit=ipod");
                                am.setParameters("av_channel_enter=sys");
                                callMethod(mparam.thisObject, "startGPS");
                                mToast.setText("Nav");
                                mToast.setGravity(17, 0, -100);
                                mToast.show();
                                if (DEBUG) log(TAG, "switch to nav from ipod");
                                setIntField(mparam.thisObject, "mtcappmode", 7);
                                return null;
                            } else if (modearray.contains("radio")) {
                                am.setParameters("av_channel_exit=ipod");
                                am.setParameters("av_channel_enter=fm");
                                am.setParameters("ctl_radio_mute=false");
                                callMethod(mparam.thisObject, "startRadio", 1);
                                mToast.setText("Radio");
                                mToast.setGravity(17, 0, -100);
                                mToast.show();
                                if (DEBUG) log(TAG, "switch to radio from ipod");
                                setIntField(mparam.thisObject, "mtcappmode", 1);
                                return null;
                            } else if (modearray.contains("dvd")) {
                                am.setParameters("av_channel_exit=ipod");
                                callMethod(mparam.thisObject, "startDVD", 1);
                                mToast.setText("DVD");
                                mToast.setGravity(17, 0, -100);
                                mToast.show();
                                if (DEBUG) log(TAG, "switch to dvd from ipod");
                                setIntField(mparam.thisObject, "mtcappmode", 2);
                                return null;
                            } else if (modearray.contains("music")) {
                                am.setParameters("av_channel_exit=ipod");
                                am.setParameters("av_channel_enter=sys");
                                callMethod(mparam.thisObject, "startMusic", 1);
                                mToast.setText("Music");
                                mToast.setGravity(17, 0, -100);
                                mToast.show();
                                if (DEBUG) log(TAG, "switch to music from ipod");
                                setIntField(mparam.thisObject, "mtcappmode", 3);
                                return null;
                            } else if (modearray.contains("video")) {
                                am.setParameters("av_channel_exit=ipod");
                                am.setParameters("av_channel_enter=sys");
                                callMethod(mparam.thisObject, "startMovie", 1);
                                mToast.setText("Video");
                                mToast.setGravity(17, 0, -100);
                                mToast.show();
                                if (DEBUG) log(TAG, "switch to video from ipod");
                                setIntField(mparam.thisObject, "mtcappmode", 4);
                                return null;
                            }
                        }

                        // av channel is aux/line-in
                        if (s.equals("line")) {
                            if (modearray.contains("nav")) {
                                am.setParameters("av_channel_exit=line");
                                am.setParameters("av_channel_enter=sys");
                                callMethod(mparam.thisObject, "startGPS");
                                mToast.setText("Nav");
                                mToast.setGravity(17, 0, -100);
                                mToast.show();
                                if (DEBUG) log(TAG, "switch to nav from line");
                                setIntField(mparam.thisObject, "mtcappmode", 7);
                                return null;
                            } else if (modearray.contains("radio")) {
                                am.setParameters("av_channel_exit=line");
                                am.setParameters("av_channel_enter=fm");
                                am.setParameters("ctl_radio_mute=false");
                                callMethod(mparam.thisObject, "startRadio", 1);
                                mToast.setText("Radio");
                                mToast.setGravity(17, 0, -100);
                                mToast.show();
                                if (DEBUG) log(TAG, "switch to radio from line");
                                setIntField(mparam.thisObject, "mtcappmode", 1);
                                return null;
                            } else if (modearray.contains("dvd")) {
                                am.setParameters("av_channel_exit=line");
                                am.setParameters("av_channel_enter=dvd");
                                callMethod(mparam.thisObject, "startDVD", 1);
                                mToast.setText("DVD");
                                mToast.setGravity(17, 0, -100);
                                mToast.show();
                                if (DEBUG) log(TAG, "switch to dvd from line");
                                setIntField(mparam.thisObject, "mtcappmode", 2);
                                return null;
                            } else if (modearray.contains("music")) {
                                am.setParameters("av_channel_exit=line");
                                am.setParameters("av_channel_enter=sys");
                                callMethod(mparam.thisObject, "startMusic", 1);
                                mToast.setText("Music");
                                mToast.setGravity(17, 0, -100);
                                mToast.show();
                                if (DEBUG) log(TAG, "switch to music from line");
                                setIntField(mparam.thisObject, "mtcappmode", 3);
                                return null;
                            } else if (modearray.contains("video")) {
                                am.setParameters("av_channel_exit=line");
                                am.setParameters("av_channel_enter=sys");
                                callMethod(mparam.thisObject, "startMovie", 1);
                                mToast.setText("Video");
                                mToast.setGravity(17, 0, -100);
                                mToast.show();
                                if (DEBUG) log(TAG, "switch to video from line");
                                setIntField(mparam.thisObject, "mtcappmode", 4);
                                return null;
                            } else if (modearray.contains("ipod")) {
                                am.setParameters("av_channel_exit=line");
                                am.setParameters("av_channel_enter=ipod");
                                callMethod(mparam.thisObject, "startIpod", 1);
                                mToast.setText("IPOD");
                                mToast.setGravity(17, 0, -100);
                                mToast.show();
                                if (DEBUG) log(TAG, "switch to ipod from line");
                                setIntField(mparam.thisObject, "mtcappmode", 5);
                                return null;
                            }
                        }

                        // fall-thru is here
                        if (DEBUG) log(TAG, "assign mtcappmode to 1 from fallthru");
                        setIntField(mparam.thisObject, "mtcappmode", 1);
                        return null;
                    }
                });
            }
        } else if (lpparam.packageName.equals(radiopkg)) {
            prefs.reload();
            if (prefs.getBoolean("resetswitch", false)) {
                findAndHookMethod(RADIO_CLASS, lpparam.classLoader, "onCreate", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(final MethodHookParam mparam) throws Throwable {
                        prefs.reload();
                        if (DEBUG) log(TAG, "hooked radioservice");
                        radio_prefs = (SharedPreferences) getObjectField(mparam.thisObject, "app_preferences");
                        editor = radio_prefs.edit();
                        radioCtx = (Context) mparam.thisObject;
                        IntentFilter learnfilter = new IntentFilter();
                        learnfilter.addAction(LEARN_INTENT);

                        learnReceiver = new BroadcastReceiver() {
                            @Override
                            public void onReceive(Context context, Intent intent) {
                                for (int i = 0; i < 30; i++) {
                                    if (DEBUG) log(TAG, "writing RadioFrequency" + i + " to local pref via intent");
                                    Intent save_intent = new Intent(SAVE_PRESET);
                                    save_intent.setPackage("com.dr8.xposedmtc");
                                    save_intent.putExtra((new StringBuilder()).append("RadioFrequency").append(i).toString(), radio_prefs.getInt((new StringBuilder()).append("RadioFrequency").append(i).toString(), 87700000));
                                    context.sendBroadcast(save_intent);
                                }
                                Toast.makeText(context, "Radio presets saved", Toast.LENGTH_SHORT).show();
                            }
                        };

                        radioCtx.registerReceiver(learnReceiver, learnfilter);
                    }
                });

                findAndHookMethod(RADIO_CLASS, lpparam.classLoader, "onDestroy", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(final MethodHookParam mparam) throws Throwable {
                        radioCtx.unregisterReceiver(learnReceiver);
                    }
                });

                findAndHookMethod(RADIO_CLASS, lpparam.classLoader, "resetRadioPreference", new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(MethodHookParam mparam) throws Throwable {
                        prefs.reload();
                        editor.clear();
                        editor.commit();
                        int mArea = getIntField(mparam.thisObject, "mArea");

                        if (DEBUG) log(TAG, "overwriting default radio presets with local prefs");
                        // fm1 - fm3
                        int j = 87700000;
                        for (int i = 0; i < 18; i++) {
                            editor.putInt((new StringBuilder()).append("RadioFrequency").append(i).toString(), prefs.getInt((new StringBuilder()).append("RadioFrequency").append(i).toString(), j));
                            j+= 200000;
                        }

                        int k = 530000;
                        // am1 - am2
                        for (int i = 18; i < 30; i++) {
                            editor.putInt((new StringBuilder()).append("RadioFrequency").append(i).toString(), prefs.getInt((new StringBuilder()).append("RadioFrequency").append(i).toString(), k));
                            k+= 10000;
                        }
                        int mBand = 0;
                        int mChannel = 0;
                        int mFreq = prefs.getInt("RadioFrequency0", 87700000);
                        boolean mSt = true;
                        boolean mLoc = false;
                        int mPty = 0;
                        boolean mAf = true;
                        boolean mTa = false;
                        boolean rdsUI = true;
                        editor.putInt("RadioBand", mBand);
                        editor.putInt("RadioChannel", mChannel);
                        editor.putInt("CurrentFreq", mFreq);
                        callMethod(mparam.thisObject, "saveStPreference", mSt);
                        callMethod(mparam.thisObject, "saveLocPreference", mLoc);
                        callMethod(mparam.thisObject, "savePtyPreference", mPty);
                        callMethod(mparam.thisObject, "saveAfPreference", mAf);
                        callMethod(mparam.thisObject, "saveTaPreference", mTa);
                        callMethod(mparam.thisObject, "saveAreaPreference", mArea);
                        callMethod(mparam.thisObject, "saveRdsUIPreference", rdsUI);
                        editor.commit();

                        return null;

                    }
                });
            }
        } else if (lpparam.packageName.equals(btpkg)) {
            prefs.reload();
            if (prefs.getBoolean("allbtobd", false)) {
                findAndHookMethod(BT_CLASS, lpparam.classLoader, "isOBDDevice", String.class, new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(final MethodHookParam mparam) throws Throwable {
                        String paramString = (String) mparam.args[0];
                        String obdname = prefs.getString("obdname", "OBD");
                        return (paramString != null) && (paramString.toUpperCase().contains(obdname));
                    }
                });
            }
        } else if (lpparam.packageName.equals(settingspkg)) {
            prefs.reload();
            if (prefs.getBoolean("allbtobd", false)) {
                if (getAndroidRelease().matches("4.2.*")) {
                    findAndHookMethod(SETTINGS_CLASS, lpparam.classLoader, "connecttodevice", String.class, String.class, new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam mparam) throws Throwable {
                            String s = (String) mparam.args[0];
                            String s1 = (String) mparam.args[1];
                            String obdname = prefs.getString("obdname", "OBD");

                            boolean flag = false;
                            if (s1 != null)
                            {
                                boolean flag1 = s1.toUpperCase().contains(obdname);
                                flag = false;
                                if (flag1)
                                    flag = true;
                            }
                            Intent intent = new Intent("com.microntek.settingsaccess");
                            intent.putExtra("accesstype", 5);
                            String s2 = "";
                            int i = 12 - s.length();
                            for (int j = 0; j < i; j++)
                                s2 = (new StringBuilder()).append(s2).append("0").toString();

                            String s3 = (new StringBuilder()).append(s2).append(s).toString();
                            String s4;
                            if (flag)
                                s4 = (new StringBuilder()).append("AT-PD").append(s3).append("\r\n").toString();
                            else
                                s4 = (new StringBuilder()).append("AT-CC").append(s3).append("\r\n").toString();
                            Log.i("Settings", (new StringBuilder()).append("write ").append(s4).toString());
                            intent.putExtra("accesscommand", s4);
                            mCtx.sendBroadcast(intent);
                            return null;
                        }
                    });
                } else {
                    findAndHookMethod(SETTINGS_CLASS, lpparam.classLoader, "isOBDDevice", String.class, new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(final MethodHookParam mparam) throws Throwable {
                            String paramString = (String) mparam.args[0];
                            String obdname = prefs.getString("obdname", "OBD");
                            return (paramString != null) && (paramString.toUpperCase().contains(obdname));
                        }
                    });
                }
            }
        } else if (lpparam.packageName.equals(btuipkg)) {
            prefs.reload();
            if (prefs.getBoolean("allbtobd", false)) {
                if (getAndroidRelease().matches("4.2.*")) {
                    findAndHookMethod(BTUI_CLASS, lpparam.classLoader, "isOBDDevice", new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(final MethodHookParam mparam) throws Throwable {
                            String paramString = (String) mparam.args[0];
                            String obdname = prefs.getString("obdname", "OBD");
                            return (paramString != null) && (paramString.toUpperCase().contains(obdname));
                        }
                    });
                }
            }
        } else {
            return;
        }
    }

    @Override
    public void initZygote(StartupParam startupParam) throws Throwable {
        prefs = new XSharedPreferences("com.dr8.xposedmtc", "com.dr8.xposedmtc_preferences");
        DEBUG = prefs.getBoolean("debug", false);
    }

}
