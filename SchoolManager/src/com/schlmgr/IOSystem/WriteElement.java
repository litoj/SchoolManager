package com.schlmgr.IOSystem;

import com.schlmgr.objects.SaveChapter;
import com.schlmgr.objects.templates.BasicData;
import com.schlmgr.objects.templates.Container;

/**
 * This class provides many methods used in the process of saving the data of
 * {@link BasicData} which extends this class.
 *
 * @author Josef Litoš
 */
public interface WriteElement {

	/**
	 * Adds multiple tags to the given {@code sb}.
	 *
	 * @param sb     object containing the data those will be written to the corresponding
	 *               {@link SaveChapter} file
	 * @param e      the currently written {@link BasicData}
	 * @param parent parent of param {@code e}
	 * @param clasS  if {@link Formatter#CLASS class} tag and the corresponding data should be added
	 * @param name   if {@link Formatter#NAME name} tag and the corresponding data should be added
	 * @param sf     if {@link Formatter#SUCCESS success} and {@link Formatter#FAIL fail} tags
	 *               and the corresponding data should be added
	 * @param desc   if {@link Formatter#DESC description} tag and the corresponding data should be added
	 * @param tags   tags to be added
	 * @param vals   values of the given param {@code tags}
	 * @param child  if {@link Formatter#CHILDREN children} tag and the corresponding data should be added
	 * @return the written form of this object
	 */
	default StringBuilder add(StringBuilder sb, BasicData e, Container parent, boolean clasS, boolean name, boolean sf, boolean desc, String[] tags, Object[] vals, boolean child) {
		boolean append;
		if (append = clasS) sb.append('"').append(Formatter.CLASS).append("\": \"").append(e.getClass().getName()).append('"');
		if (name) {
			if (append) sb.append(", ");
			else append = true;
			sb.append('"').append(Formatter.NAME).append("\": \"").append(mkSafe(e)).append('"');
		}
		if (sf && (e.getSF()[0] > 0 || e.getSF()[1] > 0)) {
			if (append) sb.append(", ");
			else append = true;
			if (e.getSF()[0] > 0) sb.append('"').append(Formatter.SUCCESS).append("\": ").append(e.getSF()[0]);
			if (e.getSF()[1] > 0) {
				if (e.getSF()[0] > 0) sb.append(", ");
				sb.append('"').append(Formatter.FAIL).append("\": ").append(e.getSF()[1]);
			}
		}
		if (desc && e.getDesc(parent) != null && !e.getDesc(parent).equals("")) {
			if (append) sb.append(", ");
			else append = true;
			sb.append('"').append(Formatter.DESC).append("\": \"").append(mkSafe(e.getDesc(parent))).append('"');
		}
		if (tags != null && tags.length != 0) {
			for (int i = 0; i < tags.length; i++) {
				if (vals[i] != null && !"".equals(vals[i])) {
					if (append) sb.append(", ");
					else append = true;
					sb.append('"').append(tags[i]).append("\": ");
					if (vals[i] instanceof Boolean || vals[i] instanceof Number) sb.append(vals[i]);
					else sb.append('"').append(mkSafe(vals[i])).append('"');
				}
			}
		}
		if (child) sb.append(", \"").append(Formatter.CHILDREN).append("\": [");
		return sb;
	}

	/**
	 * Simple way of making arrays.
	 *
	 * @param str the content of the returned String array
	 * @return the param {@code str}
	 */
	static String[] str(String... str) {
		return str;
	}

	/**
	 * Simple way of making arrays.
	 *
	 * @param obj the content of the returned Object array
	 * @return the param {@code obj}
	 */
	static Object[] obj(Object... obj) {
		return obj;
	}

	/**
	 * Adds to every {@code '\\'} and {@code '"'} chars from the
	 * {@link #toString()} method an additional {@code '\\'}.
	 *
	 * @param obj object which's name will be made safe
	 * @return the safe form of the given object
	 */
	static String mkSafe(Object obj) {
		return obj.toString().replaceAll("\\\\", "\\\\\\\\")
				.replaceAll("\"", "\\\\\"").replaceAll("\n", "\\\\n").replaceAll("\t", "\\\\t");
	}

	/**
	 * Adds requested amount of tabs to a new line in the text.
	 *
	 * @param sb      source where to add the text
	 * @param tabs    amount of tabs to be added
	 * @param toWrite is added after the tabs, if there is nothing to be added, just decrease
	 *                the amount of tabs and put here '\t'
	 * @return this object, the method has been called in.
	 */
	default WriteElement tabs(StringBuilder sb, int tabs, char toWrite) {
		sb.append('\n');
		for (int i = tabs; i > 0; i--) sb.append('\t');
		sb.append(toWrite);
		return this;
	}

	/**
	 * This method is for Formatter class to writeData Element's children. For
	 * different implementations of Element class can occur different ways of
	 * writing.
	 *
	 * @param sb            object containing the data those will be written to the corresponding
	 *                      {@link SaveChapter} file
	 * @param tabs          current amount of spaces on every new line
	 * @param currentParent parent of the object providing this method
	 * @return the same object as parameter {@code sb} or null, if nothing has been added
	 */
	StringBuilder writeData(StringBuilder sb, int tabs, com.schlmgr.objects.templates.Container currentParent);
}
