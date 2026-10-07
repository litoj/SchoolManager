package cz.litoj.schlmgr.gui.engine.IOSystem;

import cz.litoj.schlmgr.gui.engine.IOSystem.Formatter.IOSystem.GeneralPath;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

import cz.litoj.schlmgr.gui.engine.objects.SaveChapter;
import cz.litoj.schlmgr.gui.engine.objects.Word;
import cz.litoj.schlmgr.gui.engine.objects.templates.BasicData;
import cz.litoj.schlmgr.gui.engine.objects.templates.Container;
import cz.litoj.schlmgr.gui.engine.objects.templates.ContainerFile;
import cz.litoj.schlmgr.gui.engine.objects.templates.TwoSided;

/**
 * This class is used for exporting only {@link Word word objects} with their chapters,
 * they are stored in. The output syntax is the same as the one used by
 * {@link SimpleReader}.
 *
 * @author Josef Litoš
 */
public final class SimpleWriter {

	static String wordSplitter = ";";
	static String wordSeparator = "\\";

	/**
	 * Translates actual wordSplitter to a number representing the correspondent option.
	 * @return ";", "=", " = ", or " → "
	 */
	public static String getWordSplitter() {
		return wordSplitter;
	}
	
	/**
	 * Sets the separator between a word and its translations when exporting.
	 *
	 * @param splitter any non-empty string; when it starts or ends with a space,
	 * the separator is widened accordingly
	 */
	public static void setWordSplitter(String splitter) {
		if (splitter.isEmpty()) return;
		wordSplitter = splitter;
		wordSeparator = splitter.charAt(0) == ' ' || splitter.charAt(splitter.length() - 1) == ' '
				? " \\ " : "\\";
	}
	
	private int tabs = 0;
	private final StringBuilder sb = new StringBuilder();
	
	/**
	 * Saves content of the given container into specified file, adding it to the end of
	 * its content. When exactly one container is exported, its children are written
	 * directly without the {@code Name { ... }} chapter wrapper—so the file name
	 * determines the chapter name on re-import.
	 *
	 * @param dest the file to obtain the content of the source
	 * @param src  pairs of object and parent (in this order) to be saved
	 */
	public SimpleWriter(GeneralPath dest, Container[] ... src) {
		try {
			if (src.length == 1) saveContent(src[0][0], src[0][1], false);
			else for (Container[] couple : src) saveContent(couple[0], couple[1], true);
			try (OutputStreamWriter osw =
				 new OutputStreamWriter(dest.createOutputStream(true), StandardCharsets.UTF_8)) {
				osw.append(sb);
			}
			Formatter.defaultReacts.get(SimpleWriter.class + ":success").react("OK");
		} catch (Exception e) {
			Formatter.defaultReacts.get(ContainerFile.class + ":save").react(e, dest, src);
		}
	}

	private void saveContent(Container self, Container par, boolean wrap) {
		if (wrap) indentation().writeData(self, par).append(self instanceof SaveChapter ? " §{\n" : " {\n");
		tabs++;
		for (BasicData bd : self.getChildren(par)) {
			if (bd instanceof Word) {
				indentation().writeData(bd, self).append(wordSplitter);
				boolean first = true;
				for (BasicData trl : ((Container) bd).getChildren(self)) {
					if (first) first = false;
					else sb.append(wordSeparator);
					writeData(trl, self);
				}
				sb.append('\n');
			} else if (bd instanceof Container && !(bd instanceof TwoSided)) {
				saveContent((Container) bd, self, true);
			}
		}
		tabs--;
		if (wrap) indentation().sb.append("}\n");
	}
	
	private StringBuilder writeData(BasicData bd, Container par) {
		sb.append(bd.getName().replaceAll(";", "\\\\;").replaceAll("=",
			"\\\\=").replaceAll("→", "\\\\→").replaceAll("\\[", "\\\\[").replaceAll("\\]", "\\\\]"));
		String desc = bd.getDesc(par).replaceAll("\\\\","\\\\\\\\").replaceAll("\\]", "\\\\]");
		if (desc != null && !desc.isEmpty()) {
			if (desc.indexOf('\n') != -1) {
				sb.append(" [\n").append(desc).append('\n');
				indentation().sb.append(']');
			} else sb.append(" [").append(desc).append("]");
		}
		return sb;
	}
	
	private SimpleWriter indentation() {
		for (int i = tabs; i > 0; i--) sb.append('\t');
		return this;
	}
}
