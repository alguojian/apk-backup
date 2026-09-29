package com.tiqu.extractor;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * 列表适配器。
 * 勾选切换必须挂在 item 根 View 的 OnClickListener 上：
 * item 是 clickable 时 ListView.OnItemClickListener 不会触发，
 * 只监听 ListView 会出现「点了没反应」。
 */
public class AppListAdapter extends BaseAdapter {

    public interface Listener {
        void onSelectionChanged(int selectedCount);
    }

    private final LayoutInflater inflater;
    private final List<AppEntry> items = new ArrayList<>();
    private Listener listener;

    public AppListAdapter(Context context) {
        this.inflater = LayoutInflater.from(context);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void replaceAll(List<AppEntry> next) {
        items.clear();
        if (next != null) items.addAll(next);
        notifyDataSetChanged();
        notifySelection();
    }

    public void toggle(int position) {
        if (position < 0 || position >= items.size()) return;
        AppEntry e = items.get(position);
        e.selected = !e.selected;
        notifyDataSetChanged();
        notifySelection();
    }

    public List<AppEntry> getSelected() {
        List<AppEntry> out = new ArrayList<>();
        for (AppEntry e : items) {
            if (e.selected) out.add(e);
        }
        return out;
    }

    public int getSelectedCount() {
        int n = 0;
        for (AppEntry e : items) {
            if (e.selected) n++;
        }
        return n;
    }

    public void selectAll() {
        for (AppEntry e : items) e.selected = true;
        notifyDataSetChanged();
        notifySelection();
    }

    public void clearSelection() {
        for (AppEntry e : items) e.selected = false;
        notifyDataSetChanged();
        notifySelection();
    }

    private void notifySelection() {
        if (listener != null) listener.onSelectionChanged(getSelectedCount());
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public AppEntry getItem(int position) {
        return items.get(position);
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).packageName.hashCode();
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = convertView;
        Holder holder;
        if (view == null) {
            view = inflater.inflate(R.layout.item_app, parent, false);
            holder = new Holder();
            holder.icon = view.findViewById(R.id.appIcon);
            holder.name = view.findViewById(R.id.appName);
            holder.meta = view.findViewById(R.id.appMeta);
            holder.check = view.findViewById(R.id.checkSelect);
            view.setTag(holder);
        } else {
            holder = (Holder) view.getTag();
        }

        final AppEntry entry = getItem(position);
        holder.icon.setImageDrawable(entry.icon);
        holder.name.setText(entry.label);

        StringBuilder meta = new StringBuilder();
        meta.append(entry.packageName);
        if (!entry.versionName.isEmpty()) {
            meta.append("  ·  v").append(entry.versionName);
        }
        meta.append("  ·  ").append(AppEntry.formatSize(entry.sizeBytes));
        if (entry.hasSplits) {
            meta.append("  ·  split");
        }
        holder.meta.setText(meta.toString());
        holder.check.setChecked(entry.selected);
        holder.check.setClickable(false);
        holder.check.setFocusable(false);
        view.setSelected(entry.selected);

        // 整条 item 任意位置点击都切换（icon/文字/勾选框都不拦截）
        view.setOnClickListener(v -> {
            entry.selected = !entry.selected;
            holder.check.setChecked(entry.selected);
            v.setSelected(entry.selected);
            notifySelection();
        });

        return view;
    }

    private static class Holder {
        ImageView icon;
        TextView name;
        TextView meta;
        CheckBox check;
    }
}
