package com.schlmgr.gui.activity;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.schlmgr.gui.Controller;
import com.schlmgr.gui.popup.AbstractPopup;

import static com.schlmgr.gui.activity.MainActivity.c;

/**
 * This class handles {@link AbstractPopup popups} showing and dismissing on screen rotation.
 * All activities should extend this activity.
 */
public class PopupCareActivity extends AppCompatActivity {

	private boolean exists;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Controller.currentActivity = this;
	}

	@Override
	protected void onPostResume() {
		super.onPostResume();
		if (!c.popupRepaint.isEmpty() && !exists)
			for (Runnable r : c.popupRepaint) new Thread(r, "popupRepaint").start();
		exists = true;
	}

	protected boolean clear() {
		if (AbstractPopup.isActive) {
			AbstractPopup.clear();
			return true;
		} else return false;
	}

	@Override
	public void onDestroy() {
		if (AbstractPopup.isActive && !AbstractPopup.isShowing) AbstractPopup.clear();
		else runOnUiThread(() -> AbstractPopup.clean());
		super.onDestroy();
	}

	protected void oldDestroy() {
		super.onDestroy();
	}
}
