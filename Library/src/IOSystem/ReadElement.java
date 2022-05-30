package IOSystem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import objects.MainChapter;
import objects.SaveChapter;
import objects.templates.BasicData;
import objects.templates.Container;

import static IOSystem.Formatter.Data;
import IOSystem.Formatter.IOSystem.GeneralPath;

/**
 * This class provides methods used for reading the data of any {@link BasicData element} from its
 * file.
 *
 * @author Josef Litoš
 */
public abstract class ReadElement {

	/**
	 * This class holds all data for an object to be created from its content. Also provides various
	 * methods for obtaining the stored data.
	 */
	public static class Content {

		/**
		 * All parameters associated with this object.
		 */
		public final HashMap<String, Object> params;
		public MainChapter identifier;

		/**
		 * Creates a new holder for storing the data of an object that is currently being read.
		 *
		 * @param parameters the initial map with parameters for this object (usually empty)
		 * @param main the head object of the hierarchy, which this data belongs to (or {@code null}, if
		 * the hierarchy is being loaded)
		 */
		private Content(HashMap<String, Object> parameters, MainChapter main) {
			params = parameters;
			identifier = main;
		}

		/**
		 * Creates the corresponding type of item specified by {@link Formatter#CLASS class} parameter.
		 *
		 * @param par the parent of the object to be created
		 * @return the created object
		 * @throws IllegalStateException if the class parameter is missing
		 */
		public BasicData getItem(Container par) {
			if (params.get(Formatter.CLASS) == null) {
				throw new IllegalStateException("Class parameter missing");
			}
			try {
				return (BasicData) Class.forName(params.remove(Formatter.CLASS).toString())
					 .getDeclaredMethod("readData", Content.class, Container.class)
					 .invoke(null, this, par);
			} catch (IllegalAccessException | java.lang.reflect.InvocationTargetException
				 | NoSuchMethodException | SecurityException | ClassNotFoundException ex) {
				throw new IllegalArgumentException(ex);
			}
		}

		/**
		 * Converts all the stored data (except for its children) to a {@link Data} object used for
		 * transferring the data about any hierarchy element to be created.
		 *
		 * @param parent parent of the object
		 * @return the converted content without {@link Formatter#CHILDREN children} parameter
		 */
		public Data getData(Container parent) {
			Data d = new Data(params.remove(Formatter.NAME).toString(), identifier != null
				 ? identifier : parent == null ? null : parent.getIdentifier()).addPar(parent);
			int sf[] = {0, 0};
			Map<String, Object> extra = new HashMap<>();
			for (String key : ((Map<String, Object>) params.clone()).keySet()) {
				switch (key) {
					case Formatter.SUCCESS:
						sf[0] = (Integer) params.remove(key);
						break;
					case Formatter.FAIL:
						sf[1] = (Integer) params.remove(key);
						break;
					case Formatter.DESC:
						d.addDesc(params.remove(key).toString());
						break;
					default:
						if (key.equals(Formatter.CHILDREN)) {
							continue;
						}
						extra.put(key, params.get(key));
				}
			}
			return d.addSF(sf).addExtra(extra);
		}

		/**
		 * Creates all children stored in this object.
		 *
		 * @param childrenPar the item created from this object, and the parent of its children
		 * @return the children of this object
		 */
		public List<BasicData> getChildren(Container childrenPar) {
			List<BasicData> children = new ArrayList<>();
			for (Content c : ((List<Content>) params.remove(Formatter.CHILDREN))) {
				children.add(c.getItem(childrenPar));
			}
			return children;
		}

		public List<Data> getChildrenData(Container childrenPar) {
			List<Data> children = new ArrayList<>();
			for (Content c : ((List<Content>) params.remove(Formatter.CHILDREN))) {
				children.add(c.getData(childrenPar));
			}
			return children;
		}

		@Override
		public String toString() {
			return params.toString();
		}
	}

	/**
	 * Used for any text reading to keep track of the current position of the reader.
	 */
	public static final class ContentReader {

		/**
		 * Source to be read, from position {@link #index index}.
		 */
		private final String str;
		/**
		 * Where the given {@link #str source} should be read from.
		 */
		private int index = -1;

		/**
		 * The main object of the loaded object tree.
		 */
		public final Content mContent;
		/**
		 * The hierarchy which all the data belongs to.
		 */
		public MainChapter identifier;

		/**
		 * Resolves the given String into the object tree.
		 *
		 * @param s the source with all objects data
		 * @param main the hierarchy this content belongs to or {@code null} if the hierarchy is being
		 * created
		 */
		public ContentReader(String s, MainChapter main) {
			this.str = s;
			identifier = main;
			mContent = loadContent();
		}

		/**
		 * Loads the content of the following object in the {@link #str source}.
		 *
		 * @return object with all data belonging to it
		 */
		private Content loadContent() {
			Content created = new Content(new HashMap<>(), identifier);
			while (str.charAt(++index) != '{') ;
			while (loadValue(created)) ;
			return created;
		}

		/**
		 * Adds the next read value to the given object.
		 *
		 * @param content the object to add the value to
		 * @return {@code false} if the end of the current object notation has been reached,
		 * {@code true} if a value was successfully added
		 */
		private boolean loadValue(Content content) {
			StringBuilder builder = new StringBuilder();
			char ch;
			while ((ch = str.charAt(++index)) != '"') {
				if (ch == '}') {
					return false;
				}
			}
			while ((ch = str.charAt(++index)) != '"') {
				builder.append(ch);
			}
			String key = builder.toString();
			while ((ch = str.charAt(++index)) != '"' && !Character.isLetterOrDigit(ch)) {
				if (ch == '{') { //found an object
					index--;
					content.params.put(key, loadContent());
					return true;
				} else if (ch == '[') { //found an array
					List<Content> items = new LinkedList<>();
					while ((ch = str.charAt(++index)) != ']') {
						if (ch == '{') {
							index--;
							items.add(loadContent());
						}
					}
					content.params.put(key, items);
					return true;
				}
			}
			boolean string = ch == '"';
			builder = new StringBuilder();
			if (string) {
				while ((ch = str.charAt(++index)) != '"') {
					if (ch == '\\') {
						switch (ch = str.charAt(++index)) {
							case 'n':
								ch = '\n';
								break;
							case 't':
								ch = '\t';
								break;
						}
					}
					builder.append(ch);
				}
			} else {
				do {
					builder.append(ch);
				} while (Character.isLetterOrDigit(ch = str.charAt(++index)) || ch == '.');
				ch = str.charAt(--index);
			}
			try {
				if (string) {
					content.params.put(key, builder.toString());
				} else if (!Character.isDigit(builder.charAt(0))) {
					content.params.put(key, Boolean.valueOf(builder.toString()));
				} else if (ch == 'L') {
					content.params.put(key, Long.valueOf(builder.toString()));
				} else if (ch == 'f') {
					content.params.put(key, Float.valueOf(builder.toString()));
				} else if (builder.indexOf(".") > -1 || builder.indexOf("E") > -1
					 && builder.charAt(0) != '0') {
					content.params.put(key, Double.valueOf(builder.toString()));
				} else {
					content.params.put(key, Integer.valueOf(builder.toString()));
				}
			} catch (IllegalArgumentException iae) {
				throw new IllegalArgumentException("On field '" + key + "', before char: "
					 + index + "\n..." + str.substring(index > 100 ? index - 100 : 0, index)
					 + "<-- this", iae);
			}
			return true;
		}
	}

	/**
	 * Loads all data into the respective {@link SaveChapter} using {@link #readData(Content, Container)
	 * method} which all objects have to implement in static form.
	 *
	 * @param toLoad file containing the data of the loaded object
	 * @param identifier the head object of the parent hierarchy
	 * @param parent the parent of the loaded object
	 * @return the main object loaded from the given file
	 */
	public static Container loadFile(GeneralPath toLoad, MainChapter identifier, Container parent) {
		return (Container) new ContentReader(toLoad.load(), identifier).mContent.getItem(parent);
	}

	/**
	 * Calls the corresponding {@link #readData(Source, Container) readData} method implementation to
	 * create its instance. Middle–step between every loaded {@link BasicData element}.
	 *
	 * @param src {@link Source}
	 * @param cp parent of the read object
	 * @return the loaded object
	 */
	/**
	 * Creates an {@link objects.templates.BasicData} from the loaded data.
	 *
	 * @param src {@link Content}
	 * @param cp parent of the object to be created
	 * @return the loaded object
	 */
	public abstract BasicData readData(Content src, Container cp);
}
