package objects.templates;

import objects.MainChapter;

/**
 * Basic element of the school object project element hierarchy. Defines the basics
 * of all hierarchy-usable objects.
 *
 * @author Josef Litoš
 * @see MainChapter
 */
public interface BasicData extends IOSystem.WriteElement {

	static void isValid(String name) {
		if (name.contains("\n")) throw new IllegalNameException();
	}

	class IllegalNameException extends IllegalArgumentException {
	}

	/**
	 * Destroys the part of this object contained in the specified parent.
	 * If this object is not contained in the parent, nothing happens.
	 *
	 * @param parent the parent to remove this object from
	 * @return {@code true} if this object has been successfully removed
	 */
	boolean destroy(Container parent);

	/**
	 * @param parent parent of this object
	 * @return {@code true} if this object is empty (or has no meaning) in the
	 * specified parent
	 */
	default boolean isEmpty(Container parent) {
		return false;
	}

	/**
	 * @return the more_main hierarchy object that this object belongs to
	 */
	MainChapter getIdentifier();

	/**
	 * Moves this object from the specified parent to a different parent.
	 *
	 * @param oldParent old parent of this object
	 * @param newParent the new parent of this object
	 * @param newParPar parent of the new parent
	 * @return if the operation succeeded
	 */
	default boolean move(Container oldParent, Container newParent, Container newParPar) {
		if (oldParent.getIdentifier() != newParent.getIdentifier()) return false;
		oldParent.removeChild(this);
		if (!newParent.hasChild(newParPar, this)) newParent.putChild(newParPar, this);
		return true;
	}
	
	/**
	 * Moves this object from the specified parent to a different parent.
	 *
	 * @param oldParent old parent of this object
	 * @param oldParPar parent of the old parent
	 * @param newParent the new parent of this object
	 * @param newParPar parent of the new parent
	 * @return if the operation succeeded
	 */
	default boolean move(Container oldParent,
			Container oldParPar, Container newParent, Container newParPar) {
		if (oldParent.getIdentifier() != newParent.getIdentifier()) return false;
		oldParent.removeChild(oldParPar, this);
		if (!newParent.hasChild(newParPar, this)) newParent.putChild(newParPar, this);
		return true;
	}

	/**
	 * Alters the current name of this object.
	 *
	 * @param parent the parent of this object
	 * @param name   the new name for this object
	 * @return the object to have the specified name, or the old name, if renaming was
	 * unsuccessfull
	 */
	BasicData setName(Container parent, String name);

	String getName();

	/**
	 * @return the ratio of successes and fails for this object in percentage
	 */
	default int getRatio() {
		int[] sf = getSF();
		return sf[1] == 0 && sf[0] == 0 ? -1 : (100 * sf[0] / (sf[0] + sf[1]));
	}

	/**
	 * The success and fail values for this object.
	 * 
	 * @return copy of the success rate
	 */
	int[] getSF();

	/**
	 * @return the amount of tests runned on this object
	 */
	default int getSFCount() {
		return getSF()[0] + getSF()[1];
	}

	/**
	 * @return returns the object supposed to be displayed
	 */
	default BasicData getThis() {
		return this;
	}

	/**
	 * @param success if the test for this object was successful
	 */
	void addSF(boolean success);

	String getDesc(Container c);

	String putDesc(Container c, String desc);
}
