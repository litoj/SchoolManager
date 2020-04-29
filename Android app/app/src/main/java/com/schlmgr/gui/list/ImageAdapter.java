package com.schlmgr.gui.list;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.schlmgr.R;
import com.schlmgr.gui.popup.FullPicture;

import java.util.List;

import objects.templates.Container;

public class ImageAdapter extends ArrayAdapter<ImageItemModel> {
	private final LayoutInflater li;
	public final List<ImageItemModel> list;
	private final Container parent;

	public ImageAdapter(@NonNull Context context, @NonNull List<ImageItemModel> objects, Container parent) {
		super(context, R.layout.item_image, R.id.item_img_ics, objects);
		li = LayoutInflater.from(context);
		list = objects;
		this.parent = parent;
	}

	public View getView(int pos, View view, ViewGroup parent) {
		if (view == null) view = li.inflate(R.layout.item_image, parent, false);
		ImageItemModel iim = list.get(pos);
		ImageView iv = view.findViewById(R.id.item_img_1);
		iv.setOnClickListener(v -> new FullPicture(iim.bm1));
		iv.setImageBitmap(iim.bm1);
		iv.setContentDescription(iim.pic1.toString());
		TextView tv = view.findViewById(R.id.item_img_d1);
		String desc = iim.pic1.getDesc(this.parent);
		if (desc.isEmpty()) tv.setVisibility(View.GONE);
		else tv.setText(desc);
		if (iim.pic2 != null) {
			(iv = view.findViewById(R.id.item_img_2)).setImageBitmap(iim.bm1);
			iv.setContentDescription(iim.pic2.toString());
			iv.setOnClickListener(v -> new FullPicture(iim.bm2));
			tv = view.findViewById(R.id.item_img_d2);
			desc = iim.pic2.getDesc(this.parent);
			if (desc.isEmpty()) tv.setVisibility(View.GONE);
			else tv.setText(desc);
		} else view.findViewById(R.id.item_img_l2).setVisibility(View.GONE);
		return view;
	}
}

