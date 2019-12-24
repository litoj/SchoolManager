package com.schlmgr.IOSystem;

import com.schlmgr.objects.MainChapter;
import com.schlmgr.objects.Reference;
import com.schlmgr.objects.templates.Container;
import com.schlmgr.objects.templates.ContainerFile;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class is used to operate with text files. The format of all files containing
 * necessary data of every {@link com.schlmgr.objects.templates.BasicData element} is
 * supposed to look like json, but the code is all thought and writen by the author.
 * <p>
 * The content of this class is partially platform dependant.
 *
 * @author Josef Litoš
 */
public class Formatter {

	/**
	 * Default {@link OnFailListener OFLs} for various actions, keys should be in format:<p>
	 * fullClassName + ':' + specification (if necessary);<p>
	 * or just specification, if it is for global usage.
	 */
	public static final Map<String, OnFailListener> defaultOFLs = new HashMap<>();

	/**
	 * This method has to be called at the start of the program.
	 */
	public static void loadSettings() {
		mkDefaultOFLs();
		setts = new File("settings.dat");
		if (setts.exists()) {
			settings = (Map<String, String>) serialize(setts.getName(), true);
			objDir = createDir(new File(mkRealPath(settings.get("objdir"))));
		} else {
			objDir = createDir(new File("School objects"));
			settings.put("objdir", objDir.getPath());
			settings.put("language", "cz");
			settings.put("randomType", "true");
			deserializeTo(setts.getName(), settings, true);
		}
	}
	
	/**
	 * Creates the default exception handlers for various cases.<p>
	 * This method is purely platform dependant.
	 */
	private static void mkDefaultOFLs() {
		defaultOFLs.put(ContainerFile.class + ":load", (e, o) -> {
			System.err.println("Failed to load file: " +
					((File) o[0]).getAbsolutePath() + ", when loading data of: " + o[1] + ", of type: " +
					o[1].getClass() + ':');
			Logger.getLogger(ContainerFile.class.getName()).log(Level.SEVERE, null, e);
		});
		defaultOFLs.put(ContainerFile.class + ":save", (e, o) -> {
			System.err.println("Failed to save file: " +
					((File) o[0]).getAbsolutePath() + ", when saving data of: " + o[1] + ", of type: " +
					o[1].getClass() + ':');
			Logger.getLogger(ContainerFile.class.getName()).log(Level.SEVERE, null, e);
		});
		defaultOFLs.put(Reference.class + ":not_found", (e, o) -> System.err.println("Referenced element not found: " +
				o[0] + ", parent: " + o[1] + ", of type: " + o[1].getClass() +
				(o[1].getClass() == MainChapter.class ? '!' : (", with parent: " + o[2] +
						", of type: " + o[2].getClass()))));
	}

	/**
	 * Listens for any Exception from specified locations in the program.
	 */
	public interface OnFailListener {
		void onFail(Exception e, Object ... moreInfo);
	}

	/**
	 * Path to the directory that contains all {@link MainChapter hierarchies's} folders and data
	 */
	static File objDir;
	public static final String CLASS = "class", NAME = "name", SUCCESS = "s",
			FAIL = "f", CHILDREN = "cdrn", DESC = "desc";
	static Map<String, String> settings = new HashMap<>();
	static File setts;

	public static File getPath() {
		return objDir;
	}

	/**
	 * @param key tag of the setting
	 * @return the value of the given key or {@code null} if settings doesn't contain that key
	 * @see Map#get(Object)
	 */
	public static String getSetting(String key) {
		return settings.get(key);
	}

	/**
	 * @param key   tag to be added or overrated in the settings of this program
	 * @param value value associated with the given tag
	 * @see Map#put(Object, Object)
	 */
	public static void putSetting(String key, String value) {
		settings.put(key, value);
		deserializeTo(setts, settings);
	}

	/**
	 * Removes the given key from the {@link Formatter#settings}.
	 *
	 * @param key the tag that will be removed
	 */
	public static void removeSetting(String key) {
		settings.remove(key);
		deserializeTo(setts, settings);
	}

	/**
	 * This method changes the directory of saves of hierarchies.
	 *
	 * @param path the directory for this application
	 */
	public static void changeDir(String path) {
//		if (path.charAt(0) == System.getProperty("user.dir").charAt(0))
//			path = path.replaceFirst(System.getProperty("user.dir").charAt(0) + ":", "§");
		putSetting("objdir", path);
		objDir = createDir(new File(path));
	}

	/**
	 * Makes a real path.
	 *
	 * @param path § on index 0 changes to current disc name (made for Windows OS)
	 * @return the corrected path
	 */
	public static String mkRealPath(String path) {
		return path.charAt(0) == '§' ? path.replaceFirst("§",
				System.getProperty("user.dir").charAt(0) + ":") : path;
	}

	/**
	 * Makes sure the given path is a created directory.
	 *
	 * @param dir § on index 0 changes to current disc name
	 * @return the created file
	 */
	public static File createDir(File dir) {
		if (!dir.exists()) {
			dir.mkdir();
			try {
				dir.createNewFile();
			} catch (IOException ex) {
				throw new IllegalArgumentException(ex);
			}
		}
		return dir;
	}

	public static void deserializeTo(File filePath, Object toSave) {
		deserializeTo(filePath.toString(), toSave, false);
	}

	public static void deserializeTo(String filePath, Object toSave, boolean internal) {
		try (ObjectOutputStream oos = new ObjectOutputStream(internal ?
				new java.io.FileOutputStream(filePath) :
				new java.io.FileOutputStream(filePath))) {
			oos.writeObject(toSave);
		} catch (IOException ex) {
			throw new IllegalArgumentException(ex);
		}
	}

	public static Object serialize(File filePath) {
		return serialize(filePath.toString(), false);
	}

	public static Object serialize(String filePath, boolean internal) {
		try (ObjectInputStream ois = new ObjectInputStream(internal ?
				new java.io.FileInputStream(filePath) : new java.io.FileInputStream(filePath))) {
			return ois.readObject();
		} catch (IOException | ClassNotFoundException ex) {
			throw new IllegalArgumentException(ex);
		}
	}

	public static String loadFile(File filePath) {
		StringBuilder sb = new StringBuilder();
		try (FileReader fr = new FileReader(filePath)) {
			char[] buffer = new char[1024];
			int amount;
			while ((amount = fr.read(buffer)) != -1) sb.append(buffer, 0, amount);
		} catch (IOException ex) {
			throw new IllegalArgumentException(ex);
		}
		return sb.toString();
	}

	public static void saveFile(String toSave, File filePath) {
		try (FileWriter fw = new FileWriter(filePath)) {
			fw.write(toSave);
		} catch (IOException ex) {
			throw new IllegalArgumentException(ex);
		}
	}

	/**
	 * Object containing all necessary data for creating a
	 * {@link MainChapter hierarchy} {@link com.schlmgr.objects.templates.BasicData element}.
	 */
	public static class Data {

		public String name;
		public MainChapter identifier;
		public int[] sf;
		public String description = "";
		public Object[] tagVals;
		public Container par;

		Data(String name, MainChapter identifier, int s, int f, String description, Container parent, Object... tagValues) {
			this(name, identifier, s, f, description, parent);
			tagVals = tagValues;
		}

		public Data(String name, MainChapter identifier, int s, int f, String description, Container parent) {
			this(name, identifier, s, f, description);
			par = parent;
		}

		public Data(String name, MainChapter identifier, String description, Container parent) {
			this(name, identifier, description);
			par = parent;
		}

		public Data(String name, MainChapter identifier, int s, int f, Container parent) {
			this(name, identifier, s, f);
			par = parent;
		}

		public Data(String name, MainChapter identifier, int s, int f, String description) {
			this(name, identifier, description);
			this.sf = new int[]{s, f};
		}

		public Data(String name, MainChapter identifier, int s, int f) {
			this(name, identifier);
			this.sf = new int[]{s, f};
		}

		public Data(String name, MainChapter identifier, String description) {
			this(name, identifier);
			this.description = description == null ? "" : description;
		}

		public Data(String name, MainChapter identifier, Container parent) {
			this(name, identifier);
			par = parent;
		}

		public Data(String name, MainChapter identifier) {
			this.name = name;
			this.identifier = identifier;
		}

		@Override
		public String toString() {
			return name;
		}
	}
}
