package IOSystem;

import objects.Word;
import objects.templates.BasicData;
import objects.templates.Container;
import objects.templates.ContainerFile;
import objects.templates.TwoSided;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

/**
 * This class is used for exporting only {@link Word word objects} with their chapters,
 * they are stored in. The output syntax is the same as the one used by
 * {@link SimpleReader}.
 * 
 * @author Josef Litoš
 */
public class SimpleWriter {

	/**
	 * Saves content of the given container into specified file, adding it to the end of
	 * its content.
	 *
	 * @param dest the file to obtain the content of the source
	 * @param par  parent of the object to be written
	 * @param src  the object to be saved
	 */
	public static void saveWords(File dest, Container par, Container src) {
		try (OutputStreamWriter osw = new OutputStreamWriter(
				new FileOutputStream(dest, true), StandardCharsets.UTF_8)) {
			osw.append(writeChapter(new StringBuilder(), par, src));
			Formatter.defaultReacts.get(SimpleWriter.class + ":success")
					.react(src.getName());
		} catch (Exception e) {
			Formatter.defaultReacts.get(ContainerFile.class + ":save").react(e, dest, src);
		}
	}

	private static StringBuilder writeChapter(
			StringBuilder sb, Container par, Container src) {
		sb.append('\n').append(src.getName()).append("\n{");
		for (BasicData bd : src.getChildren(par)) {
			if (bd instanceof Word) {
				sb.append('\n').append(bd.getName()).append(';');
				boolean first = true;
				for (BasicData trl : ((Container) bd).getChildren(src)) {
					if (first) first = false;
					else sb.append('\\');
					sb.append(trl.getName());
				}
			} else if (bd instanceof Container && !(bd instanceof TwoSided)) {
				writeChapter(sb, src, (Container) bd);
			}
		}
		return sb.append("\n}");
	}
}
