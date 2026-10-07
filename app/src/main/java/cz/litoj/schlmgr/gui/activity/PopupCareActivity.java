package cz.litoj.schlmgr.gui.activity;

import android.os.Bundle;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;

import cz.litoj.schlmgr.gui.Controller;
import cz.litoj.schlmgr.gui.popup.AbstractPopup;

import static cz.litoj.schlmgr.gui.activity.MainActivity.c;

/**
 * This class handles {@link AbstractPopup popups} showing and dismissing on screen rotation.
 * All activities should extend this activity.
 */
public class PopupCareActivity extends AppCompatActivity {

	private boolean exists;

	private final OnBackPressedCallback backCallback = new OnBackPressedCallback(true) {
		@Override
		public void handleOnBackPressed() {
			onUserBackPressed();
		}
	};

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getOnBackPressedDispatcher().addCallback(this, backCallback);
		// Edge-to-edge is enforced for apps targeting Android 15+; opt in consistently on
		// older versions too so the insets handling applies everywhere.
		WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
		Controller.currentActivity = this;
	}

	/**
	 * Replaces the deprecated {@link android.app.Activity#onBackPressed()}.
	 * Subclasses override this instead of {@code onBackPressed()}.
	 */
	protected void onUserBackPressed() {
		goBack();
	}

	/**
	 * Hands the back event over to the system (this activity's callback is temporarily
	 * disabled), so the predictive back animations (back-to-home, cross-activity) can run.
	 */
	protected final void goBack() {
		backCallback.setEnabled(false);
		getOnBackPressedDispatcher().onBackPressed();
		backCallback.setEnabled(true);
	}

	@Override
	protected void onPostResume() {
		super.onPostResume();
		if (!c.popupRepaint.isEmpty() && !exists)
			for (Runnable r : c.popupRepaint) new Thread(r, "popupRepaint").start();
		exists = true;
		Controller.currentActivity = this;
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
		else runOnUiThread(AbstractPopup::clean);
		super.onDestroy();
	}

	protected void oldDestroy() {
		super.onDestroy();
	}
}
