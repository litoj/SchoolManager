package com.schlmgr.gui.popup;

import android.graphics.Bitmap;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.schlmgr.R;
import com.schlmgr.gui.list.ImageItemModel;

import objects.Picture;

public class FullPicture extends AbstractPopup {

	public static int size;//the bigger value of display dimensions
	private final Bitmap pic;
	private final boolean wide;

	public FullPicture(Picture picture) {
		super(R.layout.popup_pic, false);
		pic = ImageItemModel.getScaledBitmap(picture.getFile().getAbsolutePath(), size, true);
		wide = pic.getWidth() > pic.getHeight();
		create();
	}

	@Override
	protected void addContent(ViewGroup view) {
		view.findViewById(wide ? R.id.popup_pic_hscroll : R.id.popup_pic_vscroll).setVisibility(View.VISIBLE);
		ImageView iv = view.findViewById(wide ? R.id.popup_pic_h : R.id.popup_pic_v);
		iv.setImageBitmap(pic);
		iv.setOnClickListener(x -> dismiss());
	}
}
