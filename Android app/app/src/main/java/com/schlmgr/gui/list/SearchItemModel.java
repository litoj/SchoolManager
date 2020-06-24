package com.schlmgr.gui.list;

import com.schlmgr.gui.CurrentData.EasyList;

import java.util.List;

import objects.Word;
import objects.templates.BasicData;
import objects.templates.Container;

public class SearchItemModel extends HierarchyItemModel {
	public final EasyList<Container> path;

	public SearchItemModel(BasicData item, EasyList<Container> path, int pos) {
		super(item, path.get(-1), pos, false);
		flipped = false;
		this.path = path;
	}

	@Override
	public void setNew(BasicData item, Container parent) {
		setNew(item, parent, false);
	}

	public void setNew(BasicData item, List<Container> path) {
		this.path.clear();
		this.path.addAll(path);
		setNew(item, this.path.get(-1), false);
	}

	@Override
	protected String translates() {
		StringBuilder desc = new StringBuilder();
		StringBuilder trls = new StringBuilder();
		for (BasicData trl : ((Word) bd).getChildren()) {
			trls.append('\n').append(nameParser(trl.getName()));
			if (!trl.getDesc(parent).isEmpty())
				desc.append('\n').append(trl.getName()).append(':')
						.append(' ').append(trl.getDesc(parent));
		}
		info = desc.length() > 0 ? desc.substring(1) : "";
		return trls.substring(1);
	}
}
