package com.tiqu.extractor;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * 从本机已安装应用的 publicSourceDir / splitSourceDirs 复制出 APK 文件。
 * 手机与电视共用同一套逻辑。
 */
public final class ApkExtractor {

    private static final String TAG = "ApkExtractor";
    public static final String FOLDER_NAME = "APK提取";

    private ApkExtractor() {
    }

    /** 扫描已安装应用（跳过自身，按显示名排序由调用方处理）。 */
    public static List<AppEntry> loadInstalled(Context context) {
        PackageManager pm = context.getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(0);
        List<AppEntry> result = new ArrayList<>(apps.size());
        String self = context.getPackageName();

        for (ApplicationInfo info : apps) {
            if (self.equals(info.packageName)) continue;
            // 跳过没有 sourceDir 的异常条目
            if (info.publicSourceDir == null && info.sourceDir == null) continue;

            CharSequence labelCs = pm.getApplicationLabel(info);
            String label = labelCs != null ? labelCs.toString() : info.packageName;

            String version = "";
            try {
                PackageInfo pi = pm.getPackageInfo(info.packageName, 0);
                version = pi.versionName != null ? pi.versionName : "";
            } catch (PackageManager.NameNotFoundException ignored) {
            }

            long size = apkSize(info);
            boolean hasSplits = info.splitSourceDirs != null && info.splitSourceDirs.length > 0;

            result.add(new AppEntry(
                    info.packageName,
                    label,
                    version,
                    size,
                    pm.getApplicationIcon(info),
                    hasSplits));
        }
        return result;
    }

    private static long apkSize(ApplicationInfo info) {
        long total = 0;
        String main = info.publicSourceDir != null ? info.publicSourceDir : info.sourceDir;
        if (main != null) total += new File(main).length();
        if (info.splitSourceDirs != null) {
            for (String s : info.splitSourceDirs) {
                total += new File(s).length();
            }
        }
        return total;
    }

    /**
     * 提取单个应用。返回写出的文件数。
     *
     * @param destDir 目标目录；若走 MediaStore 可为 null（内部决定路径）
     */
    public static int extract(Context context, String packageName, File destDir)
            throws IOException {
        PackageManager pm = context.getPackageManager();
        ApplicationInfo info;
        try {
            info = pm.getApplicationInfo(packageName, 0);
        } catch (PackageManager.NameNotFoundException e) {
            throw new IOException("应用不存在: " + packageName, e);
        }

        String version = "";
        try {
            PackageInfo pi = pm.getPackageInfo(packageName, 0);
            version = pi.versionName != null ? sanitize(pi.versionName) : "";
        } catch (PackageManager.NameNotFoundException ignored) {
        }

        String baseName = packageName + (version.isEmpty() ? "" : "-v" + version);
        List<String> sources = new ArrayList<>();
        String main = info.publicSourceDir != null ? info.publicSourceDir : info.sourceDir;
        if (main != null) sources.add(main);
        if (info.splitSourceDirs != null) {
            sources.addAll(Arrays.asList(info.splitSourceDirs));
        }
        if (sources.isEmpty()) {
            throw new IOException("找不到 APK 路径: " + packageName);
        }

        // 多个 split 时用 zip 打包更方便；单个直接存 .apk
        if (sources.size() == 1) {
            String fileName = baseName + ".apk";
            copyOne(context, sources.get(0), fileName, destDir);
            return 1;
        }

        // 多个文件：依次写出 base + split 命名
        int written = 0;
        for (int i = 0; i < sources.size(); i++) {
            String suffix = (i == 0) ? "-base.apk" : "-split" + i + ".apk";
            copyOne(context, sources.get(i), baseName + suffix, destDir);
            written++;
        }
        return written;
    }

    private static void copyOne(Context context, String srcPath, String fileName, File destDir)
            throws IOException {
        File src = new File(srcPath);
        if (!src.isFile()) {
            throw new IOException("源文件不存在: " + srcPath);
        }

        if (Build.VERSION.SDK_INT >= 29 && destDir == null) {
            writeToMediaStore(context, src, fileName);
            return;
        }

        File dir = destDir != null ? destDir : fallbackDir(context);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("无法创建目录: " + dir.getAbsolutePath());
        }
        File out = new File(dir, fileName);
        try (InputStream in = new FileInputStream(src);
             OutputStream os = new FileOutputStream(out)) {
            copyStream(in, os);
        }
    }

    /** API 29+：写入公共下载目录 Downloads/APK提取，无需存储权限。 */
    private static void writeToMediaStore(Context context, File src, String fileName)
            throws IOException {
        ContentResolver resolver = context.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
        values.put(MediaStore.Downloads.MIME_TYPE, "application/vnd.android.package-archive");
        values.put(MediaStore.Downloads.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS + "/" + FOLDER_NAME);
        values.put(MediaStore.Downloads.IS_PENDING, 1);

        Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) {
            // 某些电视系统 MediaStore 受限，退回应用私有目录
            File dir = fallbackDir(context);
            if (!dir.exists() && !dir.mkdirs()) {
                throw new IOException("无法创建目录: " + dir.getAbsolutePath());
            }
            File out = new File(dir, fileName);
            try (InputStream in = new FileInputStream(src);
                 OutputStream os = new FileOutputStream(out)) {
                copyStream(in, os);
            }
            return;
        }

        try (InputStream in = new FileInputStream(src);
             OutputStream os = resolver.openOutputStream(uri)) {
            if (os == null) {
                throw new IOException("无法写入: " + uri);
            }
            copyStream(in, os);
        } catch (IOException e) {
            resolver.delete(uri, null, null);
            throw e;
        }

        values.clear();
        values.put(MediaStore.Downloads.IS_PENDING, 0);
        resolver.update(uri, values, null, null);
    }

    /** 兜底目录：应用外部私有目录，任何机型都可写。 */
    public static File fallbackDir(Context context) {
        File ext = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (ext == null) {
            ext = context.getFilesDir();
        }
        return new File(ext, FOLDER_NAME);
    }

    /**
     * 解析实际保存位置说明，用于界面展示。
     * API 29+ 优先显示公共下载目录。
     */
    public static String describeSaveDir(Context context) {
        if (Build.VERSION.SDK_INT >= 29) {
            File pub = new File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    FOLDER_NAME);
            return pub.getAbsolutePath();
        }
        File legacy = new File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                FOLDER_NAME);
        return legacy.getAbsolutePath();
    }

    /** 公共下载目录（API 29 以下直接写文件）。 */
    public static File publicDownloadDir() {
        return new File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                FOLDER_NAME);
    }

    private static void copyStream(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        out.flush();
    }

    private static String sanitize(String s) {
        return s.replaceAll("[\\\\/:*?\"<>|\\s]", "_");
    }

    public static void log(String msg) {
        Log.i(TAG, msg);
    }

    public static String localeSize(long bytes) {
        return String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
