package com.schlmgr.gui.popup;

import android.graphics.Bitmap;
import android.view.View;
import android.widget.ImageView;

import com.schlmgr.R;

public class FullPicture extends AbstractPopup {

	final Bitmap pic;

	public FullPicture(Bitmap pic) {
		super(R.layout.popup_pic);
		this.pic = pic;
		create();
	}

	@Override
	protected void addContent(View view) {
		ImageView iv = view.findViewById(R.id.popup_pic);
		iv.setImageBitmap(pic);
		iv.setOnClickListener(x -> dismiss());
	}
}
