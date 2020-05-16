package com.schlmgr.gui.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout.LayoutParams;
import android.widget.ImageSwitcher;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.TextView.BufferType;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.schlmgr.R;
import com.schlmgr.gui.AndroidIOSystem;
import com.schlmgr.gui.Controller;
import com.schlmgr.gui.Controller.ControlListener;
import com.schlmgr.gui.activity.SelectItemsActivity;
import com.schlmgr.gui.activity.TestActivity;
import com.schlmgr.gui.list.SearchItemModel;

import java.util.ArrayList;
import java.util.List;

import IOSystem.Formatter;
import objects.MainChapter;
import objects.Picture;
import objects.templates.TwoSided;
import testing.Test;

import static com.schlmgr.gui.Controller.dp;

public class TestFragment extends Fragment implements ControlListener {

	public static Thread control;
	public static EditText amount;
	public static EditText time;

	public static boolean picTest;
	public static final List<SearchItemModel> list = new ArrayList<>();
	public static Adapter adapter;

	public View onCreateView(@NonNull LayoutInflater inflater,
	                         ViewGroup container, Bundle savedInstanceState) {
		View root = inflater.inflate(R.layout.fragment_test, container, false);
		if (!Controller.isActive(this)) {
			adapter = new Adapter();
			list.clear();
			SelectItemsActivity.backLog = null;
		}
		ImageSwitcher type = root.findViewById(R.id.test_type);
		type.setFactory(() -> {
			ImageView iv = new ImageView(getContext());
			iv.setLayoutParams(new LayoutParams((int) (40 * dp), (int) (40 * dp)));
			return iv;
		});
		type.setImageResource((picTest = (Boolean) Formatter.getSetting("testTypePicture"))
				? R.drawable.ic_image : R.drawable.ic_word);
		type.setOnClickListener(v -> {
			type.setImageResource((picTest = !picTest)
					? R.drawable.ic_image : R.drawable.ic_word);
			int i = 0;
			for (SearchItemModel sim : list.toArray(new SearchItemModel[0])) {
				if (sim.bd instanceof TwoSided && sim.bd instanceof Picture != picTest) list.remove(i);
				else i++;
			}
		});
		(amount = root.findViewById(R.id.test_amount))
				.setText("" + Test.getAmount(), BufferType.EDITABLE);
		amount.setOnFocusChangeListener((v, hasFocus) -> {
			if (!hasFocus) {
				if (amount.getText().toString().isEmpty())
					amount.setText("" + Test.getAmount(), BufferType.EDITABLE);
				AndroidIOSystem.hideKeyboardFrom(v);
			}
		});
		(time = root.findViewById(R.id.test_time))
				.setText("" + Test.getDefaultTime(), BufferType.EDITABLE);
		time.setOnFocusChangeListener((v, hasFocus) -> {
			if (!hasFocus) {
				if (time.getText().toString().isEmpty())
					time.setText("" + Test.getDefaultTime(), BufferType.EDITABLE);
				AndroidIOSystem.hideKeyboardFrom(v);
			}
		});
		((ListView) root.findViewById(R.id.test_list)).setAdapter(adapter);
		root.findViewById(R.id.start).setOnClickListener(v -> {
			if (list.isEmpty()) return;
			if (control != null && control.isAlive()) try {
				control.join();
			} catch (Exception e) {
			}
			startActivity(new Intent(getContext(), TestActivity.class));
		});
		root.findViewById(R.id.add).setOnClickListener(v -> {
			for (SearchItemModel sim : list) if (sim.bd instanceof MainChapter) return;
			startActivity(new Intent(getContext(), SelectItemsActivity.class));
		});
		return root;
	}

	public class Adapter extends ArrayAdapter<SearchItemModel> {
		final LayoutInflater li;

		Adapter() {
			super(TestFragment.this.getContext(), R.layout.item_add_test, R.id.item_name, list);
			li = LayoutInflater.from(getContext());
			notifyDataSetChanged();
		}

		@Override
		public View getView(int index, @Nullable View view, @NonNull ViewGroup parent) {
			view = li.inflate(R.layout.item_add_test, parent, false);
			SearchItemModel item = list.get(index);
			TextView tv = view.findViewById(R.id.item_name);
			tv.setText(item.bd.getName());
			if (item.bd instanceof Picture)
				item.ic.setBounds((int) dp, 0, (int) (dp * 33), (int) (dp * 33));
			else item.ic.setBounds(0, 0, (int) (dp * 30), (int) (dp * 30));
			tv.setCompoundDrawablesRelative(item.ic, null, null, null);
			view.findViewById(R.id.btn_remove).setOnClickListener(v -> {
				list.remove(index);
				notifyDataSetChanged();
			});
			return view;
		}
	}

	@Override
	public void onResume() {
		Controller.setCurrentControl(this, 0, false, false);
		adapter.notifyDataSetChanged();
		super.onResume();
	}
}