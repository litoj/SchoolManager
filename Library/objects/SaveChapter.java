package objects;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import IOSystem.Formatter;
import IOSystem.Formatter.Data;
import IOSystem.Formatter.Reactioner;
import IOSystem.Formatter.Synchronizer;
import IOSystem.ReadElement;
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
		return mkElement(d, true);
	}

	protected static final SaveChapter mkElement(Data d, boolean full) {
		USED.waitForAccess(d.identifier);
		SaveChapter ret;
		if (ELEMENTS.get(d.identifier) == null) {
			ELEMENTS.put(d.identifier, new java.util.LinkedList<>());
			if (d.identifier.getSetting("schNameCount") == null) {
				d.identifier.putSetting("schNameCount", new HashMap<String, Integer>());
				d.identifier.putSetting("schRemoved", false);
			}
		} else {
			int hash = d.tagVals == null || d.tagVals.get("hash") == null
					? 1 : (int) d.tagVals.get("hash");
			for (SaveChapter sch : ELEMENTS.get(d.identifier)) {
				if (d.name.equals(sch.toString()) && hash == sch.hash) {
					sch.loaded = full;
					USED.endAccess(d.identifier);
					return sch;
				}
			}
		}
		ret = new SaveChapter(d, full);
		USED.endAccess(d.identifier);
		return ret;
	}

	protected SaveChapter(Data d, boolean full) {
		super(d);
		identifier = d.identifier;
		ContainerFile.isCorrect(name);
		parent = d.par;
		loaded = full;
		//hash-creator
		if (!full) {
			hash = d.tagVals.get("hash") == null ? 1 : (int) d.tagVals.get("hash");
		} else {
			Map<String, Number> map =
					(Map<String, Number>) identifier.getSetting("schNameCount");
			Number i = map.get(name);
			map.put(name, hash = i == null ? 1 : i.intValue() + 1);
			identifier.putSetting("schNameCount", map);
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
		return super.removeChild(e);
	}

	@Override
	public boolean removeChild(Container c, BasicData e) {
		if (!loaded) load(false);
		return super.removeChild(c, e);
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
		String exceptions = "";
		File dir = new File(mch.getDir(), "Chapters");
		Map<String, Number> map = new HashMap<>();
		USED.waitForAccess(mch);
		for (SaveChapter sch : ELEMENTS.get(mch)) {
			int hash = 1;
			File src = new File(dir, sch.name + ".json");
			while (hash < sch.hash && !src.renameTo(
					new File(dir, sch.name + "[" + ++hash + "].json")))
				if (hash == 1024) {
					exceptions += "\nFile '" + src + "' can't be renamed!";
					break;
				}
			if (hash < 1024 && (map.get(sch.name) == null
					|| (sch.hash = hash) > map.get(sch.name).intValue()))
				map.put(sch.name, hash);
		}
		USED.endAccess(mch);
		mch.putSetting("schNameCount", map);
		if (!exceptions.isEmpty()) throw new IllegalArgumentException(exceptions);
		mch.putSetting("schRemoved", false);
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
		Map<String, Number> map =
				(Map<String, Number>) identifier.getSetting("schNameCount");
		Number newCount = map.get(name);
		int current = newCount == null ? 1 : newCount.intValue();
		try {
			while (loading) Thread.sleep(20);
		} catch (InterruptedException ie) {
		}
		if (getSaveFile().renameTo(new File(new File(identifier.getDir(), "Chapters"), name
				+ (current == 1 ? ".json" : "[" + current + "].json")))) {
			map.put(this.name = name, hash = current);
		}
		return this;
	}

	@Override
	public boolean destroy(Container parent) {
		if (getSaveFile().delete()) {
			identifier.putSetting("schRemoved", true);
			Map<String, Number> map =
					(Map<String, Number>) identifier.getSetting("schNameCount");
			int i = map.get(name) == null ? 0 : map.get(name).intValue() - 1;
			USED.waitForAccess(identifier);
			if (i < 1) map.remove(name);
			else map.put(name, i);
			parent.removeChild(this);
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
		if (parent == null) {
			SaveChapter sch = mkElement(src.getData(null));
			for (BasicData bd : src.getChildren(sch)) sch.putChild(null, bd);
			return sch;
		}
		return mkElement(src.getData(parent), false);
	}
}
