package com.schlmgr.gui.list;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.schlmgr.R;
import com.schlmgr.gui.popup.TextPopup;

import java.util.List;

import objects.Picture;
import objects.Reference;

import static com.schlmgr.gui.Controller.dp;
import static com.schlmgr.gui.activity.MainActivity.ic_check_empty;
import static com.schlmgr.gui.activity.MainActivity.ic_check_filled;
import static com.schlmgr.gui.list.HierarchyAdapter.background;

public class SearchAdapter extends AbstractContainerAdapter<SearchItemModel> {
	private LayoutInflater li;

	public SearchAdapter(@NonNull Context context, @NonNull List<SearchItemModel> objects,
	                     Runnable occ, boolean selectActivity) {
		super(context, R.layout.item_search, R.id.item_name, objects, occ, selectActivity);
		li = LayoutInflater.from(context);
	}

	@Override
	public View getView(int index, View view, ViewGroup parent) {
		if (view == null) view = li.inflate(R.layout.item_search, parent, false);
		SearchItemModel item = list.get(index);
		((TextView) view.findViewById(R.id.item_name)).setText(item.toShow);
		TextView num = view.findViewById(R.id.h_item_number);
		num.setBackgroundColor(item.bd instanceof Reference ? 0x400000FF : background(item.bd.getRatio()));
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
		if (selectActivity || selected > -1) {
			ImageView select = view.findViewById(R.id.item_selected);
			select.setVisibility(View.VISIBLE);
			select.setImageDrawable(item.selected ? ic_check_filled:ic_check_empty);
			select.setOnClickListener(v -> {
				select.setImageDrawable((item.selected = !item.selected) ? ic_check_filled:ic_check_empty);
				selected += item.selected ? 1 : -1;
				if (item.bd instanceof Reference) ref += item.selected ? 1 : -1;
				if (occ != null) occ.run();
			});
		} else view.findViewById(R.id.item_selected).setVisibility(View.GONE);
		return view;
	}
}
