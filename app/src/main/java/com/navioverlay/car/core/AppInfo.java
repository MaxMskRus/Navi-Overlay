package com.navioverlay.car.core;

public class AppInfo implements Comparable<AppInfo> {
    public final String label;
    public final String packageName;
    public boolean selected;

    public AppInfo(String label, String packageName, boolean selected) {
        this.label = label == null ? packageName : label;
        this.packageName = packageName;
        this.selected = selected;
    }

    @Override public int compareTo(AppInfo o) {
        return this.label.compareToIgnoreCase(o.label);
    }
}
