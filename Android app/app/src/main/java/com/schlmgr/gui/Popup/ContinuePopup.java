package com.schlmgr.gui.popup;

import android.view.ViewGroup;
import android.widget.TextView;

import com.schlmgr.R;

public class ContinuePopup extends AbstractPopup {

	final String msg;
	final Thread onContinue;

	public ContinuePopup(String msg, Runnable onClick) {
		super(R.layout.popup_continue, true);
		this.msg = msg;
		onContinue = new Thread(onClick, "onContinuePopup: \"" + msg + '"');
		create();
	}

	@Override
	protected void addContent(ViewGroup view) {
		((TextView) view.findViewById(R.id.text)).setText(msg);
		view.findViewById(R.id.cancel).setOnClickListener(x -> dismiss());
		view.findViewById(R.id.ok).setOnClickListener(x -> {
			dismiss();
			onContinue.start();
		});
	}
}