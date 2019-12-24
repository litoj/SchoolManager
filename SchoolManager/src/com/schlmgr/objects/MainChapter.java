package com.schlmgr.objects;

import com.schlmgr.IOSystem.Formatter;
import com.schlmgr.IOSystem.Formatter.OnFailListener;
import com.schlmgr.IOSystem.ReadElement;
import com.schlmgr.objects.templates.BasicData;
import com.schlmgr.objects.templates.Container;
import com.schlmgr.objects.templates.ContainerFile;

import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static com.schlmgr.IOSystem.Formatter.createDir;
import static com.schlmgr.IOSystem.Formatter.deserializeTo;
import static com.schlmgr.IOSystem.Formatter.getPath;
import static com.schlmgr.IOSystem.Formatter.serialize;

/**
 * Head object of hierarchy of all {@link BasicData elemetary} objects. The
 * hierarchy is stored in its own folder under name of this object in the
 * specified {@link Formatter#getPath()}  directory}. Should be
 * {@link #name named} after the school object this hierarchy represents.
 *
 * @author Josef Litoš
 */
public class MainChapter extends com.schlmgr.objects.templates.BasicElement implements ContainerFile {

	/**
	 * Contains all loaded hierarchies. read-only data
	 */
	public static final List<MainChapter> ELEMENTS = new LinkedList<>();
	/**
	 * This file contains everything about this object and its
	 * {@link #children content} together with its own
	 * {@link #settings settings}.
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
		deserializeTo(new File(dir, "setts.dat"), settings);
	}

	public void removeSetting(String key) {
		settings.remove(key);
		deserializeTo(new File(dir, "setts.dat"), settings);
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
		super(d);
		construct(d, createDir(new File(getPath(), name)));
	}

	public MainChapter(Formatter.Data d, File dir) {
		super(d);
		construct(d, dir);
	}

	private void construct(Formatter.Data d, File dir) {
		ContainerFile.isCorrect(name);
		description = d.description;
		this.dir = dir;
		File setts = new File(dir, "setts.dat");
		if (setts.exists()) settings = (Map<String, Object>) serialize(new File(dir, "setts.dat"));
		else {
			deserializeTo(new File(dir, "setts.dat"), settings);
			createDir(new File(dir, "Chapters"));
		}
		ELEMENTS.add(this);
	}

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
	public boolean setName(Container none, String name) {
		ContainerFile.isCorrect(name);
		File newDir = new File(getPath(), name);
		for (Reference ref : Reference.ELEMENTS.get(this)) ref.pathStr[0] = name;
		for (byte i = 0; i < 5; i++)
			if (dir.renameTo(newDir)) {
				dir = newDir;
				this.name = name;
				save();
				return true;
			}
		return false;
	}

	/**
	 * This method saves this object and then removes itself from the
	 * {@link #ELEMENTS list}.
	 */
	public void close() {
		save();
		ELEMENTS.remove(this);
	}

	/**
	 * Contains its children.
	 */
	protected final java.util.List<BasicData> children = new java.util.LinkedList<>();

	@Override
	public boolean move(Container op, Container np, Container npp) {
		throw new UnsupportedOperationException("This hierarchy object cannot be moved, since it has no parent.");
	}

	@Override
	public BasicData[] getChildren() {
		return children.toArray(new BasicData[children.size()]);
	}

	@Override
	public BasicData[] getChildren(Container c) {
		return c == null ? getChildren() : null;
	}

	@Override
	public boolean putChild(Container c, BasicData e) {
		return c == null && children.add(e);
	}

	@Override
	public boolean removeChild(Container c, BasicData e) {
		return c == null && children.remove(e);
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

	private boolean loaded = false;

	/**
	 * In this implementation of {@link ContainerFile} this method loads all
	 * {@link SaveChapter file chapters} belonging to this object.
	 */
	@Override
	public void load(OnFailListener ofl, boolean thread) {
		if (loaded) return;
		else if (children.isEmpty()) {
			ReadElement.loadMch(getSaveFile());
			return;
		}
		List<SaveChapter> schs = SaveChapter.ELEMENTS.get(this);
		if (schs == null) return;
		int size;
		do for (int i = (size = schs.size()) - 1; i >= 0; i--)
			if (!schs.get(i).isLoaded()) schs.get(i).load(ofl, thread);
		while (size < schs.size());
		loaded = true;
	}

	@Override
	public boolean isLoaded() {
		return loaded;
	}

	@Override
	public StringBuilder writeData(StringBuilder sb, int tabs, Container cp) {
		deserializeTo(new File(dir, "setts.dat"), settings);
		sb.append('{');
		add(sb, this, null, true, true, true, true, null, null, true);
		return writeData0(sb, 1, cp);
	}

	/**
	 * Implementation of
	 * {@link com.schlmgr.IOSystem.ReadElement#readData(ReadElement.Source, Container) loading from String}.
	 */
	public static BasicData readData(ReadElement.Source src, Container parent) {
		Formatter.Data d = ReadElement.get(src, true, true, true, true, null);
		MainChapter mch = null;
		for (MainChapter m : ELEMENTS)
			if (m.name.equals(d.name)) {
				mch = m;
				break;
			}
		if (mch == null) {
			mch = new MainChapter(d);
		}
		src.i = mch;
		for (BasicData bd : ReadElement.loadChildren(src, mch)) mch.putChild(null, bd);
		return mch;
	}
}
