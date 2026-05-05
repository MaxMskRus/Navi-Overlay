package com.navioverlay.car.core;

import android.util.Log;
import com.navioverlay.car.BuildConfig;

public final class Logx {
    private Logx() {}
    public static final String TAG = "NaviOverlay";

    public static void d(String message) {
        if (BuildConfig.DEBUG) Log.d(TAG, message);
    }

    public static void e(String message, Throwable throwable) {
        if (BuildConfig.DEBUG) Log.e(TAG, message, throwable);
    }
}
