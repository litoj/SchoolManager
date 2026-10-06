package com.schlmgr.gui.fragments;

import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.core.content.res.ResourcesCompat;

import com.schlmgr.R;

import java.util.EnumMap;
import java.util.Objects;

/**
 * Renders the explorer's selection / paste action bars from explicit state.
 * <p>
 * All inputs are plain values ({@link #render(int, int, boolean)}, {@link #setBarVisible(boolean)}),
 * deliberately UI-toolkit-agnostic so that the whole rendering body can later be replaced by a
 * {@code @Composable SelectionActionsBar(state, onAction)} without touching the state computation
 * or the fragment's behaviour logic.
 * <p>
 * Icons are loaded fresh on every state change instead of being cached in static fields.
 * The previous static {@code Drawable}s lost their bounds (and with them the tint reference after
 * configuration changes); because {@code TextView.setCompoundDrawables()} derives the drawable's
 * size from its bounds, a bound-less drawable took up no space — the icon vanished and the label
 * text shifted to the top of the button.
 */
class SelectionActions {

	/** The actions shown in the explorer bottom bars, each with its two icon variants and size. */
	enum Action {
		REFERENCE(R.drawable.ic_reference, R.drawable.ic_reference_disabled, 40),
		CUT(R.drawable.ic_cut, R.drawable.ic_cut_disabled, 40),
		EDIT(R.drawable.ic_edit, R.drawable.ic_edit_disabled, 35),
		DELETE(R.drawable.ic_delete, R.drawable.ic_delete_disabled, 40),
		PASTE(R.drawable.ic_paste, R.drawable.ic_paste_disabled, 33);

		@DrawableRes final int enabledRes;
		@DrawableRes final int disabledRes;
		/** Icon edge length in dp. */
		final int dp;

		Action(@DrawableRes int enabledRes, @DrawableRes int disabledRes, int dp) {
			this.enabledRes = enabledRes;
			this.disabledRes = disabledRes;
			this.dp = dp;
		}
	}

	private static final int COLOR_ENABLED = 0xFFFFFFFF;
	private static final int COLOR_DISABLED = 0x66FFFFFF;

	private final View bar;
	private final EnumMap<Action, TextView> buttons = new EnumMap<>(Action.class);

	/**
	 * Binds the component to the already inflated view hierarchy.
	 *
	 * @param root any ancestor of the selection and paste bars
	 */
	SelectionActions(View root) {
		bar = root.findViewById(R.id.objects_select);
		buttons.put(Action.REFERENCE, root.findViewById(R.id.select_reference));
		buttons.put(Action.CUT, root.findViewById(R.id.select_cut));
		buttons.put(Action.EDIT, root.findViewById(R.id.select_rename));
		buttons.put(Action.DELETE, root.findViewById(R.id.select_delete));
		buttons.put(Action.PASTE, root.findViewById(R.id.objects_paste));
	}

	/**
	 * Registers the click handler of a single action. Kept as a narrow interface so it maps
	 * directly onto a composable's {@code onClick} lambda later.
	 */
	void onClick(Action action, View.OnClickListener listener) {
		buttons.get(action).setOnClickListener(listener);
	}

	/** Shows or hides the whole selection options bar. */
	void setBarVisible(boolean visible) {
		bar.setVisibility(visible ? View.VISIBLE : View.GONE);
	}

	/**
	 * Recomputes and applies the enabled state of every selection action.
	 * This is a pure state -&gt; view mapping: calling it with the same values always yields
	 * the same UI, which is the property the future composable will rely on.
	 *
	 * @param selected        number of currently selected items ({@code -1} = selecting off)
	 * @param refs            number of selected items that are references
	 * @param insideContainer whether the explorer currently sits inside a container
	 *                        (reference/cut/rename only make sense there)
	 */
	void render(int selected, int refs, boolean insideContainer) {
		setEnabled(Action.DELETE, selected > 0);
		setEnabled(Action.REFERENCE, selected > 0 && insideContainer
				&& (refs < 2 && selected < 2 || refs < 1));
		setEnabled(Action.CUT, selected > 0 && insideContainer && refs < 1);
		setEnabled(Action.EDIT, selected == 1 && refs < 1);
	}

	/**
	 * Toggles one action, including its paste counterpart used by the move/reference flow.
	 * Reloads the matching icon from resources on every actual change.
	 */
	void setEnabled(Action action, boolean enabled) {
		TextView tv = buttons.get(action);
		if (tv.isEnabled() == enabled) return;
		tv.setCompoundDrawables(null, loadIcon(tv, action, enabled), null, null);
		tv.setTextColor(enabled ? COLOR_ENABLED : COLOR_DISABLED);
		tv.setEnabled(enabled);
	}

	/**
	 * Loads a fresh, correctly bounded icon. {@link TextView#setCompoundDrawables} requires
	 * explicit bounds, so they are derived from the action's configured dp size.
	 */
	private static Drawable loadIcon(TextView tv, Action action, boolean enabled) {
		Resources res = tv.getResources();
		@DrawableRes int resId = enabled ? action.enabledRes : action.disabledRes;
		Drawable d = Objects.requireNonNull(
				ResourcesCompat.getDrawable(res, resId, tv.getContext().getTheme()));
		int size = (int) (res.getDisplayMetrics().density * action.dp);
		d.setBounds(0, 0, size, size);
		return d;
	}
}
