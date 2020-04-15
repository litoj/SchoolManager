package com.schlmgr.gui.list;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import objects.Picture;

public class ImageItemModel {
	public final Bitmap bm1;
	public final Picture pic1;
	public final Bitmap bm2;
	public final Picture pic2;

	public ImageItemModel(Picture p1, Picture p2) {
		bm1 = BitmapFactory.decodeFile((this.pic1 = p1).getFile().getPath());
		if (p2 == null) {
			this.pic2 = null;
			bm2 = null;
		} else bm2 = BitmapFactory.decodeFile((this.pic2 = p2).getFile().getPath());
	}
}
