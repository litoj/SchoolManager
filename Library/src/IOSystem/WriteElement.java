package IOSystem;

import objects.templates.BasicData;
import objects.templates.Container;

/**
 * This class provides many methods used in the process of saving the data of
 * {@link BasicData} which extends this class.
 *
 * @author Josef Litoš
 */
public interface WriteElement {

	/**
	 * This class handles and provides methods for creating a text representation of an object,
	 * enough so it can be recreated from that text afterwards without any loss.
	 * Multi-threaded access is not supported.
	 */
	class ContentWriter {
		public int tabs = -1;
		public boolean first;
		public final StringBuilder sb = new StringBuilder();
		private BasicData e;
		private Container par;

		/**
		 * Initiates the writing of a new object.
		 *
		 * @param item the object that will be currently written
		 * @param par  parent of this object
		 * @return this object
		 */
		public ContentWriter startWritingItem(BasicData item, Container par) {
			e = item;
			this.par = par;
			first = true;
			return tabs(++tabs, '{');
		}

		/**
		 * Closes the currently written object. No more data of that object can be added afterwards.
		 *
		 * @return this object
		 */
		public ContentWriter endWritingItem() {
			tabs--;
			sb.append('}');
			return this;
		}

		/**
		 * Add the specified amount of '\t' chars on a new line, ending with given character.
		 *
		 * @param tabs    the amount of '\t' chars to be added
		 * @param toWrite the char to be added to the end
		 * @return this object
		 */
		public ContentWriter tabs(int tabs, char toWrite) {
			sb.append('\n');
			for (int i = tabs; i > 0; i--) sb.append('\t');
			sb.append(toWrite);
			return this;
		}

		/**
		 * Writes the class of the current object.
		 *
		 * @return this object
		 */
		public ContentWriter addClass() {
			if (first) first = false;
			else sb.append(", ");
			sb.append('"').append(Formatter.CLASS).append("\": \"")
					.append(e.getClass().getName()).append('"');
			return this;
		}

		/**
		 * Writes the name of the current object.
		 *
		 * @return this object
		 */
		public ContentWriter addName() {
			if (first) first = false;
			else sb.append(", ");
			sb.append('"').append(Formatter.NAME).append("\": \"")
					.append(mkSafe(e.getName())).append('"');
			return this;
		}

		/**
		 * Writes the success and fail values of the current object (if not 0).
		 *
		 * @return this object
		 */
		public ContentWriter addSF() {
			if (e.getSF()[0] > 0 || e.getSF()[1] > 0) {
				if (first) first = false;
				else sb.append(", ");
				if (e.getSF()[0] > 0) sb.append('"').append(Formatter.SUCCESS)
						.append("\": ").append(e.getSF()[0]);
				if (e.getSF()[1] > 0) {
					if (e.getSF()[0] > 0) sb.append(", ");
					sb.append('"').append(Formatter.FAIL).append("\": ").append(e.getSF()[1]);
				}
			}
			return this;
		}

		/**
		 * Writes the description of the current object. The description depends on the
		 * parent specified in the initialization of the object.
		 *
		 * @return this object
		 */
		public ContentWriter addDesc() {
			if (e.getDesc(par) != null && !e.getDesc(par).equals("")) {
				if (first) first = false;
				else sb.append(", ");
				sb.append('"').append(Formatter.DESC).append("\": \"")
						.append(mkSafe(e.getDesc(par))).append('"');
			}
			return this;
		}

		/**
		 * Writes all the given data, where {@code toWrite[i][0]} is the parameter name
		 * and {code toWrite[i][1]} is the corresponding value.
		 *
		 * @param toWrite all other information necessary information about the current object.
		 * @return this object
		 */
		public ContentWriter addExtra(Object[]... toWrite) {
			for (Object[] data : toWrite) {
				if (data[1] != null && !data[1].toString().isEmpty()) {
					if (first) first = false;
					else sb.append(", ");
					sb.append('"').append(data[0]).append("\": ");
					if (data[1] instanceof Number || data[1] instanceof Boolean)
						sb.append(data[1]);
					else sb.append('"').append(mkSafe(data[1])).append('"');
				}
			}
			return this;
		}

		/**
		 * Writes the children of the current object. This method can be used only
		 * if the current object is a {@link Container}. This method is usually called
		 * the last of writing the object's data.
		 *
		 * @return this object
		 */
		public ContentWriter addChildren() {
			Container e = (Container) this.e;
			if (first) first = false;
			else sb.append(", ");
			sb.append('"').append(Formatter.CHILDREN).append("\": [");
			boolean first = true;
			for (BasicData bd : e.getChildren(par)) {
				if (bd.isEmpty(e)) continue;
				if (first) first = false;
				else sb.append(',');
				startWritingItem(bd, e);
				bd.writeData(this);
				endWritingItem();
			}
			return tabs(tabs, ']');
		}

		@Override
		public String toString() {
			return sb.substring(1);
		}
	}

	/**
	 * Adds to every {@code '\\'} and {@code '"'} chars an additional {@code '\\'}.
	 *
	 * @param obj object which's name will be made safe
	 * @return the safe form of the given object
	 */
	static String mkSafe(Object obj) {
		return obj.toString().replaceAll("\\\\", "\\\\\\\\")
				.replaceAll("\"", "\\\\\"").replaceAll("\n", "\\\\n")
				.replaceAll("\t", "\\\\t");
	}

	/**
	 * Writes only the absolute necessary information about this object.
	 *
	 * @param cw object containing the data those will be written to the
	 *           corresponding file, the maintainer of the writing process
	 * @return the same object as parameter {@code cw}
	 */
	ContentWriter writeData(ContentWriter cw);
}
