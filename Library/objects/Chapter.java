package objects;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import IOSystem.Formatter.Data;
import IOSystem.ReadElement;
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
	 * The head hierarchy object which this object belongs to.
	 */
	protected final MainChapter identifier;

	@Override
	public MainChapter getIdentifier() {
		return identifier;
	}

	@Override
	public boolean destroy(Container parent) {
		ELEMENTS.get(identifier).remove(this);
		return super.destroy(parent);
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
