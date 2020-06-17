package objects;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import IOSystem.Formatter.Data;
import IOSystem.ReadElement;
import java.util.Arrays;
import objects.templates.BasicData;
import objects.templates.Container;

/**
 * Stores other {@link BasicData hierarchy objects}.
 * One of the simplest objects of the database hierarchy.
 *
 * @author Josef Litoš
 */
public class Chapter extends objects.templates.SemiElementContainer {

	/**
	 * Contains all instances of this class created. All Chapters are sorted by
	 * the {@link MainChapter hierarchy} they belong to. read-only data
	 */
	public static final Map<MainChapter, List<Chapter>> ELEMENTS = new HashMap<>();

	/**
	 * @param d must contain {@link #name name} and {@link #identifier identifier}
	 *          and mainly the parent of this object
	 */
	public Chapter(Data d) {
		super(d);
		parent = d.par;
		identifier = d.identifier;
		if (ELEMENTS.get(identifier) == null) ELEMENTS.put(identifier, new LinkedList<>());
		ELEMENTS.get(identifier).add(this);
	}
	
	/**
	 * Converts this object to a Chapter object, its file is deleted.
	 * 
	 * @return the converted object
	 */
	@Override
	public SaveChapter convert() {
		SaveChapter ch = SaveChapter.mkElement(new Data(name, identifier).addSF(sf)
				.addDesc(description).addPar(parent));
		int moved = 0;
		BasicData[] children = getChildren();
		for (; moved < children.length; moved++) {
			if (!children[moved].move(this, parent, ch, parent)) break;
		}
		if (moved < children.length) {
			for (; moved >= 0; moved--) {
				children[moved].move(ch, parent, this, parent);
			}
			this.children.clear();
			this.children.addAll(Arrays.asList(children));
			return null;
		}
		parent.replaceChild(null, this, ch);
		ELEMENTS.get(identifier).remove(this);
		return ch;
	}

	/**
	 * The head hierarchy object which this object belongs to.
	 */
	protected final MainChapter identifier;

	@Override
	public MainChapter getIdentifier() {
		return identifier;
	}

	@Override
	public boolean destroy(Container parent) {
		if (parent != this.parent && parent != null) return false;
		ELEMENTS.get(identifier).remove(this);
		return super.destroy(this.parent);
	}

	@Override
	public ContentWriter writeData(ContentWriter cw) {
		return cw.addClass().addName().addSF().addDesc().addChildren();
	}

	/**
	 * Implementation of
	 * {@link ReadElement#readData(ReadElement.Content, Container) loading from String}.
	 */
	public static BasicData readData(ReadElement.Content src, Container parent) {
		Chapter ch = new Chapter(src.getData(parent));
		for (BasicData bd : src.getChildren(ch)) ch.putChild(parent, bd);
		return ch;
	}
}
