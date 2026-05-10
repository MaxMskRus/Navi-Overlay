package com.navioverlay.car.core;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Arrays;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class Prefs {
    private static final String FILE = "navioverlay_prefs_v4";
    private static final String KEY_RECENT_TRACKS = "recent_tracks_json";
    private static final int MAX_RECENT_TRACKS = 30;
    public static final int TEXT_ALIGN_LEFT = 0;
    public static final int TEXT_ALIGN_CENTER = 1;
    public static final int CONTROLS_SIZE_SMALL = 0;
    public static final int CONTROLS_SIZE_MEDIUM = 1;
    public static final int CONTROLS_SIZE_LARGE = 2;
    public static final int CONTROLS_SHAPE_ROUND = 0;
    public static final int CONTROLS_SHAPE_RECT = 1;
    public static final int CONTROLS_SHAPE_SQUARE = 2;
    public static final int CONTROLS_SHAPE_BORDERLESS = 3;
    public static final int CONTROLS_SPACING_COMPACT = 0;
    public static final int CONTROLS_SPACING_NORMAL = 1;
    public static final int CONTROLS_SPACING_WIDE = 2;
    public static final int PAUSE_BEHAVIOR_KEEP = 0;
    public static final int PAUSE_BEHAVIOR_HIDE = 1;
    public static final int PAUSE_BEHAVIOR_SHORT = 2;
    private final SharedPreferences sp;

    public Prefs(Context c) {
        sp = c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
        ensureDefaults();
    }

    private void ensureDefaults() {
        if (!sp.contains("inited")) {
            sp.edit()
                    .putBoolean("inited", true)
                    .putBoolean("enabled", false)
                    .putBoolean("show_only_with_trigger", true)
                    .putStringSet("triggers", new HashSet<>(Arrays.asList(Constants.DEFAULT_TRIGGER_PACKAGES)))
                    .putStringSet("players", new HashSet<>(Arrays.asList(Constants.DEFAULT_PLAYER_PACKAGES)))
                    .putStringSet("conflict_apps", new HashSet<String>())
                    .putInt("text_color", 0xFFFFFFFF)
                    .putInt("artist_color", 0xFFFFFFFF)
                    .putInt("title_color", 0xFFFFFFFF)
                    .putInt("bg_color", 0xFF111827)
                    .putInt("accent_color", 0xFF00D5FF)
                    .putInt("border_color", 0x66FFFFFF)
                    .putInt("border_width", 1)
                    .putInt("controls_border_color", 0xFF00D5FF)
                    .putInt("seek_bar_color", 0xFF00D5FF)
                    .putInt("seek_thumb_color", 0xFFFFFFFF)
                    .putInt("window_alpha", 88)
                    .putInt("text_size", 22)
                    .putBoolean("text_bold", true)
                    .putInt("text_align", TEXT_ALIGN_CENTER)
                    .putInt("text_font", 0)
                    .putInt("display_ms", 5000)
                    .putBoolean("display_while_playing", false)
                    .putInt("position", 1)
                    .putBoolean("custom_position", false)
                    .putInt("overlay_x", 0)
                    .putInt("overlay_y", 0)
                    .putInt("corner", 18)
                    .putInt("padding_x", 20)
                    .putInt("padding_y", 14)
                    .putInt("nav_grace_ms", 30000)
                    .putBoolean("feature_controls", false)
                    .putBoolean("feature_swipe_tracks", false)
                    .putBoolean("feature_snap", false)
                    .putBoolean("feature_volume_dim", false)
                    .putBoolean("feature_floating", false)
                    .putBoolean("feature_album_art", false)
                    .putBoolean("feature_fixed_window", false)
                    .putBoolean("feature_hide_with_navigation", false)
                    .putBoolean("feature_soft_recovery_system_windows", false)
                    .putBoolean("feature_seek_bar", false)
                    .putInt("fixed_window_width", 0)
                    .putInt("controls_size", CONTROLS_SIZE_MEDIUM)
                    .putInt("controls_shape", CONTROLS_SHAPE_ROUND)
                    .putInt("controls_spacing", CONTROLS_SPACING_NORMAL)
                    .putInt("pause_behavior", PAUSE_BEHAVIOR_KEEP)
                    .putString(KEY_RECENT_TRACKS, "[]")
                    .putInt("design_preset", 0)
                    .putString("last_trigger_package", "")
                    .putLong("last_trigger_at", 0L)
                    .putBoolean("english_ui", false)
                    .apply();
        }
    }

    public boolean enabled() { return sp.getBoolean("enabled", false); }
    public void setEnabled(boolean v) { sp.edit().putBoolean("enabled", v).apply(); }

    public boolean showOnlyWithTrigger() { return sp.getBoolean("show_only_with_trigger", true); }
    public void setShowOnlyWithTrigger(boolean v) { sp.edit().putBoolean("show_only_with_trigger", v).apply(); }

    public Set<String> triggers() { return new HashSet<>(sp.getStringSet("triggers", new HashSet<String>())); }
    public void setTriggers(Set<String> s) { sp.edit().putStringSet("triggers", new HashSet<>(s)).apply(); }

    public Set<String> players() { return new HashSet<>(sp.getStringSet("players", new HashSet<String>())); }
    public void setPlayers(Set<String> s) { sp.edit().putStringSet("players", new HashSet<>(s)).apply(); }
    public Set<String> conflictApps() { return new HashSet<>(sp.getStringSet("conflict_apps", new HashSet<String>())); }
    public void setConflictApps(Set<String> s) { sp.edit().putStringSet("conflict_apps", new HashSet<>(s)).apply(); }

    public boolean isTrigger(String p) { return p != null && triggers().contains(p); }
    public boolean isPlayer(String p) { return p != null && players().contains(p); }
    public boolean isConflictApp(String p) { return p != null && conflictApps().contains(p); }

    public int textColor() { return sp.getInt("text_color", 0xFFFFFFFF); }
    public void setTextColor(int v) { sp.edit().putInt("text_color", v).apply(); }

    public int artistColor() { return sp.getInt("artist_color", textColor()); }
    public void setArtistColor(int v) { sp.edit().putInt("artist_color", v).apply(); }

    public int titleColor() { return sp.getInt("title_color", textColor()); }
    public void setTitleColor(int v) { sp.edit().putInt("title_color", v).apply(); }

    public int bgColor() { return sp.getInt("bg_color", 0xFF111827); }
    public void setBgColor(int v) { sp.edit().putInt("bg_color", v).apply(); }

    public int accentColor() { return sp.getInt("accent_color", 0xFF00D5FF); }
    public void setAccentColor(int v) { sp.edit().putInt("accent_color", v).apply(); }

    public int borderColor() { return sp.getInt("border_color", 0x66FFFFFF); }
    public void setBorderColor(int v) { sp.edit().putInt("border_color", v).apply(); }

    public int borderWidth() { return sp.getInt("border_width", 1); }
    public void setBorderWidth(int v) { sp.edit().putInt("border_width", Math.max(0, Math.min(12, v))).apply(); }

    public int controlsBorderColor() { return sp.getInt("controls_border_color", 0xFF00D5FF); }
    public void setControlsBorderColor(int v) { sp.edit().putInt("controls_border_color", v).apply(); }
    public int seekBarColor() { return sp.getInt("seek_bar_color", 0xFF00D5FF); }
    public void setSeekBarColor(int v) { sp.edit().putInt("seek_bar_color", v).apply(); }
    public int seekThumbColor() { return sp.getInt("seek_thumb_color", 0xFFFFFFFF); }
    public void setSeekThumbColor(int v) { sp.edit().putInt("seek_thumb_color", v).apply(); }

    public int windowAlpha() { return sp.getInt("window_alpha", 88); }
    public void setWindowAlpha(int v) { sp.edit().putInt("window_alpha", v).apply(); }

    public int textSize() { return sp.getInt("text_size", 22); }
    public void setTextSize(int v) { sp.edit().putInt("text_size", v).apply(); }

    public boolean textBold() { return sp.getBoolean("text_bold", true); }
    public void setTextBold(boolean v) { sp.edit().putBoolean("text_bold", v).apply(); }

    public boolean textShadow() { return sp.getBoolean("text_shadow", true); }
    public void setTextShadow(boolean v) { sp.edit().putBoolean("text_shadow", v).apply(); }
    public int textAlign() { return sp.getInt("text_align", TEXT_ALIGN_CENTER); }
    public void setTextAlign(int v) { sp.edit().putInt("text_align", v == TEXT_ALIGN_LEFT ? TEXT_ALIGN_LEFT : TEXT_ALIGN_CENTER).apply(); }

    public int textFont() { return sp.getInt("text_font", 0); }
    public void setTextFont(int v) { sp.edit().putInt("text_font", Math.max(0, v)).apply(); }

    public int displayMs() { return sp.getInt("display_ms", 5000); }
    public void setDisplayMs(int v) { sp.edit().putInt("display_ms", v).apply(); }

    public boolean displayWhilePlaying() { return sp.getBoolean("display_while_playing", false); }
    public void setDisplayWhilePlaying(boolean v) { sp.edit().putBoolean("display_while_playing", v).apply(); }

    public int position() { return sp.getInt("position", 1); }
    public void setPosition(int v) { sp.edit().putInt("position", v).putBoolean("custom_position", false).apply(); }

    public boolean customPosition() { return sp.getBoolean("custom_position", false); }
    public int overlayX() { return sp.getInt("overlay_x", 0); }
    public int overlayY() { return sp.getInt("overlay_y", 0); }
    public void setOverlayPosition(int x, int y) { sp.edit().putInt("overlay_x", x).putInt("overlay_y", y).putBoolean("custom_position", true).apply(); }
    public void resetOverlayPositionToPreset() { sp.edit().putBoolean("custom_position", false).apply(); }

    public int corner() { return sp.getInt("corner", 18); }
    public void setCorner(int v) { sp.edit().putInt("corner", v).apply(); }

    public int paddingX() { return sp.getInt("padding_x", 20); }
    public void setPaddingX(int v) { sp.edit().putInt("padding_x", v).apply(); }

    public int paddingY() { return sp.getInt("padding_y", 14); }
    public void setPaddingY(int v) { sp.edit().putInt("padding_y", v).apply(); }

    public int navGraceMs() { return sp.getInt("nav_grace_ms", 30000); }
    public void setNavGraceMs(int v) { sp.edit().putInt("nav_grace_ms", v).apply(); }

    public boolean featureControls() { return sp.getBoolean("feature_controls", false); }
    public void setFeatureControls(boolean v) { sp.edit().putBoolean("feature_controls", v).apply(); }

    public boolean featureSwipeTracks() { return sp.getBoolean("feature_swipe_tracks", false); }
    public void setFeatureSwipeTracks(boolean v) { sp.edit().putBoolean("feature_swipe_tracks", v).apply(); }

    public boolean featureSnap() { return sp.getBoolean("feature_snap", false); }
    public void setFeatureSnap(boolean v) { sp.edit().putBoolean("feature_snap", v).apply(); }

    public boolean featureVolumeDim() { return sp.getBoolean("feature_volume_dim", false); }
    public void setFeatureVolumeDim(boolean v) { sp.edit().putBoolean("feature_volume_dim", v).apply(); }

    public boolean featureFloating() { return sp.getBoolean("feature_floating", false); }
    public void setFeatureFloating(boolean v) { sp.edit().putBoolean("feature_floating", v).apply(); }

    public boolean featureAlbumArt() { return sp.getBoolean("feature_album_art", false); }
    public void setFeatureAlbumArt(boolean v) { sp.edit().putBoolean("feature_album_art", v).apply(); }

    public boolean featureFixedWindow() { return sp.getBoolean("feature_fixed_window", false); }
    public void setFeatureFixedWindow(boolean v) { sp.edit().putBoolean("feature_fixed_window", v).apply(); }

    public boolean featureHideWithNavigation() { return sp.getBoolean("feature_hide_with_navigation", false); }
    public void setFeatureHideWithNavigation(boolean v) { sp.edit().putBoolean("feature_hide_with_navigation", v).apply(); }

    public boolean featureSoftRecoverySystemWindows() { return sp.getBoolean("feature_soft_recovery_system_windows", false); }
    public void setFeatureSoftRecoverySystemWindows(boolean v) { sp.edit().putBoolean("feature_soft_recovery_system_windows", v).apply(); }
    public boolean featureSeekBar() { return sp.getBoolean("feature_seek_bar", false); }
    public void setFeatureSeekBar(boolean v) { sp.edit().putBoolean("feature_seek_bar", v).apply(); }

    public int fixedWindowWidth() { return sp.getInt("fixed_window_width", 0); }
    public void setFixedWindowWidth(int v) { sp.edit().putInt("fixed_window_width", Math.max(0, v)).apply(); }
    public int controlsSize() { return clamp(sp.getInt("controls_size", CONTROLS_SIZE_MEDIUM), CONTROLS_SIZE_SMALL, CONTROLS_SIZE_LARGE); }
    public void setControlsSize(int v) { sp.edit().putInt("controls_size", clamp(v, CONTROLS_SIZE_SMALL, CONTROLS_SIZE_LARGE)).apply(); }
    public int controlsShape() { return clamp(sp.getInt("controls_shape", CONTROLS_SHAPE_ROUND), CONTROLS_SHAPE_ROUND, CONTROLS_SHAPE_BORDERLESS); }
    public void setControlsShape(int v) { sp.edit().putInt("controls_shape", clamp(v, CONTROLS_SHAPE_ROUND, CONTROLS_SHAPE_BORDERLESS)).apply(); }
    public int controlsSpacing() { return clamp(sp.getInt("controls_spacing", CONTROLS_SPACING_NORMAL), CONTROLS_SPACING_COMPACT, CONTROLS_SPACING_WIDE); }
    public void setControlsSpacing(int v) { sp.edit().putInt("controls_spacing", clamp(v, CONTROLS_SPACING_COMPACT, CONTROLS_SPACING_WIDE)).apply(); }
    public int pauseBehavior() { return clamp(sp.getInt("pause_behavior", PAUSE_BEHAVIOR_KEEP), PAUSE_BEHAVIOR_KEEP, PAUSE_BEHAVIOR_SHORT); }
    public void setPauseBehavior(int v) { sp.edit().putInt("pause_behavior", clamp(v, PAUSE_BEHAVIOR_KEEP, PAUSE_BEHAVIOR_SHORT)).apply(); }

    public int designPreset() { return sp.getInt("design_preset", 0); }
    public void setDesignPreset(int v) { sp.edit().putInt("design_preset", v).apply(); }

    public String lastTriggerPackage() { return sp.getString("last_trigger_package", ""); }
    public long lastTriggerAt() { return sp.getLong("last_trigger_at", 0L); }
    public void setLastTrigger(String pkg) {
        sp.edit()
                .putString("last_trigger_package", pkg == null ? "" : pkg)
                .putLong("last_trigger_at", System.currentTimeMillis())
                .apply();
    }

    public boolean englishUi() { return sp.getBoolean("english_ui", false); }
    public void setEnglishUi(boolean v) { sp.edit().putBoolean("english_ui", v).apply(); }

    public List<RecentTrack> recentTracks() {
        ArrayList<RecentTrack> out = new ArrayList<>();
        String raw = sp.getString(KEY_RECENT_TRACKS, "[]");
        try {
            JSONArray arr = new JSONArray(raw == null ? "[]" : raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject item = arr.optJSONObject(i);
                if (item == null) continue;
                String artist = item.optString("artist", "");
                String title = item.optString("title", "");
                if (title.isEmpty() && artist.isEmpty()) continue;
                out.add(new RecentTrack(artist, title, item.optLong("at", 0L)));
            }
        } catch (Throwable ignored) {}
        return out;
    }

    public void pushRecentTrack(String artist, String title) {
        String safeArtist = artist == null ? "" : artist.trim();
        String safeTitle = title == null ? "" : title.trim();
        if (safeArtist.isEmpty() && safeTitle.isEmpty()) return;
        try {
            List<RecentTrack> items = recentTracks();
            if (!items.isEmpty()) {
                RecentTrack last = items.get(items.size() - 1);
                if (last.title.equals(safeTitle) && last.artist.equals(safeArtist)) return;
            }
            items.add(new RecentTrack(safeArtist, safeTitle, System.currentTimeMillis()));
            while (items.size() > MAX_RECENT_TRACKS) items.remove(0);
            JSONArray arr = new JSONArray();
            for (RecentTrack item : items) {
                JSONObject obj = new JSONObject();
                obj.put("artist", item.artist);
                obj.put("title", item.title);
                obj.put("at", item.at);
                arr.put(obj);
            }
            sp.edit().putString(KEY_RECENT_TRACKS, arr.toString()).apply();
        } catch (Throwable ignored) {}
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    public static final class RecentTrack {
        public final String artist;
        public final String title;
        public final long at;

        public RecentTrack(String artist, String title, long at) {
            this.artist = artist == null ? "" : artist;
            this.title = title == null ? "" : title;
            this.at = at;
        }

        public String display() {
            if (!artist.isEmpty() && !title.isEmpty()) return artist + " - " + title;
            if (!title.isEmpty()) return title;
            return artist;
        }
    }
}
