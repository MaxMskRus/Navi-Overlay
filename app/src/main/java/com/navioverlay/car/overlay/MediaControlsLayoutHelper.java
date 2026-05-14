package com.navioverlay.car.overlay;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.navioverlay.car.core.Prefs;
import com.navioverlay.car.core.Ui;

final class MediaControlsLayoutHelper {
    private MediaControlsLayoutHelper() {}

    static void applyControlsLayout(Context c, Prefs p, LinearLayout controlsRow) {
        if (controlsRow == null) return;
        int textSize = Math.max(1, p.textSize());
        float sizeScale = 1f;
        if (p.controlsSize() == Prefs.CONTROLS_SIZE_SMALL) sizeScale = 0.88f;
        else if (p.controlsSize() == Prefs.CONTROLS_SIZE_LARGE) sizeScale = 1.16f;
        int buttonBaseDp = Math.max(34, Math.min(76, Math.round(textSize * 1.66f * sizeScale)));
        int buttonWidthDp = controlButtonWidthDp(p, buttonBaseDp);
        int buttonHeightDp = controlButtonHeightDp(buttonBaseDp);
        int iconSp = Math.max(13, Math.min(30, Math.round(textSize * 0.78f * sizeScale)));
        float spacingScale = p.controlsSpacing() == Prefs.CONTROLS_SPACING_COMPACT ? 0.78f
                : (p.controlsSpacing() == Prefs.CONTROLS_SPACING_WIDE ? 1.38f : 1f);
        int sideMargin = Math.max(4, Math.min(18, Math.round(textSize * 0.34f * spacingScale)));
        int verticalGap = Math.max(8, Math.min(18, Math.round(textSize * 0.45f)));
        int buttonWidthPx = Ui.dp(c, buttonWidthDp);
        int buttonHeightPx = Ui.dp(c, buttonHeightDp);

        controlsRow.setGravity(Gravity.CENTER);
        controlsRow.setPadding(0, Ui.dp(c, verticalGap), 0, Ui.dp(c, verticalGap));
        for (int i = 0; i < controlsRow.getChildCount(); i++) {
            TextView b = (TextView) controlsRow.getChildAt(i);
            b.setTextSize(iconSp);
            b.setIncludeFontPadding(false);
            b.setGravity(Gravity.CENTER);
            b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(buttonWidthPx, buttonHeightPx);
            lp.setMargins(Ui.dp(c, sideMargin), 0, Ui.dp(c, sideMargin), 0);
            b.setLayoutParams(lp);
        }
        applyControlButtonVisuals(c, p, controlsRow, buttonWidthDp, buttonHeightDp, p.controlsBorderColor());
    }

    private static int controlButtonHeightDp(int buttonBaseDp) {
        return buttonBaseDp;
    }

    private static int controlButtonWidthDp(Prefs p, int buttonBaseDp) {
        if (p.controlsShape() == Prefs.CONTROLS_SHAPE_RECT) {
            return Math.round(buttonBaseDp * 1.38f);
        }
        return buttonBaseDp;
    }

    private static void applyControlButtonVisuals(Context c, Prefs p, LinearLayout controlsRow, int buttonWidthDp, int buttonHeightDp, int accent) {
        if (controlsRow == null) return;
        int design = p.designPreset();
        int fill = 0x331E293B;
        int stroke = accent;
        int width = 1;
        int radius = Math.min(buttonWidthDp, buttonHeightDp) / 2;
        int text = 0xFFFFFFFF;
        float shadow = 0f;
        int shadowColor = 0;
        if (design == 1) {
            fill = 0x14070A10;
            stroke = 0x33000000;
            width = 0;
            radius = Math.max(Ui.dp(c, 10), Math.min(buttonWidthDp, buttonHeightDp) / 3);
            text = 0xFFE2E8F0;
        } else if (design == 2) {
            fill = 0x33E7F6FF;
            stroke = 0xAAE7F6FF;
            width = 2;
            radius = Math.max(Ui.dp(c, 16), Math.min(buttonWidthDp, buttonHeightDp) / 2);
            text = 0xFFFFFFFF;
            shadow = 8f;
            shadowColor = 0x66E7F6FF;
        } else if (design == 3) {
            fill = 0x2600D5FF;
            stroke = 0xFF00D5FF;
            width = 2;
            radius = Math.max(Ui.dp(c, 14), Math.min(buttonWidthDp, buttonHeightDp) / 2);
            shadow = 7f;
            shadowColor = 0x7700D5FF;
        } else if (design == 4) {
            fill = 0x33E8EEF8;
            stroke = 0x88D8E5F7;
            width = 1;
            radius = Math.max(Ui.dp(c, 18), Math.min(buttonWidthDp, buttonHeightDp) / 2);
            text = 0xFFF8FBFF;
        } else if (design == 5) {
            fill = 0xFF040607;
            stroke = 0xFFF8FAFC;
            width = 2;
            radius = Math.max(Ui.dp(c, 12), Math.min(buttonWidthDp, buttonHeightDp) / 3);
            shadow = 6f;
            shadowColor = 0xCC000000;
        } else if (design == 6) {
            fill = 0x335E7EC0;
            stroke = 0xFF95B8FF;
            width = 2;
            radius = Math.min(buttonWidthDp, buttonHeightDp) / 2;
            shadow = 7f;
            shadowColor = 0x6695B8FF;
        } else if (design == 7) {
            fill = 0x33D9C27A;
            stroke = 0xFFD9C27A;
            width = 2;
            radius = Math.max(Ui.dp(c, 14), Math.min(buttonWidthDp, buttonHeightDp) / 2);
            text = 0xFFFFF6DD;
            shadow = 7f;
            shadowColor = 0x77D9C27A;
        } else if (design == 8) {
            fill = 0x33FF6B6B;
            stroke = 0xFFFF6B6B;
            width = 2;
            radius = Math.max(Ui.dp(c, 10), Math.min(buttonWidthDp, buttonHeightDp) / 3);
            text = 0xFFFFF2F2;
            shadow = 7f;
            shadowColor = 0x66FF6B6B;
        } else if (design == 9) {
            fill = 0x338BE9FD;
            stroke = 0xFF8BE9FD;
            width = 2;
            radius = Math.min(buttonWidthDp, buttonHeightDp) / 2;
            text = 0xFFF4FBFF;
            shadow = 8f;
            shadowColor = 0x668BE9FD;
        }
        int shape = p.controlsShape();
        if (shape == Prefs.CONTROLS_SHAPE_RECT) {
            radius = Math.max(Ui.dp(c, 10), Math.round(buttonHeightDp * 0.22f));
        } else if (shape == Prefs.CONTROLS_SHAPE_SQUARE) {
            radius = Math.max(0, Ui.dp(c, 4));
        } else if (shape == Prefs.CONTROLS_SHAPE_BORDERLESS) {
            fill = 0x00000000;
            stroke = 0x00000000;
            width = 0;
            radius = 0;
            shadow = 0f;
            shadowColor = 0;
        }
        for (int i = 0; i < controlsRow.getChildCount(); i++) {
            TextView b = (TextView) controlsRow.getChildAt(i);
            b.setTextColor(text);
            b.setBackground(Ui.stroke(fill, width, stroke, radius, c));
            b.setShadowLayer(shadow, 0f, 0f, shadowColor);
        }
    }
}
