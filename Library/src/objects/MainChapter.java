package objects;

import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import IOSystem.Formatter;
import IOSystem.Formatter.Reactioner;
import IOSystem.ReadElement;
import objects.templates.BasicData;
import objects.templates.Container;
import objects.templates.ContainerFile;

import static IOSystem.Formatter.deserialize;
import static IOSystem.Formatter.getPath;
import static IOSystem.Formatter.serialize;

/**
 * Head object of hierarchy of all {@link BasicData elemetary} objects. The
 * hierarchy is stored in its own folder under name of this object in the
 * specified {@link Formatter#getPath()}  directory}. Should be
 * {@link #name named} after the school object this hierarchy represents.
 *
 * @author Josef Litoš
 */
public class MainChapter extends objects.templates.BasicElement implements ContainerFile {

	/**
	 * Contains all loaded hierarchies. read-only data
	 */
	public static final List<MainChapter> ELEMENTS = new LinkedList<>();
	/**
	 * This file contains everything about this object and its {@link #children content}
	 * together with its own {@link #settings settings}.
	 */
	protected File dir;

	public File getDir() {
		return dir;
	}

	/**
	 * Contains properties, options, or anything else that has something to do
	 * with this hierarchy.
	 */
	protected Map<String, Object> settings = new java.util.HashMap<>();

	public Object getSetting(String key) {
		return settings.get(key);
	}

	public void putSetting(String key, Object value) {
		settings.put(key, value);
		deserialize(new File(dir, "setts.dat"), settings);
	}

	public void removeSetting(String key) {
		settings.remove(key);
		deserialize(new File(dir, "setts.dat"), settings);
	}

	@Override
	public File getSaveFile() {
		return new File(dir, "main.json");
	}

	/**
	 * Only this constructor creates the head object of the hierarchy. The
	 * hierarchy files are saved in its {@link #dir directory}.
	 *
	 * @param d must contain {@link #name name} of this hierarchy.
	 */
	public MainChapter(Formatter.Data d) {
		this(d, ContainerFile.isCorrect(d.name) ? new File(getPath(), d.name) : null);
	}

	public MainChapter(Formatter.Data d, File dir) {
		super(d);
		description = d.description;
		(this.dir = dir).mkdirs();
		File setts = new File(dir, "setts.dat");
		if (setts.exists()) settings = (Map<String, Object>) serialize(setts);
		else {
			deserialize(setts, settings);
			new File(dir, "Chapters").mkdirs();
		}
		ELEMENTS.add(this);
	}

	@Override
	public int[] refreshSF() {
		if (children.isEmpty()) load(false);
		int[] sf = {0, 0};
		for (BasicData bd : getChildren()) {
			int[] childSF =
					bd instanceof Container ? ((Container) bd).refreshSF() : bd.getSF();
			sf[0] += childSF[0];
			sf[1] += childSF[1];
		}
		return (this.sf = sf).clone();
	}

	/**
	 * Deletes the whole subject, this action can't be reversed.
	 *
	 * @param parent no parent
	 * @return if the main directory was successfully deleted
	 */
	@Override
	public boolean destroy(Container parent) {
		for (int i = children.size() - 1; i >= 0; i--) children.get(i).destroy(this);
		children.clear();
		ELEMENTS.remove(this);
		return remFiles(dir);
	}

	private boolean remFiles(File src) {
		if (src.isDirectory()) for (File f : src.listFiles()) remFiles(f);
		return src.delete();
	}

	@Override
	public BasicData setName(Container none, String name) {
		ContainerFile.isCorrect(name);
		File newDir = new File(getPath(), name);
		if (Reference.ELEMENTS.get(this) != null)
			for (Reference ref : Reference.ELEMENTS.get(this)) ref.pathStr[0] = name;
		for (byte i = 0; i < 5; i++)
			if (dir.renameTo(newDir)) {
				dir = newDir;
				this.name = name;
				save();
				return this;
			}
		return this;
	}

	/**
	 * This method removes this object from the
	 * {@link #ELEMENTS list}. It disconnects from the net.
	 */
	public void close() {
		ELEMENTS.remove(this);
		SaveChapter.ELEMENTS.remove(this);
		Chapter.ELEMENTS.remove(this);
		Word.ELEMENTS.remove(this);
		Picture.ELEMENTS.remove(this);
		Reference.ELEMENTS.remove(this);
		Runtime.getRuntime().gc();
	}

	/**
	 * Contains its children.
	 */
	protected final List<BasicData> children = new LinkedList<>();

	@Override
	public boolean move(Container op, Container np, Container npp) {
		throw new UnsupportedOperationException("MainChapter can't be moved - no parent.");
	}

	@Override
	public boolean move(Container op, Container opp, Container np, Container npp) {
		throw new UnsupportedOperationException("MainChapter can't be moved - no parent.");
	}

	@Override
	public BasicData[] getChildren() {
		if (!loaded && children.isEmpty()) load(false);
		return children.toArray(new BasicData[children.size()]);
	}

	@Override
	public BasicData[] getChildren(Container c) {
		if (!loaded && children.isEmpty()) load(false);
		return getChildren();
	}

	@Override
	public boolean putChild(Container c, BasicData e) {
		if (!loaded && children.isEmpty()) load(false);
		return children.add(e);
	}

	@Override
	public boolean putChild(Container c, BasicData e, int index) {
		if (!loaded && children.isEmpty()) load(false);
		children.add(index, e);
		return true;
	}
	
	@Override
	public boolean replaceChild(Container c, BasicData old, BasicData repl) {
		if (!loaded && children.isEmpty()) load(false);
		int index = children.indexOf(old);
		if (index > -1) {
			children.set(index, repl);
			return true;
		} else return false;
	}

	@Override
	public boolean removeChild(Container c, BasicData e) {
		return children.remove(e);
	}

	@Override
	public Container removeChild(BasicData e) {
		children.remove(e);
		return null;
	}

	/**
	 * The description for this object.
	 */
	protected String description;

	@Override
	public String getDesc(Container none) {
		return description;
	}

	@Override
	public String putDesc(Container none, String desc) {
		String old = description;
		description = desc;
		return old;
	}

	@Override
	public MainChapter getIdentifier() {
		return this;
	}

	private volatile boolean saving;

	@Override
	public void save(Reactioner rtr, boolean thread) {
		Runnable r = () -> {
			try {
				if (saving) return;
				saving = true;
				Formatter.saveFile(writeData(new ContentWriter().startWritingItem(this, null))
						.endWritingItem().toString(), getSaveFile());
				saving = false;
			} catch (Exception e) {
				if (rtr != null) rtr.react(e, getSaveFile(), this);
			}
		};
		if (thread) new Thread(r, "MCh save").start();
		else r.run();
	}

	private volatile boolean loaded;
	private boolean loading;

	/**
	 * In this implementation of {@link ContainerFile} this method loads all
	 * {@link SaveChapter file chapters} belonging to this object.
	 */
	@Override
	public void load(Reactioner rtr, boolean thread) {
		if (loaded) return;
		if (thread) new Thread(() -> load0(rtr, true), "MCh load").start();
		else load0(rtr, false);
	}

	private void load0(Reactioner rtr, boolean thread) {
		if (children.isEmpty()) {
			if (!getSaveFile().exists()) {
				loaded = true;
				return;
			}
			synchronized (this) {
				if (loading) {
					while (!thread) try {
						wait();
					} catch (Exception e) {
					}
					return;
				}
				loading = true;
			}
			try {
				ReadElement.loadFile(getSaveFile(), this, this);
				if (children.isEmpty()) loaded = true;
				synchronized (this) {
					loading = false;
					notifyAll();
				}
			} catch (Exception e) {
				if (rtr != null) rtr.react(e, getSaveFile().getAbsolutePath(), this);
				return;
			}
		} else {
			List<SaveChapter> schs = SaveChapter.ELEMENTS.get(this);
			if (schs == null) return;
			int size;
			do for (int i = (size = schs.size()) - 1; i >= 0; i--)
				if (!schs.get(i).isLoaded()) schs.get(i).load(rtr, thread);
			while (size < schs.size());
			loaded = true;
		}
		if (Formatter.defaultReacts.get("MChLoaded") != null)
			Formatter.defaultReacts.get("MChLoaded").react(this);
	}

	@Override
	public boolean isLoaded() {
		return loaded;
	}

	@Override
	public int getRatio() {
		return children.isEmpty() ? -2 : super.getRatio();
	}

	@Override
	public ContentWriter writeData(ContentWriter cw) {
		deserialize(new File(dir, "setts.dat"), settings);
		return cw.addClass().addName().addSF().addDesc().addChildren();
	}

	/**
	 * Implementation of
	 * {@link ReadElement#readData(ReadElement.Content, Container) loading from String}.
	 */
	public static BasicData readData(ReadElement.Content src, Container parent) {
		Formatter.Data d = src.getData(parent);
		MainChapter mch = (MainChapter) parent;
		if (mch == null) {
			for (MainChapter m : ELEMENTS) if (m.name.equals(d.name)) return m;
			mch = new MainChapter(d);
		} else {
			mch.description = d.description;
			mch.sf = d.sf;
		}
		src.identifier = mch;
		for (BasicData bd : src.getChildren(mch)) mch.children.add(bd);
		return mch;
	}
}
