package objects;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import IOSystem.Formatter;
import IOSystem.Formatter.Data;
import IOSystem.Formatter.Reactioner;
import IOSystem.Formatter.Synchronizer;
import static IOSystem.Formatter.defaultReacts;
import IOSystem.ReadElement;
import java.util.Arrays;
import java.util.Comparator;
import objects.templates.BasicData;
import objects.templates.Container;
import objects.templates.ContainerFile;
import objects.templates.SemiElementContainer;

/**
 * Contains other hierarchy objects. Every instance of this class saves into its
 * own file.
 *
 * @author Josef Litoš
 */
public class SaveChapter extends SemiElementContainer implements ContainerFile {

	/**
	 * Contains all instances of this class created. All SaveChapters are sorted
	 * by the {@link MainChapter hierarchy} they belong to. read-only data
	 */
	public static final Map<MainChapter, List<SaveChapter>> ELEMENTS = new HashMap<>();

	private static final Synchronizer USED = new Synchronizer();

	private boolean loaded;

	@Override
	public boolean isLoaded() {
		return loaded;
	}

	/**
	 * The head hierarchy object which this object belongs to.
	 */
	protected final MainChapter identifier;

	@Override
	public MainChapter getIdentifier() {
		return identifier;
	}

	private int hash;

	/**
	 * @param d necessary information to create the new object
	 * @return the created object
	 */
	public static final SaveChapter mkElement(Data d) {
		return mkElement(d, 2);
	}

	protected static final SaveChapter mkElement(Data d, int full) {
		USED.waitForAccess(d.identifier);
		SaveChapter ret;
		if (ELEMENTS.get(d.identifier) == null) {
			ELEMENTS.put(d.identifier, new java.util.LinkedList<>());
			if (d.identifier.getSetting("schRemoved") == null) {
				d.identifier.putSetting("schRemoved", false);
			}
		} else {
			int hash = d.tagVals == null || d.tagVals.get("hash") == null
					? 1 : (int) d.tagVals.get("hash");
			if (full == 2) {
				for (SaveChapter sch : ELEMENTS.get(d.identifier))
					if (d.name.equals(sch.name) && sch.hash >= hash) hash = sch.hash + 1;
				if (d.tagVals == null ) d.tagVals = new HashMap<>();
				d.tagVals.put("hash", hash);
			} else for (SaveChapter sch : ELEMENTS.get(d.identifier)) {
				if (d.name.equals(sch.toString()) && hash == sch.hash) {
					sch.loaded = full == 1;
					USED.endAccess(d.identifier);
					return sch;
				}
			}
		}
		ret = new SaveChapter(d, full != 0);
		USED.endAccess(d.identifier);
		return ret;
	}
	
	/**
	 * Converts this object to a Chapter object, its file is deleted.
	 * 
	 * @return the converted object
	 */
	@Override
	public Chapter convert() {
		Chapter ch = new Chapter(new Data(name, identifier).addSF(sf)
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
		destroy(null);
		return ch;
	}

	protected SaveChapter(Data d, boolean full) {
		super(d);
		identifier = d.identifier;
		ContainerFile.isCorrect(name);
		parent = d.par;
		loaded = full;
		//hash-creator
		hash = d.tagVals == null || d.tagVals.get("hash") == null
				? 1 : (int) d.tagVals.get("hash");
		if (full) {
			File dir = new File(identifier.getDir(), "Chapters");
			File src = getSaveFile();
			while (src.exists()) src = new File(dir, name + "[" + ++hash + "].json");
		}
		ELEMENTS.get(d.identifier).add(this);
	}

	@Override
	public int[] refreshSF() {
		load(false);
		return super.refreshSF();
	}

	@Override
	public boolean isEmpty(Container c) {
		if (c != parent) return true;
		if (children.isEmpty()) return loaded;
		return ContainerFile.super.isEmpty(c);
	}

	@Override
	public boolean hasChild(BasicData e) {
		if (!loaded) load(false);
		return super.hasChild(e);
	}

	@Override
	public boolean hasChild(Container par, BasicData e) {
		if (!loaded) load(false);
		return super.hasChild(par, e);
	}

	@Override
	public Container removeChild(BasicData e) {
		if (!loaded) load(false);
		Container ret = super.removeChild(e);
		if (children.isEmpty()) getSaveFile().delete();
		return ret;
	}

	@Override
	public boolean removeChild(Container c, BasicData e) {
		if (!loaded) load(false);
		boolean ret = super.removeChild(c, e);
		if (children.isEmpty()) getSaveFile().delete();
		return ret;
	}

	@Override
	public boolean putChild(Container c, BasicData e) {
		if (!loaded) load(false);
		return super.putChild(c, e);
	}

	@Override
	public BasicData[] getChildren() {
		if (!loaded) load(false);
		return super.getChildren();
	}

	@Override
	public BasicData[] getChildren(Container parent) {
		if (!loaded) load(false);
		return super.getChildren(parent);
	}

	private volatile boolean saving;

	@Override
	public void save(Reactioner rtr, boolean thread) {
		Runnable r = () -> {
			try {
				if (saving) return;
				saving = true;
				Formatter.saveFile(writeData(new ContentWriter().startWritingItem(
						this, parent)).endWritingItem().toString(), getSaveFile());
				saving = false;
			} catch (Exception e) {
				if (rtr != null) rtr.react(e, getSaveFile(), this);
			}
		};
		if (thread) new Thread(r, "SCh save").start();
		else r.run();
	}

	private volatile boolean loading;

	@Override
	public void load(Reactioner rtr, boolean thread) {
		if (!isLoaded()) {
			Runnable r = () -> {
				try {
					if (loading) return;
					loading = true;
					IOSystem.ReadElement.loadFile(getSaveFile(), identifier, null);
					loading = false;
				} catch (Exception e) {
					if (rtr != null) rtr.react(e, getSaveFile(), this);
				}
			};
			if (thread) new Thread(r, "SCh load").start();
			else r.run();
		}
	}

	/**
	 * Cleans the database numbering of SaveChapters. This is needed, when
	 * SaveChapters were removed and they weren't last of the given name.
	 *
	 * @param mch the hierarchy to be cleaned
	 */
	public static void clean(MainChapter mch) {
		if (!isCleanable(mch)) return;
		mch.load(false);
		File dir = new File(mch.getDir(), "Chapters");
		USED.waitForAccess(mch);
		boolean allok = true;
		for (SaveChapter sch : ELEMENTS.get(mch)) {
			if (sch.hash == 1) continue;
			int hash = 1;
			File src = new File(dir, sch.name + ".json");
			while (src.exists() && hash < sch.hash)
				src = new File(dir, sch.name + "[" + ++hash + "].json");
			File origin = sch.getSaveFile();
			if (hash < sch.hash && origin.exists() && !origin.renameTo(
				new File(dir, sch.name + (hash == 1 ? ".json" : "[" + hash + "].json")))) {
				allok = false;
				defaultReacts.get(ContainerFile.class + ":save").react(
						new IllegalArgumentException("File can't be renamed"), src, sch);
			} else sch.hash = hash;
		}
		USED.endAccess(mch);
		if (allok) mch.putSetting("schRemoved", false);
	}

	/**
	 * Tells, if the given hierarchy can get cleaned of SaveChapter file numbers.
	 *
	 * @param mch source
	 * @return {@code true} if an image has been deleted from the hierarchy
	 */
	public static boolean isCleanable(MainChapter mch) {
		return (Boolean) mch.getSetting("schRemoved");
	}

	@Override
	public File getSaveFile() {
		return new File(new File(identifier.getDir(), "Chapters"), name
				+ (hash == 1 ? ".json" : "[" + hash + "].json"));
	}

	/**
	 * @param name the new name for this object
	 * @return {@code false} if the directory has to be
	 * {@link #clean(MainChapter) cleaned} first
	 */
	@Override
	public BasicData setName(Container none, String name) {
		load();
		int current = 1;
		File dir = new File(identifier.getDir(), "Chapters");
		File src = new File(dir, name + ".json");
		while (src.exists()) src = new File(dir, name + "[" + ++current + "].json");
		try {
			while (loading) Thread.sleep(20);
		} catch (InterruptedException ie) {
		}
		if (getSaveFile().renameTo(new File(new File(identifier.getDir(), "Chapters"),
				name + (current == 1 ? ".json" : "[" + current + "].json")))) {
			this.name = name;
			hash = current;
		}
		return this;
	}

	@Override
	public boolean destroy(Container par) {
		if (par == parent || par == null) {
			getSaveFile().delete();
			identifier.putSetting("schRemoved", true);
			USED.waitForAccess(identifier);
			parent.removeChild(this);
			ELEMENTS.get(identifier).remove(this);
			USED.endAccess(identifier);
			return true;
		}
		return false;
	}

	@Override
	public ContentWriter writeData(ContentWriter cw) {
		cw.addClass().addName().addSF().addDesc();
		if (hash > 1) cw.addExtra(new Object[]{"hash", hash});
		if (cw.tabs <= 0) cw.addChildren();
		if (loaded) save();
		return cw;
	}

	/**
	 * Implementation of
	 * {@link ReadElement#readData(ReadElement.Content, Container) loading from String}.
	 */
	public static BasicData readData(ReadElement.Content src, Container parent) {
		if (src.params.containsKey(Formatter.CHILDREN)) {
			SaveChapter sch = mkElement(src.getData(parent), parent != null ? 2 : 1);
			for (BasicData bd : src.getChildren(sch)) sch.children.add(bd);
			return sch;
		} else return mkElement(src.getData(parent), 0);
	}
}
