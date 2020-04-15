package com.schlmgr.gui;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Looper;
import android.provider.MediaStore.Images.Media;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import android.widget.PopupMenu.OnMenuItemClickListener;

import androidx.loader.content.CursorLoader;

import com.schlmgr.R;
import com.schlmgr.gui.activity.MainActivity;

import java.io.File;
import java.util.LinkedList;
import java.util.List;

public class Controller {
	public static MainActivity activity;
	public static Runnable defaultBack;
	public static float dp;
	public static Context CONTEXT;

	private static final Controller control = new Controller();

	/**
	 * Should be only called by an Activity object
	 *
	 * @return helping handler of this app
	 */
	public static Controller getControl() {
		if (control.taken) return null;
		control.taken = true;
		return control;
	}

	public int menuRes;
	public ControlListener currentControl;
	public Runnable onBackPressed = () -> defaultBack.run();
	public ImageView moreButton;
	public ImageView selectButton;
	public final List<Runnable> popupRepaint = new LinkedList<>();

	private boolean taken = false;

	public static boolean isActive(ControlListener test) {
		return test == control.currentControl || test != null &&
				control.currentControl != null && test.getClass() == control.currentControl.getClass();
	}

	public static void setCurrentControl(ControlListener o, int menuRes, boolean selectVisible, boolean back) {
		control.currentControl = o;
		if (back) control.onBackPressed = o;
		setMenuRes(menuRes);
		toggleSelectBtn(selectVisible);
	}

	public static void addPopupRepaint(Runnable forRepaint) {
		control.popupRepaint.add(forRepaint);
	}

	public static void removePopupRepaint(Runnable forRepaint) {
		control.popupRepaint.remove(forRepaint);
	}

	public static void clearPopupRepaint() {
		control.popupRepaint.clear();
	}

	public interface ControlListener extends Runnable, OnClickListener, OnMenuItemClickListener {
		/**
		 * This method is called when 'back' button is pressed.
		 */
		@Override
		default void run() {
		}

		/**
		 * This method is called when the user clicks on the 'edit' icon in the right corner
		 * of the application bar.
		 *
		 * @param v the view that has been clicked
		 */
		@Override
		default void onClick(View v) {
		}

		/**
		 * Called when a menu item is clicked. This method should react on the item click and do
		 * the appropriate actions.
		 *
		 * @param item the item that has been clicked.
		 * @return {@code true} if the item click has been processed.
		 */
		@Override
		default boolean onMenuItemClick(MenuItem item) {
			return false;
		}
	}

	public static void setMenuRes(int newMenuResource) {
		control.moreButton.setVisibility((control.menuRes = newMenuResource) == 0 ? View.GONE : View.VISIBLE);
	}

	public static void toggleSelectBtn(boolean visible) {
		control.selectButton.setVisibility(visible ? View.VISIBLE : View.GONE);
	}

	public static String translate(Class type) {
		if (type == objects.MainChapter.class)
			return activity.getString(R.string.hierarchy_mch);
		if (type == objects.SaveChapter.class
				|| type == objects.Chapter.class)
			return activity.getString(R.string.hierarchy_ch);
		if (type == objects.Reference.class)
			return activity.getString(R.string.hierarchy_ref);
		if (type == objects.Word.class)
			return activity.getString(R.string.hierarchy_word);
		if (type == objects.Picture.class)
			return activity.getString(R.string.hierarchy_picture);
		return type.getName();
	}

	/**
	 * Code to create a File from the given URI created by an Intent, has to be a file, not a folder.
	 * Original source code available at:
	 * https://stackoverflow.com/questions/6935497/android-get-gallery-image-uri-path
	 *
	 * @author Allan Jiang
	 */
	public static File getFileFromUri(Uri uri) {
		Looper.prepare();
		Cursor c = new CursorLoader(activity.getApplicationContext(), uri,
				new String[]{Media.DATA}, null, null, null).loadInBackground();
		int index = c.getColumnIndexOrThrow(Media.DATA);
		c.moveToFirst();
		return new File(c.getString(index));
	}
}
