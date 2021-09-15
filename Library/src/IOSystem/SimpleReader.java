package IOSystem;

import IOSystem.Formatter.Data;
import java.util.LinkedList;
import java.util.List;

import objects.Chapter;
import objects.SaveChapter;
import objects.Word;
import objects.templates.Container;
import objects.templates.BasicData;

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
public final class SimpleReader {
	
	public final int[] result = {0, 0, 0};
	public final List<BasicData> added = new LinkedList<>();
	final String src;
	int i = -1;
	int lineStart = 0, line = 1;
	
	public SimpleReader(String source, Container container, Container parent) {
		src = source;
		try {
			loadContent(added, container, parent);
		} catch (IllegalArgumentException iae) {
			for (BasicData bd : added) bd.destroy(container);
			throw iae;
		}
	}
	
	private void loadContent(List<BasicData> carrier, Container self, Container parent) {
		StringBuilder sb = new StringBuilder();
		List<Data> words = new LinkedList<>();
		List<Data> translates = new LinkedList<>();
		List<Data> current = words;
		char ch;
		while (++i < src.length()) {
			switch (ch = src.charAt(i)) {
				case '\\':
					if (i + 1 >= src.length()) break;
					char ch2 = src.charAt(++i);
						switch (ch2) {
							case '[':
								if (sb.length() > 0) current.add(new Data(sb.toString().trim(),
									 self.getIdentifier()).addPar(self).addDesc(getDescription()));
								else throw report(self.getName() + " - line " + line + ":\n'"
									 + src.substring(lineStart, i + 1) + "'\nExpected text. Got '\\['.");
								sb.setLength(0);
								break;
						case '\\':
						case '/':
						case '(':
						case ')':
							sb.append('\\');
						case ';':
						case '=':
						case '→':
							sb.append(ch2);
							break;
						default:
							if (sb.length() > 0) {
								current.add(new Data(sb.toString().trim(), self.getIdentifier()).addPar(self));
								sb.setLength(0);
							} else if (current.isEmpty()) throw report(self.getName() + " - line " + line + ":\n'"
								 + src.substring(lineStart, i + 1) + "'\nExpected text. Got '\\'.");
							if (current == words) result[1]++;
							else result[2]++;
							sb.append(ch2);
					}
					break;
				case ';':
				case '=':
				case '→':
					if (sb.length() > 0) {
						current.add(new Data(sb.toString().trim(), self.getIdentifier()).addPar(self));
						sb.setLength(0);
					} 
					if (current == words && !words.isEmpty()) {
						result[1]++;
						current = translates;
						break;
					} else throw report(self.getName() + " - line " + line + ":\n'"
						 + src.substring(lineStart, i + 1) + "'\nExpected '\\[', '\\n' or text. Got '" + ch + "'.");
				case '\r':
					i++;
				case '\n':
					if (sb.length() > 0) {
						current.add(new Data(sb.toString().trim(), self.getIdentifier()).addPar(self));
						sb.setLength(0);
					}
					if (current == translates && !translates.isEmpty()) {
						result[2]++;
						for (Data word : words) {
							Word w = Word.mkElement(word, translates);
							self.putChild(parent, w);
							if (carrier != null) carrier.add(w);
						}
					} else throw report(self.getName() + " - line " + line + ":\n'"
						 + src.substring(lineStart, i) + "'\nExpected ';' and text, or '{'. Got '\\n'.");
					words.clear();
					translates.clear();
					current = words;
					lineStart = i + 1;
					line++;
					break;
				case '}':
					if (src.substring(lineStart, i).trim().isEmpty()) {
						while (++i < src.length() && src.charAt(i) != '\n');
						lineStart = i + 1;
						line++;
						return;
					}
					sb.append(ch);
					break;
				case '{':
					if (i + 1 < src.length() && src.charAt(i + 1) == '\n' && current != translates) {
						lineStart = ++i;
						line++;
						Data data;
						if (words.isEmpty()) {
							if (sb.charAt(sb.length() - 1) == '§') sb.setLength(sb.length() - 1);
							data = new Data(sb.toString().trim(), self.getIdentifier()).addPar(self);
						} else data = words.get(0);
						words.clear();
						sb.setLength(0);
						Container child;
						if (src.charAt(i - 2) == '§') self.putChild(parent, child = SaveChapter.mkElement(data));
						else self.putChild(parent, child = new Chapter(data));
						if (carrier != null) carrier.add(child);
						loadContent(null, child, self);
						result[0]++;
					} else sb.append(ch);
					break;
				case ' ':
				case '\t':
					if (sb.length() == 0) break;
				default:
					sb.append(ch);
			}
		}
		if (sb.length() > 0) {
			current.add(new Data(sb.toString().trim(), self.getIdentifier()).addPar(self));
			sb.setLength(0);
		}
		if (current == translates && !translates.isEmpty()) {
			result[2]++;
			for (Data word : words) {
				Word w = Word.mkElement(word, translates);
				self.putChild(parent, w);
				if (carrier != null) carrier.add(w);
			}
		} else if (!words.isEmpty()) throw report(self.getName() + ":\n" + src.substring(lineStart)
				+ "\nExpected ';' and text. Reached end of file.");
	}

	private String getDescription() {
		StringBuilder sb = new StringBuilder();
		char ch;
		while (++i < src.length()) {
			switch (ch = src.charAt(i)) {
				case '\\':
					char ch2 = src.charAt(++i);
						switch (ch2) {
							case ']':
								return sb.toString();
							default:
								sb.append(ch2);
						}
					break;
				case '\n':
					line++;
				default:
					sb.append(ch);
			}
		}
		if (i >= src.length()) {
			throw report("line " + line + ":\n'" + src.substring(lineStart, i - lineStart > 20 ? lineStart
				 + 20 : i) + "'\nExpected '\\]' (end of description section). Reached end of file.");
		}
		return sb.toString().trim();
	}

	private static IllegalArgumentException report(String info) {
		Formatter.defaultReacts.get(SimpleReader.class + ":fail").react(info);
		return new IllegalArgumentException(info);
	}
	
	public static String[] nameResolver(String name) {
		List<String> words = new LinkedList<>();
		int start = 0;
		char prev = (char) -1;
		for (int i = 0; i < name.length(); i++) {
			char ch = name.charAt(i);
			if (prev == '\\' && ch != '\\' && ch != '/' && ch != '(' && ch != ')') {
				words.add(name.substring(start, i - 1));
				start = i;
			}
			prev = ch;
		}
		words.add(name.substring(start));
		return words.toArray(new String[words.size()]);
	}
}
