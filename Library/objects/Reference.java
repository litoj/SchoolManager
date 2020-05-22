package objects;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import IOSystem.Formatter;
import IOSystem.Formatter.Data;
import IOSystem.ReadElement;
import objects.templates.BasicData;
import objects.templates.Container;
import objects.templates.TwoSided;

import static IOSystem.Formatter.defaultReacts;

/**
 * References to another {@link BasicData} instance other than {@link MainChapter}
 * and its extensions.
 *
 * @author Josef Litoš
 */
public final class Reference implements BasicData {

	/**
	 * Contains all instances of this class created. All References are sorted by
	 * the {@link MainChapter hierarchy} they belong to. read-only data
	 */
	public static final Map<MainChapter, List<Reference>> ELEMENTS = new HashMap<>();

	private static final Formatter.Synchronizer USED = new Formatter.Synchronizer();

	/**
	 * Full path to the {@link #reference referenced object}.
	 */
	protected final Container[] path;
	protected final String[] pathStr;

	public Container[] getRefPath() {
		load();
		return path.clone();
	}

	public Container getRefPathAt(int index) {
		load();
		return path[index > 0 ? index : (path.length + index)];
	}

	/**
	 * The referenced object.
	 */
	private BasicData reference;
	private final String refStr;
	/**
	 * The number of times this object is being stored in any {@link Container}.
	 */
	int parentCount;

	/**
	 * @param ref     the referenced element
	 * @param path    path to the parent of this reference
	 * @param refPath path starting from MainChapter (inclusive) to the referenced object
	 *                (exclusive)
	 * @return new instance of this class
	 */
	public static Reference mkElement(BasicData ref,
	                                  List<Container> path, Container[] refPath) {
		return mkElement(ref, path, (Object[]) refPath);
	}

	private static Reference mkElement(Object ref,
	                                   List<Container> path, Object[] refPath) {
		if (ref instanceof MainChapter)
			throw new IllegalArgumentException("Hierarchy can't be referenced!");
		if (ref instanceof Container && !(ref instanceof TwoSided))
			for (Container c : path)
				if (c == ref)
					throw new IllegalArgumentException(
							"Can't reference " + ref + ",\nwith path: " + path);
		if (ELEMENTS.get(path.get(0).getIdentifier()) == null)
			ELEMENTS.put(path.get(0).getIdentifier(), new java.util.ArrayList<>(10));
		USED.waitForAccess(path.get(0).getIdentifier());
		for (Reference r : ELEMENTS.get((MainChapter) path.get(0))) {
			if (r.refStr.equals(ref.toString()) && refPath.length == r.pathStr.length) {
				boolean found = true;
				for (int i = 0; i < refPath.length; i++) {
					if (!refPath[i].toString().equals(r.pathStr[i])) {
						found = false;
						break;
					}
				}
				if (found) {
					r.parentCount++;
					USED.endAccess(path.get(0).getIdentifier());
					return r;
				}
			}
		}
		Reference ret = new Reference(ref, refPath, (MainChapter) path.get(0));
		USED.endAccess(path.get(0).getIdentifier());
		return ret;
	}

	private Reference(Object ref, Object[] refPath, MainChapter identifier) {
		if (ref instanceof BasicData) reference = (BasicData) ref;
		if (refPath instanceof Container[]) {
			path = (Container[]) refPath;
			pathStr = new String[refPath.length];
			for (int i = 0; i < refPath.length; i++) pathStr[i] = refPath[i].toString();
		} else {
			path = new Container[refPath.length];
			path[0] = identifier;
			pathStr = (String[]) refPath;
		}
		refStr = ref.toString();
		parentCount = 1;
		ELEMENTS.get(identifier).add(this);
	}

	public boolean isLoaded() {
		return reference != null;
	}

	private boolean load() {
		if (reference == null && (reference = usePath(1)) == null)
			throw new IllegalArgumentException("Reference load failed!");
		return true;
	}

	private BasicData usePath(int index) {
		if (index == pathStr.length)
			return find(refStr, path[index - 1], index > 1 ? path[index - 2] : null, true);
		BasicData found = find(pathStr[index], path[index - 1],
				index > 1 ? path[index - 2] : null, false);
		if ((path[index++] = (Container) found) == null) return null;
		return usePath(index);
	}

	private static BasicData find(String name,
	                              Container par, Container parpar, boolean end) {
		for (BasicData bd : par.getChildren(parpar))
			if (name.equals(bd.getName()))
				if (end) return bd;
				else if (bd instanceof Container && !(bd instanceof TwoSided)) return bd;
		new Thread(() ->
				defaultReacts.get(Reference.class + ":not_found")
						.react(name, par, parpar), "Ref finder").start();
		return null;
	}

	@Override
	public boolean move(Container oP, Container oPP, Container nP, Container nPP) {
		throw new UnsupportedOperationException("Reference cannot be moved");
	}

	@Override
	public boolean move(Container oP, Container nP, Container nPP) {
		throw new UnsupportedOperationException("Reference cannot be moved");
	}

	@Override
	public boolean isEmpty(Container c) {
		return false;
	}

	@Override
	public MainChapter getIdentifier() {
		return (MainChapter) path[0];
	}

	@Override
	public BasicData setName(Container par, String name) {
		return this;
	}

	@Override
	public String getName() {
		return refStr;
	}

	@Override
	public int[] getSF() {
		return new int[]{0, 0};
	}

	@Override
	public void addSF(boolean success) {
		load();
		reference.addSF(success);
	}

	@Override
	public String getDesc(Container c) {
		load();
		return reference.getDesc(path[path.length - 1]);
	}

	@Override
	public String putDesc(Container c, String desc) {
		load();
		return reference.putDesc(path[path.length - 1], desc);
	}

	@Override
	public String toString() {
		return getName();
	}

	@Override
	public BasicData getThis() {
		load();
		return reference;
	}

	@Override
	public boolean destroy(Container parent) {
		USED.waitForAccess(getIdentifier());
		if (--parentCount == 0) ELEMENTS.get(getIdentifier()).remove(this);
		USED.endAccess(getIdentifier());
		return parent.removeChild(this) != null || parent instanceof MainChapter;
	}

	@Override
	public ContentWriter writeData(ContentWriter cw) {
		return cw.addClass().addName().addExtra(new Object[]{"origin", mkPath()});
	}

	private String mkPath() {
		StringBuilder ret = new StringBuilder();
		for (String s : pathStr) ret.append("×").append(s);
		return ret.substring(1);
	}

	/**
	 * Implementation of
	 * {@link ReadElement#readData(ReadElement.Content, Container) loading from String}.
	 */
	public static BasicData readData(ReadElement.Content src, Container parent) {
		Data data = src.getData(parent);
		return mkElement(data.name, Arrays.asList(
				new Container[]{parent.getIdentifier(), parent}),
				((String) data.tagVals.get("origin")).split("×"));
	}
}
