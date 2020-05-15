package com.schlmgr.gui.list;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.widget.ImageView;

import java.util.LinkedList;

import objects.Picture;

import static com.schlmgr.gui.Controller.dp;

public class ImageItemModel {
	private Bitmap bm1;
	public final Picture pic1;
	private Bitmap bm2;
	public final Picture pic2;
	private volatile ImageView iv1;
	private volatile ImageView iv2;

	private static LinkedList<ImageItemModel> queue = new LinkedList<>();

	public ImageItemModel(Picture p1, Picture p2) {
		this(p1, p2, 150 * dp);
	}

	public ImageItemModel(Picture p1, Picture p2, float maxSize) {
		pic1 = p1;
		pic2 = p2;
		createBMs = () -> {
			bm1 = getScaledBitmap(pic1, maxSize);
			if (iv1 != null) iv1.post(() -> iv1.setImageBitmap(bm1));
			if (pic2 != null) {
				bm2 = getScaledBitmap(pic2, maxSize);
				if (iv2 != null) iv2.post(() -> iv2.setImageBitmap(bm2));
			}
			queue.remove(ImageItemModel.this);
			if (!queue.isEmpty()) queue.getFirst().createBMs.run();
		};
		if (queue.isEmpty()) new Thread(createBMs).start();
		queue.add(this);
	}

	final Runnable createBMs;

	public void setBm(boolean first, ImageView src) {
		if (first) iv1 = src;
		else iv2 = src;
		if ((first ? bm1 : bm2) != null) (first ? iv1 : iv2).setImageBitmap(first ? bm1 : bm2);
	}

	/**
	 * Creates a Bitmap from the given object.
	 */
	public static Bitmap getScaledBitmap(Picture pic, float maxWidth) {
		if (pic.imageRender != null) {
			if (((Bitmap) pic.imageRender).getWidth() <= maxWidth) return (Bitmap) pic.imageRender;
			else return getScaledBitmap((Bitmap) pic.imageRender, maxWidth);
		}
		return (Bitmap) (pic.imageRender = getScaledBitmap(pic.getFile().getAbsolutePath(), maxWidth));
	}

	/**
	 * Creates a Bitmap from the given path.
	 */
	public static Bitmap getScaledBitmap(String absolutePath, float maxSize) {
		return getScaledBitmap(absolutePath, maxSize, false);
	}

	/**
	 * Creates a Bitmap from the given path.
	 */
	public static Bitmap getScaledBitmap(String absolutePath, float maxSize, boolean bigger) {
		Options opts = new Options();
		opts.inJustDecodeBounds = true;
		BitmapFactory.decodeFile(absolutePath, opts);
		float ratio = (opts.outHeight > opts.outWidth != bigger ? opts.outHeight : opts.outWidth) / maxSize;
		if (ratio <= 1) return BitmapFactory.decodeFile(absolutePath);
		opts.inSampleSize = (int) ratio;
		opts.inJustDecodeBounds = false;
		return BitmapFactory.decodeFile(absolutePath, opts);
	}

	/**
	 * Creates a scaled Bitmap from the given object to the maximum specified height.
	 */
	private static Bitmap getScaledBitmap(Bitmap src, float maxHeight) {
		if (src == null) return null;
		float ratio = src.getHeight() / maxHeight;
		return ratio <= 1 ? src : Bitmap.createScaledBitmap(src, (int) (src.getWidth() / ratio),
				(int) (src.getHeight() / ratio), true);
	}
}
