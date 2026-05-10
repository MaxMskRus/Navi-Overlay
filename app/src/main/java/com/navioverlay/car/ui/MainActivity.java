package com.navioverlay.car.ui;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {
    private static final int APP_MODE_TRIGGERS = 0;
    private static final int APP_MODE_PLAYERS = 1;
    private static final int APP_MODE_CONFLICTS = 2;
    private static final int BG = 0xFF030712;
    private static final int BG_2 = 0xFF071021;
    private static final int CARD = 0xFF101827;
    private static final int CARD_TOP = 0xFF18233A;
    private static final int CARD_2 = 0xFF1A2538;
    private static final int FIELD = 0xFF0B1220;
    private static final int STROKE = 0x4460708A;
    private static final int STROKE_SOFT = 0x223E4C61;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int MUTED = 0xFFC4CBD7;
    private static final int MUTED_2 = 0xFF8EA0B8;
    private static final int GREEN = 0xFF22C55E;
    private static final int WARN = 0xFFFFB020;

    private Prefs prefs;
    private LinearLayout root;
    private TextView statusView;
    private Switch enableSwitch;
    private Switch onlyTriggerSwitch;
    private Switch englishUiSwitch;
    private ArrayList<AppInfo> installedAppsCache;

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
    }

    private void addStatusCard() {
        LinearLayout c = card(tr("Состояние", "Status"), tr("Короткая сводка по движку, разрешениям и активным приложениям.", "A short summary of the engine, permissions and active apps."));
        statusView = tv("", 16, TEXT, Typeface.BOLD, Gravity.START);
        statusView.setLineSpacing(Ui.dp(this, 4), 1.0f);
        statusView.setBackground(Ui.stroke(FIELD, 1, STROKE_SOFT, 18, this));
        Ui.pad(statusView, 16, 14, 16, 14);
        c.addView(statusView, smallGapLp());
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
        c.addView(menuButton(tr("Приложения навигации", "Navigation apps"), tr("Где показывать окно", "Where the window may appear"), v -> showAppDialog(APP_MODE_TRIGGERS)), smallGapLp());
        c.addView(menuButton(tr("Аудио приложения", "Audio apps"), tr("Откуда брать название трека", "Where track data comes from"), v -> showAppDialog(APP_MODE_PLAYERS)), smallGapLp());
        c.addView(menuButton(
                tr("Приложения с приоритетным окном", "Priority overlay apps"),
                tr("Антирадар и другие приложения, чьи собственные окна должны временно иметь приоритет", "Radar and similar apps whose own windows should temporarily take priority"),
                v -> showConflictAppsDialog()), smallGapLp());
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
        c.addView(menuButton(tr("История последних треков", "Recent track history"), tr("Открыть список последних 30 треков и быстро копировать названия", "Open the latest 30 tracks and quickly copy song names"), v -> showRecentTrackHistoryDialog()), smallGapLp());
        c.addView(menuButton(tr("Инструкция", "Instructions"), tr("Коротко о том, что делает каждая функция приложения", "Short guide explaining what each app function does"), v -> showInstructionsDialog()), smallGapLp());
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

    private void showAppDialog(int mode) {
        boolean triggers = mode == APP_MODE_TRIGGERS;
        boolean players = mode == APP_MODE_PLAYERS;
        final Dialog dialog = fullDialog();
        LinearLayout box = dialogRoot();
        dialog.setContentView(box);

        box.addView(tv(tr(
                mode == APP_MODE_TRIGGERS ? "Приложения навигации" : (players ? "Аудио приложения" : "Приложения с приоритетным окном"),
                mode == APP_MODE_TRIGGERS ? "Navigation apps" : (players ? "Audio apps" : "Priority overlay apps")
        ), 24, TEXT, Typeface.BOLD, Gravity.CENTER), matchWrap());
        box.addView(tv(tr(
                mode == APP_MODE_TRIGGERS
                        ? "Выбери приложения, поверх которых можно показывать окно."
                        : (players
                            ? "Выбери плееры, откуда брать название трека."
                            : "Выбери приложения, чьи собственные окна должны временно иметь приоритет над overlay."),
                mode == APP_MODE_TRIGGERS
                        ? "Choose apps over which the window may appear."
                        : (players
                            ? "Choose players used for track metadata."
                            : "Choose apps whose own windows should temporarily take priority over the overlay.")
        ), 14, MUTED, Typeface.NORMAL, Gravity.CENTER), matchWrap());
        space(box, 14);

        EditText search = new EditText(this);
        search.setHint(tr("Поиск по названию или пакету", "Search by label or package"));
        search.setSingleLine(true);
        search.setTextColor(TEXT);
        search.setHintTextColor(0xFF7F8EA3);
        search.setTextSize(16);
        search.setBackground(Ui.stroke(FIELD, 1, STROKE, 18, this));
        Ui.pad(search, 14, 12, 14, 12);
        box.addView(search, matchWrap());

        LinearLayout quick = new LinearLayout(this);
        quick.setOrientation(LinearLayout.HORIZONTAL);
        quick.setGravity(Gravity.CENTER);
        quick.setClipChildren(false);
        quick.setClipToPadding(false);
        quick.setPadding(0, 0, 0, Ui.dp(this, 2));
        quick.addView(secondaryButton(tr(
                mode == APP_MODE_TRIGGERS ? "Яндекс + карты" : (players ? "Популярные плееры" : "Полезные примеры"),
                mode == APP_MODE_TRIGGERS ? "Yandex + maps" : (players ? "Popular players" : "Useful examples")
        ), null), quickButtonLp(1));
        quick.addView(secondaryButton(tr("Очистить", "Clear"), null), quickButtonLp(1));
        Button popular = (Button) quick.getChildAt(0);
        Button clear = (Button) quick.getChildAt(1);
        popular.setTextSize(triggers ? 14 : 13);
        popular.setGravity(Gravity.CENTER);
        popular.setMaxLines(2);
        popular.setSingleLine(false);
        popular.setEllipsize(null);
        clear.setGravity(Gravity.CENTER);
        clear.setMaxLines(1);
        clear.setSingleLine(true);
        LinearLayout.LayoutParams quickLp = smallGapLp();
        quickLp.bottomMargin = Ui.dp(this, 6);
        box.addView(quick, quickLp);

        LinearLayout manualRow = new LinearLayout(this);
        manualRow.setOrientation(LinearLayout.HORIZONTAL);
        manualRow.setGravity(Gravity.CENTER_VERTICAL);
        EditText manualPackage = new EditText(this);
        manualPackage.setHint(tr("Добавить пакет вручную", "Add package manually"));
        manualPackage.setSingleLine(true);
        manualPackage.setTextColor(TEXT);
        manualPackage.setHintTextColor(0xFF7F8EA3);
        manualPackage.setTextSize(14);
        manualPackage.setBackground(Ui.stroke(FIELD, 1, STROKE, 16, this));
        Ui.pad(manualPackage, 12, 10, 12, 10);
        manualRow.addView(manualPackage, new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1));
        Button addManual = secondaryButton(tr("Добавить", "Add"), null);
        LinearLayout.LayoutParams addManualLp = new LinearLayout.LayoutParams(Ui.dp(this, 112), Ui.dp(this, 48));
        addManualLp.leftMargin = Ui.dp(this, 10);
        manualRow.addView(addManual, addManualLp);
        LinearLayout.LayoutParams manualRowLp = smallGapLp();
        manualRowLp.topMargin = Ui.dp(this, 0);
        manualRowLp.bottomMargin = Ui.dp(this, 8);
        box.addView(manualRow, manualRowLp);

        ScrollView sv = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        sv.addView(list);
        box.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));

        Button done = primaryButton(tr("Готово", "Done"), v -> dialog.dismiss());
        box.addView(done, btnLp());

        final Runnable[] render = new Runnable[1];
        render[0] = () -> renderApps(list, search.getText().toString(), mode);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) { render[0].run(); }
            public void afterTextChanged(Editable s) {}
        });
        popular.setOnClickListener(v -> {
            Set<String> s = mode == APP_MODE_TRIGGERS ? prefs.triggers() : (players ? prefs.players() : prefs.conflictApps());
            if (mode == APP_MODE_TRIGGERS) Collections.addAll(s, Constants.DEFAULT_TRIGGER_PACKAGES);
            else if (players) Collections.addAll(s, Constants.DEFAULT_PLAYER_PACKAGES);
            else {
                s.add("com.smart.driver.antiradar");
                s.add("com.antiradar");
            }
            saveAppSelection(mode, s);
            render[0].run();
        });
        clear.setOnClickListener(v -> { saveAppSelection(mode, new HashSet<String>()); render[0].run(); });
        addManual.setOnClickListener(v -> {
            String pkg = manualPackage.getText().toString().trim();
            if (pkg.length() < 3 || !pkg.contains(".")) {
                Toast.makeText(this, tr("Введите packageName, например ru.yandex.music", "Enter packageName, for example ru.yandex.music"), Toast.LENGTH_SHORT).show();
                return;
            }
            Set<String> s = mode == APP_MODE_TRIGGERS ? prefs.triggers() : (players ? prefs.players() : prefs.conflictApps());
            s.add(pkg);
            saveAppSelection(mode, s);
            manualPackage.setText("");
            render[0].run();
        });
        render[0].run();
        dialog.show();
        fitDialog(dialog, true);
    }

    private void renderApps(LinearLayout list, String q, int mode) {
        list.removeAllViews();
        String query = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        Set<String> selected = mode == APP_MODE_TRIGGERS ? prefs.triggers() : (mode == APP_MODE_PLAYERS ? prefs.players() : prefs.conflictApps());
        ArrayList<AppInfo> apps = loadApps(selected);
        int count = 0;
        for (AppInfo a : apps) {
            if (!query.isEmpty()
                    && !a.label.toLowerCase(Locale.ROOT).contains(query)
                    && !a.packageName.toLowerCase(Locale.ROOT).contains(query)) continue;
            CheckBox cb = new CheckBox(this);
            cb.setText(a.label + "\n" + a.packageName);
            cb.setTextColor(TEXT);
            cb.setTextSize(15);
            cb.setButtonTintList(android.content.res.ColorStateList.valueOf(prefs.accentColor()));
            cb.setBackground(Ui.stroke(CARD_2, 1, 0x223A4658, 16, this));
            cb.setChecked(selected.contains(a.packageName));
            Ui.pad(cb, 12, 10, 12, 10);
            cb.setOnCheckedChangeListener((v, on) -> {
                Set<String> s = mode == APP_MODE_TRIGGERS ? prefs.triggers() : (mode == APP_MODE_PLAYERS ? prefs.players() : prefs.conflictApps());
                if (on) s.add(a.packageName); else s.remove(a.packageName);
                saveAppSelection(mode, s);
            });
            list.addView(cb, smallGapLp());
            count++;
        }
        if (count == 0) list.addView(tv(tr("Ничего не найдено", "Nothing found"), 15, MUTED, Typeface.NORMAL, Gravity.CENTER), btnLp());
    }

    private void saveAppSelection(int mode, Set<String> values) {
        if (mode == APP_MODE_TRIGGERS) prefs.setTriggers(values);
        else if (mode == APP_MODE_PLAYERS) prefs.setPlayers(values);
        else prefs.setConflictApps(values);
    }

    private void showConflictAppsDialog() {
        showAppDialog(APP_MODE_CONFLICTS);
    }

    private void showRecentTrackHistoryDialog() {
        final Dialog dialog = fullDialog();
        LinearLayout outer = dialogRoot();
        dialog.setContentView(outer);
        outer.addView(tv(tr("История последних треков", "Recent track history"), 24, TEXT, Typeface.BOLD, Gravity.CENTER), matchWrap());
        outer.addView(tv(tr("Последние 30 треков. Справа можно быстро скопировать название строки.", "Latest 30 tracks. Use the right-side button to quickly copy the full line."), 14, MUTED, Typeface.NORMAL, Gravity.CENTER), matchWrap());
        space(outer, 12);

        ScrollView sv = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        Ui.pad(box, 0, 0, 0, 18);
        sv.addView(box, new ScrollView.LayoutParams(-1, -2));
        outer.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));

        List<Prefs.RecentTrack> tracks = prefs.recentTracks();
        if (tracks.isEmpty()) {
            box.addView(tv(tr("История пока пустая.", "History is empty for now."), 15, MUTED, Typeface.NORMAL, Gravity.CENTER), btnLp());
        } else {
            int shown = 0;
            for (int i = tracks.size() - 1; i >= 0 && shown < 30; i--, shown++) {
                Prefs.RecentTrack item = tracks.get(i);
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setBackground(Ui.stroke(CARD_2, 1, 0x223A4658, 16, this));
                Ui.pad(row, 14, 12, 12, 12);

                TextView text = tv((shown + 1) + ". " + item.display(), 15, TEXT, Typeface.NORMAL, Gravity.START);
                text.setLineSpacing(Ui.dp(this, 2), 1.0f);
                row.addView(text, new LinearLayout.LayoutParams(0, -2, 1));

                Button copy = secondaryButton(tr("Копировать", "Copy"), v -> {
                    ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("track", item.display()));
                    Toast.makeText(this, tr("Трек скопирован", "Track copied"), Toast.LENGTH_SHORT).show();
                });
                LinearLayout.LayoutParams copyLp = new LinearLayout.LayoutParams(Ui.dp(this, 112), Ui.dp(this, 42));
                copyLp.leftMargin = Ui.dp(this, 10);
                row.addView(copy, copyLp);
                box.addView(row, smallGapLp());
            }
        }

        outer.addView(secondaryButton(tr("Готово", "Done"), v -> dialog.dismiss()), btnLp());
        dialog.show();
        fitDialog(dialog, true);
    }

    private void showInstructionsDialog() {
        final Dialog dialog = fullDialog();
        LinearLayout outer = dialogRoot();
        dialog.setContentView(outer);
        outer.addView(tv(tr("Инструкция", "Instructions"), 24, TEXT, Typeface.BOLD, Gravity.CENTER), matchWrap());
        outer.addView(tv(tr("Короткое описание всех важных функций приложения простыми словами.", "A short plain-language description of the app’s important functions."), 14, MUTED, Typeface.NORMAL, Gravity.CENTER), matchWrap());
        space(outer, 12);

        ScrollView sv = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        Ui.pad(box, 0, 0, 0, 18);
        sv.addView(box, new ScrollView.LayoutParams(-1, -2));
        outer.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));

        addInstructionItem(box, tr("Включить приложение", "Enable app"), tr("Полностью включает или отключает работу Navi Overlay.", "Fully enables or disables Navi Overlay."));
        addInstructionItem(box, tr("Показывать только поверх выбранных приложений", "Show only over selected apps"), tr("Окно будет показываться только поверх выбранных навигаторов.", "The window will appear only over the selected navigation apps."));
        addInstructionItem(box, tr("Приложения навигации", "Navigation apps"), tr("Список навигаторов, поверх которых может показываться окно.", "List of navigation apps where the window may appear."));
        addInstructionItem(box, tr("Аудио приложения", "Audio apps"), tr("Список плееров, из которых брать название трека и обложку.", "List of players used for track title and album art."));
        addInstructionItem(box, tr("Приложения с приоритетным окном", "Priority overlay apps"), tr("Если у этих приложений появляется своё окно, Navi Overlay временно уступает ему место.", "If these apps show their own window, Navi Overlay temporarily gives it priority."));
        addInstructionItem(box, tr("Настройки текста", "Text settings"), tr("Размер, тень, выравнивание, шрифт и жирность текста в окне.", "Size, shadow, alignment, font and bold style of the text in the window."));
        addInstructionItem(box, tr("Настройки окна", "Window settings"), tr("Прозрачность, форма, отступы, время показа, положение окна и поведение при паузе.", "Transparency, border shape, offsets, display time, position and pause behavior."));
        addInstructionItem(box, tr("Настройки цвета", "Color settings"), tr("Меняет цвета окна, рамки, текста, полосы перемотки и других элементов.", "Changes the colors of the window, border, text, seek bar and other elements."));
        addInstructionItem(box, tr("Положение окна", "Window position"), tr("Переносит окно в один из стандартных углов или в центр экрана.", "Moves the window to one of the standard corners or to the center of the screen."));
        addInstructionItem(box, tr("Пресет дизайна", "Design preset"), tr("Быстро меняет общий стиль окна: форму, плотность и характер рамки.", "Quickly changes the overall window style: shape, density and border character."));
        addInstructionItem(box, tr("Настройка кнопок управления музыкой", "Music control buttons settings"), tr("Размер, форма и расстояние между кнопками назад, пауза и вперёд.", "Size, shape and spacing of previous, pause and next buttons."));
        addInstructionItem(box, tr("Поведение окна при паузе", "Window behavior on pause"), tr("Определяет, скрывать ли окно на паузе, оставлять его или убирать с задержкой.", "Defines whether the window hides on pause, stays visible, or hides after a short delay."));
        addInstructionItem(box, tr("Разрешения", "Permissions"), tr("Раздел для выдачи системных разрешений, без которых окно и распознавание музыки не смогут работать стабильно.", "Section for granting the system permissions required for stable overlay and music detection."));
        addInstructionItem(box, tr("Поверх окон", "Draw over apps"), tr("Разрешение на показ окна поверх навигатора и других приложений.", "Permission to show the window over navigation and other apps."));
        addInstructionItem(box, tr("Доступ к уведомлениям", "Notification access"), tr("Нужен для чтения уведомлений плееров и получения информации о треках.", "Required to read player notifications and obtain track information."));
        addInstructionItem(box, tr("Уведомления приложения", "App notifications"), tr("Дополнительное разрешение Android 13+, чтобы само приложение могло показывать свои уведомления.", "Extra Android 13+ permission so the app can show its own notifications."));
        addInstructionItem(box, tr("Специальные возможности", "Accessibility"), tr("Помогают точнее понимать, что сейчас находится на экране магнитолы или телефона.", "Helps the app more accurately understand what is currently on the screen."));
        addInstructionItem(box, tr("История использования", "Usage access"), tr("Запасной способ определить активное приложение, если системный экран меняется нестандартно.", "Fallback way to detect the active app if the system screen changes in a non-standard way."));
        addInstructionItem(box, tr("Диагностика", "Diagnostics"), tr("Помогает понять, почему окно не появляется, и позволяет быстро скопировать отчёт.", "Helps you understand why the window does not appear and lets you quickly copy a report."));
        addInstructionItem(box, tr("Показывать окно постоянно", "Show window continuously"), tr("Окно не скрывается по таймеру, пока трек считается активным.", "The window does not hide by timer while the track is considered active."));
        addInstructionItem(box, tr("Полоса перемотки трека", "Track seek bar"), tr("Появляется под текстом и позволяет перематывать трек пальцем. Работает только вместе с постоянным показом и только там, где плеер поддерживает перемотку.", "Appears under the text and lets you seek the track with your finger. Works only with continuous display and only where the player supports seeking."));
        addInstructionItem(box, tr("Показывать обложку в окне", "Show album art in the window"), tr("Добавляет обложку слева от текста, если плеер действительно передаёт картинку трека.", "Adds album art to the left of the text if the player actually provides track artwork."));
        addInstructionItem(box, tr("Тапы по обложке", "Album art taps"), tr("Если обложка включена и кнопки управления выключены, одиночный тап ставит музыку на паузу, а двойной тап открывает текущий плеер.", "If album art is enabled and control buttons are disabled, a single tap pauses the music and a double tap opens the current player."));
        addInstructionItem(box, tr("Кнопки управления музыкой в overlay", "Media control buttons in overlay"), tr("Добавляет в окно кнопки назад, пауза и вперёд прямо поверх навигатора.", "Adds previous, pause and next buttons directly inside the window over navigation."));
        addInstructionItem(box, tr("Свайп по overlay: влево/вправо переключает треки", "Swipe on overlay: left/right switches tracks"), tr("Позволяет перелистывать треки быстрым движением по самому окну.", "Lets you switch tracks with a quick swipe directly on the window."));
        addInstructionItem(box, tr("Snap: магнит к краям и центру после перетаскивания", "Snap: magnet to edges and center after dragging"), tr("После перетаскивания окно само прилипает к удобным зонам экрана.", "After dragging, the window automatically snaps to convenient screen zones."));
        addInstructionItem(box, tr("При изменении громкости временно приглушать окно", "Temporarily dim the window on volume change"), tr("Во время регулировки громкости окно становится менее заметным и потом возвращает обычную яркость.", "While adjusting volume, the window becomes less visible and then returns to normal brightness."));
        addInstructionItem(box, tr("Floating режим: показывать трек даже без навигатора", "Floating mode: show the track even without navigation"), tr("Разрешает показывать окно даже тогда, когда навигатор не открыт.", "Allows the window to appear even when navigation is not open."));
        addInstructionItem(box, tr("Сворачивать окно вместе с навигацией", "Hide the window together with navigation"), tr("Когда навигация уходит с экрана, overlay тоже прячется.", "When navigation leaves the screen, the overlay hides as well."));
        addInstructionItem(box, tr("Фиксированный размер окна", "Fixed window size"), tr("Фиксирует ширину окна и позволяет не зависеть от длины текста.", "Locks the window width so it does not depend on text length."));
        addInstructionItem(box, tr("Мягкое восстановление после системных окон", "Soft recovery after system windows"), tr("Для проблемных магнитол. После громкости или чужих окон приложение восстанавливает overlay мягче и спокойнее.", "For problematic head units. After volume or other windows, the app restores the overlay more gently."));
        addInstructionItem(box, tr("История последних треков", "Recent track history"), tr("Показывает последние 30 треков и позволяет быстро копировать названия.", "Shows the latest 30 tracks and lets you quickly copy track names."));
        addInstructionItem(box, tr("Скопировать отчёт", "Copy report"), tr("Сохраняет технический отчёт в буфер обмена, чтобы проще было отправить его для разбора.", "Copies a technical report to the clipboard so it is easier to send for troubleshooting."));
        addInstructionItem(box, tr("Проверить окно", "Preview window"), tr("Показывает тестовое окно на 5 секунд, чтобы быстро увидеть изменения.", "Shows a test window for 5 seconds so you can quickly preview changes."));
        addInstructionItem(box, tr("Проверить и восстановить", "Check and recover"), tr("Открывает нужные настройки, если приложению реально не хватает разрешений.", "Opens the needed settings if the app is really missing permissions."));

        outer.addView(secondaryButton(tr("Готово", "Done"), v -> dialog.dismiss()), btnLp());
        dialog.show();
        fitDialog(dialog, true);
    }

    private void addInstructionItem(LinearLayout parent, String title, String desc) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setBackground(Ui.stroke(CARD_2, 1, 0x223A4658, 16, this));
        Ui.pad(row, 14, 12, 14, 12);
        row.addView(tv(title, 15, TEXT, Typeface.BOLD, Gravity.START), matchWrap());
        TextView hint = tv(desc, 13, MUTED, Typeface.NORMAL, Gravity.START);
        hint.setLineSpacing(Ui.dp(this, 2), 1.0f);
        row.addView(hint, matchWrap());
        parent.addView(row, smallGapLp());
    }

    private ArrayList<AppInfo> loadApps(Set<String> selected) {
        ArrayList<AppInfo> out = new ArrayList<>();
        ArrayList<AppInfo> cached = ensureInstalledAppsCache();
        HashSet<String> seen = new HashSet<>();
        for (AppInfo item : cached) {
            out.add(new AppInfo(item.label, item.packageName, selected.contains(item.packageName)));
            seen.add(item.packageName);
        }
        for (String p : selected) {
            if (p != null && !p.isEmpty() && !seen.contains(p)) out.add(new AppInfo(tr("Добавлено вручную", "Added manually"), p, true));
        }
        Collections.sort(out, (a, b) -> {
            if (a.selected != b.selected) return a.selected ? -1 : 1;
            return a.label.compareToIgnoreCase(b.label);
        });
        return out;
    }

    private ArrayList<AppInfo> ensureInstalledAppsCache() {
        if (installedAppsCache != null) return installedAppsCache;
        installedAppsCache = new ArrayList<>();
        PackageManager pm = getPackageManager();
        Intent i = new Intent(Intent.ACTION_MAIN, null);
        i.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> infos = pm.queryIntentActivities(i, 0);
        for (ResolveInfo r : infos) {
            String p = r.activityInfo.packageName;
            String l = String.valueOf(r.loadLabel(pm));
            installedAppsCache.add(new AppInfo(l, p, false));
        }
        return installedAppsCache;
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
        addSeek(box, tr("Время показа, сек. 0 = не скрывать", "Display time, sec. 0 = do not hide"), 0, 10, prefs.displayMs() / 1000, v -> { prefs.setDisplayMs(v * 1000); TrackOverlayManager.refresh(this); });
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
        Switch alwaysOnSwitch = switchRow(box, tr("Показывать окно постоянно", "Show window continuously"), prefs.displayWhilePlaying(), on -> {
            prefs.setDisplayWhilePlaying(on);
            if (!on && prefs.featureSeekBar()) prefs.setFeatureSeekBar(false);
            TrackOverlayManager.refresh(this);
        });
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
        LinearLayout box = dialogRoot();
        dialog.setContentView(box);
        box.addView(tv(colorTargetTitle(target), 24, TEXT, Typeface.BOLD, Gravity.CENTER), matchWrap());
        box.addView(tv(tr("Нажми на цвет — тестовое окно обновится сразу.", "Tap a color — the test window updates immediately."), 14, MUTED, Typeface.NORMAL, Gravity.CENTER), matchWrap());
        space(box, 12);

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(4);
        int[] colors = target == 0 ? windowPalette() : textPalette();
        for (int col : colors) {
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER);
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
            cell.setOnClickListener(v -> {
                if (target == 0) prefs.setBgColor(col);
                else if (target == 1) prefs.setArtistColor(col);
                else if (target == 2) prefs.setTitleColor(col);
                else if (target == 3) prefs.setSeekBarColor(col);
                else prefs.setSeekThumbColor(col);
                TrackOverlayManager.test(this);
            });
        }
        box.addView(grid, matchWrap());
        box.addView(primaryButton(tr("Готово", "Done"), v -> dialog.dismiss()), btnLp());
        dialog.show();
        fitDialog(dialog, false);
    }

    private String colorTargetTitle(int target) {
        if (target == 1) return tr("Цвет исполнителя", "Artist color");
        if (target == 2) return tr("Цвет названия песни", "Track title color");
        if (target == 3) return tr("Цвет полосы перемотки", "Seek bar color");
        if (target == 4) return tr("Цвет ползунка перемотки", "Seek thumb color");
        return tr("Выбор цвета окна", "Window color");
    }

    private int[] textPalette() {
        return new int[]{0xFFFFFFFF, 0xFFE5E7EB, 0xFFCBD5E1, 0xFF94A3B8,
                0xFF00D5FF, 0xFF38BDF8, 0xFF60A5FA, 0xFF818CF8,
                0xFF22C55E, 0xFF84CC16, 0xFFFFD166, 0xFFFFA94D,
                0xFFFF6B6B, 0xFFFF4D9D, 0xFFC084FC, 0xFF2DD4BF};
    }

    private int[] windowPalette() {
        return new int[]{0xFF020617, 0xFF0B1220, 0xFF111827, 0xFF1F2937,
                0xFF0F172A, 0xFF172554, 0xFF082F49, 0xFF042F2E,
                0xFF052E16, 0xFF3F2A05, 0xFF431407, 0xFF450A0A,
                0xFF312E81, 0xFF4A044E, 0xFF1E1B4B, 0xFF18181B};
    }

    private void showBorderColorDialog(boolean controls) {
        final Dialog dialog = fullDialog();
        LinearLayout box = dialogRoot();
        dialog.setContentView(box);
        box.addView(tv(controls ? tr("Цвет рамок кнопок", "Control border color") : tr("Цвет рамки окна", "Window border color"), 24, TEXT, Typeface.BOLD, Gravity.CENTER), matchWrap());
        box.addView(tv(controls ? tr("Выбери цвет контура кнопок управления. Изменение видно сразу.", "Pick a border color for control buttons. Changes apply immediately.") : tr("Выбери цвет контура. Изменение видно сразу на тестовом окне.", "Pick a border color. The preview updates immediately."), 14, MUTED, Typeface.NORMAL, Gravity.CENTER), matchWrap());
        space(box, 12);

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(4);
        for (int col : borderPalette()) {
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER);
            TextView swatch = new TextView(this);
            swatch.setText(" ");
            swatch.setBackground(Ui.stroke(col, 2, 0xAAFFFFFF, 16, this));
            LinearLayout.LayoutParams swLp = new LinearLayout.LayoutParams(Ui.dp(this, 54), Ui.dp(this, 54));
            cell.addView(swatch, swLp);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = 0;
            lp.height = -2;
            lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            lp.setMargins(Ui.dp(this, 5), Ui.dp(this, 6), Ui.dp(this, 5), Ui.dp(this, 6));
            grid.addView(cell, lp);
            cell.setOnClickListener(v -> {
                if (controls) prefs.setControlsBorderColor(col); else prefs.setBorderColor(col);
                TrackOverlayManager.test(this);
            });
        }
        box.addView(grid, matchWrap());
        box.addView(primaryButton(tr("Готово", "Done"), v -> dialog.dismiss()), btnLp());
        dialog.show();
        fitDialog(dialog, false);
    }

    private int[] borderPalette() {
        return new int[]{
                0x00FFFFFF, 0x33FFFFFF, 0x66FFFFFF, 0xAAFFFFFF,
                0xFF00D5FF, 0xFF38BDF8, 0xFF60A5FA, 0xFF818CF8,
                0xFF22C55E, 0xFF84CC16, 0xFFFFD166, 0xFFFFA726,
                0xFFFF6B6B, 0xFFFF4D9D, 0xFFC084FC, 0xFF2DD4BF
        };
    }

    private void showFontDialog() {
        final Dialog dialog = fullDialog();
        LinearLayout outer = dialogRoot();
        dialog.setContentView(outer);
        outer.addView(tv(tr("Шрифт окна", "Window font"), 24, TEXT, Typeface.BOLD, Gravity.CENTER), matchWrap());
        outer.addView(tv(tr("Выбери шрифт по образцу. Изменение применяется сразу к overlay.", "Choose a font by sample. Changes apply to the overlay immediately."), 14, MUTED, Typeface.NORMAL, Gravity.CENTER), matchWrap());
        space(outer, 12);

        ScrollView sv = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        Ui.pad(box, 0, 0, 0, 18);
        sv.addView(box, new ScrollView.LayoutParams(-1, -2));
        outer.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));

        ArrayList<LinearLayout> rows = new ArrayList<>();
        ArrayList<TextView> names = new ArrayList<>();
        ArrayList<TextView> samples = new ArrayList<>();
        ArrayList<TextView> hints = new ArrayList<>();

        for (int i = 0; i < OverlayFonts.count(); i++) {
            final int fontIndex = i;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setBackground(Ui.stroke(CARD_2, 1, fontIndex == prefs.textFont() ? prefs.accentColor() : 0x223A4658, 16, this));
            Ui.pad(row, 16, 14, 16, 14);

            TextView name = tv(OverlayFonts.nameAt(fontIndex), 16, fontIndex == prefs.textFont() ? prefs.accentColor() : TEXT, Typeface.BOLD, Gravity.START);
            TextView sample = tv(tr("Пример / Example — АБВ abc", "Example / Пример — ABC абв"), 18, TEXT, Typeface.NORMAL, Gravity.START);
            sample.setTypeface(OverlayFonts.resolve(this, fontIndex, prefs.textBold()));
            sample.getPaint().setFakeBoldText(prefs.textBold());
            TextView hint = tv(fontIndex == 0 ? tr("Системный шрифт Android", "Android system font") : tr("Образец для overlay", "Overlay sample"), 12, MUTED_2, Typeface.NORMAL, Gravity.START);

            row.addView(name, matchWrap());
            row.addView(sample, matchWrap());
            row.addView(hint, matchWrap());
            box.addView(row, smallGapLp());
            rows.add(row);
            names.add(name);
            samples.add(sample);
            hints.add(hint);

            row.setOnClickListener(v -> {
                prefs.setTextFont(fontIndex);
                TrackOverlayManager.refresh(this);
                for (int j = 0; j < rows.size(); j++) {
                    boolean selected = j == prefs.textFont();
                    rows.get(j).setBackground(Ui.stroke(CARD_2, 1, selected ? prefs.accentColor() : 0x223A4658, 16, this));
                    names.get(j).setTextColor(selected ? prefs.accentColor() : TEXT);
                    samples.get(j).setTypeface(OverlayFonts.resolve(this, j, prefs.textBold()));
                    samples.get(j).getPaint().setFakeBoldText(prefs.textBold());
                    hints.get(j).setText(selected
                            ? tr("Выбранный шрифт overlay", "Selected overlay font")
                            : (j == 0 ? tr("Системный шрифт Android", "Android system font") : tr("Образец для overlay", "Overlay sample")));
                }
            });
        }

        outer.addView(primaryButton(tr("Готово", "Done"), v -> dialog.dismiss()), btnLp());
        dialog.show();
        fitDialog(dialog, true);
    }

    private void showTextAlignmentDialog() {
        final Dialog dialog = fullDialog();
        LinearLayout outer = dialogRoot();
        dialog.setContentView(outer);
        outer.addView(tv(tr("Выравнивание текста", "Text alignment"), 24, TEXT, Typeface.BOLD, Gravity.CENTER), matchWrap());
        outer.addView(tv(tr("Выбери, как выравнивать строку трека внутри окна.", "Choose how to align the track line inside the window."), 14, MUTED, Typeface.NORMAL, Gravity.CENTER), matchWrap());
        space(outer, 12);

        ScrollView sv = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        Ui.pad(box, 0, 0, 0, 18);
        sv.addView(box, new ScrollView.LayoutParams(-1, -2));
        outer.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));

        String[] labels = new String[]{tr("Слева", "Left"), tr("По центру", "Center")};
        int[] values = new int[]{Prefs.TEXT_ALIGN_LEFT, Prefs.TEXT_ALIGN_CENTER};
        ArrayList<RadioButton> radios = new ArrayList<>();
        int selected = prefs.textAlign();
        for (int i = 0; i < labels.length; i++) {
            final int value = values[i];
            RadioButton rb = new RadioButton(this);
            rb.setText(labels[i]);
            rb.setTextColor(TEXT);
            rb.setTextSize(16);
            rb.setButtonTintList(android.content.res.ColorStateList.valueOf(prefs.accentColor()));
            rb.setChecked(value == selected);
            rb.setBackground(Ui.stroke(CARD_2, 1, 0x223A4658, 18, this));
            Ui.pad(rb, 14, 12, 14, 12);
            rb.setOnClickListener(v -> {
                prefs.setTextAlign(value);
                TrackOverlayManager.refresh(this);
                for (RadioButton radio : radios) radio.setChecked(radio == rb);
            });
            radios.add(rb);
            box.addView(rb, smallGapLp());
        }
        box.addView(previewButton(tr("Проверить окно", "Preview window"), v -> TrackOverlayManager.test(this)), btnLp());
        outer.addView(secondaryButton(tr("Готово", "Done"), v -> dialog.dismiss()), btnLp());
        dialog.show();
        fitDialog(dialog, true);
    }

    private void showControlButtonsDialog() {
        final Dialog dialog = fullDialog();
        LinearLayout outer = dialogRoot();
        dialog.setContentView(outer);
        outer.addView(tv(tr("Настройка кнопок управления музыкой", "Music control buttons settings"), 24, TEXT, Typeface.BOLD, Gravity.CENTER), matchWrap());
        outer.addView(tv(tr("Здесь можно настроить размер, форму и расстояние между кнопками управления.", "Set button size, shape and spacing here."), 14, MUTED, Typeface.NORMAL, Gravity.CENTER), matchWrap());
        space(outer, 12);

        ScrollView sv = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        Ui.pad(box, 0, 0, 0, 18);
        sv.addView(box, new ScrollView.LayoutParams(-1, -2));
        outer.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));

        box.addView(menuButton(tr("Размер кнопок", "Button size"), tr("Маленькие, средние или крупные", "Small, medium or large"), v -> showControlButtonsSizeDialog()), smallGapLp());
        box.addView(menuButton(tr("Форма кнопок", "Button shape"), tr("Круглые, прямоугольные, квадратные или без рамок", "Round, rectangular, square or borderless"), v -> showControlButtonsShapeDialog()), smallGapLp());
        box.addView(menuButton(tr("Расстояние между кнопками", "Button spacing"), tr("Компактно, обычно или широко", "Compact, normal or wide"), v -> showControlButtonsSpacingDialog()), smallGapLp());
        box.addView(previewButton(tr("Проверить окно", "Preview window"), v -> TrackOverlayManager.test(this)), btnLp());
        outer.addView(secondaryButton(tr("Готово", "Done"), v -> dialog.dismiss()), btnLp());
        dialog.show();
        fitDialog(dialog, true);
    }

    private void showPauseBehaviorDialog() {
        final Dialog dialog = fullDialog();
        LinearLayout outer = dialogRoot();
        dialog.setContentView(outer);
        outer.addView(tv(tr("Поведение окна при паузе", "Window behavior on pause"), 24, TEXT, Typeface.BOLD, Gravity.CENTER), matchWrap());
        outer.addView(tv(tr("Выбери, что делать с окном, если музыка остановилась на паузе.", "Choose what happens to the window when playback is paused."), 14, MUTED, Typeface.NORMAL, Gravity.CENTER), matchWrap());
        space(outer, 12);

        ScrollView sv = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        Ui.pad(box, 0, 0, 0, 18);
        sv.addView(box, new ScrollView.LayoutParams(-1, -2));
        outer.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));

        String[] labels = new String[]{
                tr("Оставлять окно", "Keep the window"),
                tr("Скрывать окно", "Hide the window"),
                tr("Оставлять ненадолго и потом скрывать", "Keep briefly, then hide")
        };
        int[] values = new int[]{Prefs.PAUSE_BEHAVIOR_KEEP, Prefs.PAUSE_BEHAVIOR_HIDE, Prefs.PAUSE_BEHAVIOR_SHORT};
        ArrayList<RadioButton> radios = new ArrayList<>();
        int selected = prefs.pauseBehavior();
        for (int i = 0; i < labels.length; i++) {
            final int value = values[i];
            RadioButton rb = new RadioButton(this);
            rb.setText(labels[i]);
            rb.setTextColor(TEXT);
            rb.setTextSize(16);
            rb.setButtonTintList(android.content.res.ColorStateList.valueOf(prefs.accentColor()));
            rb.setChecked(value == selected);
            rb.setBackground(Ui.stroke(CARD_2, 1, 0x223A4658, 18, this));
            Ui.pad(rb, 14, 12, 14, 12);
            rb.setOnClickListener(v -> {
                prefs.setPauseBehavior(value);
                for (RadioButton radio : radios) radio.setChecked(radio == rb);
            });
            radios.add(rb);
            box.addView(rb, smallGapLp());
        }
        outer.addView(secondaryButton(tr("Готово", "Done"), v -> dialog.dismiss()), btnLp());
        dialog.show();
        fitDialog(dialog, true);
    }

    private void showControlButtonsSizeDialog() {
        showControlButtonsChoiceDialog(
                tr("Размер кнопок", "Button size"),
                tr("Выбери размер кнопок управления музыкой.", "Choose the size of media control buttons."),
                new String[]{tr("Маленькие", "Small"), tr("Средние", "Medium"), tr("Крупные", "Large")},
                new int[]{Prefs.CONTROLS_SIZE_SMALL, Prefs.CONTROLS_SIZE_MEDIUM, Prefs.CONTROLS_SIZE_LARGE},
                prefs.controlsSize(),
                value -> prefs.setControlsSize(value));
    }

    private void showControlButtonsShapeDialog() {
        showControlButtonsChoiceDialog(
                tr("Форма кнопок", "Button shape"),
                tr("Выбери форму и рамку кнопок управления.", "Choose the shape and border style of control buttons."),
                new String[]{tr("Круглые", "Round"), tr("Прямоугольные", "Rectangular"), tr("Квадратные", "Square"), tr("Без рамок", "Borderless")},
                new int[]{Prefs.CONTROLS_SHAPE_ROUND, Prefs.CONTROLS_SHAPE_RECT, Prefs.CONTROLS_SHAPE_SQUARE, Prefs.CONTROLS_SHAPE_BORDERLESS},
                prefs.controlsShape(),
                value -> prefs.setControlsShape(value));
    }

    private void showControlButtonsSpacingDialog() {
        showControlButtonsChoiceDialog(
                tr("Расстояние между кнопками", "Button spacing"),
                tr("Выбери, насколько кнопки должны быть разнесены друг от друга.", "Choose how far apart the buttons should be."),
                new String[]{tr("Компактно", "Compact"), tr("Обычно", "Normal"), tr("Шире", "Wide")},
                new int[]{Prefs.CONTROLS_SPACING_COMPACT, Prefs.CONTROLS_SPACING_NORMAL, Prefs.CONTROLS_SPACING_WIDE},
                prefs.controlsSpacing(),
                value -> prefs.setControlsSpacing(value));
    }

    private interface IntSetter { void set(int value); }

    private void showControlButtonsChoiceDialog(String title, String subtitle, String[] labels, int[] values, int selected, IntSetter setter) {
        final Dialog dialog = fullDialog();
        LinearLayout outer = dialogRoot();
        dialog.setContentView(outer);
        outer.addView(tv(title, 24, TEXT, Typeface.BOLD, Gravity.CENTER), matchWrap());
        outer.addView(tv(subtitle, 14, MUTED, Typeface.NORMAL, Gravity.CENTER), matchWrap());
        space(outer, 12);

        ScrollView sv = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        Ui.pad(box, 0, 0, 0, 18);
        sv.addView(box, new ScrollView.LayoutParams(-1, -2));
        outer.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));

        ArrayList<RadioButton> radios = new ArrayList<>();
        for (int i = 0; i < labels.length; i++) {
            final int value = values[i];
            RadioButton rb = new RadioButton(this);
            rb.setText(labels[i]);
            rb.setTextColor(TEXT);
            rb.setTextSize(16);
            rb.setButtonTintList(android.content.res.ColorStateList.valueOf(prefs.accentColor()));
            rb.setChecked(value == selected);
            rb.setBackground(Ui.stroke(CARD_2, 1, 0x223A4658, 18, this));
            Ui.pad(rb, 14, 12, 14, 12);
            rb.setOnClickListener(v -> {
                setter.set(value);
                TrackOverlayManager.refresh(this);
                for (RadioButton radio : radios) radio.setChecked(radio == rb);
            });
            radios.add(rb);
            box.addView(rb, smallGapLp());
        }
        box.addView(previewButton(tr("Проверить окно", "Preview window"), v -> TrackOverlayManager.test(this)), btnLp());
        outer.addView(secondaryButton(tr("Готово", "Done"), v -> dialog.dismiss()), btnLp());
        dialog.show();
        fitDialog(dialog, true);
    }

    private void showPositionDialog() {
        final Dialog dialog = fullDialog();
        LinearLayout outer = dialogRoot();
        dialog.setContentView(outer);
        outer.addView(tv(tr("Положение окна", "Window position"), 24, TEXT, Typeface.BOLD, Gravity.CENTER), matchWrap());
        outer.addView(tv(tr("Выбери стандартное положение окна. Изменение применяется сразу.", "Choose a standard window position. Changes apply immediately."), 14, MUTED, Typeface.NORMAL, Gravity.CENTER), matchWrap());
        space(outer, 12);

        ScrollView sv = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        Ui.pad(box, 0, 0, 0, 18);
        sv.addView(box, new ScrollView.LayoutParams(-1, -2));
        outer.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));

        String[] labels = new String[]{
                tr("Верх слева", "Top left"),
                tr("Верх по центру", "Top center"),
                tr("Верх справа", "Top right"),
                tr("Центр", "Center"),
                tr("Низ слева", "Bottom left"),
                tr("Низ по центру", "Bottom center"),
                tr("Низ справа", "Bottom right")
        };
        ArrayList<RadioButton> radios = new ArrayList<>();
        int selected = prefs.position();

        for (int i = 0; i < labels.length; i++) {
            final int positionIndex = i;
            RadioButton rb = new RadioButton(this);
            rb.setText(labels[i]);
            rb.setTextColor(TEXT);
            rb.setTextSize(16);
            rb.setButtonTintList(android.content.res.ColorStateList.valueOf(prefs.accentColor()));
            rb.setChecked(positionIndex == selected);
            rb.setBackground(Ui.stroke(CARD_2, 1, 0x223A4658, 18, this));
            Ui.pad(rb, 14, 12, 14, 12);
            rb.setOnClickListener(v -> {
                prefs.setPosition(positionIndex);
                TrackOverlayManager.refresh(this);
                for (int j = 0; j < radios.size(); j++) radios.get(j).setChecked(j == positionIndex);
            });
            radios.add(rb);
            box.addView(rb, smallGapLp());
        }

        box.addView(previewButton(tr("Проверить окно", "Preview window"), v -> TrackOverlayManager.test(this)), btnLp());
        outer.addView(secondaryButton(tr("Готово", "Done"), v -> dialog.dismiss()), btnLp());
        dialog.show();
        fitDialog(dialog, true);
    }

    private void showDesignPresetDialog() {
        final Dialog dialog = fullDialog();
        LinearLayout outer = dialogRoot();
        dialog.setContentView(outer);
        outer.addView(tv(tr("Пресет дизайна", "Design preset"), 24, TEXT, Typeface.BOLD, Gravity.CENTER), matchWrap());
        outer.addView(tv(tr("Выбери готовый стиль формы и оформления overlay. Изменение применяется сразу.", "Choose a ready-made overlay shape and style. Changes apply immediately."), 14, MUTED, Typeface.NORMAL, Gravity.CENTER), matchWrap());
        space(outer, 12);

        ScrollView sv = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        Ui.pad(box, 0, 0, 0, 18);
        sv.addView(box, new ScrollView.LayoutParams(-1, -2));
        outer.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));

        String[] names = new String[]{
                tr("Classic — как сейчас", "Classic — current look"),
                tr("Minimal — тонкая тёмная плашка", "Minimal — slim dark plate"),
                tr("Glass — плавные волны", "Glass — soft waves"),
                tr("Car UI — авто-стиль", "Car UI — automotive style"),
                tr("Soft — мягкая карточка", "Soft — soft card"),
                tr("Contrast — контрастный стиль", "Contrast — contrast style"),
                tr("Capsule — капсульная форма", "Capsule — capsule shape"),
                tr("Premium — плотный тёмный стиль", "Premium — dense dark style"),
                tr("Spikes — шипы", "Spikes — spikes"),
                tr("Orbit — круглый стиль", "Orbit — round style")
        };
        ArrayList<RadioButton> radios = new ArrayList<>();
        int selected = prefs.designPreset();

        for (int i = 0; i < names.length; i++) {
            final int presetIndex = i;
            RadioButton rb = new RadioButton(this);
            rb.setText(names[i]);
            rb.setTextColor(TEXT);
            rb.setTextSize(16);
            rb.setButtonTintList(android.content.res.ColorStateList.valueOf(prefs.accentColor()));
            rb.setChecked(presetIndex == selected);
            rb.setBackground(Ui.stroke(CARD_2, 1, 0x223A4658, 18, this));
            Ui.pad(rb, 14, 12, 14, 12);
            rb.setOnClickListener(v -> {
                prefs.setDesignPreset(presetIndex);
                TrackOverlayManager.refresh(this);
                for (int j = 0; j < radios.size(); j++) radios.get(j).setChecked(j == presetIndex);
            });
            radios.add(rb);
            box.addView(rb, smallGapLp());
        }

        box.addView(previewButton(tr("Проверить окно", "Preview window"), v -> TrackOverlayManager.test(this)), btnLp());
        outer.addView(secondaryButton(tr("Готово", "Done"), v -> dialog.dismiss()), btnLp());
        dialog.show();
        fitDialog(dialog, true);
    }

    private void copyDiagnosticsReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("Navi Overlay diagnostics\n");
        sb.append("enabled=").append(prefs.enabled()).append("\n");
        sb.append("overlay=").append(TrackOverlayManager.canDraw(this)).append("\n");
        sb.append("notificationListener=").append(notificationAccessEnabled(this)).append("\n");
        sb.append("postNotifications=").append(hasPostNotificationsPermission()).append("\n");
        sb.append("accessibility=").append(ForegroundDetector.isAccessibilityEnabled(this)).append("\n");
        sb.append("accessibilityServiceConnected=").append(NavigatorAccessibilityService.isServiceConnected()).append("\n");
        sb.append("accessibilityConnectedAgeSec=").append(ageSeconds(NavigatorAccessibilityService.connectedAt())).append("\n");
        sb.append("accessibilityLastEventAgeSec=").append(ageSeconds(NavigatorAccessibilityService.lastEventAt())).append("\n");
        sb.append("accessibilityLastRootReadAgeSec=").append(ageSeconds(NavigatorAccessibilityService.lastRootReadAt())).append("\n");
        sb.append("usageAccess=").append(ForegroundDetector.hasUsageAccess(this)).append("\n");
        sb.append("persistedLastTrigger=").append(prefs.lastTriggerPackage()).append("\n");
        sb.append("persistedLastTriggerAgeSec=").append(ageSeconds(prefs.lastTriggerAt())).append("\n");
        sb.append("accessibilityPackage=").append(ForegroundState.get()).append("\n");
        sb.append("usagePackage=").append(ForegroundDetector.debugCurrent(this)).append("\n");
        try {
            AppStateDetector.Result appState = new AppStateDetector(this).read();
            sb.append("navigatorVisible=").append(appState.navigatorVisible).append("\n");
            sb.append("foregroundPackage=").append(appState.foregroundPackage).append("\n");
            sb.append("navReason=").append(appState.reason).append("\n");
        } catch (Throwable t) { sb.append("appStateError=").append(t.getClass().getSimpleName()).append("\n"); }
        try {
            TrackSnapshot track = new MusicStateDetector(this).read();
            sb.append("musicPlaying=").append(track.playing).append("\n");
            sb.append("trackSourceType=").append(track.sourceType).append("\n");
            sb.append("trackPackage=").append(track.sourcePackage).append("\n");
            sb.append("artist=").append(track.artist).append("\n");
            sb.append("title=").append(track.title).append("\n");
            sb.append("featureControls=").append(prefs.featureControls()).append("\n");
            sb.append("featureSwipeTracks=").append(prefs.featureSwipeTracks()).append("\n");
            sb.append("featureSnap=").append(prefs.featureSnap()).append("\n");
            sb.append("featureVolumeDim=").append(prefs.featureVolumeDim()).append("\n");
            sb.append("featureFloating=").append(prefs.featureFloating()).append("\n");
            sb.append("featureSoftRecoverySystemWindows=").append(prefs.featureSoftRecoverySystemWindows()).append("\n");
            sb.append("featureSeekBar=").append(prefs.featureSeekBar()).append("\n");
            sb.append("textAlign=").append(prefs.textAlign()).append("\n");
            sb.append("conflictApps=").append(new ArrayList<>(prefs.conflictApps())).append("\n");
            sb.append("controlsSize=").append(prefs.controlsSize()).append("\n");
            sb.append("controlsShape=").append(prefs.controlsShape()).append("\n");
            sb.append("controlsSpacing=").append(prefs.controlsSpacing()).append("\n");
            sb.append("pauseBehavior=").append(prefs.pauseBehavior()).append("\n");
            sb.append("designPreset=").append(prefs.designPreset()).append("\n");
            sb.append("textFont=").append(OverlayFonts.nameAt(prefs.textFont())).append("\n");
            sb.append("borderColor=#").append(Integer.toHexString(prefs.borderColor())).append("\n");
            sb.append("borderWidth=").append(prefs.borderWidth()).append("\n");
            sb.append("controlsBorderColor=#").append(Integer.toHexString(prefs.controlsBorderColor())).append("\n");
            int index = 1;
            for (Prefs.RecentTrack item : prefs.recentTracks()) {
                sb.append("recentTrack").append(index++).append("=").append(item.display()).append("\n");
            }
        } catch (Throwable t) { sb.append("musicStateError=").append(t.getClass().getSimpleName()).append("\n"); }
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("Navi Overlay diagnostics", sb.toString()));
        Toast.makeText(this, tr("Диагностика скопирована", "Diagnostics copied"), Toast.LENGTH_SHORT).show();
    }

    private long ageSeconds(long ts) {
        if (ts <= 0L) return -1L;
        long delta = System.currentTimeMillis() - ts;
        return Math.max(0L, delta / 1000L);
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
        boolean allPerms = overlay && usage && acc && notif;

        AppStateDetector.Result appState;
        try { appState = new AppStateDetector(this).read(); } catch (Throwable t) { appState = null; }
        TrackSnapshot track;
        try { track = new MusicStateDetector(this).read(); } catch (Throwable t) { track = TrackSnapshot.empty(); }

        boolean enabled = prefs.enabled();
        boolean navOk = appState != null && appState.navigatorVisible;
        boolean musicOk = track != null && track.playing && track.hasText();

        SpannableStringBuilder sb = new SpannableStringBuilder();
        appendStatusLine(sb, tr("Статус: ", "Status: "), enabled ? tr("Включено", "Enabled") : tr("Выключено", "Disabled"), enabled);
        appendStatusLine(sb, tr("Разрешения: ", "Permissions: "), allPerms ? tr("Выданы", "Granted") : tr("Выданы не все", "Not all granted"), allPerms);
        appendStatusLine(sb, tr("Навигатор: ", "Navigator: "), navOk ? tr("Запущен", "Running") : tr("Нет", "No"), navOk);
        appendStatusLine(sb, tr("Музыка: ", "Music: "), musicOk ? tr("Запущена", "Running") : tr("Нет", "No"), musicOk);
        if (enabled && acc && !accConnected) {
            appendStatusLine(sb, tr("Сервис спец. возможностей: ", "Accessibility service: "), tr("Нет связи", "No connection"), false);
        }
        statusView.setText(sb);
        statusView.setTextColor(TEXT);
        if (enableSwitch != null && enableSwitch.isChecked() != prefs.enabled()) enableSwitch.setChecked(prefs.enabled());
        if (onlyTriggerSwitch != null && onlyTriggerSwitch.isChecked() != prefs.showOnlyWithTrigger()) onlyTriggerSwitch.setChecked(prefs.showOnlyWithTrigger());
        if (englishUiSwitch != null && englishUiSwitch.isChecked() != prefs.englishUi()) englishUiSwitch.setChecked(prefs.englishUi());
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

    private void appendStatusLine(SpannableStringBuilder sb, String label, String value, boolean ok) {
        int start = sb.length();
        sb.append(label).append(value).append("\n");
        int valueStart = start + label.length();
        int valueEnd = valueStart + value.length();
        sb.setSpan(new ForegroundColorSpan(ok ? GREEN : 0xFFFF4D4D), valueStart, valueEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
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

    private View menuButton(String title, String desc, View.OnClickListener listener) {
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

    private Button primaryButton(String text, View.OnClickListener l) {
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

    private Button previewButton(String text, View.OnClickListener l) {
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

    private Button secondaryButton(String text, View.OnClickListener l) {
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

    private TextView tv(String s, int sp, int color, int style, int gravity) {
        TextView t = Ui.tv(this, s, sp, color, style);
        t.setGravity(gravity);
        return t;
    }

    private GradientDrawable gradient(int top, int bottom, int radius) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{top, bottom});
        g.setCornerRadius(Ui.dp(this, radius));
        return g;
    }

    private GradientDrawable gradientButton(int left, int right, int radius) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{left, right});
        g.setCornerRadius(Ui.dp(this, radius));
        return g;
    }

    private GradientDrawable cardBg(int radius) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{CARD_TOP, CARD});
        g.setCornerRadius(Ui.dp(this, radius));
        g.setStroke(Ui.dp(this, 1), STROKE_SOFT);
        return g;
    }

    private GradientDrawable menuBg() {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xFF1A263B, 0xFF131E30});
        g.setCornerRadius(Ui.dp(this, 18));
        g.setStroke(Ui.dp(this, 1), STROKE_SOFT);
        return g;
    }

    private int lighten(int color, int amount) {
        int a = Color.alpha(color);
        int r = Math.min(255, Color.red(color) + amount);
        int g = Math.min(255, Color.green(color) + amount);
        int b = Math.min(255, Color.blue(color) + amount);
        return Color.argb(a, r, g, b);
    }

    private Dialog fullDialog() {
        Dialog d = new Dialog(this);
        Window w = d.getWindow();
        if (w != null) w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        return d;
    }

    private LinearLayout dialogRoot() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(gradient(BG, BG_2, 0));
        Ui.pad(box, 18, 20, 18, 16);
        return box;
    }

    private void fitDialog(Dialog d, boolean fullHeight) {
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(-1, fullHeight ? -1 : -2);
        }
    }

    private LinearLayout.LayoutParams matchWrap() { return new LinearLayout.LayoutParams(-1, -2); }
    private LinearLayout.LayoutParams btnLp() { LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, Ui.dp(this, 52)); lp.setMargins(0, Ui.dp(this, 12), 0, 0); return lp; }
    private LinearLayout.LayoutParams smallGapLp() { LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0, Ui.dp(this, 9), 0, 0); return lp; }
    private LinearLayout.LayoutParams weightLp(float weight) { LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(this, 58), weight); lp.setMargins(Ui.dp(this, 4), Ui.dp(this, 8), Ui.dp(this, 4), 0); return lp; }
    private LinearLayout.LayoutParams quickButtonLp(float weight) { LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(this, 64), weight); lp.setMargins(Ui.dp(this, 4), Ui.dp(this, 8), Ui.dp(this, 4), Ui.dp(this, 10)); return lp; }
    private void space(LinearLayout parent, int dp) { Space s = new Space(this); parent.addView(s, new LinearLayout.LayoutParams(1, Ui.dp(this, dp))); }

    private String tr(String ru, String en) {
        return prefs != null && prefs.englishUi() ? en : ru;
    }
}
