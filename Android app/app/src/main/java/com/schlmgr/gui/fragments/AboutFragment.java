package com.schlmgr.gui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import com.schlmgr.BuildConfig;
import com.schlmgr.R;
import com.schlmgr.gui.Controller;
import com.schlmgr.gui.Controller.ControlListener;
import com.schlmgr.gui.list.AbstractNestAdapter;

import java.util.ArrayList;
import java.util.Arrays;

import static com.schlmgr.gui.Controller.activity;

public class AboutFragment extends Fragment implements ControlListener {

	private static final TglVisibility help_create =
			new TglVisibility(R.id.help_create, R.id.help_create_layout);
	private static final TglVisibility help_create_mch =
			new TglVisibility(R.id.help_create_mch, R.id.help_create_mch_how);
	private static final TglVisibility help_create_ch =
			new TglVisibility(R.id.help_create_ch, R.id.help_create_ch_how);
	private static final TglVisibility help_create_word =
			new TglVisibility(R.id.help_create_word, R.id.help_create_word_how);
	private static final TglVisibility help_create_pic =
			new TglVisibility(R.id.help_create_pic, R.id.help_create_pic_how);
	private static final TglVisibility help_create_ref =
			new TglVisibility(R.id.help_create_ref, R.id.help_create_ref_how);

	private static final TglVisibility help_select =
			new TglVisibility(R.id.help_select, R.id.help_select_layout);
	private static final TglVisibility help_select_delete =
			new TglVisibility(R.id.help_select_delete, R.id.help_select_delete_how);
	private static final TglVisibility help_select_ref =
			new TglVisibility(R.id.help_select_ref, R.id.help_select_ref_how);
	private static final TglVisibility help_select_cut =
			new TglVisibility(R.id.help_select_cut, R.id.help_select_cut_how);
	private static final TglVisibility help_select_edit =
			new TglVisibility(R.id.help_select_edit, R.id.help_select_edit_how);

	private static final TglVisibility help_search =
			new TglVisibility(R.id.help_search, R.id.help_search_layout);
	private static final TglVisibility help_search_types =
			new TglVisibility(R.id.help_search_types, R.id.help_search_types_how);
	private static final TglVisibility help_search_regex =
			new TglVisibility(R.id.help_search_regex, R.id.help_search_regex_how);

	private static final TglVisibility help_subjdir =
			new TglVisibility(R.id.help_subjdir, R.id.help_subjdir_layout);

	private static final TglVisibility help_test =
			new TglVisibility(R.id.help_test, R.id.help_test_layout);
	private static final TglVisibility help_test_select =
			new TglVisibility(R.id.help_test_select, R.id.help_test_select_how);
	private static final TglVisibility help_test_run =
			new TglVisibility(R.id.help_test_run, R.id.help_test_run_how);
	private static final TglVisibility help_test_results =
			new TglVisibility(R.id.help_test_results, R.id.help_test_results_how);

	private static final TglVisibility help_extra =
			new TglVisibility(R.id.help_extra, R.id.help_extra_layout);
	private static final TglVisibility help_extra_naming =
			new TglVisibility(R.id.help_extra_naming, R.id.help_extra_naming_how);
	private static final TglVisibility help_extra_sch_import =
			new TglVisibility(R.id.help_extra_sch_import, R.id.help_extra_sch_import_how);
	private static final TglVisibility help_extra_words_import =
			new TglVisibility(R.id.help_extra_words_import, R.id.help_extra_words_import_how);
	private static final TglVisibility help_extra_words_export =
			new TglVisibility(R.id.help_extra_words_export, R.id.help_extra_words_export_how);
	private static final TglVisibility help_extra_import_mch =
			new TglVisibility(R.id.help_extra_import_mch, R.id.help_extra_import_mch_how);

	public View onCreateView(@NonNull LayoutInflater inflater,
													 ViewGroup container, Bundle savedInstanceState) {
		View root = inflater.inflate(R.layout.fragment_about, container, false);
		activity.getSupportActionBar().setTitle(activity.getString(R.string.app_name) + " v" + BuildConfig.VERSION_NAME);

		help_create.setTgl(root);
		help_create_mch.setTgl(root);
		help_create_ch.setTgl(root);
		help_create_word.setTgl(root);
		help_create_pic.setTgl(root);
		help_create_ref.setTgl(root);
		help_select.setTgl(root);
		help_select_delete.setTgl(root);
		help_select_ref.setTgl(root);
		help_select_cut.setTgl(root);
		help_select_edit.setTgl(root);
		help_search.setTgl(root);
		help_search_types.setTgl(root);
		help_search_regex.setTgl(root);

		((TextView) root.findViewById(R.id.help_search_regex_0_1)).setText("Char Class\n" +
				"[abc]\n[^abc]\n[a-zA-Z]\n[a-d[m-p]]\n[a-z&&[def]]\n[a-z&&[^bc]]\n[a-z&&[^m-p]]");
		((TextView) root.findViewById(R.id.help_search_regex_1_1))
				.setText("Description\na, b, or c\nAny character except a, b, or c\na through z or A " +
						"through Z, inclusive\na through d, or m through p: [a-dm-p]\nd, e, or f \na thr" +
						"ough z, except for b and c: [ad-z]\na through z, and not m through p: [a-lq-z]");
		((TextView) root.findViewById(R.id.help_search_regex_0_2))
				.setText("Regex\nX?\nX+\nX*\nX{n}\nX{n,}\nX{y,z}");
		((TextView) root.findViewById(R.id.help_search_regex_1_2))
				.setText("Description\nX occurs once or not at all\nX occurs once or more times\n" +
						"X occurs zero or more times\nX occurs n times only\nX occurs n or more times\n" +
						"X occurs at least y times but less than z times");
		((TextView) root.findViewById(R.id.help_search_regex_0_3))
				.setText("Regex\n.\n\\d\n\\D\n\\s\n\\S\n\\w\n\\W\n\\b\n\\B");
		((TextView) root.findViewById(R.id.help_search_regex_1_3))
				.setText("Description\nAny character\nAny digits ([0-9])\n" +
						"Any non-digit ([^0-9])\nAny whitespace character ([\\t\\n\\x0B\\f\\r])\n" +
						"Any non-whitespace character ([^\\s])\nAny word character ([a-zA-Z_0-9])\n" +
						"Any non-word character ([^\\w])\nA word boundary\nA non word boundary");

		help_subjdir.setTgl(root);
		help_test.setTgl(root);
		help_test_select.setTgl(root);
		help_test_run.setTgl(root);
		help_test_results.setTgl(root);
		help_extra.setTgl(root);
		help_extra_naming.setTgl(root);
		help_extra_sch_import.setTgl(root);
		help_extra_words_import.setTgl(root);
		help_extra_words_export.setTgl(root);
		help_extra_import_mch.setTgl(root);
		return root;
	}

	static class TglVisibility {
		boolean visible;
		View switcher;
		View toToggle;
		final int switcherID, toTglID;

		TglVisibility(int switcherID, int toToggleID) {
			this.switcherID = switcherID;
			this.toTglID = toToggleID;
		}

		/**
		 * Sets up the onClick action for showing and hiding {@link #toToggle}.
		 *
		 * @param root the current parent of the views.
		 */
		void setTgl(View root) {
			toToggle = root.findViewById(toTglID);
			(switcher = root.findViewById(switcherID)).setOnClickListener(v ->
					toToggle.setVisibility((visible = !visible) ? View.VISIBLE : View.GONE));
			if ((toToggle.getVisibility() == View.VISIBLE) != visible)
				toToggle.setVisibility(visible ? View.VISIBLE : View.GONE);
		}
	}

	@Override
	public void onResume() {
		Controller.setCurrentControl(this, 0, false, false);
		super.onResume();
	}

	@Override
	public void run() {
		Controller.defaultBack.run();
	}
}