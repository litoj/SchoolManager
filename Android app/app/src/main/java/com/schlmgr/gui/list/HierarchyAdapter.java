package com.schlmgr.gui.list;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.schlmgr.R;
import com.schlmgr.gui.CurrentData;
import com.schlmgr.gui.popup.TextPopup;

import java.util.List;

import IOSystem.Formatter;
import objects.MainChapter;
import objects.Picture;
import objects.Reference;

import static com.schlmgr.gui.Controller.dp;
import static com.schlmgr.gui.activity.MainActivity.ic_check_empty;
import static com.schlmgr.gui.activity.MainActivity.ic_check_filled;

public class HierarchyAdapter extends OpenListAdapter<HierarchyItemModel> {
	private LayoutInflater li;

	public HierarchyAdapter(@NonNull Context context, @NonNull List<HierarchyItemModel> objects, Runnable occ, boolean selectActivity) {
		super(context, R.layout.item_hierarchy, R.id.h_item_name, objects, occ, selectActivity);
		li = LayoutInflater.from(context);
	}

	@Override
	public View getView(int index, View view, ViewGroup parent) {
		if (view == null) view = li.inflate(R.layout.item_hierarchy, parent, false);
		HierarchyItemModel item = list.get(index);
		((TextView) view.findViewById(R.id.h_item_name)).setText(item.toShow);
		TextView num = view.findViewById(R.id.h_item_number);
		num.setBackgroundColor(item.bd instanceof Reference ? 0x600000FF : background(item.bd.getRatio()));
		num.setText((index + 1) + ".");
		if (item.bd instanceof Picture)
			item.ic.setBounds((int) dp, 0, (int) (dp * 33), (int) (dp * 33));
		else item.ic.setBounds(0, 0, (int) (dp * 30), (int) (dp * 30));
		num.setCompoundDrawablesRelative(null, null, item.ic, null);
		View iv = view.findViewById(R.id.h_item_info);
		if (!selectActivity && !item.info.isEmpty()) {
			iv.setVisibility(View.VISIBLE);
			iv.setOnClickListener((v) -> new TextPopup(item.info, item.info));
		} else if (iv.getVisibility() != View.GONE) iv.setVisibility(View.GONE);
		if (selected > -1 || selectActivity) {
			ImageView select = view.findViewById(R.id.item_selected);
			select.setVisibility(View.VISIBLE);
			select.setImageDrawable(item.selected ? ic_check_filled : ic_check_empty);
			select.setOnClickListener(v -> {
				select.setImageDrawable((item.selected = !item.selected) ? ic_check_filled : ic_check_empty);
				selected += item.selected ? 1 : -1;
				if (item.bd instanceof Reference) ref += item.selected ? 1 : -1;
				if (occ != null) occ.run();
			});
		} else view.findViewById(R.id.item_selected).setVisibility(View.GONE);
		if (item.bd instanceof MainChapter &&
				!((MainChapter) item.bd).getDir().getPath().contains(Formatter.getPath().getPath())) {
			ImageView remove = view.findViewById(R.id.item_remove);
			remove.setVisibility(View.VISIBLE);
			remove.setOnClickListener(v -> {
				MainChapter mch = (MainChapter) item.bd;
				CurrentData.ImportedMchs.removeMch(mch.getDir());
				mch.close();
				list.remove(item);
				notifyDataSetChanged();
			});
		}
		return view;
	}

	public static int background(int sf) {
		if (sf == -2) return 0;
		if (sf == -1) return 0x600000FF;
		if (sf == 0) return 0x60FF0000;
		if (sf == 50) return 0x60FFFF00;
		if (sf == 100) return 0x6000FF00;
		String ret;
		if (sf < 50) {
			ret = Integer.toHexString(sf * 256 / 50);
			return Integer.parseInt("60FF" + (ret.length() == 1 ? '0' + ret : ret) + "00", 16);
		} else {
			ret = Integer.toHexString((100 - sf) * 256 / 50);
			return Integer.parseInt("60" + (ret.length() == 1 ? '0' + ret : ret) + "FF00", 16);
		}
	}
}
