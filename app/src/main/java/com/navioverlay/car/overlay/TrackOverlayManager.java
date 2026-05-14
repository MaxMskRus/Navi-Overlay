package com.navioverlay.car.overlay;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.animation.ObjectAnimator;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.WindowMetrics;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.view.animation.LinearInterpolator;
import com.navioverlay.car.core.OverlayFonts;
import com.navioverlay.car.core.Prefs;
import com.navioverlay.car.core.Ui;
import com.navioverlay.car.engine.MusicStateDetector;
import com.navioverlay.car.engine.TrackSnapshot;

/** Overlay с быстрым обновлением текста, drag, double-tap reset и доп. функциями. */
public final class TrackOverlayManager {
    private TrackOverlayManager(){}

    private static LinearLayout overlayView;
    private static LinearLayout panelView;
    private static LinearLayout contentRow;
    private static LinearLayout specialFixedRow;
    private static LinearLayout specialRightColumn;
    private static ImageView albumArtView;
    private static TextView lineView;
    private static LinearLayout seekContainer;
    private static SeekBar seekBarView;
    private static LinearLayout controlsRow;
    private static WindowManager wm;
    private static WindowManager.LayoutParams currentParams;
    private static boolean hostAttached = false;
    private static boolean contentSoftHidden = false;
    private static String lastTitle="", lastArtist="", lastRenderedText="";
    private static Bitmap lastAlbumArt;
    private static boolean controlsVisible=false;
    private static int lastDesign=-1;
    private static final Handler h=new Handler(Looper.getMainLooper());
    private static final Runnable hideTask = TrackOverlayManager::hideOnMain;
    private static final Runnable testHideTask = TrackOverlayManager::hideTestOnMain;
    private static final Runnable undimTask = TrackOverlayManager::restoreAlphaOnMain;
    private static final Runnable hideEditModeTask = TrackOverlayManager::hideEditModeOnMain;
    private static final Runnable collapseHiddenHostTask = TrackOverlayManager::collapseHiddenHostOnMain;
    private static final Runnable recoveryTask = TrackOverlayManager::attemptDeferredRecoveryOnMain;
    private static final Runnable seekProgressTask = TrackOverlayManager::updateSeekProgressOnMain;
    private static final Runnable albumArtSingleTapTask = TrackOverlayManager::performAlbumArtSingleTapOnMain;
    private static final Runnable albumArtRefreshTask = TrackOverlayManager::refreshAlbumArtOnMain;
    private static ObjectAnimator marqueeAnimator;
    private static int lastAppliedPosition = -1;
    private static boolean lastAppliedCustomPosition = false;
    private static int lastAppliedOffsetX = -1;
    private static int lastAppliedOffsetY = -1;
    private static boolean fixedWindowEditMode = false;
    private static boolean resizingFixedWindow = false;
    private static float pinchStartSpanX = 0f;
    private static int pinchStartWidth = 0;

    private static int downX, downY;
    private static int downGravity;
    private static float touchX, touchY;
    private static boolean dragging;
    private static boolean dragArmed;
    private static boolean longPressTriggered;
    private static Runnable pendingLongPressTask;
    private static long lastTapUpMs=0L;
    private static long lastAlbumArtTapUpMs=0L;
    private static float albumArtDownX=0f;
    private static float albumArtDownY=0f;
    private static final long DOUBLE_TAP_MS=320L;
    private static final int TAP_SLOP_PX=12;
    private static final long EDIT_MODE_TIMEOUT_MS = 4000L;
    private static final long HIDDEN_HOST_COLLAPSE_DELAY_MS = 2000L;
    private static final int TEST_DISPLAY_MS = 5000;
    private static long testModeUntilAt = 0L;
    private static long volumeDimUntilAt = 0L;
    private static long sessionVisibleUntilAt = 0L;
    private static boolean sessionContinuous = false;
    private static boolean pauseHideFrozen = false;
    private static boolean sessionRecoveryRestartRequested = false;
    private static long transientRecoveryNotBeforeAt = 0L;
    private static long lastControlledRecoveryAt = 0L;
    private static Context lastAppContext;
    private static final long CONTROLLED_RECOVERY_BACKOFF_MS = 1800L;
    private static TrackSnapshot lastTrackSnapshot = TrackSnapshot.empty();
    private static final SeekSessionState seekSession = new SeekSessionState();
    private static boolean seekTrackingActive = false;
    private static boolean seekTouchActive = false;
    private static long lastSeekProgressRefreshAt = 0L;
    private static volatile String lastSeekDebugReason = "init";
    private static Bitmap placeholderAlbumArtBitmap;
    private static int albumArtRetryCount = 0;
    private static boolean albumArtWaitingForReal = false;

    public static boolean canDraw(Context c){return Settings.canDrawOverlays(c);}
    public static String lastSeekDebugReason() { return lastSeekDebugReason == null ? "" : lastSeekDebugReason; }
    public static boolean seekSessionHasRange() { return seekSession.hasRange(); }
    public static long seekSessionDurationMs() { return seekSession.durationMs(); }
    public static long seekSessionEstimatedPositionMs() { return seekSession.estimatePositionNow(); }
    public static boolean albumArtWaitingForReal() { return albumArtWaitingForReal; }
    public static int albumArtRetryCount() { return albumArtRetryCount; }
    public static void show(Context c,String title,String artist){ showInternal(c,title,artist,false); }
    public static void showTrack(Context c, TrackSnapshot track){
        if(track==null){ hide(); return; }
        TrackSnapshot enriched = MusicStateDetector.enrichWithLivePlaybackState(c, track);
        lastTrackSnapshot = enriched;
        syncSeekSessionState(enriched);
        showInternal(c, enriched.title, enriched.artist, enriched.albumArt, false);
    }
    public static void test(Context c){
        Context app = c.getApplicationContext();
        final String ft = "Очень длинное название тестовой песни, которое должно переноситься на следующую строку";
        final String fa = "Исполнитель / Группа";
        lastTrackSnapshot = TrackSnapshot.empty();
        h.post(() -> showOrUpdateOnMain(app, ft, fa, null, true, TEST_DISPLAY_MS, true));
    }

    private static void showInternal(Context raw,String title,String artist,boolean test){
        showInternal(raw, title, artist, null, test);
    }

    private static void showInternal(Context raw,String title,String artist,Bitmap albumArt,boolean test){
        Context c=raw.getApplicationContext();
        if(!canDraw(c))return;
        String t=clean(title);
        String a=clean(artist);
        if(t.isEmpty()&&a.isEmpty()&&!test)return;
        if(t.isEmpty())t="Неизвестный трек";
        final String ft=t, fa=a;
        final Bitmap cover = albumArt;
        h.post(() -> showOrUpdateOnMain(c, ft, fa, cover, test, test ? TEST_DISPLAY_MS : -1, test));
    }

    private static String clean(String s){
        if(s==null)return "";
        s=s.replace("\n"," ").replace("\r"," ").trim();
        while(s.contains("  "))s=s.replace("  "," ");
        if(s.equalsIgnoreCase("null"))return "";
        return s;
    }

    private static String format(String title,String artist){
        if(artist!=null&&!artist.trim().isEmpty())return artist.trim()+" - "+title.trim();
        return title.trim();
    }

    private static void showOrUpdateOnMain(Context c,String title,String artist,Bitmap albumArt,boolean forceRestartTimer,int overrideHideMs,boolean testMode){
        if (isTestModeActive() && !testMode) return;
        lastAppContext = c.getApplicationContext();
        Prefs p=new Prefs(c);
        if(wm==null)wm=(WindowManager)c.getSystemService(Context.WINDOW_SERVICE);
        if(wm==null)return;
        boolean wasVisible = panelView != null && panelView.getVisibility() == LinearLayout.VISIBLE;
        String nextText = format(title,artist);
        boolean sameTextAsRendered = nextText.equals(lastRenderedText);
        boolean sameAlbumArt = OverlayRenderHelper.sameBitmap(lastAlbumArt, albumArt);

        boolean newlyCreated=false;
        if(overlayView==null || panelView==null || lineView==null || contentRow==null){
            createOverlayViews(c);
            newlyCreated=true;
        }

        boolean controlsNeedUpdate = controlsVisible != p.featureControls();
        boolean designChanged = lastDesign != p.designPreset();
        boolean positionSettingsChanged = currentParams == null
                || p.customPosition() != lastAppliedCustomPosition
                || p.position() != lastAppliedPosition
                || p.paddingX() != lastAppliedOffsetX
                || p.paddingY() != lastAppliedOffsetY;

        if (newlyCreated || currentParams == null || positionSettingsChanged) {
            currentParams = params(c, p);
            lastAppliedCustomPosition = p.customPosition();
            lastAppliedPosition = p.position();
            lastAppliedOffsetX = p.paddingX();
            lastAppliedOffsetY = p.paddingY();
        }

        String text=nextText;
        if (testMode) {
            testModeUntilAt = SystemClock.uptimeMillis() + Math.max(500, overrideHideMs);
        } else if (!isTestModeActive()) {
            testModeUntilAt = 0L;
        }
        contentSoftHidden = false;
        h.removeCallbacks(collapseHiddenHostTask);
        panelView.setVisibility(LinearLayout.VISIBLE);
        panelView.setAlpha(1f);
        applyContentDimAlpha(1f);
        setOverlayTouchThrough(false);
        setOverlayCollapsedFootprint(false);
        applyAlbumArt(c, p, albumArt);
        applyStyle(p);
        if(controlsNeedUpdate || designChanged) updateControlsVisibility(c,p);
        else if(p.featureControls()) {
            controlsRow.setVisibility(LinearLayout.VISIBLE);
            applyControlsLayout(c,p);
        } else if (controlsRow != null) {
            controlsRow.setVisibility(LinearLayout.GONE);
        }
        if(!text.equals(lastRenderedText)){
            applyTrackText(p, title, artist);
            lastRenderedText=text;
        } else {
            applyTrackText(p, title, artist);
        }
        updateSeekBarVisibility(c, p);
        applySmartTextLayout(c,p,text);
        if (p.featureSeekBar() && p.displayWhilePlaying()) {
            scheduleSeekProgressUpdates(c, true);
        }
        scheduleAlbumArtRefreshIfNeeded(c, p, albumArt);

        try{
            if(newlyCreated || !hostAttached){
                wm.addView(overlayView,currentParams);
                hostAttached = true;
            }else{
                wm.updateViewLayout(overlayView,currentParams);
            }
        }catch(Exception e){
            hardResetOverlay();
            return;
        }

        lastTitle=title;
        lastArtist=artist;
        lastAlbumArt=albumArt;
        if (p.featureSoftRecoverySystemWindows()) {
            long now = SystemClock.uptimeMillis();
            if (testMode) {
                sessionContinuous = false;
                sessionVisibleUntilAt = now + Math.max(500, overrideHideMs);
            } else if (p.displayWhilePlaying()) {
                sessionContinuous = true;
                sessionVisibleUntilAt = Long.MAX_VALUE;
            } else if (forceRestartTimer) {
                sessionContinuous = false;
                sessionVisibleUntilAt = now + Math.max(0, overrideHideMs);
            } else if (!wasVisible || !sameTextAsRendered || !sameAlbumArt) {
                sessionContinuous = false;
                sessionVisibleUntilAt = now + Math.max(0, p.displayMs());
            }
        }
        boolean pauseHoldModeActive = isPauseHoldModeActive(p);
        if (pauseHoldModeActive) {
            pauseAutoHideForPauseOnMain();
        } else if (forceRestartTimer) {
            restartHideTimerForMs(Math.max(0, overrideHideMs));
        } else if (!wasVisible || !sameTextAsRendered || !sameAlbumArt) {
            restartHideTimer(p);
        }
    }

    private static boolean isTestModeActive() {
        return testModeUntilAt > SystemClock.uptimeMillis();
    }

    private static boolean isTestModeActiveAt(long now) {
        return testModeUntilAt > now;
    }

    private static boolean shouldSessionBeVisibleAt(long now) {
        if (isTestModeActiveAt(now)) return true;
        return pauseHideFrozen || sessionContinuous || sessionVisibleUntilAt > now;
    }

    private static int remainingSessionMs(long now, Prefs prefs) {
        if (isTestModeActiveAt(now)) {
            return (int) Math.max(500L, testModeUntilAt - now);
        }
        if (pauseHideFrozen) return Math.max(1000, prefs.displayMs());
        if (sessionContinuous || prefs.displayWhilePlaying()) return -1;
        return (int) Math.max(0L, sessionVisibleUntilAt - now);
    }

    private static boolean isPauseHoldModeActive(Prefs prefs) {
        if (prefs == null || prefs.displayWhilePlaying() || prefs.pauseBehavior() != Prefs.PAUSE_BEHAVIOR_KEEP) return false;
        TrackSnapshot track = lastTrackSnapshot;
        if (track == null || !track.hasText() || track.playing) return false;
        return TrackSnapshot.SOURCE_MEDIA_SESSION.equals(track.sourceType)
                || TrackSnapshot.SOURCE_NOTIFICATION.equals(track.sourceType)
                || TrackSnapshot.SOURCE_LAST_GOOD.equals(track.sourceType);
    }

    public static void pauseAutoHideForPause() {
        h.post(TrackOverlayManager::pauseAutoHideForPauseOnMain);
    }

    public static void resumeAutoHideAfterPause(Context raw) {
        Context app = raw == null ? null : raw.getApplicationContext();
        h.post(() -> resumeAutoHideAfterPauseOnMain(app));
    }

    private static void pauseAutoHideForPauseOnMain() {
        pauseHideFrozen = true;
        h.removeCallbacks(hideTask);
        h.removeCallbacks(testHideTask);
    }

    private static void resumeAutoHideAfterPauseOnMain(Context app) {
        if (!pauseHideFrozen) return;
        pauseHideFrozen = false;
        if (app == null) app = lastAppContext;
        if (app == null) return;
        Prefs prefs = new Prefs(app);
        if (isTestModeActive()) {
            testModeUntilAt = SystemClock.uptimeMillis() + TEST_DISPLAY_MS;
            restartTestHideTimerForMs(TEST_DISPLAY_MS);
            return;
        }
        if (prefs.displayWhilePlaying()) {
            sessionContinuous = true;
            sessionVisibleUntilAt = Long.MAX_VALUE;
            h.removeCallbacks(hideTask);
            return;
        }
        int ms = Math.max(1000, prefs.displayMs());
        sessionContinuous = false;
        sessionVisibleUntilAt = SystemClock.uptimeMillis() + ms;
        restartHideTimerForMs(ms);
    }

    private static boolean isHostActuallyVisible() {
        return overlayView != null
                && panelView != null
                && hostAttached
                && overlayView.isAttachedToWindow()
                && panelView.getVisibility() == LinearLayout.VISIBLE
                && !contentSoftHidden
                && panelView.getAlpha() > 0.05f
                && (lineView == null || lineView.getAlpha() > 0.05f || TextUtils.isEmpty(lineView.getText()));
    }

    private static boolean isSessionCurrentlyPresented() {
        return overlayView != null
                && panelView != null
                && hostAttached
                && overlayView.isAttachedToWindow()
                && panelView.getVisibility() == LinearLayout.VISIBLE
                && !contentSoftHidden;
    }

    private static void restoreVisibleContentState() {
        contentSoftHidden = false;
        if (panelView != null) {
            panelView.setVisibility(LinearLayout.VISIBLE);
            panelView.setAlpha(1f);
        }
        applyContentDimAlpha(1f);
    }

    private static void createOverlayViews(Context c){
        overlayView = new LinearLayout(c);
        overlayView.setOrientation(LinearLayout.HORIZONTAL);
        overlayView.setGravity(Gravity.CENTER_VERTICAL);
        overlayView.setClipToPadding(false);
        overlayView.setClipChildren(false);

        panelView = new LinearLayout(c);
        panelView.setOrientation(LinearLayout.VERTICAL);
        panelView.setGravity(Gravity.CENTER);
        panelView.setClipToPadding(false);
        panelView.setClipChildren(false);
        panelView.setVisibility(LinearLayout.GONE);
        panelView.setOnTouchListener((v,e)->handleTouch(c,e));
        overlayView.addView(panelView, new LinearLayout.LayoutParams(-2, -2));

        contentRow = new LinearLayout(c);
        contentRow.setOrientation(LinearLayout.HORIZONTAL);
        contentRow.setGravity(Gravity.CENTER_VERTICAL);
        contentRow.setClipToPadding(false);
        contentRow.setClipChildren(false);
        panelView.addView(contentRow,new LinearLayout.LayoutParams(-1,-2));

        specialFixedRow = new LinearLayout(c);
        specialFixedRow.setOrientation(LinearLayout.HORIZONTAL);
        specialFixedRow.setGravity(Gravity.CENTER_VERTICAL);
        specialFixedRow.setClipToPadding(false);
        specialFixedRow.setClipChildren(false);

        specialRightColumn = new LinearLayout(c);
        specialRightColumn.setOrientation(LinearLayout.VERTICAL);
        specialRightColumn.setGravity(Gravity.CENTER_HORIZONTAL);
        specialRightColumn.setClipToPadding(false);
        specialRightColumn.setClipChildren(false);

        albumArtView = new ImageView(c);
        albumArtView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        albumArtView.setVisibility(ImageView.GONE);
        albumArtView.setOnTouchListener((v, event) -> handleAlbumArtTouch(c, event));
        contentRow.addView(albumArtView, new LinearLayout.LayoutParams(0, 0));

        lineView=new TextView(c);
        lineView.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        lineView.getPaint().setFakeBoldText(true);
        lineView.setGravity(Gravity.CENTER);
        lineView.setHorizontallyScrolling(false);
        contentRow.addView(lineView,new LinearLayout.LayoutParams(-2,-2));

        seekContainer = new LinearLayout(c);
        seekContainer.setOrientation(LinearLayout.VERTICAL);
        seekContainer.setVisibility(LinearLayout.GONE);
        panelView.addView(seekContainer, new LinearLayout.LayoutParams(-1, -2));

        seekBarView = new SeekBar(c);
        seekBarView.setMax(1000);
        seekBarView.setSplitTrack(false);
        seekBarView.setPadding(0, 0, 0, 0);
        seekBarView.setOnTouchListener((v, event) -> {
            seekTouchActive = true;
            v.getParent().requestDisallowInterceptTouchEvent(true);
            if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                seekTouchActive = false;
            }
            return false;
        });
        seekBarView.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                long duration = effectiveSeekDurationMs();
                if (!fromUser || !seekTrackingActive || lastTrackSnapshot == null || duration <= 0L) return;
                long target = Math.min(duration, Math.max(0L, Math.round((progress / 1000f) * duration)));
                lastTrackSnapshot = new TrackSnapshot(
                        lastTrackSnapshot.sourcePackage,
                        lastTrackSnapshot.title,
                        lastTrackSnapshot.artist,
                        lastTrackSnapshot.playing,
                        lastTrackSnapshot.sourceType,
                        lastTrackSnapshot.albumArt,
                        duration,
                        target
                );
                seekSession.applyUserSeek(target);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {
                seekTrackingActive = true;
                h.removeCallbacks(hideTask);
                h.removeCallbacks(testHideTask);
            }
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                trySeekCurrentPosition(seekBar.getProgress());
                seekTrackingActive = false;
                seekTouchActive = false;
                Context ctx = seekBar.getContext().getApplicationContext();
                restartHideTimer(new Prefs(ctx));
                scheduleSeekProgressUpdates(ctx, true);
            }
        });
        seekContainer.addView(seekBarView, new LinearLayout.LayoutParams(-1, -2));

        controlsRow=new LinearLayout(c);
        controlsRow.setOrientation(LinearLayout.HORIZONTAL);
        controlsRow.setGravity(Gravity.CENTER);
        controlsRow.setClipToPadding(false);
        controlsRow.setClipChildren(false);
        controlsRow.setPadding(0, Ui.dp(c, 2), 0, Ui.dp(c, 8));
        addControlButton(c, controlsRow, "⏮", KeyEvent.KEYCODE_MEDIA_PREVIOUS);
        addControlButton(c, controlsRow, "⏯", KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
        addControlButton(c, controlsRow, "⏭", KeyEvent.KEYCODE_MEDIA_NEXT);
        panelView.addView(controlsRow,new LinearLayout.LayoutParams(-1,-2));
        controlsVisible = true; // updateControlsVisibility выставит верное значение
        lastDesign = -1;
    }

    private static boolean shouldUseExpandedFixedAlbumArt(Prefs p) {
        return p.featureFixedWindow() && p.featureControls() && !p.featureSeekBar() && p.featureAlbumArt();
    }

    private static void detachFromParent(android.view.View view){
        if(view == null)return;
        android.view.ViewParent parent = view.getParent();
        if(parent instanceof ViewGroup){
            ((ViewGroup) parent).removeView(view);
        }
    }

    private static void ensureNormalLayout(){
        if(panelView==null || contentRow==null || lineView==null || controlsRow==null || albumArtView==null)return;
        panelView.removeView(specialFixedRow);
        if(panelView.indexOfChild(contentRow) < 0){
            panelView.addView(contentRow, 0, new LinearLayout.LayoutParams(-1,-2));
        }
        detachFromParent(albumArtView);
        detachFromParent(lineView);
        detachFromParent(controlsRow);
        contentRow.removeAllViews();
        contentRow.addView(albumArtView, new LinearLayout.LayoutParams(0, 0));
        contentRow.addView(lineView, new LinearLayout.LayoutParams(-2,-2));
        if(panelView.indexOfChild(seekContainer) < 0){
            panelView.addView(seekContainer, new LinearLayout.LayoutParams(-1,-2));
        }
        if(panelView.indexOfChild(controlsRow) < 0){
            panelView.addView(controlsRow, new LinearLayout.LayoutParams(-1,-2));
        }
        specialRightColumn.removeAllViews();
        contentRow.setVisibility(LinearLayout.VISIBLE);
    }

    private static void ensureSpecialFixedLayout(){
        if(panelView==null || contentRow==null || lineView==null || controlsRow==null || albumArtView==null || specialFixedRow==null || specialRightColumn==null)return;
        panelView.removeView(contentRow);
        panelView.removeView(controlsRow);
        detachFromParent(albumArtView);
        detachFromParent(lineView);
        detachFromParent(controlsRow);
        specialFixedRow.removeAllViews();
        specialRightColumn.removeAllViews();
        specialFixedRow.addView(albumArtView, new LinearLayout.LayoutParams(0, 0));
        specialRightColumn.addView(lineView, new LinearLayout.LayoutParams(-1,-2));
        specialRightColumn.addView(controlsRow, new LinearLayout.LayoutParams(-1,-2));
        specialFixedRow.addView(specialRightColumn, new LinearLayout.LayoutParams(0, -2, 1f));
        if(panelView.indexOfChild(specialFixedRow) < 0){
            panelView.addView(specialFixedRow, 0, new LinearLayout.LayoutParams(-1,-2));
        }
    }

    private static void applyAlbumArt(Context c, Prefs p, Bitmap albumArt){
        if(contentRow==null || albumArtView==null || lineView==null)return;
        boolean wantsCover = p.featureAlbumArt();
        Bitmap effectiveArt = albumArt;
        if (wantsCover && (effectiveArt == null || effectiveArt.isRecycled()) && lastTrackSnapshot != null && lastTrackSnapshot.hasText()) {
            effectiveArt = AlbumArtHelper.getPlaceholderAlbumArt(c, Math.max(1, p.textSize()), placeholderAlbumArtBitmap);
            if (effectiveArt != null) placeholderAlbumArtBitmap = effectiveArt;
        }
        boolean showCover = wantsCover && effectiveArt != null && !effectiveArt.isRecycled();
        boolean specialLayout = showCover && shouldUseExpandedFixedAlbumArt(p);
        if (specialLayout) ensureSpecialFixedLayout(); else ensureNormalLayout();
        contentRow.setGravity(showCover ? Gravity.CENTER_VERTICAL : Gravity.CENTER);
        lineView.setGravity(showCover
                ? (specialLayout ? (Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL) : (Gravity.START | Gravity.CENTER_VERTICAL))
                : resolveTextGravity(p, false, false));
        if(showCover){
            int textSize = Math.max(1, p.textSize());
            int artDp = Math.max(44, Math.min(92, Math.round(textSize * 2.15f)));
            int baseArtPx = Ui.dp(c, artDp);
            int artPx = specialLayout ? estimateExpandedFixedAlbumArtPx(c, p, textSize, baseArtPx) : baseArtPx;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(artPx, artPx);
            lp.gravity = Gravity.CENTER_VERTICAL;
            lp.setMargins(0, 0, Ui.dp(c, Math.max(10, Math.min(18, Math.round(textSize * 0.42f)))), 0);
            albumArtView.setLayoutParams(lp);
            albumArtView.setImageBitmap(effectiveArt);
            int radius = Math.max(12, Math.min(22, Math.round(textSize * 0.55f)));
            albumArtView.setBackground(Ui.stroke(0x22141B2A, 1, 0x44FFFFFF, radius, c));
            albumArtView.setClipToOutline(true);
            albumArtView.setAlpha(1f);
            albumArtView.setVisibility(ImageView.VISIBLE);
        } else {
            ensureNormalLayout();
            albumArtView.setImageDrawable(null);
            albumArtView.setAlpha(1f);
            albumArtView.setVisibility(ImageView.GONE);
            albumArtView.setLayoutParams(new LinearLayout.LayoutParams(0, 0));
        }
    }

    private static int estimateExpandedFixedAlbumArtPx(Context c, Prefs p, int textSize, int fallbackPx) {
        int widthPx = p.fixedWindowWidth();
        if (widthPx <= 0) {
            DisplayMetrics dm = getRealDisplayMetrics(c);
            int screenWidth = Math.max(dm.widthPixels, Ui.dp(c, 320));
            int screenHeight = Math.max(dm.heightPixels, Ui.dp(c, 320));
            boolean landscape = screenWidth > screenHeight;
            int edgeMargin = Ui.dp(c, landscape ? 12 : 16);
            int maxWindowWidth = Math.max(Ui.dp(c, 220), screenWidth - edgeMargin * 2);
            float widthFactor = landscape ? 0.74f : 0.86f;
            widthPx = Math.max(Ui.dp(c, 150), Math.min(maxWindowWidth, Math.round(maxWindowWidth * widthFactor)));
        }

        float sizeScale = p.controlsSize() == Prefs.CONTROLS_SIZE_SMALL ? 0.88f
                : (p.controlsSize() == Prefs.CONTROLS_SIZE_LARGE ? 1.16f : 1f);
        int buttonBaseDp = Math.max(34, Math.min(76, Math.round(textSize * 1.66f * sizeScale)));
        int buttonHeightPx = Ui.dp(c, buttonBaseDp);
        int controlsVerticalGapPx = Ui.dp(c, Math.max(8, Math.min(18, Math.round(textSize * 0.45f))));
        int controlsBlockPx = buttonHeightPx + controlsVerticalGapPx * 2;
        int contentBlockPx = Ui.dp(c, Math.max(34, Math.min(108, Math.round(textSize * 2.35f))));
        int widthDrivenPx = Math.round(widthPx * 0.18f);
        int targetPx = Math.max(fallbackPx, Math.max(widthDrivenPx, contentBlockPx + controlsBlockPx - Ui.dp(c, 18)));
        int maxPx = Math.min(Ui.dp(c, 170), Math.round(widthPx * 0.24f));
        return Math.max(fallbackPx, Math.min(maxPx, targetPx));
    }

    private static void scheduleAlbumArtRefreshIfNeeded(Context c, Prefs p, Bitmap rawAlbumArt) {
        h.removeCallbacks(albumArtRefreshTask);
        if (!AlbumArtHelper.shouldScheduleRefresh(p, rawAlbumArt, lastTrackSnapshot)) {
            resetAlbumArtRetryState();
            return;
        }
        albumArtWaitingForReal = true;
        long delayMs = AlbumArtHelper.nextRetryDelayMs(albumArtRetryCount);
        if (delayMs < 0L) return;
        h.postDelayed(albumArtRefreshTask, delayMs);
    }

    private static void resetAlbumArtRetryState() {
        h.removeCallbacks(albumArtRefreshTask);
        albumArtWaitingForReal = false;
        albumArtRetryCount = 0;
    }

    private static void resetFixedWindowEditState() {
        fixedWindowEditMode = false;
        resizingFixedWindow = false;
        pinchStartSpanX = 0f;
        pinchStartWidth = 0;
    }

    private static void resetTouchState() {
        dragging = false;
        dragArmed = false;
        longPressTriggered = false;
        pendingLongPressTask = null;
        downX = 0;
        downY = 0;
        downGravity = 0;
        touchX = 0f;
        touchY = 0f;
        h.removeCallbacks(albumArtSingleTapTask);
        lastTapUpMs = 0L;
        lastAlbumArtTapUpMs = 0L;
    }

    private static void resetSeekState() {
        seekTrackingActive = false;
        seekTouchActive = false;
        lastSeekProgressRefreshAt = 0L;
        h.removeCallbacks(seekProgressTask);
        if (seekBarView != null) seekBarView.setProgress(0);
        lastSeekDebugReason = "reset";
    }

    private static void resetRenderedContentState() {
        lastRenderedText = "";
        lastTitle = "";
        lastArtist = "";
        lastAlbumArt = null;
        controlsVisible = false;
        lastDesign = -1;
    }

    private static void resetSessionTimingState() {
        testModeUntilAt = 0L;
        volumeDimUntilAt = 0L;
        sessionVisibleUntilAt = 0L;
        sessionContinuous = false;
        pauseHideFrozen = false;
        sessionRecoveryRestartRequested = false;
        transientRecoveryNotBeforeAt = 0L;
        lastControlledRecoveryAt = 0L;
    }

    private static void refreshAlbumArtOnMain() {
        Context c = lastAppContext;
        if (c == null || !albumArtWaitingForReal || lastTrackSnapshot == null || !lastTrackSnapshot.hasText()) return;
        Prefs p = new Prefs(c);
        if (!p.featureAlbumArt()) {
            resetAlbumArtRetryState();
            return;
        }
        TrackSnapshot refreshed = new MusicStateDetector(c).read();
        if (refreshed != null && refreshed.hasText() && safeKey(refreshed).equals(safeKey(lastTrackSnapshot)) && refreshed.hasAlbumArt()) {
            lastTrackSnapshot = new TrackSnapshot(
                    refreshed.sourcePackage,
                    refreshed.title,
                    refreshed.artist,
                    refreshed.playing,
                    refreshed.sourceType,
                    refreshed.albumArt,
                    refreshed.durationMs,
                    refreshed.positionMs
            );
            lastAlbumArt = refreshed.albumArt;
            resetAlbumArtRetryState();
            applyAlbumArt(c, p, refreshed.albumArt);
            applySmartTextLayout(c, p, lastRenderedText);
            return;
        }
        albumArtRetryCount++;
        if (albumArtRetryCount < AlbumArtHelper.maxRetryCount()) {
            h.postDelayed(albumArtRefreshTask, 900L);
        } else {
            resetAlbumArtRetryState();
        }
    }

    private static void addControlButton(Context c, LinearLayout row, String text, int keyCode){
        TextView b=new TextView(c);
        b.setText(text);
        b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        b.setGravity(Gravity.CENTER);
        b.setTextColor(0xFFFFFFFF);
        b.setIncludeFontPadding(false);
        b.setOnClickListener(v -> sendMediaKey(c,keyCode));
        row.addView(b);
    }

    /** Подгоняет кнопки управления под текущий размер текста и делает ровные отступы сверху/снизу. */
    private static void applyControlsLayout(Context c, Prefs p){
        MediaControlsLayoutHelper.applyControlsLayout(c, p, controlsRow);
    }

    private static void sendMediaKey(Context c,int keyCode){
        try{
            AudioManager am=(AudioManager)c.getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
            if(am==null)return;
            long now=SystemClock.uptimeMillis();
            am.dispatchMediaKeyEvent(new KeyEvent(now,now,KeyEvent.ACTION_DOWN,keyCode,0));
            am.dispatchMediaKeyEvent(new KeyEvent(now,now,KeyEvent.ACTION_UP,keyCode,0));
        }catch(Throwable ignored){}
    }

    private static void applyStyle(Prefs p){
        if(panelView==null || lineView==null || contentRow==null)return;
        int design=p.designPreset();
        int bg=p.bgColor();
        int stroke=p.borderColor();
        int strokeWidth=Math.max(0,p.borderWidth());
        int corner=p.corner();
        int px=20;
        int py=14;
        int textColor=p.titleColor();
        int textSize=p.textSize();
        boolean seekVisible = p.featureSeekBar() && p.displayWhilePlaying();
        float letterSpacing = 0f;
        int effectiveStroke = stroke;
        int effectiveStrokeWidth = strokeWidth;

        if(design==1){ // Minimal
            bg=0xE6070A10; effectiveStroke=(stroke & 0x00FFFFFF) | 0x33000000; effectiveStrokeWidth=Math.max(0, strokeWidth - 1);
            corner=Math.max(8,corner/2); px=Math.max(10,px-7); py=Math.max(6,py-6); letterSpacing=0.02f;
        }else if(design==2){ // Glass
            bg=0x99233142; effectiveStroke=0xA6E7F6FF; effectiveStrokeWidth=Math.max(1, strokeWidth + 1);
            corner=Math.max(26,corner+6); px=Math.max(26,px+10); py=Math.max(16,py+3); textColor=0xFFF8FCFF;
        }else if(design==3){ // Car UI
            bg=0xF0060C14; effectiveStroke=0xFF00D5FF; effectiveStrokeWidth=Math.max(2, strokeWidth + 2);
            corner=Math.max(14,corner); px=Math.max(26,px+6); py=Math.max(14,py); textColor=0xFFFFFFFF; letterSpacing=0.03f;
        }else if(design==4){ // Soft
            bg=0xF0243146; effectiveStroke=0x88D8E5F7; effectiveStrokeWidth=Math.max(1, strokeWidth + 1);
            corner=Math.max(26,corner+8); px=Math.max(24,px+8); py=Math.max(16,py+3); textColor=0xFFF7FAFF;
        }else if(design==5){ // Contrast
            bg=0xFA020304; effectiveStroke=0xFFF8FAFC; effectiveStrokeWidth=Math.max(3, strokeWidth + 2);
            corner=Math.max(10,corner-3); px=Math.max(18,px); py=Math.max(12,py-1); textColor=0xFFFFFFFF; letterSpacing=0.01f;
        }else if(design==6){ // Capsule
            bg=0xF0131B2C; effectiveStroke=0xFF95B8FF; effectiveStrokeWidth=Math.max(2, strokeWidth + 1);
            corner=Math.max(38,corner+18); px=Math.max(30,px+14); py=Math.max(11,py-3); textColor=0xFFF5F7FB; letterSpacing=0.02f;
        }else if(design==7){ // Premium
            bg=0xF0181A20; effectiveStroke=0xFFD9C27A; effectiveStrokeWidth=Math.max(2, strokeWidth + 1);
            corner=Math.max(18,corner+2); px=Math.max(28,px+10); py=Math.max(18,py+4); textColor=0xFFFFF6DD; letterSpacing=0.025f;
        }else if(design==8){ // Spikes
            bg=0xF0121622; effectiveStroke=0xFFFF6B6B; effectiveStrokeWidth=Math.max(2, strokeWidth + 1);
            corner=Math.max(12,corner); px=Math.max(24,px+6); py=Math.max(18,py+4); textColor=0xFFFFF1F1; letterSpacing=0.03f;
        }else if(design==9){ // Orbit
            bg=0xEE111A2B; effectiveStroke=0xFF8BE9FD; effectiveStrokeWidth=Math.max(2, strokeWidth + 1);
            corner=Math.max(40,corner+22); px=Math.max(34,px+18); py=Math.max(26,py+12); textColor=0xFFF4FBFF; letterSpacing=0.03f;
        }

        int topPad = py;
        int bottomPad = py;
        if (seekVisible) {
            topPad = Math.max(10, py - 2);
            bottomPad = Math.max(8, py - 4);
        }
        Ui.pad(panelView,px,topPad,px,bottomPad);
        applyPanelBackground(panelView.getContext(), bg, effectiveStrokeWidth, effectiveStroke, corner, effectiveWindowAlphaPercent(p), design);
        panelView.setAlpha(1f);
        lineView.setTextSize(textSize);
        lineView.setTypeface(OverlayFonts.resolve(panelView.getContext(), p.textFont(), p.textBold()));
        lineView.getPaint().setFakeBoldText(p.textBold());
        lineView.setLetterSpacing(letterSpacing);
        applyPresetTextEffects(p, design, textColor);
        applyTrackText(p, lastTitle, lastArtist);
        updateSeekBarVisibility(panelView.getContext().getApplicationContext(), p);
        boolean coverVisible = albumArtView != null && albumArtView.getVisibility() == ImageView.VISIBLE;
        boolean specialLayout = coverVisible && shouldUseExpandedFixedAlbumArt(p);
        lineView.setGravity(coverVisible
                ? (specialLayout ? (Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL) : (Gravity.START | Gravity.CENTER_VERTICAL))
                : resolveTextGravity(p, false, false));
        lineView.setIncludeFontPadding(true);
        applyAlbumArtFrameStyle(panelView.getContext(), design, textSize, effectiveStroke);
        if(coverVisible){
            lineView.setLineSpacing(Ui.dp(panelView.getContext(), Math.max(1, Math.min(8, Math.round(textSize * 0.14f)))), 1.0f);
            int contentMinHeight = Ui.dp(panelView.getContext(), Math.max(34, Math.min(108, Math.round(textSize * (seekVisible ? 2.15f : 2.6f)))));
            contentRow.setMinimumHeight(contentMinHeight);
            int verticalInset = Ui.dp(panelView.getContext(), Math.max(0, Math.min(6, Math.round(textSize * (seekVisible ? 0.06f : 0.12f)))));
            contentRow.setPadding(0, verticalInset, 0, verticalInset);
        }else{
            lineView.setLineSpacing(0f, 1.0f);
            contentRow.setMinimumHeight(0);
            contentRow.setPadding(0, 0, 0, 0);
        }
        lastDesign=design;
    }

    private static void applyPresetTextEffects(Prefs p, int design, int textColor){
        if(lineView==null)return;
        lineView.setShadowLayer(0f, 0f, 0f, 0);
        if (!p.textShadow()) return;
        if(design==2){ // Glass
            lineView.setShadowLayer(12f, 0f, 1.5f, 0x88E7F6FF);
        }else if(design==3){ // Car UI
            lineView.setShadowLayer(10f, 0f, 1.5f, 0x9900D5FF);
        }else if(design==4){ // Soft
            lineView.setShadowLayer(8f, 0f, 2f, 0x66263245);
        }else if(design==5){ // Contrast
            lineView.setShadowLayer(12f, 0f, 2.5f, 0xDD000000);
        }else if(design==6){ // Capsule
            lineView.setShadowLayer(10f, 0f, 1.5f, 0x8895B8FF);
        }else if(design==7){ // Premium
            lineView.setShadowLayer(11f, 0f, 1.5f, 0x99D9C27A);
        }else if(design==8){ // Spikes
            lineView.setShadowLayer(10f, 0f, 1.5f, 0x88FF6B6B);
        }else if(design==9){ // Orbit
            lineView.setShadowLayer(12f, 0f, 1.5f, 0x888BE9FD);
        }else if(design==1){ // Minimal
            lineView.setShadowLayer(6f, 0f, 1.5f, 0x66000000);
        }else{
            lineView.setShadowLayer(8f, 0f, 2f, 0xAA000000);
        }
    }

    private static void applyAlbumArtFrameStyle(Context c, int design, int textSize, int strokeColor){
        if(albumArtView==null || albumArtView.getVisibility()!=ImageView.VISIBLE)return;
        int radius = Math.max(12, Math.min(24, Math.round(textSize * 0.55f)));
        int bg = 0x22141B2A;
        int stroke = 0x44FFFFFF;
        int width = 1;
        if(design==1){
            bg = 0x14000000;
            stroke = 0x22000000;
            width = 0;
        }else if(design==2){
            bg = 0x33DDEEFF;
            stroke = 0x99F2FBFF;
            width = 2;
            radius += 4;
        }else if(design==3){
            bg = 0x2200D5FF;
            stroke = 0xFF00D5FF;
            width = 2;
        }else if(design==4){
            bg = 0x33EAF2FF;
            stroke = 0x88D8E5F7;
            width = 1;
            radius += 3;
        }else if(design==5){
            bg = 0x22000000;
            stroke = 0xFFF8FAFC;
            width = 2;
        }else if(design==6){
            bg = 0x225E7EC0;
            stroke = 0xFF95B8FF;
            width = 2;
            radius += 8;
        }else if(design==7){
            bg = 0x22D9C27A;
            stroke = 0xFFD9C27A;
            width = 2;
            radius += 2;
        }else if(design==8){
            bg = 0x22FF6B6B;
            stroke = 0xFFFF6B6B;
            width = 2;
            radius += 4;
        }else if(design==9){
            bg = 0x228BE9FD;
            stroke = 0xFF8BE9FD;
            width = 2;
            radius += 8;
        }else{
            stroke = (strokeColor & 0x00FFFFFF) | 0x66FFFFFF;
        }
        albumArtView.setBackground(Ui.stroke(bg, width, stroke, radius, c));
    }

    private static void updateControlsVisibility(Context c, Prefs p){
        if(controlsRow==null)return;
        controlsVisible=p.featureControls();
        controlsRow.setVisibility(controlsVisible?LinearLayout.VISIBLE:LinearLayout.GONE);
        if(controlsVisible) MediaControlsLayoutHelper.applyControlsLayout(c,p,controlsRow);
    }

    private static void updateSeekBarVisibility(Context c, Prefs p){
        if (seekContainer == null || seekBarView == null) return;
        boolean featureEnabled = p.featureSeekBar() && p.displayWhilePlaying();
        boolean showSeek = featureEnabled && hasEffectiveSeekRange();
        if (!showSeek) {
            lastSeekDebugReason = !featureEnabled ? "feature_disabled" : "no_range";
            seekContainer.setVisibility(LinearLayout.GONE);
            seekBarView.setProgress(0);
            if (featureEnabled && lastTrackSnapshot != null && lastTrackSnapshot.hasText()) {
                lastSeekDebugReason = "waiting_range";
                scheduleSeekProgressUpdates(c, false);
            } else {
                lastSeekDebugReason = !featureEnabled ? "feature_disabled" : "no_track";
                h.removeCallbacks(seekProgressTask);
            }
            return;
        }
        lastSeekDebugReason = "visible";
        seekContainer.setVisibility(LinearLayout.VISIBLE);
        int top = Ui.dp(c, 5);
        int bottom = Ui.dp(c, p.featureControls() ? 3 : 6);
        seekContainer.setPadding(0, top, 0, bottom);
        applySeekBarColors(p);
        if (!seekTrackingActive) {
            long duration = Math.max(1L, effectiveSeekDurationMs());
            long position = Math.max(0L, effectiveSeekPositionMs());
            int progress = Math.max(0, Math.min(1000, Math.round((position / (float) duration) * 1000f)));
            seekBarView.setProgress(progress);
        }
        scheduleSeekProgressUpdates(c, false);
    }

    private static void applySeekBarColors(Prefs p) {
        if (seekBarView == null || p == null) return;
        ColorStateList progress = ColorStateList.valueOf(p.seekBarColor());
        ColorStateList thumb = ColorStateList.valueOf(p.seekThumbColor());
        seekBarView.setProgressTintList(progress);
        seekBarView.setSecondaryProgressTintList(progress);
        seekBarView.setProgressBackgroundTintList(ColorStateList.valueOf((p.seekBarColor() & 0x00FFFFFF) | 0x44000000));
        seekBarView.setThumbTintList(thumb);
    }

    private static void scheduleSeekProgressUpdates(Context c, boolean immediate) {
        if (c != null) lastAppContext = c.getApplicationContext();
        h.removeCallbacks(seekProgressTask);
        if (seekBarView == null || seekContainer == null) return;
        if (seekTrackingActive) {
            lastSeekDebugReason = "user_drag";
            return;
        }
        if (seekTouchActive) {
            lastSeekDebugReason = "touch_active";
            return;
        }
        if (contentSoftHidden) {
            lastSeekDebugReason = "content_hidden";
            return;
        }
        if (!hostAttached) {
            lastSeekDebugReason = "host_detached";
            return;
        }
        lastSeekDebugReason = immediate ? "polling_now" : "polling_wait";
        h.postDelayed(seekProgressTask, immediate ? 0L : 850L);
    }

    private static void updateSeekProgressOnMain() {
        Context c = lastAppContext;
        if (c == null || seekBarView == null || seekContainer == null) return;
        if (seekTrackingActive) {
            lastSeekDebugReason = "user_drag";
            return;
        }
        if (seekTouchActive) {
            lastSeekDebugReason = "touch_active";
            return;
        }
        if (contentSoftHidden) {
            lastSeekDebugReason = "content_hidden";
            return;
        }
        if (!hostAttached) {
            lastSeekDebugReason = "host_detached";
            return;
        }
        Prefs prefs = new Prefs(c);
        if (!prefs.featureSeekBar() || !prefs.displayWhilePlaying()) {
            lastSeekDebugReason = "feature_disabled";
            return;
        }
        TrackSnapshot current = new MusicStateDetector(c).read();
        TrackSnapshot liveBase;
        if (current != null && current.hasText() && current.hasSeekRange()) {
            liveBase = current;
        } else if (lastTrackSnapshot != null && lastTrackSnapshot.hasText()) {
            liveBase = lastTrackSnapshot;
        } else {
            liveBase = current;
        }
        current = MusicStateDetector.enrichWithLivePlaybackState(c, liveBase);
        if (current == null || !current.hasText()) {
            lastSeekDebugReason = "no_track";
            if (lastTrackSnapshot != null && lastTrackSnapshot.hasText()) {
                scheduleSeekProgressUpdates(c, false);
            } else {
                h.removeCallbacks(seekProgressTask);
            }
            return;
        }
        syncSeekSessionState(current);
        if (!safeKey(current).equals(safeKey(lastTrackSnapshot))) {
            lastTrackSnapshot = current;
        } else {
            long duration = current.durationMs > 1000L ? current.durationMs : effectiveSeekDurationMs();
            long position = current.positionMs >= 0L ? current.positionMs : effectiveSeekPositionMs();
            lastTrackSnapshot = new TrackSnapshot(
                    current.sourcePackage,
                    current.title,
                    current.artist,
                    current.playing,
                    current.sourceType,
                    current.albumArt != null ? current.albumArt : lastTrackSnapshot.albumArt,
                    duration,
                    position
            );
        }
        if (!hasEffectiveSeekRange()) {
            lastSeekDebugReason = "no_range";
            if (seekContainer.getVisibility() == LinearLayout.VISIBLE) {
                updateSeekBarVisibility(c, prefs);
            }
            scheduleSeekProgressUpdates(c, false);
            return;
        }
        if (seekContainer.getVisibility() != LinearLayout.VISIBLE) {
            updateSeekBarVisibility(c, prefs);
        }
        long duration = Math.max(1L, effectiveSeekDurationMs());
        long position = Math.max(0L, effectiveSeekPositionMs());
        int progress = Math.max(0, Math.min(1000, Math.round((position / (float) duration) * 1000f)));
        seekBarView.setProgress(progress);
        lastSeekProgressRefreshAt = SystemClock.uptimeMillis();
        lastSeekDebugReason = "polling";
        scheduleSeekProgressUpdates(c, false);
    }

    private static String safeKey(TrackSnapshot track) {
        if (track == null) return "";
        return clean(track.sourcePackage) + "|" + clean(track.artist) + "|" + clean(track.title);
    }

    private static void syncSeekSessionState(TrackSnapshot snapshot) {
        if (snapshot == null || !snapshot.hasText()) return;
        if (seekSession.matches(snapshot)) {
            if (snapshot.hasSeekRange()) {
                seekSession.updateFromSnapshot(snapshot);
            } else {
                seekSession.markPlaying(snapshot.playing);
            }
            return;
        }
        if (snapshot.hasSeekRange()) {
            seekSession.updateFromSnapshot(snapshot);
        } else {
            seekSession.reset();
        }
    }

    private static boolean hasEffectiveSeekRange() {
        return lastTrackSnapshot != null
                && lastTrackSnapshot.hasText()
                && (lastTrackSnapshot.hasSeekRange() || (seekSession.matches(lastTrackSnapshot) && seekSession.hasRange()));
    }

    private static long effectiveSeekDurationMs() {
        if (lastTrackSnapshot != null && lastTrackSnapshot.durationMs > 1000L) return lastTrackSnapshot.durationMs;
        return seekSession.hasRange() ? seekSession.durationMs() : -1L;
    }

    private static long effectiveSeekPositionMs() {
        if (lastTrackSnapshot != null && lastTrackSnapshot.hasSeekRange()) {
            return Math.max(0L, lastTrackSnapshot.positionMs);
        }
        if (lastTrackSnapshot != null && seekSession.matches(lastTrackSnapshot) && seekSession.hasRange()) {
            return seekSession.estimatePositionNow();
        }
        return -1L;
    }

    private static void trySeekCurrentPosition(int progress) {
        if (lastTrackSnapshot == null || !lastTrackSnapshot.hasText()) return;
        Context c = lastAppContext;
        if (c == null) return;
        long duration = effectiveSeekDurationMs();
        if (duration <= 0L) return;
        long target = Math.min(duration, Math.max(0L, Math.round((progress / 1000f) * duration)));
        boolean ok = MusicStateDetector.seekTo(c, lastTrackSnapshot.sourcePackage, target);
        lastSeekDebugReason = ok ? "seek_ok" : "seek_failed";
        if (ok) {
            lastTrackSnapshot = new TrackSnapshot(
                    lastTrackSnapshot.sourcePackage,
                    lastTrackSnapshot.title,
                    lastTrackSnapshot.artist,
                    lastTrackSnapshot.playing,
                    lastTrackSnapshot.sourceType,
                    lastTrackSnapshot.albumArt,
                    duration,
                    target
            );
            seekSession.applyUserSeek(target);
        }
    }

    private static void restartHideTimer(Prefs prefs){
        h.removeCallbacks(hideTask);
        h.removeCallbacks(testHideTask);
        if (prefs != null && prefs.displayWhilePlaying()) return;
        int ms = prefs != null ? prefs.displayMs() : 0;
        if(ms>0)h.postDelayed(hideTask,ms);
    }

    private static void restartHideTimerForMs(int ms){
        h.removeCallbacks(hideTask);
        h.removeCallbacks(testHideTask);
        if(ms>0) h.postDelayed(hideTask, ms);
    }

    private static void restartTestHideTimerForMs(int ms){
        h.removeCallbacks(hideTask);
        h.removeCallbacks(testHideTask);
        if(ms>0) h.postDelayed(testHideTask, ms);
    }

    private static void applySmartTextLayout(Context c, Prefs p, String text){
        if(lineView==null || currentParams==null)return;

        DisplayMetrics dm=getRealDisplayMetrics(c);
        int screenWidth=Math.max(dm.widthPixels, Ui.dp(c,320));
        int screenHeight=Math.max(dm.heightPixels, Ui.dp(c,320));
        boolean landscape=screenWidth>screenHeight;

        int edgeMargin=Ui.dp(c, landscape ? 12 : 16);
        int maxWindowWidth=Math.max(Ui.dp(c,220), screenWidth - edgeMargin * 2);
        int minWindowWidth=Ui.dp(c,150);
        int horizontalPadding=Math.max(0,p.paddingX()*2);
        int measureSafety=Ui.dp(c,18);
        boolean coverVisible = albumArtView != null && albumArtView.getVisibility() == ImageView.VISIBLE;
        boolean fixedWindow = p.featureFixedWindow();
        boolean specialLayout = coverVisible && shouldUseExpandedFixedAlbumArt(p);
        int artWidth = coverVisible && albumArtView.getLayoutParams() != null ? albumArtView.getLayoutParams().width : 0;
        int artGap = coverVisible ? Ui.dp(c, Math.max(10, Math.min(18, Math.round(Math.max(1, p.textSize()) * 0.42f)))) : 0;
        int maxTextWidth=Math.max(Ui.dp(c,80), maxWindowWidth - horizontalPadding - measureSafety - artWidth - artGap);

        String safeText=text==null?"":text.trim();
        float measuredTextWidth=lineView.getPaint().measureText(safeText);
        boolean fitsOneLine=measuredTextWidth<=maxTextWidth;
        int targetWidth;
        int maxLines = landscape ? 2 : 3;

        lineView.setMinWidth(0);
        cancelMarquee();
        lineView.setScrollX(0);
        lineView.setHorizontallyScrolling(fixedWindow);
        lineView.setGravity(coverVisible
                ? (specialLayout ? (Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL) : (Gravity.START | Gravity.CENTER_VERTICAL))
                : resolveTextGravity(p, false, fixedWindow));
        lineView.setIncludeFontPadding(true);
        lineView.setBreakStrategy(android.text.Layout.BREAK_STRATEGY_HIGH_QUALITY);
        lineView.setHyphenationFrequency(android.text.Layout.HYPHENATION_FREQUENCY_NONE);

        if(fixedWindow){
            float widthFactor = landscape ? (coverVisible ? 0.74f : 0.68f) : (coverVisible ? 0.86f : 0.78f);
            int defaultWidth = Math.max(minWindowWidth, Math.min(maxWindowWidth, Math.round(maxWindowWidth * widthFactor)));
            int savedWidth = p.fixedWindowWidth();
            targetWidth = Math.max(minWindowWidth, Math.min(maxWindowWidth, savedWidth > 0 ? savedWidth : defaultWidth));
            lineView.setSingleLine(true);
            lineView.setMaxLines(1);
            lineView.setEllipsize(null);
            lineView.setMarqueeRepeatLimit(0);
        } else if(fitsOneLine){
            lineView.setHorizontallyScrolling(false);
            lineView.setSingleLine(true);
            lineView.setMaxLines(1);
            lineView.setEllipsize(null);
            lineView.setMarqueeRepeatLimit(0);
            targetWidth=(int)Math.ceil(measuredTextWidth)+horizontalPadding+measureSafety+artWidth+artGap;
            targetWidth=Math.max(minWindowWidth, Math.min(maxWindowWidth, targetWidth));
        }else{
            lineView.setHorizontallyScrolling(false);
            lineView.setSingleLine(false);
            lineView.setMaxLines(maxLines);
            lineView.setEllipsize(TextUtils.TruncateAt.END);
            lineView.setMarqueeRepeatLimit(0);
            targetWidth=maxWindowWidth;
        }

        if(p.featureControls()){
            float sizeScale = p.controlsSize() == Prefs.CONTROLS_SIZE_SMALL ? 0.88f
                    : (p.controlsSize() == Prefs.CONTROLS_SIZE_LARGE ? 1.16f : 1f);
            float spacingScale = p.controlsSpacing() == Prefs.CONTROLS_SPACING_COMPACT ? 0.78f
                    : (p.controlsSpacing() == Prefs.CONTROLS_SPACING_WIDE ? 1.38f : 1f);
            int buttonBaseDp=Math.max(34, Math.min(76, Math.round(Math.max(1,p.textSize()) * 1.66f * sizeScale)));
            int buttonWidthDp = p.controlsShape() == Prefs.CONTROLS_SHAPE_RECT
                    ? Math.round(buttonBaseDp * 1.38f)
                    : buttonBaseDp;
            int controlSideMargin=Math.max(4, Math.min(18, Math.round(Math.max(1,p.textSize()) * 0.34f * spacingScale)));
            targetWidth=Math.max(targetWidth, Ui.dp(c, buttonWidthDp*3 + controlSideMargin*6 + 24));
        }
        currentParams.width=targetWidth;
        currentParams.height=WindowManager.LayoutParams.WRAP_CONTENT;

        try{
            overlayView.setMinimumWidth(0);
            panelView.setMinimumWidth(0);
            panelView.setMinimumWidth(targetWidth);
            int textWidth = Math.max(Ui.dp(c,80), targetWidth - horizontalPadding - artWidth - artGap);
            LinearLayout.LayoutParams textLp = (LinearLayout.LayoutParams) lineView.getLayoutParams();
            if(textLp == null){
                textLp = new LinearLayout.LayoutParams(textWidth, -2);
            }else{
                textLp.width = textWidth;
                textLp.height = -2;
                textLp.weight = 0f;
            }
            lineView.setLayoutParams(textLp);
            lineView.setMaxWidth(textWidth);
            lineView.setMinWidth(0);
            lineView.setMinimumWidth(0);
            lineView.setPadding(0, 0, 0, 0);
            if (seekContainer != null) {
                LinearLayout.LayoutParams seekLp = (LinearLayout.LayoutParams) seekContainer.getLayoutParams();
                if (seekLp == null) seekLp = new LinearLayout.LayoutParams(-1, -2);
                seekLp.width = -1;
                seekContainer.setLayoutParams(seekLp);
                seekContainer.setPadding(0, seekContainer.getPaddingTop(), 0, seekContainer.getPaddingBottom());
            }
            panelView.requestLayout();
            overlayView.requestLayout();
            if (fixedWindow) {
                applyFixedWindowTextBehavior(textWidth, measuredTextWidth);
            } else {
                lineView.setGravity(coverVisible
                        ? (specialLayout ? (Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL) : (Gravity.START | Gravity.CENTER_VERTICAL))
                        : resolveTextGravity(p, false, false));
            }
        }catch(Exception ignored){}

        keepWindowInsideScreen(c, screenWidth, screenHeight, targetWidth);
    }

    private static void applyFixedWindowTextBehavior(int textWidth, float measuredTextWidth){
        if(lineView==null)return;
        int overflow = Math.max(0, (int) Math.ceil(measuredTextWidth - textWidth));
        if(overflow <= 0){
            lineView.setGravity(Gravity.CENTER);
            lineView.setScrollX(0);
            return;
        }
        lineView.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        lineView.setScrollX(0);
        lineView.post(() -> startMarquee(overflow));
    }

    private static int resolveTextGravity(Prefs prefs, boolean coverVisible, boolean fixedWindow) {
        if (fixedWindow) return Gravity.START | Gravity.CENTER_VERTICAL;
        if (prefs.textAlign() == Prefs.TEXT_ALIGN_LEFT) return Gravity.START | Gravity.CENTER_VERTICAL;
        return coverVisible ? Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL : Gravity.CENTER;
    }

    private static void startMarquee(int overflow){
        if(lineView==null || overflow <= 0)return;
        cancelMarquee();
        marqueeAnimator = ObjectAnimator.ofInt(lineView, "scrollX", 0, overflow);
        marqueeAnimator.setDuration(Math.max(3200L, overflow * 22L));
        marqueeAnimator.setStartDelay(700L);
        marqueeAnimator.setRepeatCount(ObjectAnimator.INFINITE);
        marqueeAnimator.setRepeatMode(ObjectAnimator.REVERSE);
        marqueeAnimator.setInterpolator(new LinearInterpolator());
        marqueeAnimator.start();
    }

    private static void cancelMarquee(){
        if(marqueeAnimator!=null){
            marqueeAnimator.cancel();
            marqueeAnimator = null;
        }
    }

    private static boolean canUseAlbumArtActions(Context c) {
        if (c == null || albumArtView == null) return false;
        if (contentSoftHidden) return false;
        Prefs prefs = new Prefs(c.getApplicationContext());
        return prefs.featureAlbumArt()
                && !prefs.featureControls()
                && albumArtView.getVisibility() == ImageView.VISIBLE
                && lastTrackSnapshot != null
                && !clean(lastTrackSnapshot.sourcePackage).isEmpty();
    }

    private static boolean handleAlbumArtTouch(Context c, MotionEvent event) {
        if (!canUseAlbumArtActions(c)) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                albumArtDownX = event.getRawX();
                albumArtDownY = event.getRawY();
                return true;
            case MotionEvent.ACTION_UP:
                float dx = event.getRawX() - albumArtDownX;
                float dy = event.getRawY() - albumArtDownY;
                if (Math.abs(dx) > TAP_SLOP_PX || Math.abs(dy) > TAP_SLOP_PX) {
                    h.removeCallbacks(albumArtSingleTapTask);
                    return true;
                }
                long now = SystemClock.uptimeMillis();
                if (now - lastAlbumArtTapUpMs <= 280L) {
                    h.removeCallbacks(albumArtSingleTapTask);
                    lastAlbumArtTapUpMs = 0L;
                    openCurrentPlayerAppOnMain(c);
                } else {
                    lastAlbumArtTapUpMs = now;
                    h.removeCallbacks(albumArtSingleTapTask);
                    h.postDelayed(albumArtSingleTapTask, 280L);
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                h.removeCallbacks(albumArtSingleTapTask);
                return true;
        }
        return false;
    }

    private static void performAlbumArtSingleTapOnMain() {
        Context c = lastAppContext;
        if (c == null || !canUseAlbumArtActions(c)) return;
        sendMediaKey(c, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
        h.postDelayed(() -> {
            Context ctx = lastAppContext;
            if (ctx != null) scheduleSeekProgressUpdates(ctx, true);
        }, 380L);
    }

    private static void openCurrentPlayerAppOnMain(Context c) {
        if (c == null || lastTrackSnapshot == null) return;
        try {
            String pkg = clean(lastTrackSnapshot.sourcePackage);
            if (pkg.isEmpty()) return;
            android.content.Intent launch = c.getPackageManager().getLaunchIntentForPackage(pkg);
            if (launch == null) return;
            launch.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(launch);
        } catch (Throwable ignored) {}
    }

    private static void showEditMode(Context c){
        if(fixedWindowEditMode)return;
        fixedWindowEditMode = true;
        try{
            if(panelView!=null){
                Prefs prefs = new Prefs(c);
            OverlayRenderHelper.applyPanelBackground(c, panelView, prefs.bgColor(), 2, 0xAAFFFFFF, Math.max(8, prefs.corner()), prefs.windowAlpha(), prefs.designPreset());
            }
        }catch(Exception ignored){}
        refresh(c);
        keepEditModeAlive();
    }

    private static float getHorizontalSpan(MotionEvent e){
        if(e.getPointerCount() < 2)return 0f;
        float min = e.getX(0);
        float max = min;
        for(int i=1;i<e.getPointerCount();i++){
            float x = e.getX(i);
            if(x < min) min = x;
            if(x > max) max = x;
        }
        return Math.abs(max - min);
    }

    private static boolean beginPinchResize(Context c, MotionEvent e, Prefs prefs){
        if(!prefs.featureFixedWindow() || overlayView==null || currentParams==null || e.getPointerCount() < 2) return false;
        cancelLongPressTask();
        showEditMode(c);
        resizingFixedWindow = true;
        dragging = false;
        dragArmed = false;
        pinchStartSpanX = Math.max(1f, getHorizontalSpan(e));
        int fallbackWidth = panelView != null && panelView.getWidth() > 0 ? panelView.getWidth() : currentParams.width;
        pinchStartWidth = prefs.fixedWindowWidth() > 0 ? prefs.fixedWindowWidth() : Math.max(Ui.dp(c, 150), fallbackWidth);
        try { panelView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); } catch (Throwable ignored) {}
        keepEditModeAlive();
        return true;
    }

    private static boolean updatePinchResize(Context c, MotionEvent e){
        if(!resizingFixedWindow || currentParams==null || wm==null) return false;
        Prefs prefs = new Prefs(c);
        DisplayMetrics dm = getRealDisplayMetrics(c);
        int screenWidth = Math.max(dm.widthPixels, Ui.dp(c,320));
        int safe = Ui.dp(c,8);
        int minWidth = Ui.dp(c,150);
        int maxWidth = Math.max(minWidth, screenWidth - safe * 2);
        float span = Math.max(1f, getHorizontalSpan(e));
        int newWidth = Math.round(pinchStartWidth * (span / Math.max(1f, pinchStartSpanX)));
        newWidth = Math.max(minWidth, Math.min(maxWidth, newWidth));
        if(newWidth == prefs.fixedWindowWidth() || newWidth == currentParams.width){
            keepEditModeAlive();
            return true;
        }
        int currentWidth = currentParams.width > 0 ? currentParams.width : pinchStartWidth;
        int anchorCenter = currentParams.x + currentWidth / 2;
        currentParams.gravity = Gravity.TOP | Gravity.START;
        currentParams.width = newWidth;
        currentParams.x = anchorCenter - newWidth / 2;
        currentParams.x = Math.max(safe, Math.min(currentParams.x, screenWidth - newWidth - safe));
        prefs.setFixedWindowWidth(newWidth);
        prefs.setOverlayPosition(currentParams.x, currentParams.y);
        lastAppliedCustomPosition = true;
        applySmartTextLayout(c, prefs, lastRenderedText);
        try{ wm.updateViewLayout(overlayView, currentParams); }catch(Exception ignored){}
        keepEditModeAlive();
        return true;
    }

    private static void finishPinchResize(Context c){
        if(!resizingFixedWindow)return;
        resizingFixedWindow = false;
        pinchStartSpanX = 0f;
        pinchStartWidth = 0;
        keepEditModeAlive();
        restartHideTimer(new Prefs(c));
    }

    private static void keepEditModeAlive(){
        h.removeCallbacks(hideEditModeTask);
        h.postDelayed(hideEditModeTask, EDIT_MODE_TIMEOUT_MS);
    }

    private static void hideEditModeOnMain(){
        if(!fixedWindowEditMode)return;
        fixedWindowEditMode = false;
        if(panelView!=null){
            try{
                Context c = panelView.getContext().getApplicationContext();
                Prefs p = new Prefs(c);
                applyStyle(p);
                applySmartTextLayout(c, p, lastRenderedText);
                if(wm!=null && currentParams!=null) wm.updateViewLayout(overlayView, currentParams);
            }catch(Exception ignored){}
        }
    }

    private static void keepWindowInsideScreen(Context c,int screenWidth,int screenHeight,int targetWidth){
        if(currentParams==null)return;
        int safe=Ui.dp(c,8);
        int targetHeight=(overlayView!=null && overlayView.getHeight()>0)?overlayView.getHeight():Ui.dp(c,96);
        if((currentParams.gravity & Gravity.START)==Gravity.START){
            int maxX=Math.max(safe,screenWidth-targetWidth-safe);
            currentParams.x=Math.max(safe,Math.min(currentParams.x,maxX));
        }
        if((currentParams.gravity & Gravity.TOP)==Gravity.TOP){
            int maxY=Math.max(safe,screenHeight-targetHeight-safe);
            currentParams.y=Math.max(safe,Math.min(currentParams.y,maxY));
        }
    }

    private static DisplayMetrics getRealDisplayMetrics(Context c){
        DisplayMetrics out=new DisplayMetrics();
        DisplayMetrics res=c.getResources().getDisplayMetrics();
        out.density=res.density;
        out.scaledDensity=res.scaledDensity;
        int rw=res.widthPixels;
        int rh=res.heightPixels;
        try{
            if(wm!=null && Build.VERSION.SDK_INT>=30){
                WindowMetrics metrics=wm.getCurrentWindowMetrics();
                Rect b=metrics.getBounds();
                if(b.width()>0 && b.height()>0){ rw=b.width(); rh=b.height(); }
            }
        }catch(Exception ignored){}
        try{
            if(wm!=null && (rw<=0 || rh<=0)){
                Display d=wm.getDefaultDisplay();
                if(d!=null){ Point size=new Point(); d.getRealSize(size); if(size.x>0 && size.y>0){ rw=size.x; rh=size.y; } }
            }
        }catch(Exception ignored){}
        try{
            int orientation=c.getResources().getConfiguration().orientation;
            if(orientation==Configuration.ORIENTATION_PORTRAIT && rw>rh){ int tmp=rw; rw=rh; rh=tmp; }
            else if(orientation==Configuration.ORIENTATION_LANDSCAPE && rh>rw){ int tmp=rw; rw=rh; rh=tmp; }
        }catch(Exception ignored){}
        out.widthPixels=Math.max(rw,Ui.dp(c,320));
        out.heightPixels=Math.max(rh,Ui.dp(c,320));
        return out;
    }

    private static boolean handleTouch(Context c, MotionEvent e){
        if (seekTouchActive) return false;
        if(panelView==null || currentParams==null || wm==null || contentSoftHidden) return false;
        switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                h.removeCallbacks(hideTask);
                if(fixedWindowEditMode) keepEditModeAlive();
                cancelLongPressTask();
                int[] location = new int[2];
                try { panelView.getLocationOnScreen(location); } catch (Throwable ignored) { location[0]=currentParams.x; location[1]=currentParams.y; }
                downX=location[0];
                downY=location[1];
                downGravity=currentParams.gravity;
                touchX=e.getRawX();
                touchY=e.getRawY();
                dragging=false;
                dragArmed=false;
                longPressTriggered=false;
                pendingLongPressTask = () -> {
                    Prefs lpPrefs = new Prefs(c);
                    if(lpPrefs.featureFixedWindow()) showEditMode(c);
                    dragArmed = true;
                    longPressTriggered = true;
                    try { panelView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); } catch (Throwable ignored) {}
                };
                h.postDelayed(pendingLongPressTask, ViewConfiguration.getLongPressTimeout());
                return true;

            case MotionEvent.ACTION_POINTER_DOWN:
                if(beginPinchResize(c, e, new Prefs(c))) return true;
                return true;

            case MotionEvent.ACTION_MOVE:
                if(resizingFixedWindow && e.getPointerCount() >= 2){
                    return updatePinchResize(c, e);
                }
                float moveDx=e.getRawX()-touchX;
                float moveDy=e.getRawY()-touchY;
                boolean movedEnough=Math.abs(moveDx)>TAP_SLOP_PX || Math.abs(moveDy)>TAP_SLOP_PX;
                if(!dragArmed){
                    if(movedEnough) cancelLongPressTask();
                    return true;
                }
                int nx=downX+(int)moveDx;
                int ny=downY+(int)moveDy;
                dragging=true;
                if(fixedWindowEditMode) keepEditModeAlive();
                currentParams.gravity=Gravity.TOP|Gravity.START;
                currentParams.x=Math.max(0,nx);
                currentParams.y=Math.max(0,ny);
                try{wm.updateViewLayout(overlayView,currentParams);}catch(Exception ignored){}
                return true;

            case MotionEvent.ACTION_POINTER_UP:
                if(resizingFixedWindow){
                    finishPinchResize(c);
                    return true;
                }
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if(resizingFixedWindow) finishPinchResize(c);
                cancelLongPressTask();
                long now=SystemClock.uptimeMillis();
                float dx=e.getRawX()-touchX;
                float dy=e.getRawY()-touchY;
                boolean smallMove=Math.abs(dx)<=TAP_SLOP_PX && Math.abs(dy)<=TAP_SLOP_PX;

                Prefs touchPrefs=new Prefs(c);
                boolean horizontalSwipe=Math.abs(dx)>=Ui.dp(c,70) && Math.abs(dx)>Math.abs(dy)*1.55f;
                if(touchPrefs.featureSwipeTracks() && horizontalSwipe && !longPressTriggered){
                    // Fast swipe must not move the overlay; dragging is allowed only after long press.
                    sendMediaKey(c, dx>0 ? KeyEvent.KEYCODE_MEDIA_NEXT : KeyEvent.KEYCODE_MEDIA_PREVIOUS);
                    lastTapUpMs=0L;
                } else if(!dragging && smallMove && now-lastTapUpMs<=DOUBLE_TAP_MS){
                    resetToSelectedPreset(c);
                    lastTapUpMs=0L;
                } else {
                    if(!dragging && smallMove) lastTapUpMs=now;
                    if(dragging) {
                        if(touchPrefs.featureSnap()) {
                            snapToNearestZone(c);
                        } else {
                            touchPrefs.setOverlayPosition(currentParams.x,currentParams.y);
                            lastAppliedCustomPosition=true;
                        }
                        if(fixedWindowEditMode) keepEditModeAlive();
                    }
                }
                restartHideTimer(touchPrefs);
                dragging=false;
                dragArmed=false;
                longPressTriggered=false;
                return true;
        }
        return false;
    }

    private static void cancelLongPressTask(){
        if(pendingLongPressTask!=null){
            h.removeCallbacks(pendingLongPressTask);
            pendingLongPressTask=null;
        }
    }

    private static void snapToNearestZone(Context c){
        if(currentParams==null)return;
        Prefs prefs = new Prefs(c);
        DisplayMetrics dm=getRealDisplayMetrics(c);
        int screenW=Math.max(dm.widthPixels,Ui.dp(c,320));
        int screenH=Math.max(dm.heightPixels,Ui.dp(c,320));
        int safe=Ui.dp(c,12);
        int width=currentParams.width>0?currentParams.width:Math.max(Ui.dp(c,220), overlayView!=null?overlayView.getWidth():Ui.dp(c,260));
        int height=(overlayView!=null && overlayView.getHeight()>0)?overlayView.getHeight():Ui.dp(c,96);
        width=Math.min(width, Math.max(Ui.dp(c,160), screenW-safe*2));
        height=Math.min(height, Math.max(Ui.dp(c,64), screenH-safe*2));

        int left=safe;
        int centerX=Math.max(safe,(screenW-width)/2);
        int right=Math.max(safe,screenW-width-safe);
        int top=safe+Ui.dp(c,8);
        int centerY=Math.max(safe,(screenH-height)/2);
        int bottom=Math.max(safe,screenH-height-safe-Ui.dp(c,8));
        int upperMidY = top + Math.max(0, (centerY - top) / 2);
        int lowerMidY = centerY + Math.max(0, (bottom - centerY) / 2);
        int[][] presets=new int[][]{
                {0, left, top},
                {1, centerX, top},
                {2, right, top},
                {7, left, upperMidY},
                {8, centerX, upperMidY},
                {9, right, upperMidY},
                {10, left, centerY},
                {3, centerX, centerY},
                {11, right, centerY},
                {12, left, lowerMidY},
                {13, centerX, lowerMidY},
                {14, right, lowerMidY},
                {4, left, bottom},
                {5, centerX, bottom},
                {6, right, bottom}
        };
        int currentCenterX=currentParams.x+width/2;
        int currentCenterY=currentParams.y+height/2;
        int bestPreset=1;
        long best=Long.MAX_VALUE;
        for(int[] preset : presets){
            int sx=preset[1];
            int sy=preset[2];
            int cx=sx+width/2;
            int cy=sy+height/2;
            long dx=cx-currentCenterX;
            long dy=cy-currentCenterY;
            long dist=dx*dx+dy*dy;
            if(dist<best){
                best=dist;
                bestPreset=preset[0];
            }
        }
        prefs.setSnapPosition(bestPreset);
        currentParams=params(c,prefs);
        lastAppliedCustomPosition=false;
        lastAppliedPosition=prefs.position();
        try{wm.updateViewLayout(overlayView,currentParams);}catch(Exception ignored){}
    }

    private static void resetToSelectedPreset(Context c){
        Prefs p=new Prefs(c);
        p.resetOverlayPositionToPreset();
        currentParams=params(c,p);
        lastAppliedCustomPosition=p.customPosition();
        lastAppliedPosition=p.position();
        if(overlayView!=null && wm!=null){ try{wm.updateViewLayout(overlayView,currentParams);}catch(Exception ignored){} }
    }

    private static WindowManager.LayoutParams params(Context c,Prefs p){
        int type=Build.VERSION.SDK_INT>=26?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY:WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams lp=new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        int edgeX = Ui.dp(c, Math.max(0, p.paddingX()));
        int edgeY = Ui.dp(c, Math.max(0, p.paddingY()));
        int centerShiftX = Ui.dp(c, p.paddingX() - 20);
        int centerShiftY = Ui.dp(c, p.paddingY() - 14);
        DisplayMetrics dm = getRealDisplayMetrics(c);
        int upperMidY = Math.max(edgeY, Math.max(0, dm.heightPixels / 4) + centerShiftY / 2);
        int lowerMidY = Math.max(edgeY, Math.max(0, dm.heightPixels / 4) - centerShiftY / 2);
        if(p.customPosition()){ lp.gravity=Gravity.TOP|Gravity.START; lp.x=Math.max(0,p.overlayX()); lp.y=Math.max(0,p.overlayY()); return lp; }
        int pos=p.snapPosition() >= 0 ? p.snapPosition() : p.position();
        if(pos==0){lp.gravity=Gravity.TOP|Gravity.START;lp.x=edgeX;lp.y=edgeY;}
        else if(pos==1){lp.gravity=Gravity.TOP|Gravity.CENTER_HORIZONTAL;lp.x=centerShiftX;lp.y=edgeY;}
        else if(pos==2){lp.gravity=Gravity.TOP|Gravity.END;lp.x=edgeX;lp.y=edgeY;}
        else if(pos==3){lp.gravity=Gravity.CENTER;lp.x=centerShiftX;lp.y=centerShiftY;}
        else if(pos==4){lp.gravity=Gravity.BOTTOM|Gravity.START;lp.x=edgeX;lp.y=edgeY;}
        else if(pos==5){lp.gravity=Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL;lp.x=centerShiftX;lp.y=edgeY;}
        else if(pos==6){lp.gravity=Gravity.BOTTOM|Gravity.END;lp.x=edgeX;lp.y=edgeY;}
        else if(pos==7){lp.gravity=Gravity.TOP|Gravity.START;lp.x=edgeX;lp.y=upperMidY;}
        else if(pos==8){lp.gravity=Gravity.TOP|Gravity.CENTER_HORIZONTAL;lp.x=centerShiftX;lp.y=upperMidY;}
        else if(pos==9){lp.gravity=Gravity.TOP|Gravity.END;lp.x=edgeX;lp.y=upperMidY;}
        else if(pos==10){lp.gravity=Gravity.CENTER_VERTICAL|Gravity.START;lp.x=edgeX;lp.y=centerShiftY;}
        else if(pos==11){lp.gravity=Gravity.CENTER_VERTICAL|Gravity.END;lp.x=edgeX;lp.y=centerShiftY;}
        else if(pos==12){lp.gravity=Gravity.BOTTOM|Gravity.START;lp.x=edgeX;lp.y=lowerMidY;}
        else if(pos==13){lp.gravity=Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL;lp.x=centerShiftX;lp.y=lowerMidY;}
        else if(pos==14){lp.gravity=Gravity.BOTTOM|Gravity.END;lp.x=edgeX;lp.y=lowerMidY;}
        else {lp.gravity=Gravity.TOP|Gravity.CENTER_HORIZONTAL;lp.x=centerShiftX;lp.y=edgeY;}
        return lp;
    }

    public static void dimTemporarily(Context raw){
        Context c=raw.getApplicationContext();
        if(!new Prefs(c).featureVolumeDim())return;
        h.post(() -> {
        if(panelView==null)return;
        try{
            Prefs prefs = new Prefs(c);
            int basePercent = Math.max(15, Math.min(100, prefs.windowAlpha()));
            int dimPercent = Math.max(12, Math.round(basePercent * 0.35f));
            volumeDimUntilAt = SystemClock.uptimeMillis() + 900L;
            OverlayRenderHelper.applyPanelBackgroundAlpha(panelView, dimPercent);
            OverlayRenderHelper.applyContentDimAlpha(lineView, albumArtView, controlsRow, seekContainer, 0.38f);
        }catch(Exception ignored){}
            h.removeCallbacks(undimTask);
            h.postDelayed(undimTask,900);
        });
    }

    private static void restoreAlphaOnMain(){
        if(panelView==null)return;
        volumeDimUntilAt = 0L;
        try{
            OverlayRenderHelper.applyPanelBackgroundAlpha(panelView, Math.max(15,Math.min(100,new Prefs(panelView.getContext()).windowAlpha())));
            OverlayRenderHelper.applyContentDimAlpha(lineView, albumArtView, controlsRow, seekContainer, 1f);
        }catch(Exception ignored){}
    }

    private static int effectiveWindowAlphaPercent(Prefs prefs) {
        return OverlayRenderHelper.effectiveWindowAlphaPercent(prefs, volumeDimUntilAt, SystemClock.uptimeMillis());
    }

    private static void applyPanelBackground(Context c, int bgColor, int strokeWidth, int strokeColor, int corner, int alphaPercent, int design) {
        OverlayRenderHelper.applyPanelBackground(c, panelView, bgColor, strokeWidth, strokeColor, corner, alphaPercent, design);
    }

    private static void applyPanelBackgroundAlpha(int alphaPercent) {
        OverlayRenderHelper.applyPanelBackgroundAlpha(panelView, alphaPercent);
    }

    private static void applyContentDimAlpha(float alpha) {
        OverlayRenderHelper.applyContentDimAlpha(lineView, albumArtView, controlsRow, seekContainer, alpha);
    }

    private static int windowPercentToDrawableAlpha(int alphaPercent) {
        return OverlayRenderHelper.windowPercentToDrawableAlpha(alphaPercent);
    }

    private static boolean sameBitmap(Bitmap a, Bitmap b) {
        return OverlayRenderHelper.sameBitmap(a, b);
    }

    public static void refresh(Context c){
        if(overlayView==null && lineView==null)return;
        Context app=c.getApplicationContext();
        boolean testMode=isTestModeActive();
        h.post(() -> showOrUpdateOnMain(app,lastTitle,lastArtist,lastAlbumArt,false,testMode?TEST_DISPLAY_MS:-1,testMode));
    }
    public static void noteTransientInterruption(Context raw, long quietMs) {
        Context app = raw.getApplicationContext();
        h.post(() -> {
            lastAppContext = app;
            Prefs prefs = new Prefs(app);
            if (!prefs.featureSoftRecoverySystemWindows() && prefs.conflictApps().isEmpty()) return;
            long now = SystemClock.uptimeMillis();
            long delay = Math.max(1200L, quietMs);
            transientRecoveryNotBeforeAt = Math.max(transientRecoveryNotBeforeAt, now + delay);
            sessionRecoveryRestartRequested = shouldSessionBeVisibleAt(now) || isSessionCurrentlyPresented();
            if (!contentSoftHidden) transientHoldOnMain();
            h.removeCallbacks(recoveryTask);
            h.postDelayed(recoveryTask, Math.max(300L, delay + 120L));
        });
    }
    public static void requestRecoveryIfNeeded(Context raw, TrackSnapshot track) {
        if (track == null || !track.hasText()) return;
        Context app = raw.getApplicationContext();
        final String title = clean(track.title);
        final String artist = clean(track.artist);
        final Bitmap art = track.albumArt;
        h.post(() -> {
            Prefs prefs = new Prefs(app);
            if (!prefs.featureSoftRecoverySystemWindows() && prefs.conflictApps().isEmpty()) return;
            attemptRecoveryIfNeededOnMain(app, title, artist, art);
        });
    }
    public static void hide(){ h.post(TrackOverlayManager::hideByEngineOnMain); }
    public static void hideNow(){ h.post(TrackOverlayManager::hideNowRespectingTestModeOnMain); }
    public static void hideNowForce(){ h.post(TrackOverlayManager::hardResetOverlay); }
    public static void markRestoreRequired(){ h.post(() -> {
        lastRenderedText = "";
        lastAlbumArt = null;
    }); }

    private static void hideByEngineOnMain() {
        if (isTestModeActive()) return;
        hideOnMain();
    }

    private static void hideNowRespectingTestModeOnMain() {
        if (isTestModeActive()) return;
        hardResetOverlay();
    }

    private static void hideOnMain(){
        if (sessionRecoveryRestartRequested && transientRecoveryNotBeforeAt > SystemClock.uptimeMillis()) {
            return;
        }
        h.removeCallbacks(hideTask);
        h.removeCallbacks(testHideTask);
        h.removeCallbacks(undimTask);
        h.removeCallbacks(hideEditModeTask);
        h.removeCallbacks(collapseHiddenHostTask);
        cancelLongPressTask();
        hideContentOnMain();
    }

    private static void transientHoldOnMain() {
        h.removeCallbacks(collapseHiddenHostTask);
        cancelLongPressTask();
        resetFixedWindowEditState();
        resetTouchState();
        contentSoftHidden = true;
        setOverlayCollapsedFootprint(false);
        if (panelView != null) {
            panelView.setVisibility(LinearLayout.VISIBLE);
            panelView.setAlpha(0f);
        }
        if (albumArtView != null && albumArtView.getVisibility() == ImageView.VISIBLE) {
            albumArtView.setAlpha(0f);
        }
        if (lineView != null) {
            lineView.setAlpha(0f);
        }
        if (controlsRow != null) {
            controlsRow.setAlpha(0f);
        }
        setOverlayTouchThrough(true);
    }

    private static void hideTestOnMain() {
        testModeUntilAt = 0L;
        hideOnMain();
    }

    private static void removeOldViewSafely(){
        if(overlayView==null || wm==null)return;
        overlayView.setOnTouchListener(null);
        try{ wm.removeViewImmediate(overlayView); }
        catch(Exception ignored){ try{ wm.removeView(overlayView); }catch(Exception ignored2){} }
        hostAttached = false;
    }

    private static void hideContentOnMain() {
        cancelMarquee();
        resetFixedWindowEditState();
        resetTouchState();
        resetAlbumArtRetryState();
        if (albumArtView != null) {
            albumArtView.setImageDrawable(null);
            albumArtView.setAlpha(0f);
            albumArtView.setVisibility(ImageView.GONE);
        }
        if (seekContainer != null) seekContainer.setVisibility(LinearLayout.GONE);
        resetSeekState();
        if (lineView != null) {
            lineView.setText("");
            lineView.setScrollX(0);
        }
        if (contentRow != null) {
            contentRow.setMinimumHeight(0);
            contentRow.setPadding(0, 0, 0, 0);
        }
        if (controlsRow != null) {
            controlsRow.setVisibility(LinearLayout.GONE);
        }
        if (panelView != null) {
            contentSoftHidden = true;
            panelView.setVisibility(LinearLayout.VISIBLE);
            panelView.setAlpha(0f);
        }
        sessionContinuous = false;
        pauseHideFrozen = false;
        sessionVisibleUntilAt = 0L;
        sessionRecoveryRestartRequested = false;
        transientRecoveryNotBeforeAt = 0L;
        setOverlayTouchThrough(true);
        h.removeCallbacks(collapseHiddenHostTask);
        h.postDelayed(collapseHiddenHostTask, HIDDEN_HOST_COLLAPSE_DELAY_MS);
        lastRenderedText = "";
        lastAlbumArt = null;
    }

    private static void collapseHiddenHostOnMain() {
        if (!contentSoftHidden) return;
        setOverlayCollapsedFootprint(true);
    }

    private static void setOverlayTouchThrough(boolean enabled) {
        if (currentParams == null) return;
        int oldFlags = currentParams.flags;
        if (enabled) {
            currentParams.flags = currentParams.flags | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        } else {
            currentParams.flags = currentParams.flags & ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        }
        if (oldFlags != currentParams.flags && hostAttached && overlayView != null && wm != null) {
            try { wm.updateViewLayout(overlayView, currentParams); } catch (Exception ignored) {}
        }
    }

    private static void setOverlayCollapsedFootprint(boolean collapsed) {
        if (currentParams == null) return;
        int targetWidth = collapsed ? 1 : WindowManager.LayoutParams.WRAP_CONTENT;
        int targetHeight = collapsed ? 1 : WindowManager.LayoutParams.WRAP_CONTENT;
        boolean changed = currentParams.width != targetWidth || currentParams.height != targetHeight;
        currentParams.width = targetWidth;
        currentParams.height = targetHeight;
        if (collapsed) {
            try {
                if (overlayView != null) overlayView.setMinimumWidth(0);
                if (panelView != null) {
                    panelView.setMinimumWidth(0);
                    panelView.setMinimumHeight(0);
                }
            } catch (Exception ignored) {}
        }
        if (changed && hostAttached && overlayView != null && wm != null) {
            try { wm.updateViewLayout(overlayView, currentParams); } catch (Exception ignored) {}
        }
    }

    private static void clearOverlayState() {
        h.removeCallbacks(collapseHiddenHostTask);
        cancelMarquee();
        resetAlbumArtRetryState();
        resetFixedWindowEditState();
        if (controlsRow != null) {
            controlsRow.removeAllViews();
        }
        if (albumArtView != null) {
            albumArtView.setImageDrawable(null);
        }
        if (lineView != null) {
            lineView.setText("");
        }
        if (panelView != null) {
            panelView.setOnTouchListener(null);
        }
        if (overlayView != null) {
            overlayView.removeAllViews();
        }
        overlayView = null;
        panelView = null;
        contentRow = null;
        albumArtView = null;
        lineView = null;
        controlsRow = null;
        seekContainer = null;
        seekBarView = null;
        wm = null;
        currentParams = null;
        hostAttached = false;
        contentSoftHidden = false;
        resetRenderedContentState();
        lastAppliedPosition = -1;
        lastAppliedCustomPosition = false;
        lastAppliedOffsetX = -1;
        lastAppliedOffsetY = -1;
        resetSessionTimingState();
        lastAppContext = null;
        resetSeekState();
        seekSession.reset();
        resetTouchState();
    }

    private static void hardResetOverlay() {
        removeOldViewSafely();
        clearOverlayState();
    }

    private static void attemptDeferredRecoveryOnMain() {
        long now = SystemClock.uptimeMillis();
        if (transientRecoveryNotBeforeAt > now) {
            h.removeCallbacks(recoveryTask);
            h.postDelayed(recoveryTask, Math.max(20L, transientRecoveryNotBeforeAt - now));
            return;
        }
        attemptRecoveryIfNeededOnMain(lastAppContext, lastTitle, lastArtist, lastAlbumArt);
    }

    private static void attemptRecoveryIfNeededOnMain(Context c, String title, String artist, Bitmap albumArt) {
        long now = SystemClock.uptimeMillis();
        if (c == null) c = lastAppContext;
        if (c == null) return;
        Prefs prefs = new Prefs(c);
        if (!prefs.featureSoftRecoverySystemWindows() && prefs.conflictApps().isEmpty()) return;
        if (!shouldSessionBeVisibleAt(now)) {
            sessionRecoveryRestartRequested = false;
            return;
        }
        if (transientRecoveryNotBeforeAt > now) return;
        String effectiveTitle = clean(title).isEmpty() ? lastTitle : title;
        String effectiveArtist = clean(artist).isEmpty() ? lastArtist : artist;
        Bitmap effectiveAlbumArt = albumArt != null ? albumArt : lastAlbumArt;
        if (isHostActuallyVisible()) {
            if (seekContainer != null && seekContainer.getVisibility() == LinearLayout.VISIBLE) {
                scheduleSeekProgressUpdates(c, true);
            }
            if (sessionRecoveryRestartRequested) {
                restoreVisibleContentState();
                applyTrackText(prefs, effectiveTitle, effectiveArtist);
                applyAlbumArt(c, prefs, effectiveAlbumArt);
                sessionRecoveryRestartRequested = false;
                transientRecoveryNotBeforeAt = 0L;
                if (isTestModeActiveAt(now)) {
                    testModeUntilAt = now + TEST_DISPLAY_MS;
                    sessionContinuous = false;
                    sessionVisibleUntilAt = testModeUntilAt;
                    restartTestHideTimerForMs(TEST_DISPLAY_MS);
                } else if (prefs.displayWhilePlaying()) {
                    sessionContinuous = true;
                    sessionVisibleUntilAt = Long.MAX_VALUE;
                    h.removeCallbacks(hideTask);
                } else if (pauseHideFrozen) {
                    h.removeCallbacks(hideTask);
                } else {
                    int ms = Math.max(1000, prefs.displayMs());
                    sessionContinuous = false;
                    sessionVisibleUntilAt = now + ms;
                    restartHideTimerForMs(ms);
                }
                scheduleSeekProgressUpdates(c, true);
            }
            return;
        }
        if (now - lastControlledRecoveryAt < CONTROLLED_RECOVERY_BACKOFF_MS) return;
        boolean restartFullTimer = sessionRecoveryRestartRequested;
        int hideMs = restartFullTimer
                ? (prefs.displayWhilePlaying() ? -1 : Math.max(1000, prefs.displayMs()))
                : remainingSessionMs(now, prefs);
        boolean testMode = isTestModeActiveAt(now);
        if (!prefs.displayWhilePlaying() && !testMode && hideMs <= 0) {
            sessionRecoveryRestartRequested = false;
            return;
        }
        lastControlledRecoveryAt = now;
        boolean softRestoreWorked = false;
        if (hostAttached && overlayView != null && panelView != null && currentParams != null && wm != null) {
            try {
                setOverlayCollapsedFootprint(false);
                restoreVisibleContentState();
                applyTrackText(prefs, effectiveTitle, effectiveArtist);
                applyAlbumArt(c, prefs, effectiveAlbumArt);
                setOverlayTouchThrough(false);
                wm.updateViewLayout(overlayView, currentParams);
                showOrUpdateOnMain(c, effectiveTitle, effectiveArtist, effectiveAlbumArt, true, testMode ? Math.max(500, hideMs) : hideMs, testMode);
                softRestoreWorked = true;
            } catch (Throwable ignored) {}
        }
        sessionRecoveryRestartRequested = false;
        transientRecoveryNotBeforeAt = 0L;
        if (softRestoreWorked) return;
        detachHostKeepSessionState();
        showOrUpdateOnMain(c, effectiveTitle, effectiveArtist, effectiveAlbumArt, true, testMode ? Math.max(500, hideMs) : hideMs, testMode);
    }

    private static void detachHostKeepSessionState() {
        h.removeCallbacks(collapseHiddenHostTask);
        cancelLongPressTask();
        resetAlbumArtRetryState();
        removeOldViewSafely();
        cancelMarquee();
        overlayView = null;
        panelView = null;
        contentRow = null;
        specialFixedRow = null;
        specialRightColumn = null;
        albumArtView = null;
        lineView = null;
        controlsRow = null;
        seekContainer = null;
        seekBarView = null;
        wm = null;
        currentParams = null;
        hostAttached = false;
        contentSoftHidden = false;
        controlsVisible = false;
        lastDesign = -1;
        lastRenderedText = "";
        lastTrackSnapshot = TrackSnapshot.empty();
        resetSeekState();
        resetTouchState();
        resetFixedWindowEditState();
    }

    private static void applyTrackText(Prefs p, String title, String artist) {
        if (lineView == null) return;
        String safeTitle = clean(title);
        String safeArtist = clean(artist);
        if (safeTitle.isEmpty()) safeTitle = "Неизвестный трек";
        if (safeArtist.isEmpty()) {
            lineView.setTextColor(p.titleColor());
            lineView.setText(safeTitle);
            return;
        }
        String separator = " - ";
        SpannableStringBuilder sb = new SpannableStringBuilder();
        int artistStart = 0;
        sb.append(safeArtist);
        int artistEnd = sb.length();
        sb.append(separator);
        int titleStart = sb.length();
        sb.append(safeTitle);
        int titleEnd = sb.length();
        sb.setSpan(new ForegroundColorSpan(p.artistColor()), artistStart, artistEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new ForegroundColorSpan(p.titleColor()), titleStart, titleEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        lineView.setText(sb);
    }
}
