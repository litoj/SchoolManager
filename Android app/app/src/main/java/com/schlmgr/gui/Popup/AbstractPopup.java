package com.schlmgr.gui.popup;

import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout.LayoutParams;
import android.widget.PopupWindow;

import com.schlmgr.gui.Controller;

import java.util.LinkedList;

import static com.schlmgr.gui.Controller.activity;

public abstract class AbstractPopup {

	public static boolean isActive;
	public static boolean isShowing;

	private static final LinkedList<AbstractPopup> showed = new LinkedList<>();

	public static void clean() {
		for (AbstractPopup ap : showed) ap.dismiss(false);
		showed.clear();
	}

	public static void clear() {
		for (AbstractPopup ap : showed) ap.dismiss(true);
		showed.clear();
	}

	final int resId;
	private boolean backBtnDismiss = true;
	public PopupWindow pw;
	private final Runnable creator = () -> create();

	protected AbstractPopup(int resourceID) {
		resId = resourceID;
		Controller.addPopupRepaint(creator);
	}

	public void dismiss() {
		dismiss(true);
	}

	public void dismiss(boolean forever) {
		backBtnDismiss = false;
		pw.dismiss();
		showed.remove(pw);
		isActive = false;
		if (forever) Controller.removePopupRepaint(creator);
	}

	protected void create() {
		isActive = isShowing = true;
		activity.runOnUiThread(() -> {
			View view = activity.getLayoutInflater().inflate(resId, null);
			pw = new PopupWindow(view, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, true);
			pw.setOnDismissListener(() -> {
				isShowing = false;
				if (backBtnDismiss) activity.onBackPressed();
			});
			view.setOnClickListener(v -> dismiss(true));
			pw.setBackgroundDrawable(new ColorDrawable(0x90000000));
			addContent(view);
			showed.add(this);
			pw.showAtLocation(view, Gravity.CENTER, 0, 0);
		});
	}

	abstract protected void addContent(View view);
}
