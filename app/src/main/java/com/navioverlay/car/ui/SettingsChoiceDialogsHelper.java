package com.navioverlay.car.ui;

import android.app.Dialog;
import android.graphics.Typeface;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.ScrollView;
import android.widget.TextView;
import com.navioverlay.car.core.OverlayFonts;
import com.navioverlay.car.core.Prefs;
import com.navioverlay.car.core.Ui;
import com.navioverlay.car.overlay.TrackOverlayManager;
import java.util.ArrayList;

final class SettingsChoiceDialogsHelper {
    private SettingsChoiceDialogsHelper() {}

    static void showBorderColorDialog(MainActivity host, boolean controls) {
        Prefs prefs = host.prefs();
        final Dialog dialog = host.fullDialog();
        LinearLayout outer = host.dialogRoot();
        dialog.setContentView(outer);
        outer.addView(host.tv(controls ? host.tr("Цвет рамок кнопок", "Control border color") : host.tr("Цвет рамки окна", "Window border color"), 24, MainActivity.TEXT, Typeface.BOLD, android.view.Gravity.CENTER), host.matchWrap());
        outer.addView(host.tv(controls ? host.tr("Выбери цвет контура кнопок управления. Изменение видно сразу.", "Pick a border color for control buttons. Changes apply immediately.") : host.tr("Выбери цвет контура. Изменение видно сразу на тестовом окне.", "Pick a border color. The preview updates immediately."), 14, MainActivity.MUTED, Typeface.NORMAL, android.view.Gravity.CENTER), host.matchWrap());
        host.space(outer, 12);

        ScrollView sv = new ScrollView(host);
        sv.setFillViewport(false);
        LinearLayout box = new LinearLayout(host);
        box.setOrientation(LinearLayout.VERTICAL);
        Ui.pad(box, 0, 0, 0, 18);
        sv.addView(box, new ScrollView.LayoutParams(-1, -2));
        outer.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));

        box.addView(host.sectionHeader(host.tr("Текущий цвет", "Current color")), host.matchWrap());
        final int initialColor = controls ? prefs.controlsBorderColor() : prefs.borderColor();
        TextView currentSwatch = new TextView(host);
        currentSwatch.setText(" ");
        currentSwatch.setBackground(Ui.stroke(initialColor, 2, 0xAAFFFFFF, 8, host));
        LinearLayout.LayoutParams currentLp = new LinearLayout.LayoutParams(Ui.dp(host, 120), Ui.dp(host, 22));
        currentLp.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        currentLp.bottomMargin = Ui.dp(host, 10);
        box.addView(currentSwatch, currentLp);

        GridLayout grid = new GridLayout(host);
        grid.setColumnCount(4);
        ArrayList<LinearLayout> cells = new ArrayList<>();
        ArrayList<Integer> values = new ArrayList<>();
        for (int col : borderPalette()) {
            LinearLayout cell = new LinearLayout(host);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(android.view.Gravity.CENTER);
            int selectedColor = controls ? prefs.controlsBorderColor() : prefs.borderColor();
            cell.setBackground(Ui.stroke(MainActivity.CARD_2, 2, col == selectedColor ? prefs.accentColor() : MainActivity.STROKE_SOFT, 18, host));
            Ui.pad(cell, 6, 6, 6, 6);
            TextView swatch = new TextView(host);
            swatch.setText(" ");
            swatch.setBackground(Ui.stroke(col, 2, 0xAAFFFFFF, 16, host));
            LinearLayout.LayoutParams swLp = new LinearLayout.LayoutParams(Ui.dp(host, 54), Ui.dp(host, 54));
            cell.addView(swatch, swLp);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = 0;
            lp.height = -2;
            lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            lp.setMargins(Ui.dp(host, 5), Ui.dp(host, 6), Ui.dp(host, 5), Ui.dp(host, 6));
            grid.addView(cell, lp);
            cells.add(cell);
            values.add(col);
            cell.setOnClickListener(v -> {
                if (controls) prefs.setControlsBorderColor(col); else prefs.setBorderColor(col);
                currentSwatch.setBackground(Ui.stroke(col, 2, 0xAAFFFFFF, 8, host));
                int current = controls ? prefs.controlsBorderColor() : prefs.borderColor();
                for (int i = 0; i < cells.size(); i++) {
                    boolean selected = values.get(i) == current;
                    cells.get(i).setBackground(Ui.stroke(MainActivity.CARD_2, 2, selected ? prefs.accentColor() : MainActivity.STROKE_SOFT, 18, host));
                }
                TrackOverlayManager.test(host);
            });
        }
        box.addView(grid, host.matchWrap());
        outer.addView(host.primaryButton(host.tr("Готово", "Done"), v -> dialog.dismiss()), host.btnLp());
        dialog.show();
        host.fitDialog(dialog, true);
    }

    static void showFontDialog(MainActivity host) {
        Prefs prefs = host.prefs();
        final Dialog dialog = host.fullDialog();
        LinearLayout outer = host.dialogRoot();
        dialog.setContentView(outer);
        outer.addView(host.tv(host.tr("Шрифт окна", "Window font"), 24, MainActivity.TEXT, Typeface.BOLD, android.view.Gravity.CENTER), host.matchWrap());
        outer.addView(host.tv(host.tr("Выбери шрифт по образцу. Изменение применяется сразу к overlay.", "Choose a font by sample. Changes apply to the overlay immediately."), 14, MainActivity.MUTED, Typeface.NORMAL, android.view.Gravity.CENTER), host.matchWrap());
        host.space(outer, 12);

        ScrollView sv = new ScrollView(host);
        LinearLayout box = new LinearLayout(host);
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
            LinearLayout row = new LinearLayout(host);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setBackground(Ui.stroke(MainActivity.CARD_2, 1, fontIndex == prefs.textFont() ? prefs.accentColor() : 0x223A4658, 16, host));
            Ui.pad(row, 16, 14, 16, 14);

            TextView name = host.tv(OverlayFonts.nameAt(fontIndex), 16, fontIndex == prefs.textFont() ? prefs.accentColor() : MainActivity.TEXT, Typeface.BOLD, android.view.Gravity.START);
            TextView sample = host.tv(host.tr("Пример / Example — АБВ abc", "Example / Пример — ABC абв"), 18, MainActivity.TEXT, Typeface.NORMAL, android.view.Gravity.START);
            sample.setTypeface(OverlayFonts.resolve(host, fontIndex, prefs.textBold()));
            sample.getPaint().setFakeBoldText(prefs.textBold());
            TextView hint = host.tv(fontIndex == 0 ? host.tr("Системный шрифт Android", "Android system font") : host.tr("Образец для overlay", "Overlay sample"), 12, MainActivity.MUTED_2, Typeface.NORMAL, android.view.Gravity.START);

            row.addView(name, host.matchWrap());
            row.addView(sample, host.matchWrap());
            row.addView(hint, host.matchWrap());
            box.addView(row, host.smallGapLp());
            rows.add(row);
            names.add(name);
            samples.add(sample);
            hints.add(hint);

            row.setOnClickListener(v -> {
                prefs.setTextFont(fontIndex);
                TrackOverlayManager.refresh(host);
                for (int j = 0; j < rows.size(); j++) {
                    boolean selected = j == prefs.textFont();
                    rows.get(j).setBackground(Ui.stroke(MainActivity.CARD_2, 1, selected ? prefs.accentColor() : 0x223A4658, 16, host));
                    names.get(j).setTextColor(selected ? prefs.accentColor() : MainActivity.TEXT);
                    samples.get(j).setTypeface(OverlayFonts.resolve(host, j, prefs.textBold()));
                    samples.get(j).getPaint().setFakeBoldText(prefs.textBold());
                    hints.get(j).setText(selected
                            ? host.tr("Выбранный шрифт overlay", "Selected overlay font")
                            : (j == 0 ? host.tr("Системный шрифт Android", "Android system font") : host.tr("Образец для overlay", "Overlay sample")));
                }
            });
        }

        outer.addView(host.primaryButton(host.tr("Готово", "Done"), v -> dialog.dismiss()), host.btnLp());
        dialog.show();
        host.fitDialog(dialog, true);
    }

    static void showTextAlignmentDialog(MainActivity host) {
        Prefs prefs = host.prefs();
        final Dialog dialog = host.fullDialog();
        LinearLayout outer = host.dialogRoot();
        dialog.setContentView(outer);
        outer.addView(host.tv(host.tr("Выравнивание текста", "Text alignment"), 24, MainActivity.TEXT, Typeface.BOLD, android.view.Gravity.CENTER), host.matchWrap());
        outer.addView(host.tv(host.tr("Выбери, как выравнивать строку трека внутри окна.", "Choose how to align the track line inside the window."), 14, MainActivity.MUTED, Typeface.NORMAL, android.view.Gravity.CENTER), host.matchWrap());
        host.space(outer, 12);

        ScrollView sv = new ScrollView(host);
        LinearLayout box = new LinearLayout(host);
        box.setOrientation(LinearLayout.VERTICAL);
        Ui.pad(box, 0, 0, 0, 18);
        sv.addView(box, new ScrollView.LayoutParams(-1, -2));
        outer.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));

        String[] labels = new String[]{host.tr("Слева", "Left"), host.tr("По центру", "Center")};
        int[] values = new int[]{Prefs.TEXT_ALIGN_LEFT, Prefs.TEXT_ALIGN_CENTER};
        ArrayList<RadioButton> radios = new ArrayList<>();
        int selected = prefs.textAlign();
        for (int i = 0; i < labels.length; i++) {
            final int value = values[i];
            RadioButton rb = new RadioButton(host);
            rb.setText(labels[i]);
            rb.setTextColor(MainActivity.TEXT);
            rb.setTextSize(16);
            rb.setButtonTintList(android.content.res.ColorStateList.valueOf(prefs.accentColor()));
            rb.setChecked(value == selected);
            rb.setBackground(Ui.stroke(MainActivity.CARD_2, 1, 0x223A4658, 18, host));
            Ui.pad(rb, 14, 12, 14, 12);
            rb.setOnClickListener(v -> {
                prefs.setTextAlign(value);
                TrackOverlayManager.refresh(host);
                for (RadioButton radio : radios) radio.setChecked(radio == rb);
            });
            radios.add(rb);
            box.addView(rb, host.smallGapLp());
        }
        box.addView(host.previewButton(host.tr("Проверить окно", "Preview window"), v -> TrackOverlayManager.test(host)), host.btnLp());
        outer.addView(host.secondaryButton(host.tr("Готово", "Done"), v -> dialog.dismiss()), host.btnLp());
        dialog.show();
        host.fitDialog(dialog, true);
    }

    static void showControlButtonsDialog(MainActivity host) {
        final Dialog dialog = host.fullDialog();
        LinearLayout outer = host.dialogRoot();
        dialog.setContentView(outer);
        outer.addView(host.tv(host.tr("Настройка кнопок управления музыкой", "Music control buttons settings"), 24, MainActivity.TEXT, Typeface.BOLD, android.view.Gravity.CENTER), host.matchWrap());
        outer.addView(host.tv(host.tr("Здесь можно настроить размер, форму и расстояние между кнопками управления.", "Set button size, shape and spacing here."), 14, MainActivity.MUTED, Typeface.NORMAL, android.view.Gravity.CENTER), host.matchWrap());
        host.space(outer, 12);

        ScrollView sv = new ScrollView(host);
        LinearLayout box = new LinearLayout(host);
        box.setOrientation(LinearLayout.VERTICAL);
        Ui.pad(box, 0, 0, 0, 18);
        sv.addView(box, new ScrollView.LayoutParams(-1, -2));
        outer.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));

        box.addView(host.menuButton(host.tr("Размер кнопок", "Button size"), host.tr("Маленькие, средние или крупные", "Small, medium or large"), v -> showControlButtonsSizeDialog(host)), host.smallGapLp());
        box.addView(host.menuButton(host.tr("Форма кнопок", "Button shape"), host.tr("Круглые, прямоугольные, квадратные или без рамок", "Round, rectangular, square or borderless"), v -> showControlButtonsShapeDialog(host)), host.smallGapLp());
        box.addView(host.menuButton(host.tr("Расстояние между кнопками", "Button spacing"), host.tr("Компактно, обычно или широко", "Compact, normal or wide"), v -> showControlButtonsSpacingDialog(host)), host.smallGapLp());
        box.addView(host.previewButton(host.tr("Проверить окно", "Preview window"), v -> TrackOverlayManager.test(host)), host.btnLp());
        outer.addView(host.secondaryButton(host.tr("Готово", "Done"), v -> dialog.dismiss()), host.btnLp());
        dialog.show();
        host.fitDialog(dialog, true);
    }

    static void showPauseBehaviorDialog(MainActivity host) {
        Prefs prefs = host.prefs();
        final Dialog dialog = host.fullDialog();
        LinearLayout outer = host.dialogRoot();
        dialog.setContentView(outer);
        outer.addView(host.tv(host.tr("Поведение окна при паузе", "Window behavior on pause"), 24, MainActivity.TEXT, Typeface.BOLD, android.view.Gravity.CENTER), host.matchWrap());
        outer.addView(host.tv(host.tr("Выбери, что делать с окном, если музыка остановилась на паузе.", "Choose what happens to the window when playback is paused."), 14, MainActivity.MUTED, Typeface.NORMAL, android.view.Gravity.CENTER), host.matchWrap());
        host.space(outer, 12);

        ScrollView sv = new ScrollView(host);
        LinearLayout box = new LinearLayout(host);
        box.setOrientation(LinearLayout.VERTICAL);
        Ui.pad(box, 0, 0, 0, 18);
        sv.addView(box, new ScrollView.LayoutParams(-1, -2));
        outer.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));

        String[] labels = new String[]{
                host.tr("Оставлять окно", "Keep the window"),
                host.tr("Скрывать окно", "Hide the window"),
                host.tr("Оставлять ненадолго и потом скрывать", "Keep briefly, then hide")
        };
        int[] values = new int[]{Prefs.PAUSE_BEHAVIOR_KEEP, Prefs.PAUSE_BEHAVIOR_HIDE, Prefs.PAUSE_BEHAVIOR_SHORT};
        ArrayList<RadioButton> radios = new ArrayList<>();
        int selected = prefs.pauseBehavior();
        for (int i = 0; i < labels.length; i++) {
            final int value = values[i];
            RadioButton rb = new RadioButton(host);
            rb.setText(labels[i]);
            rb.setTextColor(MainActivity.TEXT);
            rb.setTextSize(16);
            rb.setButtonTintList(android.content.res.ColorStateList.valueOf(prefs.accentColor()));
            rb.setChecked(value == selected);
            rb.setBackground(Ui.stroke(MainActivity.CARD_2, 1, 0x223A4658, 18, host));
            Ui.pad(rb, 14, 12, 14, 12);
            rb.setOnClickListener(v -> {
                prefs.setPauseBehavior(value);
                for (RadioButton radio : radios) radio.setChecked(radio == rb);
            });
            radios.add(rb);
            box.addView(rb, host.smallGapLp());
        }
        outer.addView(host.secondaryButton(host.tr("Готово", "Done"), v -> dialog.dismiss()), host.btnLp());
        dialog.show();
        host.fitDialog(dialog, true);
    }

    static void showPositionDialog(MainActivity host) {
        Prefs prefs = host.prefs();
        final Dialog dialog = host.fullDialog();
        LinearLayout outer = host.dialogRoot();
        dialog.setContentView(outer);
        outer.addView(host.tv(host.tr("Положение окна", "Window position"), 24, MainActivity.TEXT, Typeface.BOLD, android.view.Gravity.CENTER), host.matchWrap());
        outer.addView(host.tv(host.tr("Выбери стандартное положение окна. Изменение применяется сразу.", "Choose a standard window position. Changes apply immediately."), 14, MainActivity.MUTED, Typeface.NORMAL, android.view.Gravity.CENTER), host.matchWrap());
        host.space(outer, 12);

        ScrollView sv = new ScrollView(host);
        LinearLayout box = new LinearLayout(host);
        box.setOrientation(LinearLayout.VERTICAL);
        Ui.pad(box, 0, 0, 0, 18);
        sv.addView(box, new ScrollView.LayoutParams(-1, -2));
        outer.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));

        String[] labels = new String[]{
                host.tr("Верх слева", "Top left"),
                host.tr("Верх по центру", "Top center"),
                host.tr("Верх справа", "Top right"),
                host.tr("Между верхом и центром слева", "Upper middle left"),
                host.tr("Между верхом и центром по центру", "Upper middle center"),
                host.tr("Между верхом и центром справа", "Upper middle right"),
                host.tr("По центру слева", "Middle left"),
                host.tr("Центр", "Center"),
                host.tr("По центру справа", "Middle right"),
                host.tr("Между центром и низом слева", "Lower middle left"),
                host.tr("Между центром и низом по центру", "Lower middle center"),
                host.tr("Между центром и низом справа", "Lower middle right"),
                host.tr("Низ слева", "Bottom left"),
                host.tr("Низ по центру", "Bottom center"),
                host.tr("Низ справа", "Bottom right")
        };
        int[] positionValues = new int[]{
                0, 1, 2,
                7, 8, 9,
                10, 3, 11,
                12, 13, 14,
                4, 5, 6
        };
        ArrayList<RadioButton> radios = new ArrayList<>();
        int selected = prefs.selectedPosition();

        for (int i = 0; i < labels.length; i++) {
            final int positionIndex = positionValues[i];
            RadioButton rb = new RadioButton(host);
            rb.setText(labels[i]);
            rb.setTextColor(MainActivity.TEXT);
            rb.setTextSize(16);
            rb.setButtonTintList(android.content.res.ColorStateList.valueOf(prefs.accentColor()));
            rb.setChecked(positionIndex == selected);
            rb.setBackground(Ui.stroke(MainActivity.CARD_2, 1, 0x223A4658, 18, host));
            Ui.pad(rb, 14, 12, 14, 12);
            rb.setOnClickListener(v -> {
                prefs.setPosition(positionIndex);
                TrackOverlayManager.refresh(host);
                for (int j = 0; j < radios.size(); j++) radios.get(j).setChecked(positionValues[j] == positionIndex);
            });
            radios.add(rb);
            box.addView(rb, host.smallGapLp());
        }

        box.addView(host.previewButton(host.tr("Проверить окно", "Preview window"), v -> TrackOverlayManager.test(host)), host.btnLp());
        outer.addView(host.secondaryButton(host.tr("Готово", "Done"), v -> dialog.dismiss()), host.btnLp());
        dialog.show();
        host.fitDialog(dialog, true);
    }

    static void showDesignPresetDialog(MainActivity host) {
        Prefs prefs = host.prefs();
        final Dialog dialog = host.fullDialog();
        LinearLayout outer = host.dialogRoot();
        dialog.setContentView(outer);
        outer.addView(host.tv(host.tr("Пресет дизайна", "Design preset"), 24, MainActivity.TEXT, Typeface.BOLD, android.view.Gravity.CENTER), host.matchWrap());
        outer.addView(host.tv(host.tr("Выбери готовый стиль формы и оформления overlay. Изменение применяется сразу.", "Choose a ready-made overlay shape and style. Changes apply immediately."), 14, MainActivity.MUTED, Typeface.NORMAL, android.view.Gravity.CENTER), host.matchWrap());
        host.space(outer, 12);

        ScrollView sv = new ScrollView(host);
        LinearLayout box = new LinearLayout(host);
        box.setOrientation(LinearLayout.VERTICAL);
        Ui.pad(box, 0, 0, 0, 18);
        sv.addView(box, new ScrollView.LayoutParams(-1, -2));
        outer.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));

        String[] names = new String[]{
                host.tr("Classic — как сейчас", "Classic — current look"),
                host.tr("Minimal — тонкая тёмная плашка", "Minimal — slim dark plate"),
                host.tr("Glass — плавные волны", "Glass — soft waves"),
                host.tr("Car UI — авто-стиль", "Car UI — automotive style"),
                host.tr("Soft — мягкая карточка", "Soft — soft card"),
                host.tr("Contrast — контрастный стиль", "Contrast — contrast style"),
                host.tr("Capsule — капсульная форма", "Capsule — capsule shape"),
                host.tr("Premium — плотный тёмный стиль", "Premium — dense dark style"),
                host.tr("Spikes — шипы", "Spikes — spikes"),
                host.tr("Orbit — круглый стиль", "Orbit — round style")
        };
        ArrayList<RadioButton> radios = new ArrayList<>();
        int selected = prefs.designPreset();

        for (int i = 0; i < names.length; i++) {
            final int presetIndex = i;
            RadioButton rb = new RadioButton(host);
            rb.setText(names[i]);
            rb.setTextColor(MainActivity.TEXT);
            rb.setTextSize(16);
            rb.setButtonTintList(android.content.res.ColorStateList.valueOf(prefs.accentColor()));
            rb.setChecked(presetIndex == selected);
            rb.setBackground(Ui.stroke(MainActivity.CARD_2, 1, 0x223A4658, 18, host));
            Ui.pad(rb, 14, 12, 14, 12);
            rb.setOnClickListener(v -> {
                prefs.setDesignPreset(presetIndex);
                TrackOverlayManager.refresh(host);
                for (int j = 0; j < radios.size(); j++) radios.get(j).setChecked(j == presetIndex);
            });
            radios.add(rb);
            box.addView(rb, host.smallGapLp());
        }

        box.addView(host.previewButton(host.tr("Проверить окно", "Preview window"), v -> TrackOverlayManager.test(host)), host.btnLp());
        outer.addView(host.secondaryButton(host.tr("Готово", "Done"), v -> dialog.dismiss()), host.btnLp());
        dialog.show();
        host.fitDialog(dialog, true);
    }

    private static void showControlButtonsSizeDialog(MainActivity host) {
        Prefs prefs = host.prefs();
        showControlButtonsChoiceDialog(
                host,
                host.tr("Размер кнопок", "Button size"),
                host.tr("Выбери размер кнопок управления музыкой.", "Choose the size of media control buttons."),
                new String[]{host.tr("Маленькие", "Small"), host.tr("Средние", "Medium"), host.tr("Крупные", "Large")},
                new int[]{Prefs.CONTROLS_SIZE_SMALL, Prefs.CONTROLS_SIZE_MEDIUM, Prefs.CONTROLS_SIZE_LARGE},
                prefs.controlsSize(),
                prefs::setControlsSize);
    }

    private static void showControlButtonsShapeDialog(MainActivity host) {
        Prefs prefs = host.prefs();
        showControlButtonsChoiceDialog(
                host,
                host.tr("Форма кнопок", "Button shape"),
                host.tr("Выбери форму и рамку кнопок управления.", "Choose the shape and border style of control buttons."),
                new String[]{host.tr("Круглые", "Round"), host.tr("Прямоугольные", "Rectangular"), host.tr("Квадратные", "Square"), host.tr("Без рамок", "Borderless")},
                new int[]{Prefs.CONTROLS_SHAPE_ROUND, Prefs.CONTROLS_SHAPE_RECT, Prefs.CONTROLS_SHAPE_SQUARE, Prefs.CONTROLS_SHAPE_BORDERLESS},
                prefs.controlsShape(),
                prefs::setControlsShape);
    }

    private static void showControlButtonsSpacingDialog(MainActivity host) {
        Prefs prefs = host.prefs();
        showControlButtonsChoiceDialog(
                host,
                host.tr("Расстояние между кнопками", "Button spacing"),
                host.tr("Выбери, насколько кнопки должны быть разнесены друг от друга.", "Choose how far apart the buttons should be."),
                new String[]{host.tr("Компактно", "Compact"), host.tr("Обычно", "Normal"), host.tr("Шире", "Wide")},
                new int[]{Prefs.CONTROLS_SPACING_COMPACT, Prefs.CONTROLS_SPACING_NORMAL, Prefs.CONTROLS_SPACING_WIDE},
                prefs.controlsSpacing(),
                prefs::setControlsSpacing);
    }

    private interface IntSetter { void set(int value); }

    private static void showControlButtonsChoiceDialog(MainActivity host, String title, String subtitle, String[] labels, int[] values, int selected, IntSetter setter) {
        Prefs prefs = host.prefs();
        final Dialog dialog = host.fullDialog();
        LinearLayout outer = host.dialogRoot();
        dialog.setContentView(outer);
        outer.addView(host.tv(title, 24, MainActivity.TEXT, Typeface.BOLD, android.view.Gravity.CENTER), host.matchWrap());
        outer.addView(host.tv(subtitle, 14, MainActivity.MUTED, Typeface.NORMAL, android.view.Gravity.CENTER), host.matchWrap());
        host.space(outer, 12);

        ScrollView sv = new ScrollView(host);
        LinearLayout box = new LinearLayout(host);
        box.setOrientation(LinearLayout.VERTICAL);
        Ui.pad(box, 0, 0, 0, 18);
        sv.addView(box, new ScrollView.LayoutParams(-1, -2));
        outer.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));

        ArrayList<RadioButton> radios = new ArrayList<>();
        for (int i = 0; i < labels.length; i++) {
            final int value = values[i];
            RadioButton rb = new RadioButton(host);
            rb.setText(labels[i]);
            rb.setTextColor(MainActivity.TEXT);
            rb.setTextSize(16);
            rb.setButtonTintList(android.content.res.ColorStateList.valueOf(prefs.accentColor()));
            rb.setChecked(value == selected);
            rb.setBackground(Ui.stroke(MainActivity.CARD_2, 1, 0x223A4658, 18, host));
            Ui.pad(rb, 14, 12, 14, 12);
            rb.setOnClickListener(v -> {
                setter.set(value);
                TrackOverlayManager.refresh(host);
                for (RadioButton radio : radios) radio.setChecked(radio == rb);
            });
            radios.add(rb);
            box.addView(rb, host.smallGapLp());
        }
        box.addView(host.previewButton(host.tr("Проверить окно", "Preview window"), v -> TrackOverlayManager.test(host)), host.btnLp());
        outer.addView(host.secondaryButton(host.tr("Готово", "Done"), v -> dialog.dismiss()), host.btnLp());
        dialog.show();
        host.fitDialog(dialog, true);
    }

    private static int[] borderPalette() {
        return new int[]{
                0x00FFFFFF, 0x33FFFFFF, 0x66FFFFFF, 0xAAFFFFFF,
                0xFF00D5FF, 0xFF38BDF8, 0xFF60A5FA, 0xFF818CF8,
                0xFF22C55E, 0xFF84CC16, 0xFFFFD166, 0xFFFFA726,
                0xFFFF6B6B, 0xFFFF4D9D, 0xFFC084FC, 0xFF2DD4BF,
                0xFFFFFFFF, 0xFFBAE6FD, 0xFF2563EB, 0xFFA3E635,
                0xFFFBBF24, 0xFFFF8A65, 0xFFE9D5FF, 0xFF14B8A6
        };
    }
}
