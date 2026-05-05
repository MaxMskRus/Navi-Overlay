package com.navioverlay.car.services;

import android.app.*;
import android.content.*;
import android.os.*;
import android.content.pm.ServiceInfo;
import android.media.AudioManager;
import com.navioverlay.car.core.Constants;
import com.navioverlay.car.core.Logx;
import com.navioverlay.car.core.Prefs;
import com.navioverlay.car.engine.AppStateDetector;
import com.navioverlay.car.engine.MusicStateDetector;
import com.navioverlay.car.engine.StateEngine;
import com.navioverlay.car.engine.TrackSnapshot;

/**
 * v9: системный движок состояния.
 * Не ждёт разовых событий. Сам читает: что на экране + что играет + какой трек.
 */
public class MonitorService extends Service {
    public static final String ACTION_START = "com.navioverlay.car.action.START_MONITOR";
    public static final String ACTION_POKE = "com.navioverlay.car.action.POKE_MONITOR";
    private static final long RESTART_THROTTLE_MS = 15000L;
    private static final long RESTART_DELAY_MS = 5000L;
    private static final long HEARTBEAT_DELAY_MS = 90_000L;
    private static final int HEARTBEAT_REQUEST_CODE = 4110;

    private HandlerThread workerThread;
    private Handler workerHandler;
    private AppStateDetector appDetector;
    private MusicStateDetector musicDetector;
    private StateEngine engine;
    private long tickCount = 0L;
    private static long lastRestartRequestAt = 0L;
    private String lastNotificationText = "";
    private int lastMusicVolume = -1;
    private long lastHeartbeatScheduledAt = 0L;

    private final Runnable loop = new Runnable() {
        @Override public void run() {
            int next = 2500;
            try { next = tick(); } catch (Throwable t) { Logx.e("ENGINE tick failed", t); next = 1500; }
            if (workerHandler != null) {
                workerHandler.postDelayed(this, Math.max(350, Math.min(5000, next)));
            }
        }
    };

    public static void start(Context c){
        try{
            Intent i = new Intent(c, MonitorService.class);
            i.setAction(ACTION_START);
            if(Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
        }catch(Throwable t){ Logx.e("start monitor failed",t); }
    }

    public static void poke(Context c){
        try{
            Intent i = new Intent(c, MonitorService.class);
            i.setAction(ACTION_POKE);
            if(Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
        }catch(Throwable t){ Logx.e("poke monitor failed",t); }
    }

    public static void stop(Context c){
        try{
            cancelHeartbeat(c.getApplicationContext());
            c.stopService(new Intent(c, MonitorService.class));
        }catch(Throwable t){ Logx.e("stop monitor failed",t); }
    }

    @Override public void onCreate(){
        super.onCreate();
        createChannel();
        startAsForeground("Движок активен · ожидание");
        workerThread = new HandlerThread("NaviOverlayMonitor");
        workerThread.start();
        workerHandler = new Handler(workerThread.getLooper());
        appDetector = new AppStateDetector(this);
        musicDetector = new MusicStateDetector(this);
        engine = new StateEngine(this);
        workerHandler.removeCallbacksAndMessages(null);
        workerHandler.post(loop);
        scheduleHeartbeat(this, HEARTBEAT_DELAY_MS);
        Logx.d("EngineService/MonitorService created");
    }

    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(!new Prefs(this).enabled()) {
            stopLoop();
            cancelHeartbeat(this);
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (appDetector == null) appDetector = new AppStateDetector(this);
        if (musicDetector == null) musicDetector = new MusicStateDetector(this);
        if (engine == null) engine = new StateEngine(this);
        ensureWorkerHandler();
        String action = intent == null ? "" : intent.getAction();
        workerHandler.removeCallbacks(loop);
        if (ACTION_POKE.equals(action)) {
            workerHandler.post(loop);
        } else {
            workerHandler.post(loop);
        }
        scheduleHeartbeat(this, HEARTBEAT_DELAY_MS);
        return START_STICKY;
    }

    @Override public void onTaskRemoved(Intent rootIntent) {
        if (new Prefs(this).enabled()) scheduleRestart();
        super.onTaskRemoved(rootIntent);
    }

    @Override public void onDestroy(){
        stopLoop();
        cancelHeartbeat(this);
        if(new Prefs(this).enabled()) scheduleRestart();
        if (workerThread != null) {
            workerThread.quitSafely();
            workerThread = null;
        }
        workerHandler = null;
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent){ return null; }

    private int tick(){
        tickCount++;
        Prefs p = new Prefs(this);
        if(!p.enabled()) {
            stopSelf();
            return 2500;
        }
        checkVolumeReaction(p);
        AppStateDetector.Result appState = appDetector.read();
        TrackSnapshot track = musicDetector.read();
        updateForegroundNotification(appState, track);
        int next = engine.tick(appState, track);
        scheduleHeartbeat(this, HEARTBEAT_DELAY_MS);
        if (tickCount % 20 == 0) Logx.d("ENGINE heartbeat next="+next+" nav="+appState.navigatorVisible+" fg="+appState.foregroundPackage);
        return next;
    }

    private void checkVolumeReaction(Prefs p) {
        try {
            AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
            if (am == null) return;
            int v = am.getStreamVolume(AudioManager.STREAM_MUSIC);
            if (lastMusicVolume >= 0 && v != lastMusicVolume) {
                ForegroundState.markTransientUi(2800L);
                if (p.featureVolumeDim()) com.navioverlay.car.overlay.TrackOverlayManager.dimTemporarily(this);
            }
            lastMusicVolume = v;
        } catch (Throwable ignored) {}
    }

    private void updateForegroundNotification(AppStateDetector.Result appState, TrackSnapshot track) {
        boolean nav = appState != null && appState.navigatorVisible;
        boolean music = track != null && track.playing && track.hasText();
        String text;
        if (nav && music) text = "Движок активен · навигатор и музыка запущены";
        else if (nav) text = "Движок активен · навигатор запущен";
        else if (music) text = "Движок активен · музыка запущена";
        else text = "Движок активен · ожидание";
        if (text.equals(lastNotificationText)) return;
        lastNotificationText = text;
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(Constants.FOREGROUND_ID, notif(text));
        } catch (Throwable ignored) {}
    }

    private void scheduleRestart(){
        try{
            if (!new Prefs(this).enabled()) return;
            long now = System.currentTimeMillis();
            if (now - lastRestartRequestAt < RESTART_THROTTLE_MS) return;
            lastRestartRequestAt = now;
            Intent restart = new Intent(this, com.navioverlay.car.receivers.RestartReceiver.class);
            PendingIntent pi = PendingIntent.getBroadcast(this, 4108, restart, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            AlarmManager am = (AlarmManager)getSystemService(ALARM_SERVICE);
            if (am != null) am.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + RESTART_DELAY_MS, pi);
        }catch(Throwable ignored){}
    }

    private void createChannel(){
        if(Build.VERSION.SDK_INT>=26){
            NotificationChannel ch=new NotificationChannel(Constants.CHANNEL_ID,"Navi Overlay",NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Постоянный движок мониторинга навигатора и музыки"); ch.setShowBadge(false);
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
        }
    }

    private void startAsForeground(String text){
        Notification n = notif(text);
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(Constants.FOREGROUND_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(Constants.FOREGROUND_ID, n);
        }
    }

    private Notification notif(String text){
        Intent i=new Intent(this, com.navioverlay.car.ui.MainActivity.class);
        PendingIntent pi=PendingIntent.getActivity(this,0,i,PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,Constants.CHANNEL_ID):new Notification.Builder(this);
        return b.setSmallIcon(com.navioverlay.car.R.drawable.ic_stat_overlay).setContentTitle("Navi Overlay").setContentText(text).setContentIntent(pi).setOngoing(true).setOnlyAlertOnce(true).build();
    }

    private void ensureWorkerHandler() {
        if (workerThread == null) {
            workerThread = new HandlerThread("NaviOverlayMonitor");
            workerThread.start();
        }
        if (workerHandler == null) {
            workerHandler = new Handler(workerThread.getLooper());
        }
    }

    private void stopLoop() {
        if (workerHandler != null) {
            workerHandler.removeCallbacksAndMessages(null);
        }
    }

    private static PendingIntent heartbeatPendingIntent(Context c) {
        Intent i = new Intent(c, MonitorService.class);
        i.setAction(ACTION_POKE);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        if (Build.VERSION.SDK_INT >= 26) {
            return PendingIntent.getForegroundService(c, HEARTBEAT_REQUEST_CODE, i, flags);
        }
        return PendingIntent.getService(c, HEARTBEAT_REQUEST_CODE, i, flags);
    }

    private void scheduleHeartbeat(Context c, long delayMs) {
        if (!new Prefs(c).enabled()) return;
        long targetAt = System.currentTimeMillis() + Math.max(20_000L, delayMs);
        if (Math.abs(targetAt - lastHeartbeatScheduledAt) < 10_000L) return;
        lastHeartbeatScheduledAt = targetAt;
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(ALARM_SERVICE);
            if (am == null) return;
            PendingIntent pi = heartbeatPendingIntent(c.getApplicationContext());
            if (Build.VERSION.SDK_INT >= 23) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, targetAt, pi);
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, targetAt, pi);
            }
        } catch (Throwable t) {
            Logx.e("schedule heartbeat failed", t);
        }
    }

    private static void cancelHeartbeat(Context c) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(ALARM_SERVICE);
            if (am == null) return;
            am.cancel(heartbeatPendingIntent(c));
        } catch (Throwable ignored) {}
    }
}
