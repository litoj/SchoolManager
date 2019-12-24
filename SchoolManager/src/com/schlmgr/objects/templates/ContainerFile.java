package com.schlmgr.objects.templates;

import com.schlmgr.IOSystem.Formatter;
import com.schlmgr.IOSystem.Formatter.OnFailListener;

import java.io.File;

import static com.schlmgr.IOSystem.Formatter.defaultOFLs;

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
		if (name.length() > 150)
			throw new IllegalArgumentException("Name can't be longer than 150 characters");
		for (char ch : "/|\\:\"?*§$[]".toCharArray())
			if (name.contains("" + ch))
				throw new IllegalArgumentException("Name can't contain /|\\:\"?*§$[]");
		return true;
	}

	/**
	 * @return the File that this object is saved in.
	 */
	File getSaveFile();

	default void save() {
		save(defaultOFLs.get(ContainerFile.class + ":save"));
	}

	default void save(OnFailListener ofl) {
		save(ofl, true);
	}

	default void save(boolean thread) {
		save(defaultOFLs.get(ContainerFile.class + ":save"), thread);
	}

	/**
	 * Saves this object into its {@link #getSaveFile() own file}.
	 *
	 * @param ofl what to do, if the operation doesn't succeed, {@code null} for no action
	 */
	default void save(OnFailListener ofl, boolean thread) {
		if (thread)
			new Thread(() -> {
				try {
					Formatter.saveFile(writeData(new StringBuilder(), 0, null).toString(), getSaveFile());
				} catch (Exception e) {
					if (ofl != null) ofl.onFail(e, getSaveFile(), this);
				}
			}).start();
		else
			try {
				Formatter.saveFile(writeData(new StringBuilder(), 0, null).toString(), getSaveFile());
			} catch (Exception e) {
				if (ofl != null) ofl.onFail(e, getSaveFile(), this);
			}
	}

	/**
	 * @return if this object has already loaded all its content from its {@link #getSaveFile() file}.
	 */
	boolean isLoaded();

	default void load() {
		load(defaultOFLs.get(ContainerFile.class + ":load"));
	}

	default void load(OnFailListener ofl) {
		load(ofl, true);
	}

	default void load(boolean thread) {
		load(defaultOFLs.get(ContainerFile.class + ":load"), thread);
	}

	/**
	 * Loads this object from its {@link #getSaveFile() own file}.
	 *
	 * @param ofl what to do, if the operation doesn't succeed, {@code null} for no action
	 */
	default void load(OnFailListener ofl, boolean thread) {
		if (!isLoaded()) {
			if (thread)
				new Thread(() -> {
					try {
						com.schlmgr.IOSystem.ReadElement.loadSch(getSaveFile(), getIdentifier(), null);
					} catch (Exception e) {
						if (ofl != null) ofl.onFail(e, getSaveFile(), this);
					}
				}).start();
			else
				try {
					com.schlmgr.IOSystem.ReadElement.loadSch(getSaveFile(), getIdentifier(), null);
				} catch (Exception e) {
					if (ofl != null) ofl.onFail(e, getSaveFile(), this);
				}
		}
	}
}
