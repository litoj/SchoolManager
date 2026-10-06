package com.schlmgr.gui.engine.IOSystem;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.schlmgr.gui.engine.objects.MainChapter;
import com.schlmgr.gui.engine.objects.templates.Container;
import com.schlmgr.gui.engine.objects.templates.ContainerFile;

/**
 * This class is used to operate with text files. The format of all files containing necessary data
 * of every {@link com.schlmgr.gui.engine.objects.templates.BasicData element} is json, but the code was implemented by the
 * author without using any templates.
 *
 * @author Josef Litoš
 */
public final class Formatter {

	/**
	 * Default {@link Reactioner object} made for answering on various actions, keys should be in
	 * format:
	 * <p>
	 * fullClassName + ':' + specification (if necessary);
	 * <p>
	 * or just specification, if it is for global usage.
	 */
	public static final Map<String, Reactioner> defaultReacts = new HashMap<>();

	/**
	 * Used as file I/O interface.
	 */
	private static IOSystem ios;

	public static IOSystem getIOSystem() {
		return ios;
	}

	/**
	 * Reacts on any event from specified locations in the program given various parameters.
	 */
	public interface Reactioner {

		void react(Object... moreInfo);
	}

	public static final String CLASS = "class", NAME = "name", SUCCESS = "s",
		 FAIL = "f", CHILDREN = "cdrn", DESC = "desc";

	/**
	 * @return path to directory currently set for containing objects.
	 */
	public static IOSystem.GeneralPath getSubjectsDir() {
		return ios.subjDir;
	}

	/**
	 * This method changes the directory of saves of hierarchies.
	 *
	 * @param path the directory for this application as String (either path or uri)
	 * @return if the dir was changed, {@code false} when given same dir
	 */
	public static boolean changeDir(IOSystem.GeneralPath path) {
		if (ios.subjDir != null && ios.subjDir.equals(path)) {
			return false;
		}
		try {
			ios.changeDir(path);
		} catch (IllegalArgumentException iae) {
			resetDir();
			return false;
		}
		while (MainChapter.ELEMENTS.size() > 0) {
			MainChapter.ELEMENTS.get(0).close();
		}
		if (onDirChanged != null) onDirChanged.react(path);
		return true;
	}

	/**
	 * Optional hook called whenever the subjects directory successfully changes so the
	 * platform layer can persist it (the engine itself never persists options).
	 */
	public static Reactioner onDirChanged;

	/**
	 * Restores the default subjects directory.
	 */
	public static void resetDir() {
		changeDir(ios.getDefaultSubjectsDir());
	}

	/**
	 * @param e Exception to be rendered
	 * @return complete road of the Exception and its causes
	 */
	public static String getStackTrace(Throwable e) {
		OutputStream os = new ByteArrayOutputStream();
		e.printStackTrace(new PrintStream(os));
		return os.toString();
	}

	/**
	 * File operations depend on platform, this class defines all methods required to run the program
	 * and can be platform dependant.
	 * <p>
	 * Only the firstly created instance will be used during the run.
	 * <p>
	 * Also all of them have to create all necessary {@link Formatter#defaultReacts} by implementing
	 * the method {@link #mkDefaultReacts()} for the library to work.
	 */
	public static abstract class IOSystem {

		/**
		 * Path to the directory that contains all {@link MainChapter hierarchies's} folders and data.
		 */
		protected GeneralPath subjDir;

		/**
		 * Loads the subjects directory and defines this instance as the {@link Formatter#ios}.
		 * The engine does not persist options; the platform layer owns them and passes the
		 * subjects directory directly.
		 *
		 * @param subjDir the subjects directory (path or uri string), or {@code null} for the default
		 */
		protected IOSystem(Object subjDir) {
			if (Formatter.ios != null) {
				return;
			}
			Formatter.ios = this;
			mkDefaultReacts();
			if (subjDir != null) try {
				changeDir(createGeneralPath(subjDir));
			} catch (Exception e) {
				defaultReacts.get(Formatter.class + ":newSrcDir").react(e, subjDir);
				resetDir();
			}
			if (this.subjDir == null) {
				this.subjDir = getDefaultSubjectsDir();
			}
			setDefaults(this.subjDir.equals(getDefaultSubjectsDir()));
		}

		/**
		 * Loads and sets all outside-library used values (interface flags etc.) after the
		 * subjects directory is known.
		 *
		 * @param first if the program returned to its first-launch state (reset to default dir)
		 */
		protected void setDefaults(boolean first) {
		}

		/**
		 * Creates the default exception handlers for various cases.<p>
		 * This method is completely platform dependant.
		 */
		protected abstract void mkDefaultReacts();

		public GeneralPath createGeneralPath(Object src) {
			return createGeneralPath(src, false);
		}

		public GeneralPath createGeneralPath(Object src, boolean internal) {
			return new FilePath(src.toString(), internal);
		}

		/**
		 * @return default {@link #subjDir objects storage}
		 */
		public abstract GeneralPath getDefaultSubjectsDir();

		/**
		 * Any necessary actions needed when the {@link #subjDir objects storage} is changed.
		 *
		 * @param newDir the new directory for storing school subjects
		 * @return the new {@link #subjDir objects storage}
		 */
		protected GeneralPath changeDir(GeneralPath newDir) {
			return subjDir = newDir;
		}

		public OutputStream outputInternal(GeneralPath file) throws IOException {
			return outputInternal(file, false);
		}

		public abstract OutputStream outputInternal(GeneralPath file, boolean append) throws IOException;

		public abstract InputStream inputInternal(GeneralPath file) throws IOException;

		public static interface GeneralPath {

			public String getOriginalName();

			public String getName();

			public default void save(String content) {
				save(content, false);
			}

			public default void save(String content, boolean append) {
				try ( OutputStreamWriter osw
					 = new OutputStreamWriter(createOutputStream(append), StandardCharsets.UTF_8)) {
					osw.write(content);
				} catch (IOException e) {
					defaultReacts.get(ContainerFile.class + ":save")
						 .react(e, getOriginalName(), ios.createGeneralPath(getOriginalName()));
				}
			}

			public default String load() {
				StringBuilder sb = new StringBuilder(4096);
				try ( InputStreamReader isr
					 = new InputStreamReader(createInputStream(), StandardCharsets.UTF_8)) {
					char[] buffer = new char[1024];
					int amount;
					while ((amount = isr.read(buffer)) != -1) {
						sb.append(buffer, 0, amount);
					}
				} catch (IOException e) {
					defaultReacts.get(ContainerFile.class + ":load")
						 .react(e, getOriginalName(), ios.createGeneralPath(getOriginalName()));
					return null;
				}
				return sb.toString();
			}

			public default void deserialize(Object toSave) {
				try ( ObjectOutputStream oos = new ObjectOutputStream(createOutputStream())) {
					oos.writeObject(toSave);
				} catch (IOException ex) {
					throw new IllegalArgumentException(ex);
				}
			}

			public default Object serialize() {
				try ( ObjectInputStream ois = new ObjectInputStream(createInputStream())) {
					return ois.readObject();
				} catch (IOException | ClassNotFoundException ex) {
					throw new IllegalArgumentException(ex);
				}
			}

			public default OutputStream createOutputStream() throws IOException {
				return createOutputStream(false);
			}

			public OutputStream createOutputStream(boolean append) throws IOException;

			public InputStream createInputStream() throws IOException;

			public GeneralPath getChild(String name);
			
			public boolean hasChild(String name);

			public GeneralPath getParentDir();

			public boolean renameTo(String newName);

			public GeneralPath moveTo(GeneralPath newPath);

			public default boolean copyTo(GeneralPath destination) {
				if (destination.exists()) {
					return false;
				}
				try ( InputStream is = createInputStream();  OutputStream os = destination.createOutputStream()) {
					byte[] buffer = new byte[32768];
					int amount;
					while ((amount = is.read(buffer)) != -1) {
						os.write(buffer, 0, amount);
					}
					return true;
				} catch (java.io.IOException ex) {
					throw new IllegalArgumentException(ex);
				}
			}

			public boolean delete();

			public boolean exists();

			public boolean isDir();

			public boolean isEmpty();

			public boolean equals(GeneralPath comparison);

			public GeneralPath[] listFiles();
		}
	}

	/**
	 * Object containing all necessary data for creating a {@link MainChapter hierarchy}
	 * {@link com.schlmgr.gui.engine.objects.templates.BasicData element}. Used for every hierarchy object creating.
	 */
	public static class Data {

		public String name;
		public MainChapter identifier;
		public int[] sf;
		public String description = "";
		public Map<String, Object> tagVals;
		public Container par;

		public Data(String name, MainChapter identifier) {
			this.name = name;
			this.identifier = identifier;
		}

		public Data addSF(int[] successAndFail) {
			sf = successAndFail;
			return this;
		}

		public Data addDesc(String description) {
			this.description = description == null ? "" : description;
			return this;
		}

		public Data addPar(Container parent) {
			par = parent;
			return this;
		}

		public Data addExtra(Map<String, Object> tagValues) {
			tagVals = tagValues;
			return this;
		}

		@Override
		public String toString() {
			return "name=\"" + name + "\", description=" + description + "\", success="
				 + sf[0] + ", fail=" + sf[1] + ", parent=" + par + ", extra="
				 + tagVals.toString();
		}
	}

	/**
	 * Used to avoid data loss, corruption and possible concurrent modification exceptions.
	 */
	public static class Synchronizer {

		private final Map<MainChapter, Integer> hashes = new HashMap<>();

		/**
		 * All keys' of {@link MainChapter#ELEMENTS} hashCodes, which values are currently being used.
		 */
		private final List<Integer> USED = new LinkedList<>();

		public void waitForAccess(MainChapter lock) {
			Integer hashCode = hashes.get(lock);
			if (hashCode == null) {
				hashes.put(lock, hashCode = lock.hashCode());
			}
			synchronized (hashCode) {
				if (USED.contains(hashCode)) try {
					while (USED.contains(hashCode)) {
						hashCode.wait();
					}
				} catch (InterruptedException ie) {
					throw new IllegalThreadStateException("Interrupting is not allowed!");
				}
				USED.add(hashCode);
			}
		}

		public void endAccess(MainChapter unlock) {
			Integer hashCode = hashes.get(unlock);
			synchronized (hashCode) {
				USED.remove(hashCode);
				hashCode.notify();
			}
		}
	}
}
