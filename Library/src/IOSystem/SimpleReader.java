package IOSystem;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import objects.Chapter;
import objects.MainChapter;
import objects.SaveChapter;
import objects.Word;
import objects.templates.Container;

import static IOSystem.Formatter.Data;

/**
 * Creates hierarchy object structure out of {@link Word words} inside {@link SaveChapter}
 * or {@link Chapter} based on the amount of their content.
 * <p>
 * Used sytnax:
 * <ul>
 * <li>chapter:
 * <p>	name of the chapter
 * <p>	{
 * <p>	content of that chapter (including another chapters)
 * <p>	}
 * <li>word:
 * <p>	name\another name\more synonyms;translate\a synonym\...
 * </ul>
 *
 * @author Josef Litoš
 */
public class SimpleReader {

	/**
	 * @param saveTo     where to save the loaded data
	 * @param source     where are the data to be read
	 * @param startIndex the first read chapter (inclusive)
	 * @param endIndex   the last chapter to be read (exclusive), put -1 for no limit
	 * @param minWords   how many words wil be created before the end of loading, put -1
	 *                   for no limit
	 * @return amount of created chapters, words, and translates
	 */
	static int[] loadWords(Container saveTo, Container parent, SimpleChapter source,
	                       int startIndex, int endIndex, int minWords) {
		int[] ret = {0, 0, 0};
		if (minWords == -1) minWords = Integer.MAX_VALUE;
		MainChapter mch = saveTo.getIdentifier();
		Data bd = new Data(null, mch).addPar(saveTo);
		if (source.chaps != null) {
			if (endIndex == -1) endIndex = source.chaps.length;
			startIndex--;
			endIndex--;
			while (startIndex++ < endIndex && ret[1] + source.lines.length < minWords) {
				bd.name = source.chaps[startIndex].name;
				Container ch = source.chaps[startIndex].sch
						? SaveChapter.mkElement(bd) : new Chapter(bd);
				saveTo.putChild(parent, ch);
				int[] x = loadWords(ch, saveTo, source.chaps[startIndex], 0, -1, minWords);
				ret[0]++;
				ret[1] += x[1];
				ret[2] += x[2];
			}
		}
		for (SimpleLine sl : source.lines) {
			ArrayList<Data> bds = new ArrayList<>(sl.words[1].length);
			for (String trl : sl.words[1]) bds.add(new Data(trl, mch).addPar(saveTo));
			for (String s : sl.words[0]) {
				bd.name = s;
				saveTo.putChild(parent, Word.mkElement(bd, new ArrayList<>(bds)));
			}
			ret[1] += sl.words[0].length;
			ret[2] += sl.words[1].length;
		}
		return ret;
	}

	/**
	 * Loads everything to chapters in the specified name format.
	 *
	 * @param source     where to read the data from
	 * @param parent     the object where all created chapters and their content will be
	 *                   stored
	 * @param prevPar    parent of the given param {@code parent}
	 * @param startIndex the first read chapter (inclusive)
	 * @param endIndex   the last chapter to be read (exclusive), put -1 for no limit
	 * @param wordCount  amount of words read before starting another
	 *                   {@link SaveChapter} (after the last chapter in the boundary is
	 *                   loaded)
	 * @param startNum   increasing number used to name all the created chapters
	 * @param psFix      prefix and suffix for the names of created chapters with the
	 *                   current increasing {@code startNum} value
	 * @param b          if the containers should save into separate files
	 * @return the final amount of read chapters and of all created words
	 */
	public static int[] sortLoad(String source, Container parent,
	                             Container prevPar, int startIndex, int endIndex,
	                             int wordCount, int startNum, String[] psFix, boolean b) {
		MainChapter mch = parent.getIdentifier();
		int[] res = {0, 0, 0}, i;
		SimpleChapter sch = getContent(source);
		if (sch.chaps != null)
			if (endIndex == -1 || endIndex > sch.chaps.length) endIndex = sch.chaps.length;
		do {
			Data bd = new Data(psFix[0] + startNum++ + psFix[1], mch).addPar(parent);
			Container c = b ? SaveChapter.mkElement(bd) : new Chapter(bd);
			i = loadWords(c, parent, sch, startIndex, -1, wordCount);
			res[0] += i[0];
			startIndex += i[0];
			res[1] += i[1];
			res[2] += i[2];
			if (i[1] <= 0) break;
			else parent.putChild(prevPar, c);
		} while (startIndex < endIndex);
		return res;
	}

	/**
	 * Loads everything from the given file based on the given parameters.
	 *
	 * @param source     where to read the data from
	 * @param parent     the object where all created chapters and their content will be
	 *                   stored
	 * @param prevPar    parent of the given param {@code parent}
	 * @param startIndex the first read chapter (inclusive)
	 * @param endIndex   the last chapter to be read (exclusive), put -1 for no limit
	 * @param wordCount  amount of words read before the end, put -1 for no limit
	 * @return the final amount of read chapters and of all created words
	 */
	public static int[] simpleLoad(String source, Container parent, Container prevPar,
	                               int startIndex, int endIndex, int wordCount) {
		return loadWords(parent, prevPar,
				getContent(source), startIndex, endIndex, wordCount);
	}

	/**
	 * Creates hierarchy of words and chapters.
	 * Used for converting simple text to database format.
	 *
	 * @param source where to get the content from
	 * @return the created simple hierarchy
	 */
	private static SimpleChapter getContent(String source) {
		String[] lines = source.split("\n");
		return new SimpleChapter(
				null, new Lines(0, Arrays.copyOf(lines, lines.length + 1)));
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
			LinkedList<SimpleLine> sLines = new LinkedList<>();
			LinkedList<SimpleChapter> schs = new LinkedList<>();
			sorter:
			for (int i = lines.i; i < lines.length - 1; i++) {
				if (lines.str[i].length() <= 2) {//2 for occasional char codepoint-13
					if (lines.str[i].length() == 0) continue;
					switch (lines.str[i].charAt(0)) {
						case '}':
							lines.i = i;
							break sorter;
						case '{':
							schs.add(new SimpleChapter(lines.str[(lines.i = i + 1) - 2], lines));
							i = lines.i;
					}
				} else if (lines.str[i + 1].length() > 2) {//every word line has at least 3
					String[] names = new String[2];         //chars - word, ';', translate
					String line = lines.str[i];
					boolean first = true;
					StringBuilder sb = new StringBuilder();
					for (int j = 0; j < line.length(); j++) {
						switch (line.charAt(j)) {
							case '\\':
								switch (line.charAt(j + 1)) {
									case ';':
										j++;
										sb.append(';');
										break;
									case '\\':
									case '/':
									case '(':
									case ')':
										j++;
										sb.append('\\');
									default:
										sb.append(line.charAt(j));
								}
								break;
							case ';':
								names[first ? 0 : 1] = sb.toString();
								if (first) {
									first = false;
									sb.setLength(0);
								} else j = line.length();
								break;
							default:
								sb.append(line.charAt(j));
						}
					}
					if (names[0] == null || sb.length() == 0) {
						Formatter.defaultReacts.get(SimpleReader.class + ":fail").react(line);
						throw new IllegalArgumentException();
					}
					names[1] = sb.toString();
					sLines.add(new SimpleLine(names));
				}
			}
			this.lines = sLines.toArray(new SimpleLine[sLines.size()]);
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

		@Override
		public String toString() {
			return name;
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

	public static String[] nameResolver(String name) {
		List<String> words = new LinkedList<>();
		int start = 0;
		char prev = (char) -1;
		for (int i = 0; i < name.length(); i++) {
			char ch = name.charAt(i);
			if (prev == '\\' && ch != '\\' && ch != '/' && ch != '/' && ch != ')') {
				words.add(name.substring(start, i - 1));
				start = i;
			}
			prev = ch;
		}
		words.add(name.substring(start));
		return words.toArray(new String[words.size()]);
	}
}
