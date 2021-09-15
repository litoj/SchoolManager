package com.schlmgr.gui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.NumberPicker;
import android.widget.TextView.BufferType;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.schlmgr.R;
import com.schlmgr.gui.AndroidIOSystem;
import com.schlmgr.gui.Controller;
import com.schlmgr.gui.Controller.ControlListener;
import com.schlmgr.gui.list.HierarchyItemModel;

import IOSystem.Formatter;
import IOSystem.SimpleWriter;
import testing.Test;

public class SettingsFragment extends Fragment implements ControlListener {

	private NumberPicker wordSplit;
	private EditText amount;
	private EditText time;

	public View onCreateView(@NonNull LayoutInflater inflater,
													 ViewGroup container, Bundle savedInstanceState) {
		View root = inflater.inflate(R.layout.fragment_settings, container, false);

		CompoundButton parse = root.findViewById(R.id.setts_him_parse);
		parse.setChecked(HierarchyItemModel.parse);
		parse.setOnClickListener(v -> HierarchyItemModel.setParse(parse.isChecked()));

		CompoundButton defFlip = root.findViewById(R.id.setts_him_flipped);
		defFlip.setChecked(HierarchyItemModel.defFlip);
		defFlip.setOnClickListener(v -> HierarchyItemModel.setDefFlip(defFlip.isChecked()));

		CompoundButton flipAll = root.findViewById(R.id.setts_him_allflip);
		flipAll.setChecked(HierarchyItemModel.flipAllOnClick);
		flipAll.setOnClickListener(v -> HierarchyItemModel.setFlipAllOnClick(flipAll.isChecked()));

		CompoundButton position = root.findViewById(R.id.setts_choose_pos);
		position.setChecked((Boolean) Formatter.getSetting("doChoosePos"));
		position.setOnClickListener(v -> Formatter.putSetting("doChoosePos", position.isChecked()));

		CompoundButton desc = root.findViewById(R.id.setts_show_desc);
		desc.setChecked(HierarchyItemModel.show_desc);
		desc.setOnClickListener(v -> HierarchyItemModel.setShowDesc(desc.isChecked()));

		wordSplit = root.findViewById(R.id.setts_word_splitter);
		wordSplit.setMinValue(0);
		wordSplit.setMaxValue(3);
		wordSplit.setDisplayedValues(new String[]{"';'", "'='", "' = '", "' → '"});
		wordSplit.setValue(getWordSplit(SimpleWriter.getWordSplitter()));

		amount = root.findViewById(R.id.setts_test_amount);
		amount.setText("" + Test.getAmount(), BufferType.EDITABLE);
		amount.setOnFocusChangeListener((v, hasFocus) -> {
			if (!hasFocus) {
				if (amount.getText().toString().isEmpty())
					amount.setText("" + Test.getAmount(), BufferType.EDITABLE);
				AndroidIOSystem.hideKeyboardFrom(v);
			}
		});

		time = root.findViewById(R.id.setts_test_time);
		time.setText("" + Test.getDefaultTime(), BufferType.EDITABLE);
		time.setOnFocusChangeListener((v, hasFocus) -> {
			if (!hasFocus) {
				if (time.getText().toString().isEmpty())
					time.setText("" + Test.getDefaultTime(), BufferType.EDITABLE);
				AndroidIOSystem.hideKeyboardFrom(v);
			}
		});

		CompoundButton clever = root.findViewById(R.id.setts_test_clever);
		clever.setChecked(Test.isClever());
		clever.setOnClickListener(v -> Test.setClever(clever.isChecked()));

		CompoundButton testPic = root.findViewById(R.id.setts_test_type);
		testPic.setChecked((Boolean) Formatter.getSetting("defaultTestTypePicture"));
		testPic.setOnClickListener(v ->
				Formatter.putSetting("defaultTestTypePicture", testPic.isChecked()));
		return root;
	}

	public static int getWordSplit(String wordSplitter) {
		switch (wordSplitter) {
			case "=":
				return 1;
			case " = ":
				return 2;
			case " → ":
				return 3;
			case ";":
			default:
				return 0;
		}
	}

	public static String getWordSplit(int wordSplitter) {
		switch (wordSplitter) {
			case 1:
				return "=";
			case 2:
				return " = ";
			case 3:
				return " → ";
			case 0:
			default:
				return ";";
		}
	}

	@Override
	public void onDestroy() {
		Test.setAmount(Integer.parseInt(amount.getText().toString()));
		Test.setDefaultTime(Integer.parseInt(time.getText().toString()));
		SimpleWriter.setWordSplitter(getWordSplit(wordSplit.getValue()));
		super.onDestroy();
	}

	@Override
	public void onResume() {
		Controller.setCurrentControl(this, 0, false, false);
		super.onResume();
	}
}