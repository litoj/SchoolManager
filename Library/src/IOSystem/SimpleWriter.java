package IOSystem;

import IOSystem.Formatter.IOSystem.GeneralPath;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

import objects.SaveChapter;
import objects.Word;
import objects.templates.BasicData;
import objects.templates.Container;
import objects.templates.ContainerFile;
import objects.templates.TwoSided;

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
	
	public static void setWordSplitter(String splitter) {
		switch (splitter) {
			case " = ":
			case " → ":
				wordSplitter = splitter;
				wordSeparator = " \\ ";
				break;
			case ";":
			case "=":
				wordSplitter = splitter;
			default:
				wordSeparator = "\\";
		}
		Formatter.putSetting("exportWordSplit", splitter);
	}
	
	private int tabs = 0;
	private final StringBuilder sb = new StringBuilder();
	
	/**
	 * Saves content of the given container into specified file, adding it to the end of
	 * its content.
	 *
	 * @param dest the file to obtain the content of the source
	 * @param src  pairs of object and parent (in this order) to be saved
	 */
	public SimpleWriter(GeneralPath dest, Container[] ... src) {
		try {
			for (Container[] couple : src) saveContent(couple[0], couple[1]);
			try (OutputStreamWriter osw =
				 new OutputStreamWriter(dest.createOutputStream(true), StandardCharsets.UTF_8)) {
				osw.append(sb);
			}
			Formatter.defaultReacts.get(SimpleWriter.class + ":success").react("OK");
		} catch (Exception e) {
			Formatter.defaultReacts.get(ContainerFile.class + ":save").react(e, dest, src);
		}
	}

	private void saveContent(Container self, Container par) {
		indentation().writeData(self, par).append(self instanceof SaveChapter ? " §{\n" : " {\n");
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
				saveContent((Container) bd, self);
			}
		}
		tabs--;
		indentation().sb.append("}\n");
	}
	
	private StringBuilder writeData(BasicData bd, Container par) {
		sb.append(bd.getName().replaceAll(";", "\\;").replaceAll("=", "\\=").replaceAll("→", "\\→"));
		String desc = bd.getDesc(par);
		if (desc != null && !desc.isEmpty()) sb.append(" \\[").append(desc).append("\\]");
		return sb;
	}
	
	private SimpleWriter indentation() {
		for (int i = tabs; i > 0; i--) sb.append('\t');
		return this;
	}
}
