package com.tiqu.extractor;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.UiModeManager;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 主界面。手机触屏与电视遥控器共用：
 * · 上下键移动焦点，OK 勾选，菜单键全选，返回键清空/退出
 * · 焦点描边放大，电视远距离可读
 */
public class MainActivity extends Activity {

    private static final int REQ_STORAGE = 1001;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private AppListAdapter adapter;
    private List<AppEntry> allApps = new ArrayList<>();

    private TextView itemCountView;
    private TextView selectedCountView;
    private TextView savePathView;
    private TextView emptyView;
    private TextView remoteHint;
    private EditText searchBox;
    private ListView listView;
    private Button btnExtract;
    private Button btnSelectAll;
    private Button btnClear;
    private View loadingView;
    private TextView loadingText;

    private AlertDialog progressDialog;
    private boolean isTv;
    private boolean firstResume = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        isTv = detectTv();

        itemCountView = findViewById(R.id.itemCount);
        selectedCountView = findViewById(R.id.selectedCount);
        savePathView = findViewById(R.id.savePathView);
        emptyView = findViewById(R.id.emptyView);
        remoteHint = findViewById(R.id.remoteHint);
        searchBox = findViewById(R.id.searchBox);
        listView = findViewById(R.id.appList);
        btnExtract = findViewById(R.id.btnExtract);
        btnSelectAll = findViewById(R.id.btnSelectAll);
        btnClear = findViewById(R.id.btnClear);
        loadingView = findViewById(R.id.loadingView);
        loadingText = findViewById(R.id.loadingText);

        if (isTv) {
            remoteHint.setVisibility(View.VISIBLE);
            // 电视上搜索仍可用，但不抢初始焦点，避免弹出输入法
            searchBox.setFocusable(true);
            searchBox.setFocusableInTouchMode(true);
        }

        adapter = new AppListAdapter(this);
        adapter.setListener(count -> {
            selectedCountView.setText(getString(R.string.selected_count, count));
            btnExtract.setEnabled(count > 0);
        });
        listView.setAdapter(adapter);
        listView.setEmptyView(emptyView);
        listView.setItemsCanFocus(true);
        listView.setFocusable(true);
        listView.setFocusableInTouchMode(true);
        // 点击/OK 勾选在 AppListAdapter 的 item OnClickListener 里
        // 不要再 setOnItemClickListener：item 可点时会和 item 点击叠加成「切两次」

        savePathView.setText(getString(R.string.save_path, ApkExtractor.describeSaveDir(this)));

        searchBox.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
                applyFilter(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        btnSelectAll.setOnClickListener(v -> adapter.selectAll());
        btnClear.setOnClickListener(v -> adapter.clearSelection());
        btnExtract.setOnClickListener(v -> startExtract());
        btnExtract.setEnabled(false);

        ensureStorageThenLoad();
    }

    private boolean detectTv() {
        UiModeManager ui = (UiModeManager) getSystemService(UI_MODE_SERVICE);
        if (ui != null && ui.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION) {
            return true;
        }
        PackageManager pm = getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
                || pm.hasSystemFeature("android.hardware.television");
    }

    private void ensureStorageThenLoad() {
        if (Build.VERSION.SDK_INT < 29) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        REQ_STORAGE);
                return;
            }
        }
        loadAppsAsync();
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // 权限弹窗点完「允许/拒绝」回来：立刻重新加载应用列表
        loadAppsAsync();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 从系统设置、安装器、其他应用返回时刷新列表
        // 首次进入由 onCreate 已经加载过，跳过避免重复请求
        if (firstResume) {
            firstResume = false;
            return;
        }
        loadAppsAsync();
    }

    private void loadAppsAsync() {
        showLoading(true, getString(R.string.loading_apps));
        // 刷新时保留已勾选的包名，避免切一下设置就丢勾选
        final java.util.HashSet<String> keepSelected = new java.util.HashSet<>();
        for (AppEntry e : allApps) {
            if (e.selected) keepSelected.add(e.packageName);
        }
        io.execute(() -> {
            List<AppEntry> apps = ApkExtractor.loadInstalled(this);
            Collections.sort(apps, new Comparator<AppEntry>() {
                @Override
                public int compare(AppEntry a, AppEntry b) {
                    return a.label.compareToIgnoreCase(b.label);
                }
            });
            for (AppEntry e : apps) {
                e.selected = keepSelected.contains(e.packageName);
            }
            mainHandler.post(() -> {
                showLoading(false, null);
                allApps = apps;
                applyFilter(searchBox.getText().toString());
                focusFirstApp();
            });
        });
    }

    private void showLoading(boolean show, String message) {
        if (loadingView == null) return;
        loadingView.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show && message != null && loadingText != null) {
            loadingText.setText(message);
        }
        // 加载中禁用操作，避免空点
        if (btnExtract != null) btnExtract.setEnabled(!show && adapter != null && adapter.getSelectedCount() > 0);
        if (btnSelectAll != null) btnSelectAll.setEnabled(!show);
        if (btnClear != null) btnClear.setEnabled(!show);
        if (searchBox != null) searchBox.setEnabled(!show);
    }

    /** 电视启动后直接把焦点放到第一个应用，遥控立刻可上下移动。 */
    private void focusFirstApp() {
        if (listView.getCount() > 0) {
            listView.post(() -> {
                listView.setSelection(0);
                listView.requestFocus();
                View first = listView.getChildAt(0);
                if (first != null) first.requestFocus();
            });
        } else {
            btnExtract.requestFocus();
        }
    }

    private void applyFilter(String query) {
        String q = query == null ? "" : query.trim().toLowerCase();
        List<AppEntry> shown = new ArrayList<>();
        for (AppEntry e : allApps) {
            if (q.isEmpty()
                    || e.label.toLowerCase().contains(q)
                    || e.packageName.toLowerCase().contains(q)) {
                shown.add(e);
            }
        }
        adapter.replaceAll(shown);
        itemCountView.setText(getString(R.string.item_count, allApps.size()));
        if (allApps.isEmpty()) {
            emptyView.setText(R.string.no_apps);
        } else if (shown.isEmpty()) {
            emptyView.setText(R.string.no_match);
        }
    }

    private void startExtract() {
        List<AppEntry> selected = adapter.getSelected();
        if (selected.isEmpty()) return;

        showProgressDialog(selected.size());

        io.execute(() -> {
            int ok = 0;
            String lastError = null;
            File destDir = null;
            if (Build.VERSION.SDK_INT < 29) {
                destDir = ApkExtractor.publicDownloadDir();
            }

            for (AppEntry entry : selected) {
                try {
                    ok += ApkExtractor.extract(this, entry.packageName, destDir);
                } catch (Exception e) {
                    lastError = entry.label + ": " + e.getMessage();
                    ApkExtractor.log("extract fail " + entry.packageName + " " + e);
                }
            }

            final int done = ok;
            final String err = lastError;
            mainHandler.post(() -> {
                dismissProgressDialog();
                if (err != null && done == 0) {
                    Toast.makeText(this,
                            getString(R.string.extract_fail, err),
                            Toast.LENGTH_LONG).show();
                } else if (err != null) {
                    Toast.makeText(this,
                            getString(R.string.extract_done, done) + "\n" + err,
                            Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this,
                            getString(R.string.extract_done, done),
                            Toast.LENGTH_LONG).show();
                }
                // 提取完把焦点交回列表/提取按钮，方便连续操作
                focusFirstApp();
            });
        });
    }

    /**
     * 遥控器按键：
     * MENU / 颜色键 → 全选
     * BACK → 有勾选先清勾选，搜索有字先清搜索，再退出
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_TV_CONTENTS_MENU:
                adapter.selectAll();
                return true;
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_CLEAR:
                if (adapter.getSelectedCount() > 0) {
                    adapter.clearSelection();
                    Toast.makeText(this, "已清空勾选", Toast.LENGTH_SHORT).show();
                    return true;
                }
                if (searchBox.getText().length() > 0) {
                    searchBox.setText("");
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                // 列表在焦点时，左键跳到「提取选中」，减少横移到按钮的步数
                if (listView.hasFocus() && adapter.getSelectedCount() > 0) {
                    btnExtract.requestFocus();
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (listView.hasFocus() && adapter.getSelectedCount() > 0) {
                    btnExtract.requestFocus();
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_F4:
            case KeyEvent.KEYCODE_PROG_GREEN:
                if (adapter.getSelectedCount() > 0) {
                    startExtract();
                    return true;
                }
                break;
            default:
                break;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void showProgressDialog(int total) {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
        View content = getLayoutInflater().inflate(android.R.layout.simple_list_item_1, null);
        TextView text = content.findViewById(android.R.id.text1);
        text.setText(getString(R.string.extracting) + " (0/" + total + ")");
        text.setPadding(48, 40, 48, 24);
        progressDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.extract_selected)
                .setView(content)
                .setCancelable(false)
                .create();
        progressDialog.show();
    }

    private void dismissProgressDialog() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
        progressDialog = null;
    }

    @Override
    protected void onDestroy() {
        dismissProgressDialog();
        io.shutdownNow();
        super.onDestroy();
    }
}
