package objects.templates;

import IOSystem.Formatter.Data;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Default implementation of a hierarchy object and full implementation of a
 * {@link Container} that cares about its parent. Defines the system for objects used in
 * a {@link testing.Test}. They have their name and their other side, both in its own
 * instance of the same class.
 *
 * @param <T> object which has two versions, but only one is manipulable with
 * @author Josef Litoš
 */
public abstract class TwoSided<T extends TwoSided> extends Element implements Container {

	/**
	 * {@code true} if and only if the object is the main one
	 * (all its methods can be used).
	 */
	public final boolean isMain;
	/**
	 * Amount of parents this object is stored in
	 */
	protected int parentCount;

	protected TwoSided(Data bd, boolean isMain, Map<objects.MainChapter, List<T>> NET) {
		super(bd);
		this.isMain = isMain;
		NET.get(identifier).add((T) this);
		parentCount = 1;
	}

	/**
	 * Contains all objects, which belong to this object.
	 */
	protected final Map<Container, List<T>> children = new java.util.HashMap<>();

	@Override
	public TwoSided[] getChildren() {
		List<TwoSided> l = new LinkedList<>();
		for (List<T> ch : children.values()) l.addAll(ch);
		return l.toArray(new TwoSided[0]);
	}

	@Override
	public TwoSided[] getChildren(Container c) {
		return children.get(c) == null ? null : children.get(c).toArray(new TwoSided[0]);
	}

	@Override
	public boolean putChild(Container c, BasicData e) {
		if (!(e instanceof TwoSided)) return false;
		if (children.get(c) == null) {
			parentCount++;
			children.put(c, new LinkedList<>());
		} else if (children.get(c).contains((T) e)) return false;
		return children.get(c).add((T) e);
	}

	@Override
	public boolean removeChild(Container parent, BasicData child) {
		child.destroy(parent);
		remove1(parent, (T) child);
		return true;
	}

	@Override
	public Container removeChild(BasicData e) {
		if (!(e instanceof TwoSided)) return null;
		for (Container c : children.keySet())
			if (children.get(c).remove(e)) {
				e.destroy(c);
				remove1(c, (T) e);
				return c;
			}
		return null;
	}

	@Override
	public int[] refreshSF() {
		return sf.clone();
	}
	
	@Override
	public boolean move(Container op, Container np, Container npp) {
		if (children.get(op) == null || !super.move(op, np, npp) || !isMain) return false;
		move(op, np);
		return true;
	}
	
	@Override
	public boolean move(Container op, Container opp, Container np, Container npp) {
		if (children.get(op) == null
				|| !super.move(op, opp, np, npp) || !isMain) return false;
		move(op, np);
		return true;
	}
	
	/**
	 * Takes care of moving this object from one parent to another.
	 * 
	 * @param op old parent of this object
	 * @param np new parent to associate with
	 */
	protected void move(Container op, Container np) {
		List<T> list = children.remove(op);
		if (children.get(np) != null) {
			List<T> newContent = children.get(np);
			List<T> toAdd = new LinkedList<>();
			for (T old : list)
				cycle :
				{
					for (T t : newContent) if (t == old) break cycle;
					toAdd.add(old);
					if (isMain) old.move(op, np);
				}
			newContent.addAll(toAdd);
		} else children.put(np, list);
	}

	/**
	 * Called to end the process of removing a child from the main object.
	 *
	 * @param parent where the object is located
	 * @param toRem  the object to be removed
	 */
	protected void remove1(Container parent, T toRem) {
		children.get(parent).remove(toRem);
		if (children.get(parent).isEmpty()) {
			children.remove(parent);
			if (isMain) parent.removeChild(this);
		}
	}

	@Override
	public boolean isEmpty(Container c) {
		return isMain && (children.get(c) == null || children.get(c).isEmpty());
	}

	@Override
	public StringBuilder writeData(StringBuilder sb, int tabs, Container cp) {
		tabs(sb, tabs++, '{').add(sb, this, cp, true, true, true, true, null, null, true);
		boolean first = true;
		for (BasicData bd : getChildren(cp))
			if (!bd.isEmpty(this)) {
				if (first) first = false;
				else sb.append(',');
				tabs(sb, tabs, '{').add(sb, bd, cp,
						false, true, false, true, null, null, false).append('}');
			}
		tabs(sb, tabs - 1, ']');
		return sb.append('}');
	}
}
