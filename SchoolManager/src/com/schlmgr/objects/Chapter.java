package com.schlmgr.objects;

import com.schlmgr.IOSystem.Formatter.Data;

import java.util.List;

import com.schlmgr.objects.templates.BasicData;
import com.schlmgr.objects.templates.Container;

/**
 * Stores other {@link BasicData hierarchy objects}.
 *
 * @author Josef Litoš
 */
public class Chapter extends com.schlmgr.objects.templates.SemiElementContainer {

	/**
	 * Contains all instances of this class created. All Chapters are sorted by
	 * the {@link MainChapter hierarchy} they belong to. read-only data
	 */
	public static final java.util.Map<MainChapter, List<Chapter>> ELEMENTS = new java.util.HashMap<>();

	/**
	 * The head hierarchy object which this object belongs to.
	 */
	protected final MainChapter identifier;

	@Override
	public MainChapter getIdentifier() {
		return identifier;
	}

	/**
	 * @param d must contain {@link #name name} and
	 *          {@link #identifier identifier} and mainly the parent of this object
	 */
	public Chapter(Data d) {
		super(d);
		parent = d.par;
		identifier = d.identifier;
		if (ELEMENTS.get(identifier) == null) ELEMENTS.put(identifier, new java.util.LinkedList<>());
		ELEMENTS.get(identifier).add(this);
	}

	@Override
	public boolean destroy(Container parent) {
		ELEMENTS.get(identifier).remove(this);
		return super.destroy(parent);
	}

	@Override
	public StringBuilder writeData(StringBuilder sb, int tabs, Container cp) {
		tabs(sb, tabs++, '{').add(sb, this, cp, true, true, true, true, null, null, true);
		return writeData0(sb, tabs, cp);
	}

	/**
	 * Implementation of
	 * {@link com.schlmgr.IOSystem.ReadElement#readData(com.schlmgr.IOSystem.ReadElement.Source, com.schlmgr.objects.templates.Container) loading from String}.
	 */
	public static BasicData readData(com.schlmgr.IOSystem.ReadElement.Source src, Container parent) {
		Chapter ch = new Chapter(com.schlmgr.IOSystem.ReadElement.get(src, true, true, true, true, parent));
		for (BasicData bd : com.schlmgr.IOSystem.ReadElement.loadChildren(src, ch))
			ch.putChild(parent, bd);
		return ch;
	}
}
