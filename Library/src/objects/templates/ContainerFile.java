package objects.templates;

import IOSystem.Formatter.Reactioner;

import static IOSystem.Formatter.defaultReacts;

/**
 * Instance of this class save their content into its own file.
 *
 * @author Joset Litoš
 */
public interface ContainerFile extends Container {

	/**
	 * @param name name to be tested for ability to be saved as a file
	 * @return whether this name can be used as a name for this object
	 */
	static boolean isCorrect(String name) {
		if (name.length() > 150) {
			defaultReacts.get(ContainerFile.class + ":name").react(true);
			throw new IllegalArgumentException("Name can't be longer than 150 characters!");
		}
		for (char ch : "/|\\:\"?*§$[]\n\t".toCharArray())
			if (name.contains("" + ch)) {
				defaultReacts.get(ContainerFile.class + ":name").react(false);
				throw new IllegalArgumentException("Name can't contain /|\\:\"?*§$[]\\t\\n");
			}
		return true;
	}

	/**
	 * @return the File that this object is saved in.
	 */
	IOSystem.Formatter.IOSystem.GeneralPath getSaveFile();

	default void save() {
		save(defaultReacts.get(ContainerFile.class + ":save"));
	}

	default void save(Reactioner rtr) {
		save(rtr, true);
	}

	default void save(boolean thread) {
		save(defaultReacts.get(ContainerFile.class + ":save"), thread);
	}

	/**
	 * Saves this object into its {@link #getSaveFile() own file}.
	 *
	 * @param rtr    what to do, if the operation doesn't succeed, {@code null} for no action
	 * @param thread if the action should be run in a separated thread
	 */
	void save(Reactioner rtr, boolean thread);

	/**
	 * @return if this object has already loaded all its content from its {@link #getSaveFile() file}.
	 */
	boolean isLoaded();

	default void load() {
		load(defaultReacts.get(ContainerFile.class + ":load"));
	}

	default void load(Reactioner ofl) {
		load(ofl, true);
	}

	default void load(boolean thread) {
		load(defaultReacts.get(ContainerFile.class + ":load"), thread);
	}

	/**
	 * Loads this object from its {@link #getSaveFile() own file}.
	 *
	 * @param rtr    what to do, if the operation doesn't succeed, {@code null} for no action
	 * @param thread if the action should be run in a separated thread
	 */
	void load(Reactioner rtr, boolean thread);
}
