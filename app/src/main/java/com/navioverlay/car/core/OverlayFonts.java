package com.navioverlay.car.core;

import android.content.Context;
import android.os.Build;
import android.graphics.Typeface;
import com.navioverlay.car.R;

public final class OverlayFonts {
    private OverlayFonts() {}

    private static final String[] NAMES = {
            "System Default",
            "Brushboy",
            "Celtes SP",
            "Fractal",
            "Magnolia Script",
            "Marvin",
            "Oldtimer",
            "Rostov",
            "Serati",
            "Unsightly",
            "Comfortaa",
            "Rounded Sans Serif 7",
            "Inter",
            "Manrope",
            "Rubik",
            "Ubuntu",
            "Exo 2"
    };

    private static final int[] RES_IDS = {
            0,
            R.font.brushboy_regular,
            R.font.celtes_sp_regular,
            R.font.fractal_regular,
            R.font.magnolia_regular,
            R.font.marvin_regular,
            R.font.oldtimer_regular,
            R.font.rostov_regular,
            R.font.serati_regular,
            R.font.unsightly_regular,
            R.font.comfortaa_regular,
            R.font.rounded_sans_serif_7_regular,
            R.font.inter_regular,
            R.font.manrope_regular,
            R.font.rubik_regular,
            R.font.ubuntu_regular,
            R.font.exo_2_regular
    };

    public static int count() {
        return NAMES.length;
    }

    public static String nameAt(int index) {
        return NAMES[safeIndex(index)];
    }

    public static Typeface resolve(Context context, int index, boolean bold) {
        int safe = safeIndex(index);
        Typeface base;
        if (RES_IDS[safe] == 0) {
            base = Typeface.DEFAULT;
        } else {
            try {
                base = Build.VERSION.SDK_INT >= 26
                        ? context.getResources().getFont(RES_IDS[safe])
                        : Typeface.DEFAULT;
            } catch (Throwable ignored) {
                base = Typeface.DEFAULT;
            }
        }
        if (base == null) base = Typeface.DEFAULT;
        return Typeface.create(base, bold ? Typeface.BOLD : Typeface.NORMAL);
    }

    private static int safeIndex(int index) {
        if (index < 0 || index >= NAMES.length) return 0;
        return index;
    }
}
