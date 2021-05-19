package com.schlmgr.gui.popup;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.schlmgr.R;
import com.schlmgr.gui.list.HierarchyItemModel;
import com.schlmgr.gui.list.TestItemModel;

import java.util.ArrayList;
import java.util.List;

import objects.Picture;
import objects.templates.TwoSided;

import static com.schlmgr.gui.Controller.activity;

public class TestResultsPopup extends AbstractPopup {
	private final List<TestItemModel> list;
	private final boolean picTest;
	private final float success;

	public TestResultsPopup(ArrayList<TestItemModel> items, float success) {
		super(R.layout.popup_test_results, true);
		list = (List<TestItemModel>) items.clone();
		picTest = items.get(0).sp.t instanceof Picture;
		this.success = success;
		create();
	}

	@Override
	protected void addContent(ViewGroup view) {
		view.findViewById(R.id.ok).setOnClickListener(v -> dismiss());
		((TextView) view.findViewById(R.id.popup_test_success))
				.setText(activity.getString(R.string.success_rate) + ": " + success + "%");
		RecyclerView rv = view.findViewById(R.id.popup_test_list);
		rv.setAdapter(new Adapter());
		rv.setLayoutManager(new LinearLayoutManager(rv.getContext()));
	}

	private abstract class ResultHolder extends RecyclerView.ViewHolder {

		final View view;
		final TextView name;
		final TextView written;

		protected ResultHolder(@NonNull View itemView) {
			super(itemView);
			view = itemView;
			name = view.findViewById(R.id.test_name);
			written = view.findViewById(R.id.test_name_written);
		}

		protected void setData(TestItemModel item) {
			name.setText(item.sp.t.getName());
			if (!item.answer.isEmpty()) {
				written.setText(item.answer);
				written.setTextColor(item.correct ? 0xFF00AA00 : 0xFFAA0000);
			} else written.setText(null);
		}
	}

	private class ImageHolder extends ResultHolder {
		final ImageView img1;
		final ImageView img2;

		private ImageHolder(@NonNull View itemView) {
			super(itemView);
			img1 = view.findViewById(R.id.img_1);
			img2 = view.findViewById(R.id.img_2);
		}

		@Override
		protected void setData(TestItemModel item) {
			super.setData(item);
			item.iim.iv1 = img1;
			item.iim.setBm(true, img1);
			img1.setContentDescription(item.iim.pic1.toString());
			img1.setOnClickListener(view -> new FullPicture(item.iim.pic1));
			if (item.iim.pic2 != null) {
				item.iim.iv2 = img2;
				item.iim.setBm(false, img2);
				img2.setContentDescription(item.iim.pic2.toString());
				img2.setOnClickListener(view -> new FullPicture(item.iim.pic2));
			} else img2.setVisibility(View.GONE);
		}
	}

	private class TranslateHolder extends ResultHolder {
		final TextView hint;

		private TranslateHolder(@NonNull View itemView) {
			super(itemView);
			hint = view.findViewById(R.id.test_hint);
		}

		@Override
		protected void setData(TestItemModel item) {
			super.setData(item);
			StringBuilder trls = new StringBuilder();
			for (TwoSided trl : item.children)
				trls.append('\n').append(HierarchyItemModel.nameParser(trl.getName()));
			hint.setText(trls.substring(1));
		}
	}

	private class Adapter extends RecyclerView.Adapter<ResultHolder> {

		@NonNull
		@Override
		public ResultHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
			return picTest ? new ImageHolder(LayoutInflater.from(parent.getContext())
					.inflate(R.layout.item_results_pic, parent, false))
					: new TranslateHolder(LayoutInflater.from(parent.getContext())
					.inflate(R.layout.item_results_word, parent, false));
		}

		@Override
		public void onBindViewHolder(@NonNull ResultHolder holder, int position) {
			holder.setData(list.get(position));
		}

		@Override
		public int getItemCount() {
			return list.size();
		}
	}
}
