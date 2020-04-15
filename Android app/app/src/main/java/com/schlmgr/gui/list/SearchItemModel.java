package com.schlmgr.gui.list;

import com.schlmgr.gui.CurrentData.EasyList;

import objects.templates.BasicData;
import objects.templates.Container;

public class SearchItemModel extends HierarchyItemModel {
	public final EasyList<? extends BasicData> path;

	public SearchItemModel(BasicData item, EasyList<Container> path, int pos) {
		super(item, path.get(-1), pos, false);
		this.path = path;
	}

	@Override
	public void setNew(BasicData item, Container parent) {
		setNew(item, parent, false);
	}
}
