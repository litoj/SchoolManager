package com.schlmgr.gui.popup;

import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout.LayoutParams;
import android.widget.PopupWindow;

import com.schlmgr.gui.Controller;

import java.util.LinkedList;

import static com.schlmgr.gui.Controller.activity;
import static com.schlmgr.gui.Controller.currentActivity;

public abstract class AbstractPopup {

	public static boolean isActive;
	public static boolean isShowing;

	private static final LinkedList<AbstractPopup> showed = new LinkedList<>();

	/**
	 * Will display again.
	 */
	public static void clean() {
		for (AbstractPopup ap : showed) ap.dismiss(false);
		showed.clear();
	}

	/**
	 * Won't be displayed again.
	 */
	public static void clear() {
		for (AbstractPopup ap : showed) ap.dismiss(true);
		showed.clear();
	}

	final int resId;
	private boolean backBtnDismiss = true;
	private PopupWindow pw;
	private final Runnable creator = () -> create();
	private final boolean onlyMain;

	protected AbstractPopup(int resourceID, boolean onlyMain) {
		resId = resourceID;
		this.onlyMain = onlyMain;
		Controller.addPopupRepaint(creator);
	}

	public void dismiss() {
		dismiss(true);
	}

	public void dismiss(boolean forever) {
		backBtnDismiss = false;
		pw.dismiss();
		showed.remove(this);
		isActive = false;
		if (forever) Controller.removePopupRepaint(creator);
	}

	protected void create() {
		isActive = isShowing = true;
		(onlyMain ? activity : currentActivity).runOnUiThread(() -> {
			ViewGroup view = (ViewGroup)
					(onlyMain ? activity : currentActivity).getLayoutInflater().inflate(resId, null);
			pw = new PopupWindow(view, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, true);
			pw.setOnDismissListener(() -> {
				isShowing = false;
				if (backBtnDismiss) (onlyMain ? activity : currentActivity).onBackPressed();
			});
			pw.setBackgroundDrawable(new ColorDrawable(0x90000000));
			addContent(view);
			showed.add(this);
			pw.showAtLocation(view, Gravity.CENTER, 0, 0);
		});
	}

	abstract protected void addContent(ViewGroup view);
}
