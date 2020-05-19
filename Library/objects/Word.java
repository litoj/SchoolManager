package objects;

import IOSystem.Formatter;
import IOSystem.Formatter.Data;
import IOSystem.ReadElement;
import objects.templates.BasicData;
import objects.templates.Container;
import objects.templates.TwoSided;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Contains name and translates for its name. The translates are sorted under
 * the given {@link Chapter}, which they belong to.
 *
 * @author Josef Litoš
 */
public class Word extends TwoSided<Word> {

	/**
	 * Contains all instances of this class created as the {@link #isMain main} version.
	 * All Words are sorted by the {@link MainChapter hierarchy} they belong to.
	 * read-only data
	 */
	public static final Map<MainChapter, List<Word>> TRANSLATES = new HashMap<>();

	/**
	 * Contains all instances of this class created as the {@link #isMain non-main}
	 * version. All Translates are sorted by the {@link MainChapter hierarchy} they belong
	 * to. read-only data
	 */
	public static final Map<MainChapter, List<Word>> ELEMENTS = new HashMap<>();
	
	private static final Formatter.Synchronizer USED = new Formatter.Synchronizer();

	/**
	 * The only allowed way to create Word objects. Automatically controls its
	 * existence and returns the proper Word.
	 *
	 * @param d         all the necessary data to create new {@link Word} object
	 * @param translates translates for this object under one {@link Chapter} must contain
	 *                   their {@link Data#name name} each, the list can lose its content
	 * @return new {@link #Word(Data, Word) Word object} if the word doesn't exist yet,
	 * otherwise returns the word object with the same name and adds the new translations.
	 */
	public static Word mkElement(Data d, List<Data> translates) {
		if (translates == null || translates.isEmpty()) throw new NullPointerException();
		if (ELEMENTS.get(d.identifier) == null) {
			ELEMENTS.put(d.identifier, new LinkedList<>());
			TRANSLATES.put(d.identifier, new LinkedList<>());
		}
		USED.waitForAccess(d.identifier);
		for (Word w : ELEMENTS.get(d.identifier)) {
			if (d.name.equals(w.name)) {
				if (w.children.get(d.par) == null) {
					w.children.put(d.par, new LinkedList<>());
					w.parentCount++;
				}
				if (d.description != null && !d.description.isEmpty())
					w.putDesc(d.par, d.description);
				w.addTranslates(translates, d.par);
				USED.endAccess(d.identifier);
				return w;
			}
		}
		Word ret = new Word(d, translates);
		USED.endAccess(d.identifier);
		return ret;
	}

	/**
	 * Creates a word translate, the other part of Word class. Connects with give Word.
	 * 
	 * @param d		data necessary for creating a translate, must contain parent
	 * @param main	the word to connect to
	 * @return the created translate
	 */
	public static Word mkTranslate(Data d, Word main) {
		USED.waitForAccess(main.identifier);
		for (Word t : TRANSLATES.get(d.identifier)) {
			if (d.name.equals(t.name)) {
				if (t.children.get(d.par) != null) {
					for (BasicData w : t.children.get(d.par)) if (w == main) return t;
				} else {
					t.parentCount++;
					t.children.put(d.par, new LinkedList<>());
				}
				t.sf[0] += main.sf[0];
				t.sf[1] += main.sf[1];
				if (d.description != null && !d.description.isEmpty())
					t.putDesc(d.par, d.description);
				t.children.get(d.par).add(main);
				main.children.get(d.par).add(t);
				USED.endAccess(main.identifier);
				return t;
			}
		}
		d.sf = main.sf.clone();
		Word w = new Word(d, main);
		main.children.get(d.par).add(w);
		USED.endAccess(main.identifier);
		return w;
	}
	
	/**
	 * Creates all children of this object. Checks for potential doubling of
	 * translates.
	 *
	 * @param translates all the necessary data for every new translate created
	 * @param parent     Chapter containing this word
	 */
	private void addTranslates(List<Data> translates, Container parent) {
		for (Word t : TRANSLATES.get(identifier)) {
			for (int i = translates.size() - 1; i >= 0; i--) {
				condition:
				if (translates.get(i).name.equals(t.name)) {
					if (t.children.get(parent) != null) {
						for (BasicData w : t.children.get(parent)) if (w == this) {
							translates.remove(i);
							break condition;
						}
					} else {
						t.parentCount++;
						t.children.put(parent, new LinkedList<>());
					}
					Data d = translates.get(i);
					t.sf[0] += sf[0];
					t.sf[1] += sf[1];
					if (d.description != null && !d.description.isEmpty())
						t.putDesc(d.par, d.description);
					t.children.get(parent).add(this);
					children.get(parent).add(t);
					translates.remove(i);
				}
			}
		}
		for (Data d : translates) {
			d.sf = sf.clone();
			children.get(parent).add(new Word(d, this));
		}
	}

	/**
	 * This constructor is used only to create translates.
	 */
	private Word(Data bd, Word word) {
		super(bd, false, TRANSLATES);
		children.put(bd.par, new LinkedList<>(Arrays.asList(word)));
	}

	/**
	 * This constructor is used only to create main instance of this class.
	 */
	private Word(Data bd, List<Data> translates) {
		super(bd, true, ELEMENTS);
		children.put(bd.par, new LinkedList<>());
		addTranslates(translates, bd.par);
	}

	@Override
	public BasicData setName(Container ch, String name) {
		if (this.name.equals(name) || children.isEmpty()) return this;
		USED.waitForAccess(identifier);
		for (Word w : (isMain ? ELEMENTS : TRANSLATES)
				.get(identifier).toArray(new Word[0]))
			if (w.name.equals(name)) {
				if (w.getDesc(ch) == null || w.getDesc(ch).equals(""))
					w.putDesc(ch, getDesc(ch));
				if (parentCount == 1)
					(isMain ? ELEMENTS : TRANSLATES).get(identifier).remove(this);
				setName0(isMain ? ch.removeChild(this) : null, ch, w);
				USED.endAccess(identifier);
				return w;
			}
		Word ret;
		if (children.keySet().size() == 1) {
			ret = this;
			this.name = name;
		}
		else setName0(isMain ? ch.removeChild(this) : null, ch,
				ret = new Word(this, ch, name));
		USED.endAccess(identifier);
		return ret;
	}

	private Word(Word src, Container par, String newName) {
		super(new Data(newName, src.identifier)
				.addSF(new int[]{0, 0}).addDesc(src.description.get(par))
				.addPar(par), src.isMain, src.isMain ? ELEMENTS : TRANSLATES);
	}

	private void setName0(Container parpar, Container ch, Word w) {
		parentCount--;
		if (w.children.get(ch) == null) {
			if(isMain) ch.putChild(parpar, w);
			w.children.put(ch, children.get(ch));
			w.parentCount++;
		} else {
			List<Word> toAdd = new LinkedList<>();
			for(Word word : children.get(ch)){
				test:
				{
					for(Word child : w.children.get(ch)) if(child == word) break test;
					toAdd.add(word);
				}
			}
			w.children.get(ch).addAll(toAdd);
		}
		for (Word trl : children.remove(ch)) {
			trl.children.get(ch).remove(this);
			trl.children.get(ch).add(w);
		}
	}

	@Override
	public boolean destroy(Container parent) {
		if (isMain) {
			for (TwoSided t : children.remove(parent)) {
				((Word) t).remove1(parent, this);
				t.destroy(parent);
			}
			parent.removeChild(this);
			if (--parentCount == 0) {
				USED.waitForAccess(identifier);
				ELEMENTS.get(identifier).remove(this);
				USED.endAccess(identifier);
			}
		} else if (children.get(parent) == null && --parentCount == 0) {
			USED.waitForAccess(identifier);
			TRANSLATES.get(identifier).remove(this);
			USED.endAccess(identifier);
		}
		return true;
	}

	/**
	 * Implementation of
	 * {@link ReadElement#readData(ReadElement.Source, Container) loading from String}.
	 */
	public static BasicData readData(ReadElement.Source src, Container parent) {
		Data data = ReadElement.get(src, true, true, true, true, parent);
		List<Data> children = ReadElement.readChildren(src, true, false, true, parent);
		return mkElement(data, children);
	}
}
