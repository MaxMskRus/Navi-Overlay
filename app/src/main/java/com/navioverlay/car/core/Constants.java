package com.navioverlay.car.core;

public final class Constants {
    private Constants() {}
    public static final String CHANNEL_ID = "navioverlay_monitor";
    public static final int FOREGROUND_ID = 4107;
    public static final String[] DEFAULT_TRIGGER_PACKAGES = new String[]{
            "ru.yandex.yandexnavi", "ru.yandex.yandexmaps", "com.yandex.navigator",
            "com.google.android.apps.maps", "ru.dublgis.dgismobile", "com.waze"
    };
    public static final String[] DEFAULT_PLAYER_PACKAGES = new String[]{
            "ru.yandex.music", "com.yandex.music", "com.google.android.apps.youtube.music",
            "com.spotify.music", "com.maxmpz.audioplayer", "com.vkontakte.android",
            "com.musicolet.android", "com.foobar2000.foobar2000"
    };
}
