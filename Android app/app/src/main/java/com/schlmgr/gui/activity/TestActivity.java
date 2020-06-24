package com.schlmgr.gui.activity;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.schlmgr.R;
import com.schlmgr.gui.Controller;
import com.schlmgr.gui.fragments.TestFragment;
import com.schlmgr.gui.list.HierarchyItemModel;
import com.schlmgr.gui.list.SearchItemModel;
import com.schlmgr.gui.list.TestItemModel;
import com.schlmgr.gui.popup.FullPicture;
import com.schlmgr.gui.popup.TestResultsPopup;

import java.util.ArrayList;
import java.util.List;

import objects.MainChapter;
import objects.Picture;
import objects.Word;
import objects.templates.Container;
import objects.templates.TwoSided;
import testing.Test;
import testing.Test.SrcPath;

import static com.schlmgr.gui.fragments.TestFragment.picTest;

public class TestActivity extends PopupCareActivity {

	private static long backTime;
	private static Test test;
	private static TextView timer;
	private static Adapter adapter;
	private static final ArrayList<TestItemModel> list = new ArrayList<>();
	private static TestActivity taInstance;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		taInstance = this;
		if (test == null) {
			test = picTest ? new Test<>(Picture.class) : new Test<>(Word.class);
			ArrayList<List<Container>> list = new ArrayList<>(TestFragment.list.size());
			for (SearchItemModel sim : TestFragment.list) {
				List<Container> path = new ArrayList<>(sim.path);
				path.add((Container) sim.bd);
				list.add(path);
			}
			List<SrcPath> paths = test.convertAll(list);
			if (paths.isEmpty()) {
				test = null;
				Toast.makeText(getApplicationContext(),
						R.string.fail_no_objects, Toast.LENGTH_SHORT).show();
				super.onBackPressed();
				return;
			}
			adapter = new Adapter();
			test.setTested(Integer.parseInt(TestFragment.amount.getText().toString()), sl -> {
				if (test == null) return false;
				runOnUiThread(() -> {
					if (timer == null) return;
					if (sl <= 30) timer.setTextColor(sl % 2 == 0 ? 0xFFDD0000 : 0xFFDDDDDD);
					if (sl > 0) timer.setText(sl + "s");
					else onSubmit();
				});
				return true;
			}, Integer.parseInt(TestFragment.time.getText().toString()), paths);
			for (Object sp : test.getTestSrc()) this.list.add(new TestItemModel((SrcPath) sp));
			test.startTest();
		}
		setContentView(R.layout.activity_test);
		timer = findViewById(R.id.test_timer);
		findViewById(R.id.ok).setOnClickListener(v -> onSubmit());
		ListView lv = findViewById(R.id.test_list);
		adapter.li = getLayoutInflater();
		lv.setAdapter(adapter);
	}

	private static void reset() {
		test = null;
		timer = null;
		adapter.notifyDataSetInvalidated();
		adapter = null;
		list.clear();
		taInstance.defaultBack();
		taInstance = null;
		TestFragment.list.clear();
		SelectItemsActivity.backLog = null;
	}

	private void defaultBack() {
		super.onBackPressed();
	}

	@Override
	public void onDestroy() {
		if (test == null) oldDestroy();
		else super.onDestroy();
	}

	private void onSubmit() {
		int i = 0;
		int success = 0;
		for (TestItemModel tim : list) {
			boolean correct;
			String text = tim.v == null ? "" :
					((TextView) tim.v.findViewById(R.id.test_name)).getText().toString();
			if (correct = test.isAnswer(i++, text))
				success++;
			tim.correct = correct;
			tim.answer = text;
			tim.v = null;
		}
		((MainChapter) list.get(0).sp.srcPath.get(0)).save();
		new TestResultsPopup(list, success * 100f / list.size());
		reset();
	}

	private static class Adapter extends ArrayAdapter<TestItemModel> {
		private LayoutInflater li;

		Adapter() {
			super(taInstance.getApplicationContext(), picTest ? R.layout.item_test_pic
					: R.layout.item_test_word, R.id.test_name, list);
		}

		@Override
		public View getView(int pos, @Nullable View v, @NonNull ViewGroup par) {
			TestItemModel item = list.get(pos);
			if (item.v == null) {
				item.v = v = li.inflate(picTest ?
						R.layout.item_test_pic : R.layout.item_test_word, par, false);
				if (picTest) {
					ImageView iv = item.v.findViewById(R.id.img_1);
					item.iim.setBm(true, iv);
					iv.setContentDescription(item.iim.pic1.toString());
					iv.setOnClickListener(view -> new FullPicture(item.iim.pic1));
					iv = item.v.findViewById(R.id.img_2);
					if (item.iim.pic2 != null) {
						item.iim.setBm(false, iv);
						iv.setContentDescription(item.iim.pic2.toString());
						iv.setOnClickListener(view -> new FullPicture(item.iim.pic2));
					} else iv.setVisibility(View.GONE);
				} else {
					StringBuilder trls = new StringBuilder();
					for (TwoSided trl : item.children)
						trls.append('\n').append(HierarchyItemModel.nameParser(trl.getName()));
					((TextView) v.findViewById(R.id.test_hint)).setText(trls.substring(1));
				}
			}
			return item.v;
		}
	}

	@Override
	public void onBackPressed() {
		if (clear()) return;
		if (System.currentTimeMillis() - backTime > 3000) {
			backTime = System.currentTimeMillis();
			Toast.makeText(getApplicationContext(), R.string.press_exit, Toast.LENGTH_SHORT).show();
		} else reset();
	}

}
