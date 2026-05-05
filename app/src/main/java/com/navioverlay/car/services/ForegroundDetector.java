package com.navioverlay.car.services;

import android.annotation.SuppressLint;
import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.os.Build;
import android.os.Process;
import android.provider.Settings;
import android.text.TextUtils;
import com.navioverlay.car.core.Prefs;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ForegroundDetector { private ForegroundDetector(){}
    @SuppressLint("WrongConstant")
    public static boolean hasUsageAccess(Context c){
        if (hasUsageAccessByAppOps(c)) return true;
        return hasUsageAccessByProbe(c);
    }
    public static boolean isAccessibilityEnabled(Context c){ String enabled= Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES); return enabled!=null && enabled.toLowerCase(Locale.ROOT).contains(c.getPackageName().toLowerCase(Locale.ROOT)+"/"); }
    public static String currentByUsage(Context c){ if(!hasUsageAccess(c))return ""; try{long end=System.currentTimeMillis(),start=end-20000; UsageStatsManager u=(UsageStatsManager)c.getSystemService(Context.USAGE_STATS_SERVICE); UsageEvents ev=u.queryEvents(start,end); UsageEvents.Event e=new UsageEvents.Event(); String last=""; while(ev.hasNextEvent()){ev.getNextEvent(e); int t=e.getEventType(); if(t==UsageEvents.Event.MOVE_TO_FOREGROUND || (Build.VERSION.SDK_INT>=29 && t==UsageEvents.Event.ACTIVITY_RESUMED)) last=e.getPackageName();} return last==null?"":last;}catch(Exception e){return "";} }

    @SuppressLint("WrongConstant")
    private static boolean hasUsageAccessByAppOps(Context c){
        try{
            AppOpsManager a=(AppOpsManager)c.getSystemService(Context.APP_OPS_SERVICE);
            if(a==null) return false;
            int m=a.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.getPackageName());
            return m==AppOpsManager.MODE_ALLOWED;
        }catch(Exception e){return false;}
    }

    private static boolean hasUsageAccessByProbe(Context c){
        try{
            UsageStatsManager u=(UsageStatsManager)c.getSystemService(Context.USAGE_STATS_SERVICE);
            if(u==null) return false;
            long end=System.currentTimeMillis();
            long start=end-60_000L;

            UsageEvents ev=u.queryEvents(start,end);
            if(ev!=null){
                UsageEvents.Event e=new UsageEvents.Event();
                while(ev.hasNextEvent()){
                    ev.getNextEvent(e);
                    String pkg=e.getPackageName();
                    if(!TextUtils.isEmpty(pkg)) return true;
                }
            }

            List<UsageStats> stats=u.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end);
            if(stats!=null){
                for(UsageStats stat: stats){
                    if(stat!=null && !TextUtils.isEmpty(stat.getPackageName())) return true;
                }
            }
            return false;
        }catch(Exception e){return false;}
    }

    /**
     * true только когда выбранное приложение навигации реально является текущим экраном.
     * Шторка/системные окна считаются временным перекрытием: пока они открыты — не показываем,
     * после закрытия Accessibility polling восстановит текущий nav-пакет.
     */
    public static boolean shouldShow(Context c){ Prefs p=new Prefs(c); if(!p.showOnlyWithTrigger()) return true; Set<String> triggers=p.triggers(); if(triggers.isEmpty()) return true;
        String fg=ForegroundState.get();
        if(!TextUtils.isEmpty(fg) && ForegroundState.ageMs()<120000 && triggers.contains(fg)) return true;
        if(ForegroundState.isTransientSystemWindow()) return false;
        String u=currentByUsage(c);
        if(!TextUtils.isEmpty(u)){
            if(triggers.contains(u)) return true;
            if(!ForegroundState.isTransientPackage(u)) return false;
        }
        // запасной вариант для китайских ГУ/прошивок: если последним нормальным приложением был навигатор
        // и после него не было другого обычного приложения, считаем, что пользователь вернулся в навигатор.
        return !TextUtils.isEmpty(ForegroundState.lastTrigger()) && ForegroundState.lastTriggerAgeMs()<10*60*1000 && TextUtils.isEmpty(u);
    }
    public static String debugCurrent(Context c){ String a=ForegroundState.get(); String u=currentByUsage(c); return "Accessibility: "+(a.isEmpty()?"нет данных":a)+" / UsageStats: "+(u.isEmpty()?"нет данных":u)+" / LastNav: "+(ForegroundState.lastTrigger().isEmpty()?"нет":ForegroundState.lastTrigger()); }
}
