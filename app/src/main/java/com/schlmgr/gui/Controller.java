package com.schlmgr.gui;

import android.content.Context;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import android.widget.PopupMenu.OnMenuItemClickListener;

import com.schlmgr.R;
import androidx.activity.ComponentActivity;

import com.schlmgr.gui.activity.MainActivity;

import java.util.LinkedList;
import java.util.List;

public class Controller {
	public static MainActivity activity;
	public static ComponentActivity currentActivity;
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
	private boolean selectVisible;
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

	/**
	 * Shows or hides the 'more options' button. The buttons may not exist yet when
	 * this is called (the main fragment inflates during setContentView, before the
	 * activity wires its action bar views), so the desired state is remembered in
	 * {@link #menuRes} and applied once the views exist (see {@link #applyBarState()}).
	 */
	public static void setMenuRes(int newMenuResource) {
		control.menuRes = newMenuResource;
		if (control.moreButton != null)
			control.moreButton.setVisibility(newMenuResource == 0 ? View.GONE : View.VISIBLE);
	}

	public static void toggleSelectBtn(boolean visible) {
		control.selectVisible = visible;
		if (control.selectButton != null)
			control.selectButton.setVisibility(visible ? View.VISIBLE : View.GONE);
	}

	/**
	 * Applies the remembered 'more'/'select' button visibility to the action bar views.
	 * Called by MainActivity right after it obtains the views, so that requests made
	 * during fragment inflation (before the views existed) take effect.
	 */
	public static void applyBarState() {
		setMenuRes(control.menuRes);
		toggleSelectBtn(control.selectVisible);
	}

	public static String translate(Class type) {
		if (type == com.schlmgr.gui.engine.objects.MainChapter.class)
			return activity.getString(R.string.hierarchy_mch);
		if (type == com.schlmgr.gui.engine.objects.SaveChapter.class
				|| type == com.schlmgr.gui.engine.objects.Chapter.class)
			return activity.getString(R.string.hierarchy_ch);
		if (type == com.schlmgr.gui.engine.objects.Reference.class)
			return activity.getString(R.string.hierarchy_ref);
		if (type == com.schlmgr.gui.engine.objects.Word.class)
			return activity.getString(R.string.hierarchy_word);
		if (type == com.schlmgr.gui.engine.objects.Picture.class)
			return activity.getString(R.string.hierarchy_picture);
		return type.getName();
	}
}
