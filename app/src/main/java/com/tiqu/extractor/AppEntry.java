package com.tiqu.extractor;

import android.graphics.drawable.Drawable;

/** 列表中的一行：已安装应用。 */
public class AppEntry {
    public final String packageName;
    public final String label;
    public final String versionName;
    public final long sizeBytes;
    public final Drawable icon;
    public final boolean hasSplits;

    public boolean selected;

    public AppEntry(
            String packageName,
            String label,
            String versionName,
            long sizeBytes,
            Drawable icon,
            boolean hasSplits) {
        this.packageName = packageName;
        this.label = label;
        this.versionName = versionName == null ? "" : versionName;
        this.sizeBytes = sizeBytes;
        this.icon = icon;
        this.hasSplits = hasSplits;
        this.selected = false;
    }

    public static String formatSize(long bytes) {
        if (bytes <= 0) return "—";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format("%.0f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format("%.1f MB", mb);
        double gb = mb / 1024.0;
        return String.format("%.2f GB", gb);
    }
}
