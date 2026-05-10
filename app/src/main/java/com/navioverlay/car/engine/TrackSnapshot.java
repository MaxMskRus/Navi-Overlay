package com.navioverlay.car.engine;

import android.graphics.Bitmap;

public final class TrackSnapshot {
    public static final String SOURCE_NONE = "none";
    public static final String SOURCE_MEDIA_SESSION = "mediasession";
    public static final String SOURCE_NOTIFICATION = "notification";
    public static final String SOURCE_LAST_GOOD = "last_good";

    public final String sourcePackage;
    public final String title;
    public final String artist;
    public final boolean playing;
    public final long readAt;
    public final String sourceType;
    public final Bitmap albumArt;
    public final long durationMs;
    public final long positionMs;

    public TrackSnapshot(String sourcePackage, String title, String artist, boolean playing) {
        this(sourcePackage, title, artist, playing, SOURCE_NONE, null, -1L, -1L);
    }

    public TrackSnapshot(String sourcePackage, String title, String artist, boolean playing, String sourceType) {
        this(sourcePackage, title, artist, playing, sourceType, null, -1L, -1L);
    }

    public TrackSnapshot(String sourcePackage, String title, String artist, boolean playing, String sourceType, Bitmap albumArt) {
        this(sourcePackage, title, artist, playing, sourceType, albumArt, -1L, -1L);
    }

    public TrackSnapshot(String sourcePackage, String title, String artist, boolean playing, String sourceType, Bitmap albumArt, long durationMs, long positionMs) {
        this.sourcePackage = clean(sourcePackage);
        this.title = clean(title);
        this.artist = clean(artist);
        this.playing = playing;
        this.sourceType = clean(sourceType).isEmpty() ? SOURCE_NONE : clean(sourceType);
        this.albumArt = albumArt;
        this.durationMs = Math.max(-1L, durationMs);
        this.positionMs = Math.max(-1L, positionMs);
        this.readAt = System.currentTimeMillis();
    }

    public boolean hasText() { return !title.isEmpty() || !artist.isEmpty(); }
    public boolean hasAlbumArt() { return albumArt != null; }
    public boolean hasSeekRange() { return durationMs > 1000L && positionMs >= 0L && positionMs <= durationMs + 1000L; }
    public String key() { return sourcePackage + "|" + title + "|" + artist; }

    public static TrackSnapshot empty() { return new TrackSnapshot("", "", "", false, SOURCE_NONE); }

    public static String clean(String s) {
        if (s == null) return "";
        s = s.replace('\n', ' ').replace('\r', ' ').trim();
        while (s.contains("  ")) s = s.replace("  ", " ");
        if (s.equalsIgnoreCase("null") || s.equals("—") || s.equals("-")) return "";
        return s;
    }

    @Override public String toString() {
        return "TrackSnapshot{" + sourcePackage + ", source=" + sourceType + ", playing=" + playing + ", " + artist + " - " + title + "}";
    }
}
