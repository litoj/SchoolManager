package IOSystem;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import objects.MainChapter;
import objects.templates.Container;
import objects.templates.ContainerFile;
import testing.Test;

/**
 * This class is used to operate with text files. The format of all files containing necessary data
 * of every {@link objects.templates.BasicData element} is json, but the code was implemented by the
 * author without using any templates.
 *
 * @author Josef Litoš
 */
public class Formatter {

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
	 * @param key tag of the setting
	 * @return the value of the given key or {@code null} if settings doesn't contain that key
	 * @see Map#get(Object)
	 */
	public static Object getSetting(String key) {
		return ios.settings.get(key);
	}

	/**
	 * @param key tag to be added or overrated in the settings of this program
	 * @param value value associated with the given tag
	 * @see Map#put(Object, Object)
	 */
	public static void putSetting(String key, Object value) {
		ios.settings.put(key, value);
		ios.settsPath.deserialize(ios.settings);
	}

	/**
	 * Removes the given key from the {@link IOSystem#settings}.
	 *
	 * @param key the tag that will be removed
	 */
	public static void removeSetting(String key) {
		ios.settings.remove(key);
		ios.settsPath.deserialize(ios.settings);
	}

	/**
	 * This method changes the directory of saves of hierarchies.
	 *
	 * @param path the directory for this application as String (either path or uri)
	 * @return if the dir was changed, {@code false} when given same dir
	 */
	public static boolean changeDir(String path) {
		if (ios.subjDir.getOriginalName().equals(path)) {
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
		putSetting("subjdir", path);
		return true;
	}

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
		 * Settings file of the program, contains all {@link #settings hierarchy independant variables}.
		 */
		protected GeneralPath settsPath;
		/**
		 * All file-stored variables and options for the program.
		 */
		protected Map<String, Object> settings = new HashMap<>();

		/**
		 * Path to the directory that contains all {@link MainChapter hierarchies's} folders and data.
		 */
		protected GeneralPath subjDir;

		/**
		 * Loads all stored variables and defines its extension as the {@link Formatter#ios}.
		 *
		 * @param setts {@link #settsPath}
		 */
		protected IOSystem(String setts) {
			if (Formatter.ios != null) {
				return;
			}
			Formatter.ios = this;
			mkDefaultReacts();
			this.settsPath = createGeneralPath(setts);
			if (settsPath.exists()) {
				try {
					settings = (Map<String, Object>) settsPath.serialize();
					try {
						changeDir(settings.get("subjdir").toString());
					} catch (Exception e) {
						defaultReacts.get(Formatter.class + ":newSrcDir")
							 .react(e, settings.get(""));
						resetDir();
					}
					Object value;
					if ((value = settings.get("defaultTestTime")) != null) {
						Test.setDefaultTime((Integer) value);
					} else {
						settings.put("defaultTestTime", 180);
					}
					if ((value = settings.get("isClever")) != null) {
						Test.setClever((Boolean) value);
					} else {
						settings.put("isClever", true);
					}
					if ((value = settings.get("testAmount")) != null) {
						Test.setAmount((Integer) value);
					} else {
						settings.put("testAmount", 10);
					}
					setDefaults(false);
				} catch (Exception e) {
					defaultReacts.get(Formatter.class + ":newSrcDir").react(e, setts);
					resetDir();
				}
			}
			if (subjDir == null) {
				resetDir();
				settings.put("subjdir", getDefaultSubjectsDir());
				settings.put("defaultTestTime", 180);
				settings.put("isClever", true);
				settings.put("testAmount", 10);
				setDefaults(true);
				settsPath.deserialize(settings);
			}
		}

		/**
		 * Loads and sets all outside-library used values stored in {@link #settings}.
		 *
		 * @param first if the program returns to its first-launch state (reset)
		 */
		protected void setDefaults(boolean first) {
		}

		/**
		 * Creates the default exception handlers for various cases.<p>
		 * This method is completely platform dependant.
		 */
		protected abstract void mkDefaultReacts();

		public GeneralPath createGeneralPath(String src) {
			return new FilePath(src);
		}

		/**
		 * @return default {@link #subjDir objects storage}
		 */
		public abstract String getDefaultSubjectsDir();

		/**
		 * Any necessary actions needed when the {@link #subjDir objects storage} is changed.
		 *
		 * @param newDir the new directory for storing school subjects
		 * @return the path that will be saved to the {@link #settsPath settings}
		 */
		protected GeneralPath changeDir(String newDir) {
			return subjDir = getIOSystem().createGeneralPath(newDir);
		}

		/**
		 * Makes a real path.
		 *
		 * @param path § on index 0 changes to current disc name (for USB disc portability)
		 * @return the corrected path
		 */
		public abstract String mkRealPath(String path);

		public static interface GeneralPath {

			public String getOriginalName();
			
			public String getName();

			public default void save(String content) {
				try ( OutputStreamWriter osw
					 = new OutputStreamWriter(createOutputStream(), StandardCharsets.UTF_8)) {
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

			public OutputStream createOutputStream() throws IOException;

			public InputStream createInputStream() throws IOException;

			public void deserialize(Object toSave);

			public Object serialize();

			public GeneralPath getChild(String name);

			public GeneralPath getParentDir();

			public boolean renameTo(String newName);

			public GeneralPath moveTo(GeneralPath newPath);

			public default boolean copyTo(GeneralPath destination) {
				if (destination.exists()) {
					return false;
				}
				try ( InputStream is = createInputStream();
					 OutputStream os = destination.createOutputStream()) {
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

			public GeneralPath[] listFiles();
		}
	}

	/**
	 * Object containing all necessary data for creating a {@link MainChapter hierarchy}
	 * {@link objects.templates.BasicData element}. Used for every hierarchy object creating.
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
