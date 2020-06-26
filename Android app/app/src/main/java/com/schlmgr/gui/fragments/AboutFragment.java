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

	private static final TglVisibility versions_release =
			new TglVisibility(R.id.versions_release, R.id.versions_release_list);
	private static final TglVisibility versions_beta =
			new TglVisibility(R.id.versions_beta, R.id.versions_beta_list);

	public View onCreateView(@NonNull LayoutInflater inflater,
	                         ViewGroup container, Bundle savedInstanceState) {
		View root = inflater.inflate(R.layout.fragment_about, container, false);
		activity.getSupportActionBar().setTitle(activity.getString(R.string.app_name) + " " + BuildConfig.VERSION_NAME);
		new Thread(() -> {
			versions_release.setTgl(root);
			versions_beta.setTgl(root);
			RecyclerView rvRelease = (RecyclerView) versions_release.toToggle;
			String[][] releases = {
					{"3.5", "- improved the visual look of search field\n-added item separating lines\n",
							"- search field now hidden, must scroll up to use it"},
					{"3.4", "- remake of search field handling\n- improved performance\n" +
							"- added many new search syntax features, see help\n" +
							"- fixed popup bugs\n- enhanced test results color saturation"},
					{"3.0", "- added SaveChapter import\n- added subject cleanup\n" +
							"- added 'file-chapter to chapter' and vice versa conversion option\n" +
							"- added TAB character by writing \\t in any description"},
					{"2.7", "- changed version view\n- separated into nested lists\n" +
							"- improved clever test item selecting algorithm"},
					{"2.6", "- complete hierarchy saving and loading mechanism remake\n" +
							"- improved hierarchy I/O navigation and simplified use\n" +
							"- new I/O improves compatibility and ignores potential json format" +
							" mistakes\n- I/O now resistant to human imperfection"},
					{"2.3", "- improved two-sided displaying\n- added old version compatibility" +
							" maintainer\n- added fullscreen image in picture editor\n" +
							"- made exception handler functional"},
					{"2.2", "- added success rate revaluation option\n- added uncaught exception" +
							" handler\n- fixed crash on 'Set subjects directory' option"},
					{"2.1", "- fixed settings not saving\n- fixed test results not staying on screen\n" +
							"- improved code structure, some javadoc added"},
					{"2.0", "- improved pictures loading speed\n- added nested scroll for image editing" +
							" + creating\n- fixed twosided partially renaming (deleting its children)"},
					{"1.5", "- improved dir selector behavior\n- fixed transition between explorer and" +
							" dir selector\n- fixed gui bad icon display\n- fixed word deletion" +
							" null-pointer\n- fixed SaveChapter hash errors"},
					{"1.2", "- redesigned inefficient and unsafe database operations\n" +
							"- fixed test end possible crash"},
					{"1.1", "- fixed chapters with file not saving\n" +
							"- fixed renaming file-chapter - app crash"},
					{"1.0", "- customized 'About' tab- added help- formatted this tab"}
			};
			rvRelease.setAdapter(new VersionAdapter(rvRelease, (ScrollView) root, releases));
			RecyclerView rvBeta = (RecyclerView) versions_beta.toToggle;
			String[][] betas = {
					{"Beta 8.6", "- added test result with correct answers window\n" +
							"- fixed directory chooser crashes when between storage\n" +
							"- fixed test with reference bug\n- fixed test item duplication"},
					{"Beta 8.4", "- changed reference button function while in search list\n" +
							"- fixed items not selectable when using select button in search list\n" +
							"- fixed test with reference bug\n- fixed test item duplication"},
					{"Beta 8.2", "- changed creating interface\n- fixed back-button presses\n" +
							"- fixed library testing and reference issues"},
					{"Beta 8.0", "- added test source picker\n- implemented testing\n" +
							"- changed testing interface"},
					{"Beta 7.5", "- implemented menu after selecting\n- added delete function\n" +
							"- added reference function\n- added cut function\n- added edit function"},
					{"Beta 7.0", "- new picture function implemented\n- new word function adapted for" +
							" multiple translates\n- fixed popup stays on screen"},
					{"Beta 6.6", "- rapidly improved startup time\n- restructured code to use more" +
							" inheritance\n- improved library's platform independency\n" +
							"- added choose dir options\n- added external storage workflow\n" +
							"- fixed permission issues\n- fix: app crash after removed dir"},
					{"Beta 6.3", "- added settings functions\n- corrected design\n" +
							"- added interface options"},
					{"Beta 6.2", "- added showing parsed names (set as default behaviour)\n" +
							"- added more sorting methods\n- fix: search not parsing searched text"},
					{"Beta 6.1", "- fix: translate description not saving\n" +
							"- fix: word creation not creating last word"},
					{"Beta 6.0", "- added 'New Subject/Chapter/Word' option\n" +
							"- fix: not auto-saving after change"},
					{"Beta 5.3", "- added 'Change source folder' option\n" +
							"- fix: autosaving not working (data loss)"},
					{"Beta 5.1", "- remake: Searching interface and GUI, Search field auto-hide\n- added"
							+ " search syntax variants\n- fix: Reference change search path (app crash)"},
					{"Beta 5.0", "- added Search field, IOException handlers\n" +
							"- fix: database data loss, fix: not saving added objects"},
					{"Beta 4.1", "- added App Icon, remake: Sorting interface"},
					{"Beta 4.0", "- added Sorting options (alphabet + success rate)\n" +
							"- fix: Reference bugs"},
					{"Beta 3.1", "- added Word hierarchy import, Word description screen"},
					{"Beta 3.0", "- added 'more' menu, Word hierarchy export"},
					{"Beta 2.1", "- improved Exception handling, added copy button\n" +
							"- remake: app menu design"},
					{"Beta 2.0", "- added Image displaying, Exception informer\n- fix: Reference bugs"},
					{"Beta 1.2", "- added Reference support, AutoSave changes\n" +
							"- fix: large description"},
					{"Beta 1.1", "- added icons and success color indication,\n" +
							"- added Word translation toggle, multiple description support"},
					{"Beta 1.0", "Able to show content of compiled hierarchies"}
			};
			rvBeta.setAdapter(new VersionAdapter(rvBeta, (ScrollView) root, betas));
		}).start();

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

	private static class VersionAdapter
			extends AbstractNestAdapter<String[], VersionAdapter.VersionHolder> {

		protected VersionAdapter(RecyclerView rv, ScrollView firstScroll, String[]... items) {
			super(new ArrayList<>(Arrays.asList(items)));
			update(rv, firstScroll);
		}

		@NonNull
		@Override
		public VersionHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
			return new VersionHolder(LayoutInflater.from(
					parent.getContext()).inflate(R.layout.item_version, parent, false));
		}

		private class VersionHolder extends AbstractNestAdapter.ViewHolder {

			final TextView name;
			final TextView description;

			VersionHolder(@NonNull View itemView) {
				super(itemView);
				name = itemView.findViewById(R.id.version_name);
				description = itemView.findViewById(R.id.version_description);
			}

			@Override
			protected void setData(int pos) {
				String[] item = list.get(pos);
				name.setText(item[0]);
				description.setText(item[1]);
			}
		}

	}
}