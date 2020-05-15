package com.schlmgr.gui.activity;

import androidx.appcompat.app.AppCompatActivity;

import com.schlmgr.gui.popup.AbstractPopup;

import static com.schlmgr.gui.activity.MainActivity.c;

public class PopupCareActivity extends AppCompatActivity {

	private boolean exists;

	@Override
	protected void onPostResume() {
		super.onPostResume();
		if (!c.popupRepaint.isEmpty() && !exists)
			for (Runnable r : c.popupRepaint) new Thread(r).start();
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
		runOnUiThread(() -> AbstractPopup.clean());
		super.onDestroy();
	}
}
