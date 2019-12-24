package com.schlmgr.IOSystem;

import com.schlmgr.objects.Chapter;
import com.schlmgr.objects.MainChapter;
import com.schlmgr.objects.SaveChapter;
import com.schlmgr.objects.Word;
import com.schlmgr.objects.templates.Container;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedList;

import static com.schlmgr.IOSystem.Formatter.Data;

/**
 * @author Josef Litoš
 */
public class SimpleReader {

	/**
	 * @param saveTo     where to save the loaded data
	 * @param source     where are the data to be read
	 * @param startIndex the first read chapter (inclusive)
	 * @param endIndex   the last chapter to be read (exclusive), put -1 for no limit
	 * @param minWords   how many words wil be created before the end of loading, put -1 for no limit
	 * @return amount of created chapters, words, and translates
	 */
	static int[] loadWords(Container saveTo, Container parent, SimpleChapter[] source, int startIndex, int endIndex, int minWords) {
		int[] ret = {0, 0, 0};
		if (startIndex >= source.length) return ret;
		if (endIndex == -1) endIndex = source.length;
		if (minWords == -1) minWords = Integer.MAX_VALUE;
		MainChapter mch = saveTo.getIdentifier();
		Data bd = new Data(null, mch);
		do {
			bd.par = saveTo;
			ret[0]++;
			bd.name = source[startIndex].name;
			Container ch = source[startIndex].sch ? SaveChapter.mkElement(bd) : new Chapter(bd);
			saveTo.putChild(parent, ch);
			if (source[startIndex].chaps != null) {
				int[] x = loadWords(ch, saveTo, source[startIndex].chaps, 0, -1, minWords);
				ret[1] += x[1];
				ret[2] += x[2];
			}
			bd.par = ch;
			for (SimpleLine sl : source[startIndex].lines) {
				ArrayList<Data> bds = new ArrayList<>(sl.words[1].length);
				for (String trl : sl.words[1]) bds.add(new Data(trl, mch, ch));
				for (String s : sl.words[0]) {
					bd.name = s;
					ch.putChild(saveTo, Word.mkElement(bd, new ArrayList<>(bds)));
				}
				ret[1] += sl.words[0].length;
				ret[2] += sl.words[1].length;
			}
		} while (++startIndex < endIndex && ret[1] <= minWords);
		return ret;
	}

	/**
	 * Loads everything from the given file based on the given parameters.
	 *
	 * @param source     where to read the data from
	 * @param parent     the object where all created chapters and their content will be stored
	 * @param prevPar    parent of the given param {@code parent}
	 * @param startIndex the first read chapter (inclusive)
	 * @param endIndex   the last chapter to be read (exclusive), put -1 for no limit
	 * @param wordCount  amount of words read before starting another
	 *                   {@link SaveChapter} (after the last chapter in the boundary is loaded)
	 * @param startNum   increasing number used to name all the created chapters
	 * @param psFix      prefix and suffix for the names of created chapters with the
	 *                   current increasing {@code startNum} value
	 * @param b          if the containers should save into separate files
	 * @return the final amount of read chapters and of all created words
	 */
	public static int[] sortLoad(File source, Container parent, Container prevPar, int startIndex, int endIndex, int wordCount, int startNum, String[] psFix, boolean b) {
		MainChapter mch = parent.getIdentifier();
		int[] res = {0, 0, 0}, i;
		SimpleChapter[] sch = getContent(source);
		if (endIndex == -1 || endIndex > sch.length) endIndex = sch.length;
		do {
			Data bd = new Data(psFix[0] + startNum++ + psFix[1], mch, parent);
			Container c = b ? SaveChapter.mkElement(bd) : new Chapter(bd);
			parent.putChild(prevPar, c);
			i = loadWords(c, parent, sch, startIndex, -1, wordCount);
			res[0] += i[0];
			startIndex += i[0];
			res[1] += i[1];
			res[2] += i[2];
		} while (i[0] > 0 && startIndex < endIndex);
		return res;
	}

	/**
	 * Loads everything from the given file based on the given parameters.
	 *
	 * @param source     where to read the data from
	 * @param parent     the object where all created chapters and ther content will be stored
	 * @param prevPar    parent of the given param {@code parent}
	 * @param startIndex the first read chapter (inclusive)
	 * @param endIndex   the last chapter to be read (exclusive), put -1 for no limit
	 * @param wordCount  amount of words read before the end, put -1 for no limit
	 * @return the final amount of read chapters and of all created words
	 */
	public static int[] simpleLoad(File source, Container parent, Container prevPar, int startIndex, int endIndex, int wordCount) {
		return loadWords(parent, prevPar, getContent(source), startIndex, endIndex, wordCount);
	}

	/**
	 * Creates hierarchy of words and chapters. Used for converting simple text to programs database.
	 *
	 * @param source where to get the content from
	 * @return the created simple hierarchy
	 */
	private static SimpleChapter[] getContent(File source) {
		return new SimpleChapter(null, new Lines(0, Formatter.loadFile(source).split("\n"))).chaps;
	}

	private static class Lines {

		int i;
		final int length;
		final String[] str;

		Lines(int index, String[] lines) {
			i = index;
			str = lines;
			length = lines.length;
		}
	}

	/**
	 * Stores chapters and word-lines. Part of the simple hierarchy loading.
	 */
	private static class SimpleChapter {

		SimpleChapter[] chaps;
		SimpleLine[] lines;
		String name;
		boolean sch;

		SimpleChapter(String name, Lines lines) {
			this.name = name;
			sch = false;
			LinkedList<SimpleLine> sls = new LinkedList<>();
			LinkedList<SimpleChapter> schs = new LinkedList<>();
			sorter:
			for (int i = lines.i; i < lines.length - 1; i++) {
				if (lines.str[i].length() <= 1) {
					if (lines.str[i].length() == 0) continue;
					switch (lines.str[i].charAt(0)) {
						case '}':
							lines.i = i;
							break sorter;
						case '{':
							schs.add(new SimpleChapter(lines.str[(lines.i = i + 1) - 2], lines));
							i = lines.i;
					}
				} else if (lines.str[i + 1].length() != 1 || lines.str[i + 1].charAt(0) != '{') {
					LinkedList<String> ll = new LinkedList<>();
					String str = lines.str[i];
					StringBuilder sb = new StringBuilder();
					for (int j = 0; j < str.length(); j++) {
						switch (str.charAt(j)) {
							case '\\':
								switch (str.charAt(j + 1)) {
									case ';':
										j++;
										sb.append(';');
										break;
									case '\\':
									case '/':
										j++;
										sb.append('\\');
									default:
										sb.append(str.charAt(j));
								}
								break;
							case ';':
								ll.add(sb.toString());
								sb.setLength(0);
								break;
							default:
								sb.append(str.charAt(j));
						}
					}
					ll.add(sb.toString());
					sls.add(new SimpleLine(ll.toArray(new String[0])));
				}
			}
			this.lines = sls.toArray(new SimpleLine[sls.size()]);
			if (!schs.isEmpty()) {
				chaps = schs.toArray(new SimpleChapter[schs.size()]);
				if (schs.size() > 10) {
					sch = true;
					return;
				}
				int words = 0;
				for (SimpleChapter sc : schs) {
					if (!sc.sch) words += sc.lines.length;
					if (words > 80) {
						sch = true;
						break;
					}
				}
			}
		}
	}

	/**
	 * Contains words on one line sorted by their position.
	 */
	private static class SimpleLine {

		String[][] words;

		public SimpleLine(String[] names) {
			words = new String[][]{split(names[0]), split(names[1])};
		}

		private static String[] split(String src) {
			if (src.indexOf('\\') == -1) return new String[]{src};
			LinkedList<String> array = new LinkedList<>();
			int i = 0;
			for (int j = 0; j < src.length() - 1; j++) {
				if (src.charAt(j) == '\\') {
					switch (src.charAt(j + 1)) {
						case '\\':
						case '/':
							j++;
							break;
						default:
							array.add(src.substring(i, j));
							i = ++j;
					}
				}
			}
			array.add(src.substring(i));
			return array.toArray(new String[0]);
		}
	}
}
