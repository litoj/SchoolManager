package cz.litoj.schlmgr.gui.list;

import cz.litoj.schlmgr.gui.CurrentData.EasyList;

import java.util.ArrayList;
import java.util.List;

import cz.litoj.schlmgr.gui.engine.objects.Word;
import cz.litoj.schlmgr.gui.engine.objects.templates.BasicData;
import cz.litoj.schlmgr.gui.engine.objects.templates.Container;

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
		List<BasicData> withDesc = new ArrayList<>();
		for (BasicData trl : ((Word) bd).getChildren()) {
			trls.append('\n').append(nameParser(trl.getName()));
			if (!trl.getDesc(parent).isEmpty()) withDesc.add(trl);
		}
		// Show the owning translate's name only when its description
		// would otherwise be ambiguous among multiple descriptions.
		boolean showNames = withDesc.size() > 1;
		for (BasicData trl : withDesc) {
			desc.append('\n');
			if (showNames) desc.append(trl.getName()).append(": ");
			desc.append(trl.getDesc(parent));
		}
		info = desc.length() > 0 ? desc.substring(1) : "";
		return trls.substring(1);
	}
}
