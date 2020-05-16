package com.schlmgr.gui.list;

import android.content.Context;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;

import java.util.List;

public abstract class AbstractContainerAdapter<T extends HierarchyItemModel> extends ArrayAdapter<T> {
	public final List<T> list;
	public int selected;
	public Runnable occ;
	public int ref;
	final boolean selectActivity;

	public AbstractContainerAdapter(@NonNull Context context, int resource, int textViewResourceId,
	                                @NonNull List<T> objects, Runnable occ, boolean sA) {
		super(context, resource, textViewResourceId, objects);
		list = objects;
		this.occ = occ;
		selected = (selectActivity = sA) ? 0 : -1;
		for (HierarchyItemModel him : objects) {
			if (him.isSelected()) {
				if (selected == -1) selected += 2;
				else selected++;
			}
		}
	}
}
