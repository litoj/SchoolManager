package com.schlmgr.gui.popup;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.schlmgr.R;
import com.schlmgr.gui.list.HierarchyItemModel;
import com.schlmgr.gui.list.ImageItemModel;

import java.util.List;

import objects.Picture;
import objects.templates.BasicData;
import objects.templates.Container;
import objects.templates.TwoSided;

import static com.schlmgr.gui.Controller.activity;

public class TestResultsPopup extends AbstractPopup {
	private final List<TestedItem> list;
	private final boolean picTest;
	private final float success;

	public TestResultsPopup(List<TestedItem> items, float success) {
		super(R.layout.popup_test_results);
		list = items;
		picTest = items.get(0).ts instanceof Picture;
		this.success = success;
		create();
	}

	@Override
	protected void addContent(ViewGroup view) {
		view.findViewById(R.id.ok).setOnClickListener(v -> dismiss());
		((TextView) view.findViewById(R.id.popup_test_success)).setText(activity.getString(R.string.success_rate) + ": " + success + "%");
		LayoutInflater li = LayoutInflater.from(view.getContext());
		((ListView) view.findViewById(R.id.popup_test_list)).setAdapter(
				new ArrayAdapter<TestedItem>(view.getContext(), picTest ? R.layout.item_results_pic :
						R.layout.item_results_word, R.id.test_name, list) {

					@Override
					public View getView(int pos, @Nullable View v, @NonNull ViewGroup par) {
						TestedItem item = list.get(pos);
						if (item.v == null) {
							item.v = v = li.inflate(picTest ? R.layout.item_results_pic : R.layout.item_results_word, par, false);
							if (picTest) {
								BasicData[] pics = item.ts.getChildren(item.par);
								for (int i = 1; i < pics.length; i += 2) {
									ImageItemModel iim = new ImageItemModel((Picture) pics[i - 1], (Picture) pics[i]);
									View vImg = li.inflate(R.layout.item_test_image, (LinearLayout) v, false);
									((LinearLayout) v).addView(vImg, 0);
									ImageView iv = vImg.findViewById(R.id.img_1);
									iv.setOnClickListener(view -> new FullPicture(iim.pic1));
									iv.setImageBitmap(iim.getBitmap(true));
									iv.setContentDescription(iim.pic1.toString());
									(iv = vImg.findViewById(R.id.img_2)).setImageBitmap(iim.getBitmap(false));
									iv.setContentDescription(iim.pic2.toString());
									iv.setOnClickListener(view -> new FullPicture(iim.pic2));
								}
								if (pics.length % 2 == 1) {
									ImageItemModel iim = new ImageItemModel((Picture) pics[pics.length - 1], null);
									View vImg = li.inflate(R.layout.item_test_image, (LinearLayout) v, false);
									((LinearLayout) v).addView(vImg, 0);
									ImageView iv = vImg.findViewById(R.id.img_1);
									iv.setOnClickListener(view -> new FullPicture(iim.pic1));
									iv.setImageBitmap(iim.getBitmap(true));
									iv.setContentDescription(iim.pic1.toString());
									vImg.findViewById(R.id.img_2).setVisibility(View.GONE);
								}
							} else {
								StringBuilder trls = new StringBuilder();
								for (BasicData trl : item.ts.getChildren(item.par))
									trls.append('\n').append(HierarchyItemModel.nameParser(trl.getName()));
								((TextView) v.findViewById(R.id.test_hint)).setText(trls.substring(1));
							}
							((TextView) v.findViewById(R.id.test_name)).setText(item.ts.toString());
							TextView written = v.findViewById(R.id.test_name_written);
							if (!item.text.isEmpty()) written.setText(item.text);
							if (!item.c) written.setTextColor(0x88FF0000);
						}
						return item.v;
					}
				});
	}

	public static class TestedItem {
		private View v;
		private final boolean c;
		private final TwoSided ts;
		private final Container par;
		private final String text;

		public TestedItem(boolean correct, TwoSided item, Container parrent, String written) {
			c = correct;
			ts = item;
			par = parrent;
			text = written;
		}
	}
}
