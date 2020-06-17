package objects.templates;

/**
 * This hierarchy object can contain other hierarchy objects.
 *
 * @author Josef Litoš
 */
public interface Container extends BasicData {

	BasicData[] getChildren();

	BasicData[] getChildren(Container parent);

	boolean putChild(Container parent, BasicData e);

	boolean putChild(Container parent, BasicData e, int index);
	
	boolean replaceChild(Container parent, BasicData old, BasicData replacement);

	boolean removeChild(Container parent, BasicData e);

	Container removeChild(BasicData e);

	/**
	 * Counts success and fail values of its children and sets the result as its own.
	 * Reference objects values are not used (they always return {0, 0}).
	 *
	 * @return copy of the success and fail values for this object
	 */
	int[] refreshSF();

	default boolean hasChild(BasicData e) {
		for (BasicData bd : getChildren()) if (e.equals(bd)) return true;
		return false;
	}

	default boolean hasChild(Container par, BasicData e) {
		if (getChildren(par) == null) return false;
		for (BasicData bd : getChildren(par)) if (e.equals(bd)) return true;
		return false;
	}

	@Override
	default boolean destroy(Container c) {
		for (BasicData bd : getChildren(c)) bd.destroy(this);
		return c.removeChild(this) != null;
	}

	@Override
	default boolean isEmpty(Container c) {
		for (BasicData bd : getChildren(c)) if (!bd.isEmpty(this)) return false;
		return true;
	}
}
