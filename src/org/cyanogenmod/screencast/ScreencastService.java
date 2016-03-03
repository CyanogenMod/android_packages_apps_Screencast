/*
 * Copyright (C) 2013 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.cyanogenmod.screencast;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.net.Uri;
import android.os.Environment;
import android.os.IBinder;
import android.os.Process;
import android.os.RemoteException;
import android.os.StatFs;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.widget.Toast;
import android.graphics.Point;

import java.io.File;
import java.lang.reflect.Method;
import java.util.Timer;
import java.util.TimerTask;

public class ScreencastService extends Service {
    private static final String LOGTAG = "ScreencastService";
    public static final String SCREENCASTER_NAME = "hidden:screen-recording";
    public static final String PREFS = "preferences";
    static final String KEY_RECORDING = "recording";
    private long startTime;
    private Timer timer;
    private Notification.Builder mBuilder;
    RecordingDevice mRecorder;

    private static final String ACTION_START_SCREENCAST = "org.cyanogenmod.ACTION_START_SCREENCAST";
    private static final String ACTION_STOP_SCREENCAST = "org.cyanogenmod.ACTION_STOP_SCREENCAST";

    private static final String SHOW_TOUCHES = "show_touches";

    private BroadcastReceiver mUserSwitchReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_USER_BACKGROUND)) {
                context.startService(new Intent(ACTION_STOP_SCREENCAST)
                        .setClass(context, ScreencastService.class));
            }
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    void cleanup() {
        String recorderPath = null;
        if (mRecorder != null) {
            recorderPath = mRecorder.getRecordingFilePath();
            mRecorder.stop();
            mRecorder = null;
        }
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        stopForeground(true);
        if (recorderPath != null) {
            sendShareNotification(recorderPath);
        }
    }

    @Override
    public void onCreate() {
        stopCasting();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_USER_BACKGROUND);
        registerReceiver(mUserSwitchReceiver, filter);
        super.onCreate();
    }

    @Override
    public void onDestroy() {
        stopCasting();
        unregisterReceiver(mUserSwitchReceiver);
        super.onDestroy();
    }

    private boolean hasAvailableSpace() {
        StatFs stat = new StatFs(Environment.getExternalStorageDirectory().getPath());
        long bytesAvailable = stat.getBlockSizeLong() * stat.getBlockCountLong();
        long megAvailable = bytesAvailable / 1048576;
        return megAvailable >= 100;
    }

    public void updateNotification(Context context) {
        long timeElapsed = SystemClock.elapsedRealtime() - startTime;
        mBuilder.setContentText(getString(R.string.video_length,
                DateUtils.formatElapsedTime(timeElapsed / 1000)));
        startForeground(1, mBuilder.build());
    }

    protected Point getNativeResolution() {
        DisplayManager dm = (DisplayManager)getSystemService(DISPLAY_SERVICE);
        Display display = dm.getDisplay(Display.DEFAULT_DISPLAY);
        Point ret = new Point();
        try {
            display.getRealSize(ret);
        }
        catch (Exception e) {
            try {
                Method mGetRawH = Display.class.getMethod("getRawHeight");
                Method mGetRawW = Display.class.getMethod("getRawWidth");
                ret.x = (Integer) mGetRawW.invoke(display);
                ret.y = (Integer) mGetRawH.invoke(display);
            }
            catch (Exception ex) {
                display.getSize(ret);
            }
        }
        return ret;
    }

    void registerScreencaster() throws RemoteException {
        DisplayManager dm = (DisplayManager)getSystemService(DISPLAY_SERVICE);
        Display display = dm.getDisplay(Display.DEFAULT_DISPLAY);
        DisplayMetrics metrics = new DisplayMetrics();
        display.getMetrics(metrics);

        assert mRecorder == null;
        Point size = getNativeResolution();
        // size = new Point(1080, 1920);
        mRecorder = new RecordingDevice(this, size.x, size.y);
        VirtualDisplay vd = mRecorder.registerVirtualDisplay(this,
                SCREENCASTER_NAME, size.x, size.y, metrics.densityDpi);
        if (vd == null)
            cleanup();
    }

    private void stopCasting() {
        getSharedPreferences(ScreencastService.PREFS, 0)
                .edit().putBoolean(ScreencastService.KEY_RECORDING, false).apply();
        // clean show_touches settings if user enable show_touches in this activity
        if (Process.myUserHandle().isOwner()) {
            Settings.System.putInt(getContentResolver(), SHOW_TOUCHES, 0);
        }
        cleanup();

        if (!hasAvailableSpace()) {
            Toast.makeText(this, R.string.insufficient_storage, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            return START_NOT_STICKY;
        }
        if ("org.cyanogenmod.server.display.SCAN".equals(intent.getAction())) {
            return START_STICKY;
        } else if ("org.cyanogenmod.server.display.STOP_SCAN".equals(intent.getAction())) {
           return START_STICKY;
        } else if (TextUtils.equals(intent.getAction(), ACTION_START_SCREENCAST)
                 || TextUtils.equals(intent.getAction(), "com.cyanogenmod.ACTION_START_SCREENCAST")
                ) {
            try {
                if (!hasAvailableSpace()) {
                    Toast.makeText(this, R.string.not_enough_storage, Toast.LENGTH_LONG).show();
                    return START_NOT_STICKY;
                }
                startTime = SystemClock.elapsedRealtime();
                registerScreencaster();
                mBuilder = createNotificationBuilder();

                if (Process.myUserHandle().isOwner()) {
                    Settings.System.putInt(getContentResolver(), SHOW_TOUCHES, 1);
                    addNotificationTouchButton(true);
                }

                timer = new Timer();
                timer.scheduleAtFixedRate(new TimerTask() {
                    @Override
                    public void run() {
                        updateNotification(ScreencastService.this);
                    }
                }, 100, 1000);

                getSharedPreferences(ScreencastService.PREFS, 0)
                        .edit().putBoolean(ScreencastService.KEY_RECORDING, true).apply();
                return START_STICKY;
            }
            catch (Exception e) {
                Log.e("Mirror", "error", e);
            }
        } else if (TextUtils.equals(intent.getAction(), ACTION_STOP_SCREENCAST)) {
            stopCasting();
        } else if (intent.getAction().equals("org.cyanogenmod.SHOW_TOUCHES")) {
            String showTouchesValue = intent.getStringExtra(SHOW_TOUCHES);
            mBuilder = createNotificationBuilder();
            addNotificationTouchButton("on".equals(showTouchesValue));
            return START_STICKY;
        }
        return START_NOT_STICKY;
    }

    private void addNotificationTouchButton(boolean showingTouches) {
        Intent showTouchesIntent = new Intent("org.cyanogenmod.SHOW_TOUCHES");
        showTouchesIntent.setClass(this, ScreencastService.class);
        if (showingTouches) {
            Settings.System.putInt(getContentResolver(), SHOW_TOUCHES, 1);
            showTouchesIntent.putExtra(SHOW_TOUCHES, "off");
            mBuilder.addAction(R.drawable.ic_stat_rating_important,
                    getString(R.string.show_touches),
                    PendingIntent.getService(this, 0,
                            showTouchesIntent, PendingIntent.FLAG_UPDATE_CURRENT));
        } else {
            Settings.System.putInt(getContentResolver(), SHOW_TOUCHES, 0);
            showTouchesIntent.putExtra(SHOW_TOUCHES, "on");
            mBuilder.addAction(R.drawable.ic_stat_rating_not_important,
                    getString(R.string.show_touches),
                    PendingIntent.getService(this, 0,
                            showTouchesIntent, PendingIntent.FLAG_UPDATE_CURRENT));
        }
    }

    private Notification.Builder createNotificationBuilder() {
        Notification.Builder builder = new Notification.Builder(this)
                .setOngoing(true)
                .setSmallIcon(R.drawable.ic_stat_device_access_video)
                .setContentTitle(getString(R.string.recording));
        Intent stopRecording = new Intent(ACTION_STOP_SCREENCAST);
        stopRecording.setClass(this, ScreencastService.class);
        builder.addAction(R.drawable.stop, getString(R.string.stop),
                PendingIntent.getService(this, 0, stopRecording, 0));
        return builder;
    }

    private void sendShareNotification(String recordingFilePath) {
        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        // share the screencast file
        mBuilder = createShareNotificationBuilder(recordingFilePath);
        notificationManager.notify(0, mBuilder.build());
    }

    private Notification.Builder createShareNotificationBuilder(String file) {
        Intent sharingIntent = new Intent(Intent.ACTION_SEND);
        sharingIntent.setType("video/mp4");
        Uri uri = Uri.parse("file://" + file);
        sharingIntent.putExtra(Intent.EXTRA_STREAM, uri);
        sharingIntent.putExtra(Intent.EXTRA_SUBJECT, new File(file).getName());
        Intent chooserIntent = Intent.createChooser(sharingIntent, null);
        chooserIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        long timeElapsed = SystemClock.elapsedRealtime() - startTime;

        Log.i(LOGTAG, "Video complete: " + uri);

        Intent open = new Intent(Intent.ACTION_VIEW);
        open.setDataAndType(uri, "video/mp4");
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent contentIntent =
                PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_CANCEL_CURRENT);

        Notification.Builder builder = new Notification.Builder(this)
        .setWhen(System.currentTimeMillis())
        .setSmallIcon(R.drawable.ic_stat_device_access_video)
        .setContentTitle(getString(R.string.recording_ready_to_share))
        .setContentText(getString(R.string.video_length,
                DateUtils.formatElapsedTime(timeElapsed / 1000)))
        .addAction(android.R.drawable.ic_menu_share, getString(R.string.share),
                PendingIntent.getActivity(this, 0, chooserIntent, PendingIntent.FLAG_CANCEL_CURRENT))
        .setContentIntent(contentIntent);
        return builder;
    }
}
