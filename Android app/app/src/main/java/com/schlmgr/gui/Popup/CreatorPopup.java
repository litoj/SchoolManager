package com.schlmgr.gui.popup;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.TextView;

import com.schlmgr.R;
import com.schlmgr.gui.fragments.MainFragment;
import com.schlmgr.gui.list.HierarchyAdapter;

import IOSystem.Formatter;

public class CreatorPopup extends AbstractPopup {

	public Button ok;
	public EditText et_name;
	public EditText et_desc;
	public ViewGroup view;
	public NumberPicker np;
	public View npLayout;

	final String type;
	final Includer toInclude;

	public CreatorPopup(String header, Includer toInclude) {
		super(R.layout.popup_creator, true);
		type = header;
		this.toInclude = toInclude;
		create();
	}

	@Override
	protected void addContent(ViewGroup view) {
		this.view = view;
		((TextView) view.findViewById(R.id.popup_new_type)).setText(type);
		String text = "";
		if (et_name != null) text = et_name.getText().toString();
		(et_name = view.findViewById(R.id.popup_new_name)).setText(text);
		if (et_desc != null) text = et_desc.getText().toString();
		(et_desc = view.findViewById(R.id.popup_new_desc)).setText(text);

		np = view.findViewById(R.id.popup_pos_choose);
		int max = MainFragment.VS.contentAdapter.list.size() + 1;
		np.setMaxValue(max);
		np.setMinValue(1);
		np.setValue(max);
		npLayout = (View) np.getParent();
		if ((Boolean) Formatter.getSetting("doChoosePos")
				&& MainFragment.VS.contentAdapter instanceof HierarchyAdapter) {
			npLayout.setVisibility(View.VISIBLE);
		}
		ok = view.findViewById(R.id.ok);
		view.findViewById(R.id.cancel).setOnClickListener(x -> dismiss());
		View include = toInclude.onInclude(LayoutInflater.from(view.getContext()), this);
		if (include != null) ((LinearLayout) et_desc.getParent()).addView(include, 2);
	}

	public interface Includer {
		View onInclude(LayoutInflater li, CreatorPopup cp);
	}
}
