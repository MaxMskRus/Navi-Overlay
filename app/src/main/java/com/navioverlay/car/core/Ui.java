package com.navioverlay.car.core;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.TextView;

public final class Ui {
    private Ui() {}

    public static int dp(Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    public static void pad(View view, int left, int top, int right, int bottom) {
        Context context = view.getContext();
        view.setPadding(dp(context, left), dp(context, top), dp(context, right), dp(context, bottom));
    }

    public static GradientDrawable round(int color, int radius, Context context) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, radius));
        return drawable;
    }

    public static GradientDrawable stroke(int color, int stroke, int strokeColor, int radius, Context context) {
        GradientDrawable drawable = round(color, radius, context);
        drawable.setStroke(dp(context, stroke), strokeColor);
        return drawable;
    }

    public static TextView tv(Context context, String text, int sp, int color, int style) {
        TextView textView = new TextView(context);
        textView.setText(text);
        textView.setTextSize(sp);
        textView.setTextColor(color);
        textView.setTypeface(Typeface.DEFAULT, style);
        return textView;
    }

    public static String hex(int color) {
        return String.format("#%08X", color);
    }

    public static int parse(String value, int defaultColor) {
        try {
            return Color.parseColor(value.trim());
        } catch (IllegalArgumentException | NullPointerException ignored) {
            return defaultColor;
        }
    }
}
