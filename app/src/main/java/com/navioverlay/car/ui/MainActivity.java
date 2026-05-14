package com.navioverlay.car.ui;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Space;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.navioverlay.car.core.AppInfo;
import com.navioverlay.car.core.Constants;
import com.navioverlay.car.core.OverlayFonts;
import com.navioverlay.car.core.Prefs;
import com.navioverlay.car.core.Ui;
import com.navioverlay.car.engine.AppStateDetector;
import com.navioverlay.car.engine.MusicStateDetector;
import com.navioverlay.car.engine.TrackSnapshot;
import com.navioverlay.car.overlay.TrackOverlayManager;
import com.navioverlay.car.services.ForegroundDetector;
import com.navioverlay.car.services.ForegroundState;
import com.navioverlay.car.services.MonitorService;
import com.navioverlay.car.services.NavigatorAccessibilityService;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    static final int BG = 0xFF030712;
    static final int BG_2 = 0xFF071021;
    static final int CARD = 0xFF101827;
    static final int CARD_TOP = 0xFF18233A;
    static final int CARD_2 = 0xFF1A2538;
    static final int FIELD = 0xFF0B1220;
    static final int STROKE = 0x4460708A;
    static final int STROKE_SOFT = 0x223E4C61;
    static final int TEXT = 0xFFFFFFFF;
    static final int MUTED = 0xFFC4CBD7;
    static final int MUTED_2 = 0xFF8EA0B8;
    static final int GREEN = 0xFF22C55E;
    static final int WARN = 0xFFFFB020;

    private Prefs prefs;
    private LinearLayout root;
    private TextView statusView;
    private Switch enableSwitch;
    private Switch onlyTriggerSwitch;
    private Switch englishUiSwitch;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = new Prefs(this);
        requestPostNotificationsRuntimeOnly();
        buildUi();
        refreshStatus();
        if (prefs.enabled()) MonitorService.start(this);
    }

    @Override protected void onResume() {
        super.onResume();
        if (prefs.enabled()) MonitorService.poke(this);
        refreshStatus();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setBackground(gradient(BG, BG_2, 0));

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        Ui.pad(root, 20, 24, 20, 30);
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));
        setContentView(scroll);

        TextView title = tv("Navi Overlay", 36, TEXT, Typeface.BOLD, Gravity.CENTER);
        title.setLetterSpacing(0.01f);
        root.addView(title, matchWrap());
        TextView subtitle = tv(tr("Музыка поверх навигатора — аккуратно и без отвлечения", "Music over navigation — clean and distraction-free"), 15, MUTED, Typeface.NORMAL, Gravity.CENTER);
        subtitle.setLineSpacing(Ui.dp(this, 2), 1.0f);
        root.addView(subtitle, matchWrap());
        space(root, 18);

        addStatusCard();
        addMainCard();
        addAppsCard();
        addSettingsCard();
        addPermissionsCard();
        addDiagnosticsCard();
        addSupportCard();
    }

    private void addStatusCard() {
        LinearLayout c = card(tr("Состояние", "Status"), tr("Короткая сводка по движку, разрешениям и активным приложениям.", "A short summary of the engine, permissions and active apps."));
        statusView = tv("", 16, TEXT, Typeface.BOLD, Gravity.START);
        statusView.setGravity(Gravity.CENTER);
        statusView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        statusView.setLineSpacing(Ui.dp(this, 4), 1.0f);
        statusView.setBackground(Ui.stroke(FIELD, 1, STROKE_SOFT, 18, this));
        Ui.pad(statusView, 16, 14, 16, 14);
        c.addView(statusView, smallGapLp());
        Button appStateButton = previewButton(tr("Подробнее", "Details"), v -> showAppStateDialog());
        appStateButton.setTextSize(14);
        appStateButton.setMinHeight(0);
        appStateButton.setMinimumHeight(0);
        LinearLayout.LayoutParams appStateLp = new LinearLayout.LayoutParams(-1, Ui.dp(this, 44));
        appStateLp.setMargins(0, Ui.dp(this, 10), 0, 0);
        c.addView(appStateButton, appStateLp);
    }

    private void addMainCard() {
        LinearLayout c = card(tr("Главное", "Main"), tr("Быстрое включение приложения, основной режим показа и язык интерфейса.", "Quick app enable, core display mode and interface language."));
        enableSwitch = switchRow(c, tr("Включить приложение", "Enable app"), prefs.enabled(), on -> {
            prefs.setEnabled(on);
            if (on) MonitorService.start(this); else { MonitorService.stop(this); TrackOverlayManager.hideNowForce(); }
            refreshStatus();
        });
        onlyTriggerSwitch = switchRow(c, tr("Показывать только поверх выбранных приложений", "Show only over selected apps"), prefs.showOnlyWithTrigger(), on -> {
            prefs.setShowOnlyWithTrigger(on);
            TrackOverlayManager.hideNowForce();
        });
        englishUiSwitch = switchRow(c, "English UI", prefs.englishUi(), on -> {
            prefs.setEnglishUi(on);
            recreate();
        });
    }

    private void addAppsCard() {
        LinearLayout c = card(tr("Приложения", "Apps"), tr("Выбор навигаторов и плееров вынесен в отдельные аккуратные списки.", "Choose navigation apps and music players in separate lists."));
        c.addView(menuButton(tr("Приложения навигации", "Navigation apps"), tr("Где показывать окно", "Where the window may appear"), v -> AppSelectionDialogHelper.show(this, prefs, AppSelectionDialogHelper.MODE_TRIGGERS)), smallGapLp());
        c.addView(menuButton(tr("Аудио приложения", "Audio apps"), tr("Откуда брать название трека", "Where track data comes from"), v -> AppSelectionDialogHelper.show(this, prefs, AppSelectionDialogHelper.MODE_PLAYERS)), smallGapLp());
        c.addView(menuButton(
                tr("Приложения с приоритетным окном", "Priority overlay apps"),
                tr("Антирадар и другие приложения, чьи собственные окна должны временно иметь приоритет", "Radar and similar apps whose own windows should temporarily take priority"),
                v -> AppSelectionDialogHelper.show(this, prefs, AppSelectionDialogHelper.MODE_CONFLICTS)), smallGapLp());
    }

    private void addSettingsCard() {
        LinearLayout c = card(tr("Настройки", "Settings"), tr("Все настройки окна, текста, цветов и дополнительных функций собраны в одном месте.", "All window, text, color and extra feature settings are collected in one place."));
        c.addView(menuButton(tr("Настройки текста", "Text settings"), tr("Размер текста, шрифт и жирный шрифт", "Text size, font and bold text"), v -> showTextSettingsDialog()), smallGapLp());
        c.addView(menuButton(tr("Настройки окна", "Window settings"), tr("Прозрачность, форма, отступы, время и положение", "Transparency, shape, offsets, time and position"), v -> showWindowSettingsDialog()), smallGapLp());
        c.addView(menuButton(tr("Настройки цвета", "Color settings"), tr("Цвет окна, рамки, кнопок и текста", "Window, border, control and text colors"), v -> showColorSettingsDialog()), smallGapLp());
        c.addView(menuButton(tr("Дополнительные функции", "Extra features"), tr("Управление музыкой, обложка, fixed mode и прочее", "Music controls, album art, fixed mode and more"), v -> showAdditionalFeaturesDialog()), smallGapLp());
    }

    private void addPermissionsCard() {
        LinearLayout c = card(tr("Разрешения", "Permissions"), tr("Выдай все разрешения один раз, чтобы движок работал стабильно.", "Grant permissions once so the engine works reliably."));
        c.addView(menuButton(tr("Поверх окон", "Draw over apps"), tr("Разрешить показ окна над навигатором", "Allow the window above navigation apps"), v -> startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())))), smallGapLp());
        c.addView(menuButton(tr("Доступ к уведомлениям", "Notification access"), tr("Нужен для MediaSession и уведомлений плееров", "Needed for MediaSession and player notifications"), v -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))), smallGapLp());
        c.addView(menuButton(tr("Уведомления приложения", "App notifications"), tr("Нужно только на Android 13+", "Needed only on Android 13+"), v -> openPostNotificationsFlow()), smallGapLp());
        c.addView(menuButton(tr("Специальные возможности", "Accessibility"), tr("Определяет, открыт ли навигатор на экране", "Detects whether navigation is on screen"), v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))), smallGapLp());
        c.addView(menuButton(tr("История использования", "Usage access"), tr("Запасной способ определения активного приложения", "Fallback way to detect the active app"), v -> startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))), smallGapLp());
    }

    private void addDiagnosticsCard() {
        LinearLayout c = card(tr("Диагностика", "Diagnostics"), tr("Короткая проверка, если окно не появляется.", "Quick checks if the window does not appear."));
        c.addView(menuButton(tr("Проверить активное приложение", "Check active app"), tr("Показать текущий пакет Android", "Show the current Android package"), v -> {
            refreshStatus();
            Toast.makeText(this, ForegroundDetector.debugCurrent(this), Toast.LENGTH_LONG).show();
        }), smallGapLp());
        c.addView(menuButton(tr("Проверить и восстановить", "Check and recover"), tr("Откроет нужные настройки только если правда есть проблема", "Opens the needed settings only if there is a real problem"), v -> checkAndRecover()), smallGapLp());
        c.addView(menuButton(tr("Скопировать отчёт", "Copy report"), tr("Собрать статусы разрешений, навигатора и музыки", "Collect permission, navigator and music status"), v -> copyDiagnosticsReport()), smallGapLp());
        c.addView(menuButton(tr("История последних треков", "Recent track history"), tr("Открыть список последних 30 треков и быстро копировать названия", "Open the latest 30 tracks and quickly copy song names"), v -> InfoDialogsHelper.showRecentTrackHistoryDialog(this, prefs)), smallGapLp());
        c.addView(menuButton(tr("Описание функций", "Feature descriptions"), tr("Коротко о том, что делает каждая функция приложения", "Short guide explaining what each app function does"), v -> InfoDialogsHelper.showInstructionsDialog(this, prefs)), smallGapLp());
        c.addView(menuButton(
                tr("Инструкция по настройкам", "Setup instructions"),
                tr("Что нужно включить для корректной и стабильной работы приложения", "What to enable for correct and stable app operation"),
                v -> InfoDialogsHelper.showSetupInstructionsDialog(this, prefs)), smallGapLp());
    }

    private void addSupportCard() {
        LinearLayout c = card(
                tr("Поддержка автора", "Support the author"),
                tr("Если приложение оказалось полезным, то Вы можете поддержать его развитие и автора проекта.", "If the app turned out to be useful, you can support its development and the author of the project."));
        c.addView(previewButton(tr("Выразить благодарность", "Show appreciation"), v -> {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://yoomoney.ru/to/4100119532290079"));
                startActivity(intent);
            } catch (Throwable t) {
                Toast.makeText(this, tr("Не удалось открыть ссылку", "Failed to open the link"), Toast.LENGTH_SHORT).show();
            }
        }), btnLp());
    }

    private interface BoolCb { void set(boolean b); }
    private Switch switchRow(LinearLayout parent, String label, boolean checked, BoolCb cb) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(Ui.stroke(CARD_2, 1, STROKE_SOFT, 18, this));
        Ui.pad(row, 14, 12, 12, 12);

        TextView name = tv(label, 16, TEXT, Typeface.BOLD, Gravity.START);
        row.addView(name, new LinearLayout.LayoutParams(0, -2, 1));

        Switch sw = new Switch(this);
        sw.setChecked(checked);
        sw.setOnCheckedChangeListener((buttonView, isChecked) -> cb.set(isChecked));
        row.addView(sw, new LinearLayout.LayoutParams(Ui.dp(this, 56), -2));
        parent.addView(row, smallGapLp());
        return sw;
    }

    private void showTextSettingsDialog() {
        final Dialog dialog = fullDialog();
        LinearLayout outer = dialogRoot();
        dialog.setContentView(outer);

        outer.addView(tv(tr("Настройки текста", "Text settings"), 24, TEXT, Typeface.BOLD, Gravity.CENTER), matchWrap());
        outer.addView(tv(tr("Настройки текста применяются сразу. Здесь можно изменить размер и стиль текста.", "Text settings apply immediately. Change size and style here."), 14, MUTED, Typeface.NORMAL, Gravity.CENTER), matchWrap());
        space(outer, 12);

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(false);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        Ui.pad(box, 0, 0, 0, 18);
        sv.addView(box, new ScrollView.LayoutParams(-1, -2));
        outer.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));

        addSeek(box, tr("Размер текста", "Text size"), 1, 100, prefs.textSize(), v -> { prefs.setTextSize(v); TrackOverlayManager.refresh(this); });
        switchRow(box, tr("Жирный шрифт", "Bold text"), prefs.textBold(), on -> { prefs.setTextBold(on); TrackOverlayManager.refresh(this); });
        switchRow(box, tr("Тень текста", "Text shadow"), prefs.textShadow(), on -> { prefs.setTextShadow(on); TrackOverlayManager.refresh(this); });
        box.addView(menuButton(tr("Выравнивание текста", "Text alignment"), tr("Выбор: слева или по центру", "Choose: left or center"), v -> showTextAlignmentDialog()), smallGapLp());
        box.addView(menuButton(tr("Шрифт окна", "Window font"), tr("Выбор шрифта из набора образцов", "Pick a font from preview samples"), v -> showFontDialog()), smallGapLp());
        box.addView(previewButton(tr("Проверить окно", "Preview window"), v -> TrackOverlayManager.test(this)), btnLp());

        outer.addView(secondaryButton(tr("Готово", "Done"), v -> dialog.dismiss()), btnLp());
        dialog.show();
        fitDialog(dialog, true);
    }

    private void showWindowSettingsDialog() {
        final Dialog dialog = fullDialog();
        LinearLayout outer = dialogRoot();
        dialog.setContentView(outer);

        outer.addView(tv(tr("Настройки окна", "Window settings"), 24, TEXT, Typeface.BOLD, Gravity.CENTER), matchWrap());
        outer.addView(tv(tr("Настройки окна применяются сразу. Здесь собраны форма, время и положение overlay.", "Window settings apply immediately. Shape, timing and position live here."), 14, MUTED, Typeface.NORMAL, Gravity.CENTER), matchWrap());
        space(outer, 12);

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(false);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        Ui.pad(box, 0, 0, 0, 18);
        sv.addView(box, new ScrollView.LayoutParams(-1, -2));
        outer.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));

        addSeek(box, tr("Прозрачность окна", "Window transparency"), 15, 100, prefs.windowAlpha(), v -> { prefs.setWindowAlpha(v); TrackOverlayManager.refresh(this); });
        addSeek(box, tr("Скругление углов", "Corner radius"), 0, 40, prefs.corner(), v -> { prefs.setCorner(v); TrackOverlayManager.refresh(this); });
        addSeek(box, tr("Толщина рамки окна", "Window border thickness"), 0, 12, prefs.borderWidth(), v -> { prefs.setBorderWidth(v); TrackOverlayManager.refresh(this); });
        addSeek(box, tr("Горизонтальный отступ", "Horizontal offset"), 8, 48, prefs.paddingX(), v -> { prefs.setPaddingX(v); TrackOverlayManager.refresh(this); });
        addSeek(box, tr("Вертикальный отступ", "Vertical offset"), 6, 100, prefs.paddingY(), v -> { prefs.setPaddingY(v); TrackOverlayManager.refresh(this); });
        addSeek(box, tr("Время показа, сек.", "Display time, sec."), 1, 10, Math.max(1, prefs.displayMs() / 1000), v -> { prefs.setDisplayMs(v * 1000); TrackOverlayManager.refresh(this); });
        TextView displayHint = tv(
                tr("Чтобы окно не скрывалось само, включи функцию «Показывать окно постоянно».", "To keep the window visible, enable “Show window continuously”."),
                12, MUTED_2, Typeface.NORMAL, Gravity.START);
        LinearLayout.LayoutParams displayHintLp = matchWrap();
        displayHintLp.setMargins(Ui.dp(this, 8), Ui.dp(this, 2), Ui.dp(this, 8), 0);
        box.addView(displayHint, displayHintLp);
        box.addView(menuButton(tr("Положение окна", "Window position"), tr("Выбор одного из стандартных положений окна", "Choose one of the standard window positions"), v -> showPositionDialog()), smallGapLp());
        box.addView(menuButton(tr("Пресет дизайна", "Design preset"), tr("Готовые стили формы и оформления overlay", "Ready-made overlay shape and style presets"), v -> showDesignPresetDialog()), smallGapLp());
        box.addView(menuButton(tr("Настройка кнопок управления музыкой", "Music control buttons settings"), tr("Размер, форма и расстояние между кнопками", "Size, shape and spacing of control buttons"), v -> showControlButtonsDialog()), smallGapLp());
        box.addView(menuButton(tr("Поведение окна при паузе", "Window behavior on pause"), tr("Что делать с окном, если трек поставлен на паузу", "What the window should do when the track is paused"), v -> showPauseBehaviorDialog()), smallGapLp());
        box.addView(previewButton(tr("Проверить окно", "Preview window"), v -> TrackOverlayManager.test(this)), btnLp());

        outer.addView(secondaryButton(tr("Готово", "Done"), v -> dialog.dismiss()), btnLp());
        dialog.show();
        fitDialog(dialog, true);
    }

    private void showColorSettingsDialog() {
        final Dialog dialog = fullDialog();
        LinearLayout outer = dialogRoot();
        dialog.setContentView(outer);

        outer.addView(tv(tr("Настройки цвета", "Color settings"), 24, TEXT, Typeface.BOLD, Gravity.CENTER), matchWrap());
        outer.addView(tv(tr("Цветовые настройки применяются сразу. Здесь собраны все цвета overlay.", "Color settings apply immediately. All overlay colors are collected here."), 14, MUTED, Typeface.NORMAL, Gravity.CENTER), matchWrap());
        space(outer, 12);

        ScrollView sv = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        Ui.pad(box, 0, 0, 0, 18);
        sv.addView(box, new ScrollView.LayoutParams(-1, -2));
        outer.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));

        box.addView(menuButton(tr("Выбор цвета окна", "Window color"), tr("Палитра фона окна", "Background palette"), v -> showColorDialog(0)), smallGapLp());
        box.addView(menuButton(tr("Цвет рамки окна", "Window border color"), tr("Палитра для контура overlay", "Overlay border palette"), v -> showBorderColorDialog(false)), smallGapLp());
        box.addView(menuButton(tr("Цвет рамок кнопок", "Control border color"), tr("Палитра для кнопок ⏮ ⏯ ⏭", "Palette for ⏮ ⏯ ⏭ buttons"), v -> showBorderColorDialog(true)), smallGapLp());
        box.addView(menuButton(tr("Цвет полосы перемотки", "Seek bar color"), tr("Цвет самой линии перемотки трека", "Color of the track seek line"), v -> showColorDialog(3)), smallGapLp());
        box.addView(menuButton(tr("Цвет ползунка перемотки", "Seek thumb color"), tr("Цвет кружка, за который тянут полосу", "Color of the draggable seek thumb"), v -> showColorDialog(4)), smallGapLp());
        box.addView(menuButton(tr("Цвет исполнителя", "Artist color"), tr("Отдельный цвет для исполнителя", "Separate color for artist text"), v -> showColorDialog(1)), smallGapLp());
        box.addView(menuButton(tr("Цвет названия песни", "Track title color"), tr("Отдельный цвет для названия песни", "Separate color for song title"), v -> showColorDialog(2)), smallGapLp());
        box.addView(previewButton(tr("Проверить окно", "Preview window"), v -> TrackOverlayManager.test(this)), btnLp());

        outer.addView(secondaryButton(tr("Готово", "Done"), v -> dialog.dismiss()), btnLp());
        dialog.show();
        fitDialog(dialog, true);
    }

    private void showAdditionalFeaturesDialog() {
        final Dialog dialog = fullDialog();
        LinearLayout outer = dialogRoot();
        dialog.setContentView(outer);

        outer.addView(tv(tr("Дополнительные функции", "Extra features"), 24, TEXT, Typeface.BOLD, Gravity.CENTER), matchWrap());
        outer.addView(tv(tr("Включай только то, что реально нужно. Все функции применяются сразу.", "Enable only what you really need. Changes apply immediately."), 14, MUTED, Typeface.NORMAL, Gravity.CENTER), matchWrap());
        space(outer, 12);

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(false);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        Ui.pad(box, 0, 0, 0, 18);
        sv.addView(box, new ScrollView.LayoutParams(-1, -2));
        outer.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));

        switchRow(box, tr("Кнопки управления музыкой в overlay", "Media control buttons in overlay"), prefs.featureControls(), on -> { prefs.setFeatureControls(on); TrackOverlayManager.refresh(this); });
        switchRow(box, tr("Свайп по overlay: влево/вправо переключает треки", "Swipe on overlay: left/right switches tracks"), prefs.featureSwipeTracks(), on -> prefs.setFeatureSwipeTracks(on));
        switchRow(box, tr("Snap: магнит к краям и центру после перетаскивания", "Snap: magnet to edges and center after dragging"), prefs.featureSnap(), on -> prefs.setFeatureSnap(on));
        switchRow(box, tr("При изменении громкости временно приглушать окно", "Temporarily dim the window on volume change"), prefs.featureVolumeDim(), on -> prefs.setFeatureVolumeDim(on));
        switchRow(box, tr("Floating режим: показывать трек даже без навигатора", "Floating mode: show the track even without navigation"), prefs.featureFloating(), on -> prefs.setFeatureFloating(on));
        switchRow(box, tr("Показывать обложку в окне", "Show album art in the window"), prefs.featureAlbumArt(), on -> { prefs.setFeatureAlbumArt(on); TrackOverlayManager.refresh(this); });
        switchRow(box, tr("Сворачивать окно вместе с навигацией", "Hide the window together with navigation"), prefs.featureHideWithNavigation(), on -> prefs.setFeatureHideWithNavigation(on));
        Switch alwaysOnSwitch = switchRow(box, tr("Показывать окно постоянно", "Show window continuously"), prefs.displayWhilePlaying(), on -> {});
        LinearLayout seekRow = new LinearLayout(this);
        seekRow.setOrientation(LinearLayout.HORIZONTAL);
        seekRow.setGravity(Gravity.CENTER_VERTICAL);
        seekRow.setBackground(Ui.stroke(CARD_2, 1, STROKE_SOFT, 18, this));
        Ui.pad(seekRow, 14, 12, 12, 12);
        TextView seekName = tv(tr("Полоса перемотки трека", "Track seek bar"), 16, TEXT, Typeface.BOLD, Gravity.START);
        seekRow.addView(seekName, new LinearLayout.LayoutParams(0, -2, 1));
        Switch seekSwitch = new Switch(this);
        seekSwitch.setChecked(prefs.featureSeekBar());
        seekSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.setFeatureSeekBar(isChecked);
            TrackOverlayManager.refresh(this);
        });
        seekRow.addView(seekSwitch, new LinearLayout.LayoutParams(Ui.dp(this, 56), -2));
        box.addView(seekRow, smallGapLp());
        TextView seekHint = tv(tr("Работает только вместе с функцией «Показывать окно постоянно». Полоса появляется под текстом и позволяет тянуть позицию трека пальцем.", "Works only with “Show window continuously”. The bar appears under the text and lets you drag the track position with your finger."), 12, MUTED_2, Typeface.NORMAL, Gravity.START);
        LinearLayout.LayoutParams seekHintLp = matchWrap();
        seekHintLp.setMargins(Ui.dp(this, 8), Ui.dp(this, 2), Ui.dp(this, 8), 0);
        box.addView(seekHint, seekHintLp);
        Runnable syncSeekRow = () -> {
            boolean enabled = alwaysOnSwitch.isChecked();
            seekRow.setAlpha(enabled ? 1f : 0.5f);
            seekHint.setAlpha(enabled ? 1f : 0.65f);
            seekSwitch.setEnabled(enabled);
            if (!enabled && seekSwitch.isChecked()) seekSwitch.setChecked(false);
        };
        alwaysOnSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.setDisplayWhilePlaying(isChecked);
            if (!isChecked && prefs.featureSeekBar()) prefs.setFeatureSeekBar(false);
            syncSeekRow.run();
            TrackOverlayManager.refresh(this);
        });
        syncSeekRow.run();
        switchRow(box, tr("Фиксированный размер окна", "Fixed window size"), prefs.featureFixedWindow(), on -> {
            prefs.setFeatureFixedWindow(on);
            TrackOverlayManager.refresh(this);
        });
        TextView fixedHint = tv(tr("Ширину окна можно зафиксировать и потом менять двумя пальцами, как при увеличении или уменьшении изображения.", "You can lock the window width and then resize it with two fingers, like zooming an image."), 12, MUTED_2, Typeface.NORMAL, Gravity.START);
        LinearLayout.LayoutParams fixedHintLp = matchWrap();
        fixedHintLp.setMargins(Ui.dp(this, 8), Ui.dp(this, 2), Ui.dp(this, 8), 0);
        box.addView(fixedHint, fixedHintLp);
        switchRow(box, tr("Мягкое восстановление после системных окон", "Soft recovery after system windows"), prefs.featureSoftRecoverySystemWindows(), on -> prefs.setFeatureSoftRecoverySystemWindows(on));
        TextView recoveryHint = tv(
                tr("Для проблемных магнитол. После громкости или чужих системных окон приложение не пытается сразу вернуть overlay, а ждёт короткую паузу и восстанавливает его мягко.",
                        "For problematic head units. After volume or other system windows, the app waits for a short quiet period and restores the overlay softly instead of forcing it immediately."),
                12, MUTED_2, Typeface.NORMAL, Gravity.START);
        LinearLayout.LayoutParams recoveryHintLp = matchWrap();
        recoveryHintLp.setMargins(Ui.dp(this, 8), Ui.dp(this, 2), Ui.dp(this, 8), 0);
        box.addView(recoveryHint, recoveryHintLp);
        box.addView(previewButton(tr("Проверить окно", "Preview window"), v -> TrackOverlayManager.test(this)), btnLp());

        outer.addView(secondaryButton(tr("Готово", "Done"), v -> dialog.dismiss()), btnLp());
        dialog.show();
        fitDialog(dialog, true);
    }
    private interface IntCb { void set(int v); }
    private void addSeek(LinearLayout box, String label, int min, int max, int value, IntCb cb) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setBackground(Ui.stroke(CARD_2, 1, 0x223A4658, 16, this));
        Ui.pad(wrap, 14, 10, 14, 10);
        TextView t = tv(label + ": " + value, 15, TEXT, Typeface.BOLD, Gravity.START);
        SeekBar sb = new SeekBar(this);
        sb.setMax(max - min);
        sb.setProgress(value - min);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { int v = min + progress; t.setText(label + ": " + v); cb.set(v); }
            public void onStartTrackingTouch(SeekBar seekBar) {}
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        wrap.addView(t, matchWrap());
        wrap.addView(sb, matchWrap());
        box.addView(wrap, smallGapLp());
    }

    private void showColorDialog(int target) {
        final Dialog dialog = fullDialog();
        LinearLayout outer = dialogRoot();
        dialog.setContentView(outer);
        outer.addView(tv(colorTargetTitle(target), 24, TEXT, Typeface.BOLD, Gravity.CENTER), matchWrap());
        outer.addView(tv(tr("Нажми на цвет — тестовое окно обновится сразу.", "Tap a color — the test window updates immediately."), 14, MUTED, Typeface.NORMAL, Gravity.CENTER), matchWrap());
        space(outer, 12);

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(false);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        Ui.pad(box, 0, 0, 0, 18);
        sv.addView(box, new ScrollView.LayoutParams(-1, -2));
        outer.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));

        box.addView(sectionHeader(tr("Текущий цвет", "Current color")), matchWrap());
        View currentSwatch = new View(this);
        currentSwatch.setBackground(Ui.stroke(currentColorForTarget(target), 1, 0x88FFFFFF, 8, this));
        LinearLayout.LayoutParams currentLp = new LinearLayout.LayoutParams(Ui.dp(this, 120), Ui.dp(this, 22));
        currentLp.gravity = Gravity.CENTER_HORIZONTAL;
        currentLp.bottomMargin = Ui.dp(this, 10);
        box.addView(currentSwatch, currentLp);

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(4);
        int[] colors = target == 0 ? windowPalette() : textPalette();
        ArrayList<LinearLayout> cells = new ArrayList<>();
        ArrayList<Integer> values = new ArrayList<>();
        for (int col : colors) {
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER);
            cell.setBackground(Ui.stroke(CARD_2, 2, col == currentColorForTarget(target) ? prefs.accentColor() : STROKE_SOFT, 18, this));
            Ui.pad(cell, 6, 6, 6, 6);
            TextView swatch = new TextView(this);
            swatch.setText(" ");
            swatch.setBackground(Ui.stroke(col, 2, 0x88FFFFFF, 16, this));
            LinearLayout.LayoutParams swLp = new LinearLayout.LayoutParams(Ui.dp(this, 54), Ui.dp(this, 54));
            cell.addView(swatch, swLp);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = 0;
            lp.height = -2;
            lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            lp.setMargins(Ui.dp(this, 5), Ui.dp(this, 6), Ui.dp(this, 5), Ui.dp(this, 6));
            grid.addView(cell, lp);
            cells.add(cell);
            values.add(col);
            cell.setOnClickListener(v -> {
                if (target == 0) prefs.setBgColor(col);
                else if (target == 1) prefs.setArtistColor(col);
                else if (target == 2) prefs.setTitleColor(col);
                else if (target == 3) prefs.setSeekBarColor(col);
                else prefs.setSeekThumbColor(col);
                currentSwatch.setBackground(Ui.stroke(col, 1, 0x88FFFFFF, 8, this));
                int selectedColor = currentColorForTarget(target);
                for (int i = 0; i < cells.size(); i++) {
                    boolean selected = values.get(i) == selectedColor;
                    cells.get(i).setBackground(Ui.stroke(CARD_2, 2, selected ? prefs.accentColor() : STROKE_SOFT, 18, this));
                }
                TrackOverlayManager.test(this);
            });
        }
        box.addView(grid, matchWrap());
        outer.addView(primaryButton(tr("Готово", "Done"), v -> dialog.dismiss()), btnLp());
        dialog.show();
        fitDialog(dialog, true);
    }

    private String colorTargetTitle(int target) {
        if (target == 1) return tr("Цвет исполнителя", "Artist color");
        if (target == 2) return tr("Цвет названия песни", "Track title color");
        if (target == 3) return tr("Цвет полосы перемотки", "Seek bar color");
        if (target == 4) return tr("Цвет ползунка перемотки", "Seek thumb color");
        return tr("Выбор цвета окна", "Window color");
    }

    private int currentColorForTarget(int target) {
        if (target == 1) return prefs.artistColor();
        if (target == 2) return prefs.titleColor();
        if (target == 3) return prefs.seekBarColor();
        if (target == 4) return prefs.seekThumbColor();
        return prefs.bgColor();
    }

    private int[] textPalette() {
        return new int[]{0xFFFFFFFF, 0xFFE5E7EB, 0xFFCBD5E1, 0xFF94A3B8,
                0xFF00D5FF, 0xFF38BDF8, 0xFF60A5FA, 0xFF818CF8,
                0xFF22C55E, 0xFF84CC16, 0xFFFFD166, 0xFFFFA94D,
                0xFFFF6B6B, 0xFFFF4D9D, 0xFFC084FC, 0xFF2DD4BF,
                0xFFF8FAFC, 0xFFBAE6FD, 0xFF7DD3FC, 0xFF2563EB,
                0xFFA3E635, 0xFFFBBF24, 0xFFFF8A65, 0xFFE9D5FF};
    }

    private int[] windowPalette() {
        return new int[]{0xFF020617, 0xFF0B1220, 0xFF111827, 0xFF1F2937,
                0xFF0F172A, 0xFF172554, 0xFF082F49, 0xFF042F2E,
                0xFF052E16, 0xFF3F2A05, 0xFF431407, 0xFF450A0A,
                0xFF312E81, 0xFF4A044E, 0xFF1E1B4B, 0xFF18181B,
                0xFF0F1C2E, 0xFF132A3A, 0xFF1B3A2F, 0xFF2A2116,
                0xFF3B1F2B, 0xFF2D3748, 0xFF3A2F5D, 0xFF243447};
    }

    private void showBorderColorDialog(boolean controls) { SettingsChoiceDialogsHelper.showBorderColorDialog(this, controls); }

    private void showFontDialog() { SettingsChoiceDialogsHelper.showFontDialog(this); }
    private void showTextAlignmentDialog() { SettingsChoiceDialogsHelper.showTextAlignmentDialog(this); }
    private void showControlButtonsDialog() { SettingsChoiceDialogsHelper.showControlButtonsDialog(this); }
    private void showPauseBehaviorDialog() { SettingsChoiceDialogsHelper.showPauseBehaviorDialog(this); }
    private void showPositionDialog() { SettingsChoiceDialogsHelper.showPositionDialog(this); }
    private void showDesignPresetDialog() { SettingsChoiceDialogsHelper.showDesignPresetDialog(this); }

    private void copyDiagnosticsReport() {
        AppStateDetector.Result appState = null;
        String appStateError = "";
        try { appState = new AppStateDetector(this).read(); } catch (Throwable t) { appStateError = t.getClass().getSimpleName(); }
        TrackSnapshot track = null;
        String musicStateError = "";
        try { track = new MusicStateDetector(this).read(); } catch (Throwable t) { musicStateError = t.getClass().getSimpleName(); }
        String report = DiagnosticsReportBuilder.build(
                prefs,
                TrackOverlayManager.canDraw(this),
                notificationAccessEnabled(this),
                com.navioverlay.car.services.MusicNotificationListenerService.hasFreshNotifications(),
                com.navioverlay.car.services.MusicNotificationListenerService.hasUsableNotifications(),
                com.navioverlay.car.services.MusicNotificationListenerService.latestNotificationsCount(),
                AppRuntimeStatusHelper.ageMsToSeconds(com.navioverlay.car.services.MusicNotificationListenerService.latestNotificationsAgeMs()),
                AppRuntimeStatusHelper.ageMsToSeconds(com.navioverlay.car.services.MusicNotificationListenerService.staleThresholdMs()),
                AppRuntimeStatusHelper.ageMsToSeconds(com.navioverlay.car.services.MusicNotificationListenerService.fallbackStaleThresholdMs()),
                hasPostNotificationsPermission(),
                AppRuntimeStatusHelper.canScheduleExactAlarmsCompat(this),
                ForegroundDetector.isAccessibilityEnabled(this),
                NavigatorAccessibilityService.isServiceConnected(),
                AppRuntimeStatusHelper.ageSecondsFromTimestamp(NavigatorAccessibilityService.connectedAt()),
                AppRuntimeStatusHelper.ageSecondsFromTimestamp(NavigatorAccessibilityService.lastEventAt()),
                AppRuntimeStatusHelper.ageSecondsFromTimestamp(NavigatorAccessibilityService.lastRootReadAt()),
                ForegroundDetector.hasUsageAccess(this),
                AppRuntimeStatusHelper.ageMsToSeconds(ForegroundState.ageMs()),
                ForegroundState.lastTrigger(),
                AppRuntimeStatusHelper.ageMsToSeconds(ForegroundState.lastTriggerAgeMs()),
                ForegroundState.isTransientSystemWindow(),
                ForegroundState.hasRecentTransientUi(),
                AppRuntimeStatusHelper.ageMsToSeconds(ForegroundState.reverseCameraAgeMs()),
                AppRuntimeStatusHelper.ageMsToSeconds(ForegroundState.volumeUiAgeMs()),
                ForegroundState.get(),
                ForegroundState.currentClass(),
                ForegroundState.currentWindowHint(),
                ForegroundDetector.debugCurrent(this),
                appState,
                AppRuntimeStatusHelper.isNavigatorRunningForStatus(this, prefs, appState),
                appStateError,
                track,
                AppRuntimeStatusHelper.isMusicRunningForStatus(this, prefs, track, ForegroundState.get(), ForegroundDetector.currentByUsage(this)),
                musicStateError,
                TrackOverlayManager.lastSeekDebugReason(),
                TrackOverlayManager.seekSessionHasRange(),
                AppRuntimeStatusHelper.ageMsToSeconds(TrackOverlayManager.seekSessionDurationMs()),
                AppRuntimeStatusHelper.ageMsToSeconds(TrackOverlayManager.seekSessionEstimatedPositionMs()),
                TrackOverlayManager.albumArtWaitingForReal(),
                TrackOverlayManager.albumArtRetryCount()
        );
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("Navi Overlay diagnostics", report));
        Toast.makeText(this, tr("Диагностика скопирована", "Diagnostics copied"), Toast.LENGTH_SHORT).show();
    }

    private void refreshStatus() {
        if (statusView == null) return;
        boolean overlay = TrackOverlayManager.canDraw(this);
        boolean usage = ForegroundDetector.hasUsageAccess(this);
        boolean acc = ForegroundDetector.isAccessibilityEnabled(this);
        boolean accConnected = NavigatorAccessibilityService.isServiceConnected();
        boolean notif = notificationAccessEnabled(this);
        // "Уведомления приложения" не должны ломать общий статус разрешений:
        // для Android 12 и ниже их физически нет, а для Android 13+ это отдельная
        // дополнительная опция, не критичная для базовой работы overlay-движка.
        boolean allPerms = overlay && notif && (acc || usage);

        AppStateDetector.Result appState;
        try { appState = new AppStateDetector(this).read(); } catch (Throwable t) { appState = null; }
        TrackSnapshot track;
        try { track = new MusicStateDetector(this).read(); } catch (Throwable t) { track = TrackSnapshot.empty(); }

        boolean enabled = prefs.enabled();
        SpannableStringBuilder sb = new SpannableStringBuilder();
        appendStatusLine(sb, tr("Статус: ", "Status: "), enabled ? tr("Включено", "Enabled") : tr("Выключено", "Disabled"), enabled ? GREEN : 0xFFFF4D4D);
        appendStatusLine(sb, tr("Разрешения: ", "Permissions: "), allPerms ? tr("Выданы", "Granted") : tr("Выданы не все", "Not all granted"), allPerms ? GREEN : WARN);
        if (enabled && acc && !accConnected) {
            appendStatusLine(sb, tr("Сервис спец. возможностей: ", "Accessibility service: "), tr("Нет связи", "No connection"), 0xFFFF4D4D);
        }
        statusView.setText(sb);
        statusView.setTextColor(TEXT);
        if (enableSwitch != null && enableSwitch.isChecked() != prefs.enabled()) enableSwitch.setChecked(prefs.enabled());
        if (onlyTriggerSwitch != null && onlyTriggerSwitch.isChecked() != prefs.showOnlyWithTrigger()) onlyTriggerSwitch.setChecked(prefs.showOnlyWithTrigger());
        if (englishUiSwitch != null && englishUiSwitch.isChecked() != prefs.englishUi()) englishUiSwitch.setChecked(prefs.englishUi());
    }

    private String appLabelOrFallback(String pkg, String emptyText) {
        if (pkg == null || pkg.isEmpty()) return emptyText;
        try {
            android.content.pm.ApplicationInfo info = getPackageManager().getApplicationInfo(pkg, 0);
            CharSequence label = getPackageManager().getApplicationLabel(info);
            String text = label == null ? "" : label.toString().trim();
            return text.isEmpty() ? pkg : text;
        } catch (Throwable ignored) {
            return pkg;
        }
    }

    private String formatAgeStatus(long ageMs, String emptyText) {
        if (ageMs == Long.MAX_VALUE || ageMs < 0L) return emptyText;
        long sec = Math.max(0L, ageMs / 1000L);
        if (sec < 1L) return tr("Только что", "Just now");
        if (sec < 60L) return sec + " " + tr("сек назад", "sec ago");
        long minutes = sec / 60L;
        if (minutes < 60L) return minutes + " " + minuteWord(minutes) + " " + tr("назад", "ago");
        long hours = minutes / 60L;
        long remainMinutes = minutes % 60L;
        if (remainMinutes <= 0L) return hours + " " + hourWord(hours) + " " + tr("назад", "ago");
        return hours + " " + hourWord(hours) + " " + remainMinutes + " " + minuteWord(remainMinutes) + " " + tr("назад", "ago");
    }

    private int ageColor(long ageMs, long freshMs, long warnMs) {
        if (ageMs == Long.MAX_VALUE || ageMs < 0L) return 0xFFFF4D4D;
        if (ageMs <= freshMs) return GREEN;
        if (ageMs <= warnMs) return WARN;
        return 0xFFFF4D4D;
    }

    private void showAppStateDialog() {
        final Dialog dialog = fullDialog();
        LinearLayout outer = dialogRoot();
        dialog.setContentView(outer);

        outer.addView(tv(tr("Состояние приложения", "Application status"), 24, TEXT, Typeface.BOLD, Gravity.CENTER), matchWrap());
        outer.addView(tv(tr("Подробные статусы и текущие настройки приложения.", "Detailed runtime statuses and current app settings."), 14, MUTED, Typeface.NORMAL, Gravity.CENTER), matchWrap());
        space(outer, 12);

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(false);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        Ui.pad(box, 0, 0, 0, 18);
        sv.addView(box, new ScrollView.LayoutParams(-1, -2));
        outer.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));

        AppStateDetector.Result appState;
        try { appState = new AppStateDetector(this).read(); } catch (Throwable t) { appState = null; }
        TrackSnapshot track;
        try { track = new MusicStateDetector(this).read(); } catch (Throwable t) { track = TrackSnapshot.empty(); }

        String usageCurrent = ForegroundDetector.currentByUsage(this);
        String accessibilityCurrent = ForegroundState.get();
        boolean musicPlaying = AppRuntimeStatusHelper.isMusicPlayingForStatus(prefs, track);
        boolean musicRunning = AppRuntimeStatusHelper.isMusicRunningForStatus(this, prefs, track, accessibilityCurrent, usageCurrent);
        String lastNavPkg = AppRuntimeStatusHelper.lastSeenNavigatorPackage(prefs, appState);
        String lastPlayerPkg = AppRuntimeStatusHelper.lastSeenPlayerPackage(prefs, track, accessibilityCurrent, usageCurrent);

        box.addView(sectionHeader(tr("Текущие статусы", "Current statuses")), matchWrap());
        box.addView(infoRow(tr("Окно поверх приложений", "Draw over apps"), TrackOverlayManager.canDraw(this) ? tr("Разрешено", "Allowed") : tr("Не разрешено", "Not allowed"), TrackOverlayManager.canDraw(this) ? GREEN : 0xFFFF4D4D), smallGapLp());
        box.addView(infoRow(tr("Доступ к уведомлениям", "Notification access"), notificationAccessEnabled(this) ? tr("Включен", "Enabled") : tr("Выключен", "Disabled"), notificationAccessEnabled(this) ? GREEN : 0xFFFF4D4D), smallGapLp());
        box.addView(infoRow(tr("Уведомления приложения", "App notifications"), hasPostNotificationsPermission() ? tr("Разрешены", "Allowed") : tr("Не разрешены", "Not allowed"), hasPostNotificationsPermission() ? GREEN : WARN), smallGapLp());
        box.addView(infoRow(tr("Спец. возможности", "Accessibility"), ForegroundDetector.isAccessibilityEnabled(this) ? tr("Включены", "Enabled") : tr("Выключены", "Disabled"), ForegroundDetector.isAccessibilityEnabled(this) ? GREEN : 0xFFFF4D4D), smallGapLp());
        box.addView(infoRow(tr("История использования", "Usage access"), ForegroundDetector.hasUsageAccess(this) ? tr("Разрешена", "Allowed") : tr("Не разрешена", "Not allowed"), ForegroundDetector.hasUsageAccess(this) ? GREEN : WARN), smallGapLp());
        box.addView(infoRow(tr("Служба спец. возможностей", "Accessibility service"), NavigatorAccessibilityService.isServiceConnected() ? tr("Подключена", "Connected") : tr("Нет связи", "No connection"), NavigatorAccessibilityService.isServiceConnected() ? GREEN : 0xFFFF4D4D), smallGapLp());
        box.addView(infoRow(tr("Последний навигатор", "Last navigator"), appLabelOrFallback(lastNavPkg, tr("Нет данных", "No data")), lastNavPkg.isEmpty() ? 0xFFFF4D4D : TEXT), smallGapLp());
        String persistedTrackPkg = prefs.lastSeenTrackPackage();
        if (lastPlayerPkg.isEmpty()) lastPlayerPkg = persistedTrackPkg;
        box.addView(infoRow(tr("Последний плеер", "Last player"), appLabelOrFallback(lastPlayerPkg, tr("Нет данных", "No data")), lastPlayerPkg.isEmpty() ? 0xFFFF4D4D : TEXT), smallGapLp());
        String musicText = musicPlaying
                ? tr("Да", "Yes")
                : (musicRunning ? tr("Нет, но плеер открыт", "No, but player is open") : tr("Нет", "No"));
        box.addView(infoRow(tr("Музыка сейчас играет", "Music playing now"), musicText, musicPlaying ? GREEN : (musicRunning ? WARN : 0xFFFF4D4D)), smallGapLp());
        String lastTrackText;
        int lastTrackColor;
        if (track != null && track.hasText()) {
            lastTrackText = track.artist.isEmpty() ? track.title : track.artist + " - " + track.title;
            lastTrackColor = TEXT;
        } else if (!prefs.lastSeenTrackTitle().isEmpty() || !prefs.lastSeenTrackArtist().isEmpty()) {
            lastTrackText = prefs.lastSeenTrackArtist().isEmpty()
                    ? prefs.lastSeenTrackTitle()
                    : prefs.lastSeenTrackArtist() + " - " + prefs.lastSeenTrackTitle();
            lastTrackColor = TEXT;
        } else {
            lastTrackText = tr("Нет данных", "No data");
            lastTrackColor = 0xFFFF4D4D;
        }
        box.addView(infoRow(tr("Последний трек", "Last track"), lastTrackText, lastTrackColor), smallGapLp());
        box.addView(infoRow(tr("Последнее системное окно", "Last system window"),
                currentSystemWindowLabel(appState, prefs),
                currentSystemWindowColor(appState, prefs)), smallGapLp());
        long lastDataAgeMs = track != null && track.hasText()
                ? System.currentTimeMillis() - track.readAt
                : (prefs.lastSeenTrackAt() > 0L ? System.currentTimeMillis() - prefs.lastSeenTrackAt() : Long.MAX_VALUE);
        box.addView(infoRow(tr("Последнее обновление данных", "Last data update"), formatAgeStatus(lastDataAgeMs, tr("Нет данных", "No data")), ageColor(lastDataAgeMs, 5_000L, 20_000L)), smallGapLp());

        box.addView(sectionHeader(tr("Текущие настройки", "Current settings")), matchWrap());
        box.addView(infoRow(tr("Размер текста", "Text size"), String.valueOf(prefs.textSize()), TEXT), smallGapLp());
        box.addView(infoRow(tr("Шрифт", "Font"), OverlayFonts.nameAt(prefs.textFont()), TEXT), smallGapLp());
        box.addView(infoRow(tr("Выравнивание текста", "Text alignment"), prefs.textAlign() == Prefs.TEXT_ALIGN_LEFT ? tr("Слева", "Left") : tr("По центру", "Center"), TEXT), smallGapLp());
        box.addView(infoRow(tr("Прозрачность окна", "Window transparency"), prefs.windowAlpha() + "%", TEXT), smallGapLp());
        box.addView(infoRow(tr("Время показа", "Display time"), prefs.displayWhilePlaying() ? tr("Постоянно", "Continuous") : (prefs.displayMs() / 1000) + " " + tr("сек", "sec"), TEXT), smallGapLp());
        box.addView(infoRow(tr("Положение окна", "Window position"), positionName(prefs.selectedPosition()), TEXT), smallGapLp());
        box.addView(infoRow(tr("Пресет дизайна", "Design preset"), String.valueOf(prefs.designPreset()), TEXT), smallGapLp());
        box.addView(colorInfoRow(tr("Цвет окна", "Window color"), prefs.bgColor()), smallGapLp());
        box.addView(colorInfoRow(tr("Цвет рамки окна", "Window border color"), prefs.borderColor()), smallGapLp());
        box.addView(colorInfoRow(tr("Цвет рамок кнопок", "Control border color"), prefs.controlsBorderColor()), smallGapLp());
        box.addView(colorInfoRow(tr("Цвет исполнителя", "Artist color"), prefs.artistColor()), smallGapLp());
        box.addView(colorInfoRow(tr("Цвет названия песни", "Track title color"), prefs.titleColor()), smallGapLp());
        box.addView(colorInfoRow(tr("Цвет полосы перемотки", "Seek bar color"), prefs.seekBarColor()), smallGapLp());
        box.addView(colorInfoRow(tr("Цвет ползунка перемотки", "Seek thumb color"), prefs.seekThumbColor()), smallGapLp());
        box.addView(infoRow(tr("Кнопки управления музыкой", "Music control buttons"), onOff(prefs.featureControls()), prefs.featureControls() ? GREEN : MUTED_2), smallGapLp());
        box.addView(infoRow(tr("Обложка", "Album art"), onOff(prefs.featureAlbumArt()), prefs.featureAlbumArt() ? GREEN : MUTED_2), smallGapLp());
        box.addView(infoRow(tr("Магнит окна", "Snap"), onOff(prefs.featureSnap()), prefs.featureSnap() ? GREEN : MUTED_2), smallGapLp());
        box.addView(infoRow(tr("Приглушение при громкости", "Volume dim"), onOff(prefs.featureVolumeDim()), prefs.featureVolumeDim() ? GREEN : MUTED_2), smallGapLp());
        box.addView(infoRow(tr("Показывать без навигатора", "Floating mode"), onOff(prefs.featureFloating()), prefs.featureFloating() ? GREEN : MUTED_2), smallGapLp());
        box.addView(infoRow(tr("Фиксированный размер окна", "Fixed window size"), onOff(prefs.featureFixedWindow()), prefs.featureFixedWindow() ? GREEN : MUTED_2), smallGapLp());
        box.addView(infoRow(tr("Мягкое восстановление", "Soft recovery"), onOff(prefs.featureSoftRecoverySystemWindows()), prefs.featureSoftRecoverySystemWindows() ? GREEN : MUTED_2), smallGapLp());
        box.addView(infoRow(tr("Полоса перемотки", "Seek bar"), onOff(prefs.featureSeekBar()), prefs.featureSeekBar() ? GREEN : MUTED_2), smallGapLp());
        box.addView(infoRow(tr("Поведение при паузе", "Pause behavior"), pauseBehaviorName(prefs.pauseBehavior()), TEXT), smallGapLp());

        outer.addView(secondaryButton(tr("Готово", "Done"), v -> dialog.dismiss()), btnLp());
        dialog.show();
        fitDialog(dialog, true);
    }

    TextView sectionHeader(String text) {
        TextView t = tv(text, 18, TEXT, Typeface.BOLD, Gravity.CENTER);
        LinearLayout.LayoutParams lp = matchWrap();
        lp.setMargins(0, Ui.dp(this, 8), 0, Ui.dp(this, 4));
        t.setLayoutParams(lp);
        return t;
    }

    private View infoRow(String label, String value, int valueColor) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setBackground(Ui.stroke(CARD_2, 1, STROKE_SOFT, 16, this));
        Ui.pad(row, 14, 12, 14, 12);
        TextView l = tv(label, 14, MUTED, Typeface.NORMAL, Gravity.CENTER);
        TextView v = tv(value, 16, valueColor, Typeface.BOLD, Gravity.CENTER);
        v.setLineSpacing(Ui.dp(this, 2), 1.0f);
        row.addView(l, matchWrap());
        row.addView(v, matchWrap());
        return row;
    }

    private View colorInfoRow(String label, int color) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setBackground(Ui.stroke(CARD_2, 1, STROKE_SOFT, 16, this));
        Ui.pad(row, 14, 12, 14, 12);

        TextView l = tv(label, 14, MUTED, Typeface.NORMAL, Gravity.CENTER);
        row.addView(l, matchWrap());

        View swatch = new View(this);
        GradientDrawable drawable = Ui.stroke(color, 1, 0x55FFFFFF, 8, this);
        swatch.setBackground(drawable);
        LinearLayout.LayoutParams swatchLp = new LinearLayout.LayoutParams(Ui.dp(this, 96), Ui.dp(this, 18));
        swatchLp.gravity = Gravity.CENTER_HORIZONTAL;
        swatchLp.topMargin = Ui.dp(this, 6);
        row.addView(swatch, swatchLp);
        return row;
    }

    private String onOff(boolean value) {
        return value ? tr("Включено", "Enabled") : tr("Выключено", "Disabled");
    }

    private String minuteWord(long value) {
        if (!prefs.englishUi()) {
            long mod10 = value % 10L;
            long mod100 = value % 100L;
            if (mod10 == 1L && mod100 != 11L) return "минута";
            if (mod10 >= 2L && mod10 <= 4L && (mod100 < 12L || mod100 > 14L)) return "минуты";
            return "минут";
        }
        return value == 1L ? "minute" : "minutes";
    }

    private String hourWord(long value) {
        if (!prefs.englishUi()) {
            long mod10 = value % 10L;
            long mod100 = value % 100L;
            if (mod10 == 1L && mod100 != 11L) return "час";
            if (mod10 >= 2L && mod10 <= 4L && (mod100 < 12L || mod100 > 14L)) return "часа";
            return "часов";
        }
        return value == 1L ? "hour" : "hours";
    }

    private String pauseBehaviorName(int value) {
        if (value == Prefs.PAUSE_BEHAVIOR_HIDE) return tr("Скрывать окно", "Hide the window");
        if (value == Prefs.PAUSE_BEHAVIOR_SHORT) return tr("Ненадолго оставить и скрыть", "Keep briefly, then hide");
        return tr("Оставлять окно", "Keep the window");
    }

    private String positionName(int value) {
        switch (value) {
            case 0: return tr("Верх слева", "Top left");
            case 1: return tr("Верх по центру", "Top center");
            case 2: return tr("Верх справа", "Top right");
            case 3: return tr("Между верхом и центром слева", "Upper middle left");
            case 4: return tr("Между верхом и центром по центру", "Upper middle center");
            case 5: return tr("Между верхом и центром справа", "Upper middle right");
            case 6: return tr("По центру слева", "Middle left");
            case 7: return tr("Центр", "Center");
            case 8: return tr("По центру справа", "Middle right");
            case 9: return tr("Между центром и низом слева", "Lower middle left");
            case 10: return tr("Между центром и низом по центру", "Lower middle center");
            case 11: return tr("Между центром и низом справа", "Lower middle right");
            case 12: return tr("Низ слева", "Bottom left");
            case 13: return tr("Низ по центру", "Bottom center");
            case 14: return tr("Низ справа", "Bottom right");
            default: return String.valueOf(value);
        }
    }

    private String currentSystemWindowLabel(AppStateDetector.Result appState, Prefs prefs) {
        if (appState != null && appState.reverseCameraActive) return tr("Задний ход", "Reverse camera");
        if (appState != null && appState.volumeUiActive) return tr("Окно громкости", "Volume window");
        if (appState != null && appState.transientOverlay) return tr("Временное внешнее окно", "Transient external window");
        String persisted = prefs.lastSystemWindowType();
        if ("reverse".equals(persisted)) return tr("Задний ход", "Reverse camera");
        if ("volume".equals(persisted)) return tr("Окно громкости", "Volume window");
        if ("conflict".equals(persisted)) return tr("Приоритетное приложение", "Priority app");
        if ("transient".equals(persisted)) return tr("Временное внешнее окно", "Transient external window");
        return tr("Нет", "None");
    }

    private int currentSystemWindowColor(AppStateDetector.Result appState, Prefs prefs) {
        if (appState != null && (appState.reverseCameraActive || appState.volumeUiActive || appState.transientOverlay)) return WARN;
        long at = prefs.lastSystemWindowAt();
        if (at <= 0L) return GREEN;
        long ageMs = System.currentTimeMillis() - at;
        return ageColor(ageMs, 15_000L, 120_000L);
    }

    private void checkAndRecover() {
        if (!prefs.enabled()) {
            Toast.makeText(this, tr("Сначала включи приложение", "Enable the app first"), Toast.LENGTH_SHORT).show();
            return;
        }
        if (!TrackOverlayManager.canDraw(this)) {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
            return;
        }
        if (!notificationAccessEnabled(this)) {
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
            return;
        }
        if (!ForegroundDetector.isAccessibilityEnabled(this)) {
            Toast.makeText(this, tr("Спец. возможности выключены. Открой и снова включи Navi Overlay.", "Accessibility is off. Open settings and enable Navi Overlay again."), Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            return;
        }
        if (prefs.showOnlyWithTrigger() && !ForegroundDetector.hasUsageAccess(this)) {
            Toast.makeText(this, tr("Нужен доступ к истории использования.", "Usage access is required."), Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
            return;
        }
        if (!NavigatorAccessibilityService.isServiceConnected()) {
            Toast.makeText(this, tr("Служба спец. возможностей включена, но не отвечает. Открой этот экран и переподключи Navi Overlay.", "Accessibility is enabled but not responding. Open the screen and reconnect Navi Overlay."), Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            return;
        }
        MonitorService.poke(this);
        refreshStatus();
        Toast.makeText(this, tr("Движок перепроверен. Критичных проблем не найдено.", "Engine rechecked. No critical problems found."), Toast.LENGTH_SHORT).show();
    }

    private void appendStatusLine(SpannableStringBuilder sb, String label, String value, int valueColor) {
        int start = sb.length();
        sb.append(label).append(value).append("\n");
        int valueStart = start + label.length();
        int valueEnd = valueStart + value.length();
        sb.setSpan(new ForegroundColorSpan(valueColor), valueStart, valueEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
    private void requestPostNotificationsRuntimeOnly() {
        try {
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 3301);
            }
        } catch (Throwable ignored) {}
    }

    private void openPostNotificationsFlow() {
        try {
            if (Build.VERSION.SDK_INT < 33) {
                Toast.makeText(this, tr("На Android 12 и ниже отдельное разрешение уведомлений не требуется", "On Android 12 and below a separate notification permission is not required"), Toast.LENGTH_LONG).show();
                return;
            }
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 3301);
                return;
            }
            Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            i.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            startActivity(i);
        } catch (Throwable t) {
            try {
                Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()));
                startActivity(i);
            } catch (Throwable ignored) {
                Toast.makeText(this, tr("Не удалось открыть настройки уведомлений", "Failed to open notification settings"), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private boolean hasPostNotificationsPermission() {
        return Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean notificationAccessEnabled(Context c) {
        try {
            String s = Settings.Secure.getString(c.getContentResolver(), "enabled_notification_listeners");
            return s != null && s.toLowerCase(Locale.ROOT).contains(c.getPackageName().toLowerCase(Locale.ROOT));
        } catch (Throwable t) { return false; }
    }

    private LinearLayout card(String title, String desc) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(cardBg(24));
        Ui.pad(c, 18, 17, 18, 18);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, Ui.dp(this, 15));
        root.addView(c, lp);

        TextView h = tv(title, 22, TEXT, Typeface.BOLD, Gravity.CENTER);
        h.setLetterSpacing(0.01f);
        c.addView(h, matchWrap());
        if (!TextUtils.isEmpty(desc)) {
            TextView d = tv(desc, 14, MUTED, Typeface.NORMAL, Gravity.CENTER);
            d.setLineSpacing(Ui.dp(this, 2), 1.0f);
            LinearLayout.LayoutParams dlp = matchWrap();
            dlp.setMargins(0, Ui.dp(this, 7), 0, Ui.dp(this, 6));
            c.addView(d, dlp);
        }
        return c;
    }

    View menuButton(String title, String desc, View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(menuBg());
        Ui.pad(row, 14, 12, 12, 12);

        TextView accent = new TextView(this);
        accent.setBackground(Ui.round(prefs.accentColor(), 6, this));
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(Ui.dp(this, 4), Ui.dp(this, 38));
        alp.setMargins(0, 0, Ui.dp(this, 12), 0);
        row.addView(accent, alp);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView titleView = tv(title, 16, TEXT, Typeface.BOLD, Gravity.START);
        titleView.setSingleLine(false);
        texts.addView(titleView, matchWrap());
        if (!TextUtils.isEmpty(desc)) {
            TextView descView = tv(desc, 13, MUTED_2, Typeface.NORMAL, Gravity.START);
            descView.setLineSpacing(Ui.dp(this, 1), 1.0f);
            texts.addView(descView, matchWrap());
        }
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
        TextView arrow = tv("›", 32, prefs.accentColor(), Typeface.BOLD, Gravity.CENTER);
        row.addView(arrow, new LinearLayout.LayoutParams(Ui.dp(this, 30), -2));
        row.setOnClickListener(listener);
        return row;
    }

    Button primaryButton(String text, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setTextColor(Color.WHITE);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setBackground(gradientButton(prefs.accentColor(), lighten(prefs.accentColor(), 30), 18));
        if (l != null) b.setOnClickListener(l);
        return b;
    }

    Button previewButton(String text, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setTextColor(Color.WHITE);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setBackground(gradientButton(0xFF2DDFFF, 0xFFC85BFF, 18));
        if (l != null) b.setOnClickListener(l);
        return b;
    }

    Button secondaryButton(String text, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setTextColor(TEXT);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setIncludeFontPadding(false);
        b.setPadding(Ui.dp(this, 8), 0, Ui.dp(this, 8), 0);
        b.setBackground(Ui.stroke(CARD_2, 1, STROKE, 16, this));
        if (l != null) b.setOnClickListener(l);
        return b;
    }

    TextView tv(String s, int sp, int color, int style, int gravity) {
        TextView t = Ui.tv(this, s, sp, color, style);
        t.setGravity(gravity);
        return t;
    }

    GradientDrawable gradient(int top, int bottom, int radius) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{top, bottom});
        g.setCornerRadius(Ui.dp(this, radius));
        return g;
    }

    GradientDrawable gradientButton(int left, int right, int radius) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{left, right});
        g.setCornerRadius(Ui.dp(this, radius));
        return g;
    }

    GradientDrawable cardBg(int radius) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{CARD_TOP, CARD});
        g.setCornerRadius(Ui.dp(this, radius));
        g.setStroke(Ui.dp(this, 1), STROKE_SOFT);
        return g;
    }

    GradientDrawable menuBg() {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xFF1A263B, 0xFF131E30});
        g.setCornerRadius(Ui.dp(this, 18));
        g.setStroke(Ui.dp(this, 1), STROKE_SOFT);
        return g;
    }

    int lighten(int color, int amount) {
        int a = Color.alpha(color);
        int r = Math.min(255, Color.red(color) + amount);
        int g = Math.min(255, Color.green(color) + amount);
        int b = Math.min(255, Color.blue(color) + amount);
        return Color.argb(a, r, g, b);
    }

    Dialog fullDialog() {
        Dialog d = new Dialog(this);
        Window w = d.getWindow();
        if (w != null) w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        return d;
    }

    LinearLayout dialogRoot() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(gradient(BG, BG_2, 0));
        Ui.pad(box, 18, 20, 18, 16);
        return box;
    }

    void fitDialog(Dialog d, boolean fullHeight) {
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(-1, fullHeight ? -1 : -2);
        }
    }

    LinearLayout.LayoutParams matchWrap() { return new LinearLayout.LayoutParams(-1, -2); }
    LinearLayout.LayoutParams btnLp() { LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, Ui.dp(this, 52)); lp.setMargins(0, Ui.dp(this, 12), 0, 0); return lp; }
    LinearLayout.LayoutParams smallGapLp() { LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0, Ui.dp(this, 9), 0, 0); return lp; }
    LinearLayout.LayoutParams weightLp(float weight) { LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(this, 58), weight); lp.setMargins(Ui.dp(this, 4), Ui.dp(this, 8), Ui.dp(this, 4), 0); return lp; }
    LinearLayout.LayoutParams quickButtonLp(float weight) { LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(this, 64), weight); lp.setMargins(Ui.dp(this, 4), Ui.dp(this, 8), Ui.dp(this, 4), Ui.dp(this, 10)); return lp; }
    void space(LinearLayout parent, int dp) { Space s = new Space(this); parent.addView(s, new LinearLayout.LayoutParams(1, Ui.dp(this, dp))); }

    String tr(String ru, String en) {
        return prefs != null && prefs.englishUi() ? en : ru;
    }

    Prefs prefs() { return prefs; }
}
