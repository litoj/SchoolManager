package com.schlmgr.gui;

import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Helper for handling the enforced edge-to-edge mode (Android 15+) by applying the system
 * bar insets as padding to the given view.
 */
public final class EdgeToEdge {

	private EdgeToEdge() {
	}

	/**
	 * Applies the system bar insets to the target view as padding.
	 *
	 * @param target     the view to pad (typically the activity's content root)
	 * @param includeTop {@code true} to also pad the top (status bar). Pass {@code false}
	 *                   when the top inset is already handled elsewhere (e.g. by an
	 *                   {@code AppBarLayout} with {@code fitsSystemWindows="true"}).
	 */
	public static void apply(View target, boolean includeTop) {
		ViewCompat.setOnApplyWindowInsetsListener(target, (v, insets) -> {
			Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
			v.setPadding(bars.left, includeTop ? bars.top : 0, bars.right, bars.bottom);
			return insets;
		});
	}
}
