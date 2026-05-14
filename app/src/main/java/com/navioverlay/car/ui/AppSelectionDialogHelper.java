package com.navioverlay.car.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import com.navioverlay.car.core.AppInfo;
import com.navioverlay.car.core.Constants;
import com.navioverlay.car.core.Prefs;
import com.navioverlay.car.core.Ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class AppSelectionDialogHelper {
    static final int MODE_TRIGGERS = 0;
    static final int MODE_PLAYERS = 1;
    static final int MODE_CONFLICTS = 2;

    private static final int BG = 0xFF030712;
    private static final int BG_2 = 0xFF071021;
    private static final int CARD_2 = 0xFF1A2538;
    private static final int FIELD = 0xFF0B1220;
    private static final int STROKE = 0x4460708A;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int MUTED = 0xFFC4CBD7;

    private static ArrayList<AppInfo> installedAppsCache;

    private AppSelectionDialogHelper() {}

    static void show(Activity activity, Prefs prefs, int mode) {
        boolean triggers = mode == MODE_TRIGGERS;
        boolean players = mode == MODE_PLAYERS;
        final Dialog dialog = fullDialog(activity);
        LinearLayout box = dialogRoot(activity);
        dialog.setContentView(box);

        box.addView(tv(activity, tr(prefs,
                mode == MODE_TRIGGERS ? "Приложения навигации" : (players ? "Аудио приложения" : "Приложения с приоритетным окном"),
                mode == MODE_TRIGGERS ? "Navigation apps" : (players ? "Audio apps" : "Priority overlay apps")
        ), 24, TEXT, Typeface.BOLD, Gravity.CENTER), matchWrap());
        box.addView(tv(activity, tr(prefs,
                mode == MODE_TRIGGERS
                        ? "Выбери приложения, поверх которых можно показывать окно."
                        : (players
                        ? "Выбери плееры, откуда брать название трека."
                        : "Выбери приложения, чьи собственные окна должны временно иметь приоритет над overlay."),
                mode == MODE_TRIGGERS
                        ? "Choose apps over which the window may appear."
                        : (players
                        ? "Choose players used for track metadata."
                        : "Choose apps whose own windows should temporarily take priority over the overlay.")
        ), 14, MUTED, Typeface.NORMAL, Gravity.CENTER), matchWrap());
        space(activity, box, 14);

        EditText search = new EditText(activity);
        search.setHint(tr(prefs, "Поиск по названию или пакету", "Search by label or package"));
        search.setSingleLine(true);
        search.setTextColor(TEXT);
        search.setHintTextColor(0xFF7F8EA3);
        search.setTextSize(16);
        search.setBackground(Ui.stroke(FIELD, 1, STROKE, 18, activity));
        Ui.pad(search, 14, 12, 14, 12);
        box.addView(search, matchWrap());

        LinearLayout quick = new LinearLayout(activity);
        quick.setOrientation(LinearLayout.HORIZONTAL);
        quick.setGravity(Gravity.CENTER);
        quick.setClipChildren(false);
        quick.setClipToPadding(false);
        quick.setPadding(0, 0, 0, Ui.dp(activity, 2));
        quick.addView(secondaryButton(activity, prefs, tr(prefs,
                mode == MODE_TRIGGERS ? "Яндекс + карты" : (players ? "Популярные плееры" : "Полезные примеры"),
                mode == MODE_TRIGGERS ? "Yandex + maps" : (players ? "Popular players" : "Useful examples")
        ), null), quickButtonLp(activity, 1));
        quick.addView(secondaryButton(activity, prefs, tr(prefs, "Очистить", "Clear"), null), quickButtonLp(activity, 1));
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
        LinearLayout.LayoutParams quickLp = smallGapLp(activity);
        quickLp.bottomMargin = Ui.dp(activity, 6);
        box.addView(quick, quickLp);

        LinearLayout manualRow = new LinearLayout(activity);
        manualRow.setOrientation(LinearLayout.HORIZONTAL);
        manualRow.setGravity(Gravity.CENTER_VERTICAL);
        EditText manualPackage = new EditText(activity);
        manualPackage.setHint(tr(prefs, "Добавить пакет вручную", "Add package manually"));
        manualPackage.setSingleLine(true);
        manualPackage.setTextColor(TEXT);
        manualPackage.setHintTextColor(0xFF7F8EA3);
        manualPackage.setTextSize(14);
        manualPackage.setBackground(Ui.stroke(FIELD, 1, STROKE, 16, activity));
        Ui.pad(manualPackage, 12, 10, 12, 10);
        manualRow.addView(manualPackage, new LinearLayout.LayoutParams(0, Ui.dp(activity, 48), 1));
        Button addManual = secondaryButton(activity, prefs, tr(prefs, "Добавить", "Add"), null);
        LinearLayout.LayoutParams addManualLp = new LinearLayout.LayoutParams(Ui.dp(activity, 112), Ui.dp(activity, 48));
        addManualLp.leftMargin = Ui.dp(activity, 10);
        manualRow.addView(addManual, addManualLp);
        LinearLayout.LayoutParams manualRowLp = smallGapLp(activity);
        manualRowLp.bottomMargin = Ui.dp(activity, 8);
        box.addView(manualRow, manualRowLp);

        ScrollView sv = new ScrollView(activity);
        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        sv.addView(list);
        box.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));

        box.addView(primaryButton(activity, prefs, tr(prefs, "Готово", "Done"), v -> dialog.dismiss()), btnLp(activity));

        final Runnable[] render = new Runnable[1];
        render[0] = () -> renderApps(activity, prefs, list, search.getText().toString(), mode);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) { render[0].run(); }
            public void afterTextChanged(Editable s) {}
        });
        popular.setOnClickListener(v -> {
            Set<String> s = selectedSet(prefs, mode);
            if (mode == MODE_TRIGGERS) Collections.addAll(s, Constants.DEFAULT_TRIGGER_PACKAGES);
            else if (players) Collections.addAll(s, Constants.DEFAULT_PLAYER_PACKAGES);
            else {
                s.add("com.smart.driver.antiradar");
                s.add("com.antiradar");
            }
            saveSelection(prefs, mode, s);
            render[0].run();
        });
        clear.setOnClickListener(v -> {
            saveSelection(prefs, mode, new HashSet<String>());
            render[0].run();
        });
        addManual.setOnClickListener(v -> {
            String pkg = manualPackage.getText().toString().trim();
            if (pkg.length() < 3 || !pkg.contains(".")) {
                Toast.makeText(activity, tr(prefs, "Введите packageName, например ru.yandex.music", "Enter packageName, for example ru.yandex.music"), Toast.LENGTH_SHORT).show();
                return;
            }
            Set<String> s = selectedSet(prefs, mode);
            s.add(pkg);
            saveSelection(prefs, mode, s);
            manualPackage.setText("");
            render[0].run();
        });
        render[0].run();
        dialog.show();
        fitDialog(dialog, true);
    }

    private static void renderApps(Activity activity, Prefs prefs, LinearLayout list, String q, int mode) {
        list.removeAllViews();
        String query = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        Set<String> selected = selectedSet(prefs, mode);
        ArrayList<AppInfo> apps = loadApps(activity, prefs, selected);
        int count = 0;
        for (AppInfo a : apps) {
            if (!query.isEmpty()
                    && !a.label.toLowerCase(Locale.ROOT).contains(query)
                    && !a.packageName.toLowerCase(Locale.ROOT).contains(query)) continue;
            CheckBox cb = new CheckBox(activity);
            cb.setText(a.label + "\n" + a.packageName);
            cb.setTextColor(TEXT);
            cb.setTextSize(15);
            cb.setButtonTintList(android.content.res.ColorStateList.valueOf(prefs.accentColor()));
            cb.setBackground(Ui.stroke(CARD_2, 1, 0x223A4658, 16, activity));
            cb.setChecked(selected.contains(a.packageName));
            Ui.pad(cb, 12, 10, 12, 10);
            cb.setOnCheckedChangeListener((v, on) -> {
                Set<String> s = selectedSet(prefs, mode);
                if (on) s.add(a.packageName); else s.remove(a.packageName);
                saveSelection(prefs, mode, s);
            });
            list.addView(cb, smallGapLp(activity));
            count++;
        }
        if (count == 0) list.addView(tv(activity, tr(prefs, "Ничего не найдено", "Nothing found"), 15, MUTED, Typeface.NORMAL, Gravity.CENTER), btnLp(activity));
    }

    private static Set<String> selectedSet(Prefs prefs, int mode) {
        if (mode == MODE_TRIGGERS) return prefs.triggers();
        if (mode == MODE_PLAYERS) return prefs.players();
        return prefs.conflictApps();
    }

    private static void saveSelection(Prefs prefs, int mode, Set<String> values) {
        if (mode == MODE_TRIGGERS) prefs.setTriggers(values);
        else if (mode == MODE_PLAYERS) prefs.setPlayers(values);
        else prefs.setConflictApps(values);
    }

    private static ArrayList<AppInfo> loadApps(Activity activity, Prefs prefs, Set<String> selected) {
        ArrayList<AppInfo> out = new ArrayList<>();
        ArrayList<AppInfo> cached = ensureInstalledAppsCache(activity);
        HashSet<String> seen = new HashSet<>();
        for (AppInfo item : cached) {
            out.add(new AppInfo(item.label, item.packageName, selected.contains(item.packageName)));
            seen.add(item.packageName);
        }
        for (String p : selected) {
            if (p != null && !p.isEmpty() && !seen.contains(p)) {
                out.add(new AppInfo(tr(prefs, "Добавлено вручную", "Added manually"), p, true));
            }
        }
        Collections.sort(out, (a, b) -> {
            if (a.selected != b.selected) return a.selected ? -1 : 1;
            return a.label.compareToIgnoreCase(b.label);
        });
        return out;
    }

    private static ArrayList<AppInfo> ensureInstalledAppsCache(Activity activity) {
        if (installedAppsCache != null) return installedAppsCache;
        installedAppsCache = new ArrayList<>();
        PackageManager pm = activity.getPackageManager();
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

    private static Dialog fullDialog(Activity activity) {
        Dialog d = new Dialog(activity);
        Window w = d.getWindow();
        if (w != null) w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        return d;
    }

    private static LinearLayout dialogRoot(Activity activity) {
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(gradient(activity, BG, BG_2, 0));
        Ui.pad(box, 18, 20, 18, 16);
        return box;
    }

    private static void fitDialog(Dialog d, boolean fullHeight) {
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(-1, fullHeight ? -1 : -2);
        }
    }

    private static TextView tv(Activity activity, String s, int sp, int color, int style, int gravity) {
        TextView t = Ui.tv(activity, s, sp, color, style);
        t.setGravity(gravity);
        return t;
    }

    private static Button primaryButton(Activity activity, Prefs prefs, String text, View.OnClickListener l) {
        Button b = new Button(activity);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setTextColor(Color.WHITE);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setBackground(gradientButton(activity, prefs.accentColor(), lighten(prefs.accentColor(), 30), 18));
        if (l != null) b.setOnClickListener(l);
        return b;
    }

    private static Button secondaryButton(Activity activity, Prefs prefs, String text, View.OnClickListener l) {
        Button b = new Button(activity);
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
        b.setPadding(Ui.dp(activity, 8), 0, Ui.dp(activity, 8), 0);
        b.setBackground(Ui.stroke(CARD_2, 1, STROKE, 16, activity));
        if (l != null) b.setOnClickListener(l);
        return b;
    }

    private static GradientDrawable gradient(Activity activity, int top, int bottom, int radius) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{top, bottom});
        g.setCornerRadius(Ui.dp(activity, radius));
        return g;
    }

    private static GradientDrawable gradientButton(Activity activity, int left, int right, int radius) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{left, right});
        g.setCornerRadius(Ui.dp(activity, radius));
        return g;
    }

    private static int lighten(int color, int amount) {
        int a = Color.alpha(color);
        int r = Math.min(255, Color.red(color) + amount);
        int g = Math.min(255, Color.green(color) + amount);
        int b = Math.min(255, Color.blue(color) + amount);
        return Color.argb(a, r, g, b);
    }

    private static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(-1, -2);
    }

    private static LinearLayout.LayoutParams btnLp(Activity activity) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, Ui.dp(activity, 52));
        lp.setMargins(0, Ui.dp(activity, 12), 0, 0);
        return lp;
    }

    private static LinearLayout.LayoutParams smallGapLp(Activity activity) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, Ui.dp(activity, 9), 0, 0);
        return lp;
    }

    private static LinearLayout.LayoutParams quickButtonLp(Activity activity, float weight) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(activity, 64), weight);
        lp.setMargins(Ui.dp(activity, 4), Ui.dp(activity, 8), Ui.dp(activity, 4), Ui.dp(activity, 10));
        return lp;
    }

    private static void space(Activity activity, LinearLayout parent, int dp) {
        Space s = new Space(activity);
        parent.addView(s, new LinearLayout.LayoutParams(1, Ui.dp(activity, dp)));
    }

    private static String tr(Prefs prefs, String ru, String en) {
        return prefs != null && prefs.englishUi() ? en : ru;
    }
}
