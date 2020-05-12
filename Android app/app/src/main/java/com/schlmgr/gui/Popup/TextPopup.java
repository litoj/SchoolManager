package com.schlmgr.gui.popup;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.ViewGroup;
import android.widget.TextView;

import com.schlmgr.R;

import static com.schlmgr.gui.Controller.activity;

public class TextPopup extends AbstractPopup {

	final String msg, fullMsg;

	public TextPopup(String msg, String fullMsg) {
		super(R.layout.popup_text);
		this.msg = msg;
		this.fullMsg = fullMsg;
		create();
	}

	@Override
	protected void addContent(ViewGroup view) {
		((TextView) view.findViewById(R.id.text)).setText(msg);
		view.findViewById(R.id.copy).setOnClickListener(x -> {
			((ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE))
					.setPrimaryClip(ClipData.newPlainText(msg, fullMsg));
			dismiss();
		});
		view.findViewById(R.id.ok).setOnClickListener(x -> dismiss());
	}
}
