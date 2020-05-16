package com.schlmgr.gui.list;

import android.graphics.drawable.Drawable;

import com.schlmgr.R;
import com.schlmgr.gui.Controller;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import IOSystem.Formatter;
import objects.MainChapter;
import objects.Picture;
import objects.Reference;
import objects.Word;
import objects.templates.BasicData;
import objects.templates.Container;
import objects.templates.TwoSided;
import testing.NameReader;

public class HierarchyItemModel {

	public static List<HierarchyItemModel> convert(BasicData[] content, Container parent) {
		return convert(new ArrayList<>(Arrays.asList(content)), parent);
	}

	public static <T extends BasicData> List<HierarchyItemModel>
	convert(List<T> list, Container parent) {
		List<HierarchyItemModel> ret = new ArrayList<>(list.size());
		int pos = 1;
		for (int i = 0; i < list.size(); i++) {
			BasicData bd = list.get(i);
			if (bd instanceof Container && !(bd instanceof TwoSided))
				ret.add(new HierarchyItemModel(list.remove(i--), parent, pos++));
		}
		for (int i = 0; i < list.size(); i++)
			if (list.get(i) instanceof TwoSided)
				ret.add(new HierarchyItemModel(list.remove(i--), parent, pos++));
		for (BasicData bd : list) ret.add(new HierarchyItemModel(bd, parent, pos++));
		return ret;
	}

	public static boolean parse = true;
	public static boolean defFlip = true;
	public static boolean flipAllOnClick;

	public static void setParse(boolean on) {
		Formatter.putSetting("HIMparse", parse = on);
	}

	public static void setDefFlip(boolean on) {
		Formatter.putSetting("HIMflip", defFlip = on);
	}

	public static void setFlipAllOnClick(boolean on) {
		Formatter.putSetting("HIMflipAll", flipAllOnClick = on);
	}

	public boolean flipped;
	public final int position;
	boolean selected;

	public void setSelected(boolean isSelected) {
		selected = isSelected;
	}

	public boolean isSelected() {
		return selected;
	}

	public BasicData bd;
	public Container parent;
	public Drawable ic;
	public String toShow;
	String info = "";

	public void flip() {
		if (!(bd instanceof Word)) return;
		if (flipped = !flipped) toShow = translates();
		else {
			toShow = nameParser(bd.getName());
			info = bd.getDesc(parent);
		}
	}

	private String translates() {
		StringBuilder desc = new StringBuilder();
		StringBuilder trls = new StringBuilder();
		for (BasicData trl : ((Word) bd).getChildren(parent)) {
			trls.append('\n').append(nameParser(trl.getName()));
			if (!trl.getDesc(parent).isEmpty())
				desc.append('\n').append(trl.getName()).append(':')
						.append(' ').append(trl.getDesc(parent));
		}
		info = desc.length() > 0 ? desc.substring(1) : "";
		return trls.substring(1);
	}

	public static String nameParser(String name) {
		if (parse && (name.contains("\\/") || name.contains(")") && name.contains("/"))) {
			String[] names = NameReader.readName(name);
			if (names.length == 1) name = names[0];
			else {
				StringBuilder sb = new StringBuilder();
				for (String s : names) sb.append(s.length() > 18 ? '\n' : '\\').append(s);
				name = sb.substring(1);
			}
		}
		return name;
	}

	public void setNew(BasicData item, Container parent) {
		setNew(item, parent, true);
	}

	public void setNew(BasicData item, Container parent, boolean flip) {
		bd = item;
		this.parent = parent;
		toShow = nameParser(bd.getName());
		flipped = false;
		if (bd instanceof Container) {
			if (bd instanceof TwoSided) {
				if (bd instanceof Picture)
					ic = Controller.activity.getResources().getDrawable(R.drawable.ic_image);
				else if (bd instanceof Word) {
					ic = Controller.activity.getResources().getDrawable(R.drawable.ic_word);
					flipped = !defFlip;
					if (flip) flip();
					else info = bd.getDesc(parent);
				}
			} else {
				if (bd instanceof MainChapter)
					ic = Controller.activity.getResources().getDrawable(R.drawable.ic_subject);
				else ic = Controller.activity.getResources().getDrawable(R.drawable.ic_chapter);
			}
		} else if (bd instanceof Reference)
			ic = Controller.activity.getResources().getDrawable(R.drawable.ic_ref);
	}

	public HierarchyItemModel(BasicData item, Container parent, int position) {
		this(item, parent, position, true);
	}

	public HierarchyItemModel(BasicData item, Container parent, int position, boolean flip) {
		this.position = position;
		setNew(item, parent, flip);
	}
}
