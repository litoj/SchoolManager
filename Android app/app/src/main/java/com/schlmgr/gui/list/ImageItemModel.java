package com.schlmgr.gui.list;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.util.LinkedList;

import objects.Picture;

import static com.schlmgr.gui.Controller.dp;

public class ImageItemModel {
	private Bitmap bm1;
	public final Picture pic1;
	private Bitmap bm2;
	public final Picture pic2;

	private static LinkedList<ImageItemModel> queue = new LinkedList<>();

	public ImageItemModel(Picture p1, Picture p2) {
		pic1 = p1;
		pic2 = p2;
		createBms = () -> {
			synchronized (ImageItemModel.this) {
				bm1 = getScaledBitmap(pic1);
				notify();
				if (pic2 != null) bm2 = getScaledBitmap(pic2);
				notify();
			}
			queue.removeFirst();
			if (!queue.isEmpty()) queue.getFirst().createBms.run();
		};
		if (queue.isEmpty()) new Thread(createBms).start();
		queue.add(this);
	}

	private final Runnable createBms;

	public Bitmap getBitmap(boolean first) {
		if ((first ? bm1 : bm2) == null) synchronized (this) {
			try {
				while ((first ? bm1 : bm2) == null) wait();
			} catch (InterruptedException ie) {
			}
		}
		return first ? bm1 : bm2;
	}

	/**
	 * Creates a Bitmap from the given object. Maximum height is 150dp.
	 */
	public static Bitmap getScaledBitmap(Picture pic) {
		if (pic.imageRender != null) return (Bitmap) pic.imageRender;
		return (Bitmap) (pic.imageRender = getScaledBitmap(pic.getFile().getAbsolutePath()));
	}

	/**
	 * Creates a Bitmap from the given object. Maximum height is 150dp.
	 */
	public static Bitmap getScaledBitmap(String absolutePath) {
		return getScaledBitmap(absolutePath, 150 * dp);
	}

	/**
	 * Creates a Bitmap from the given object.
	 */
	public static Bitmap getScaledBitmap(String absolutePath, float pixels) {
		Bitmap bm = BitmapFactory.decodeFile(absolutePath);
		float ratio = bm.getHeight() / pixels;
		return ratio <= 1 ? bm : Bitmap.createScaledBitmap(bm, (int) (bm.getWidth() / ratio),
				(int) (bm.getHeight() / ratio), true);
	}
}
