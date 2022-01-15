package testing;

import java.util.ArrayList;

import java.util.List;
import objects.templates.BasicData;

/**
 * Can {@link #readName(Object)} decode a String written in a specific format.
 *
 * @author Josef Litoš
 */
public class NameReader {

		/**
		 * Source to be read, from position {@link #i i}.
		 */
		private final String str;
		private final int length;
		/**
		 * Where the given {@link #str source} should be read from.
		 */
		private int i = 0;
		
		public final String[] result;

		public NameReader(String s) {
			this.str = s;
			length = str.length();
			List<StringBuilder> parts = new ArrayList<>(3);
			getParts(parts);
			result = new String[parts.size()];
			int i = 0;
			for (StringBuilder sb : parts) result[i++] = sb.toString();
		}

	/**
	 * Reads the {@link BasicData#getName() name} and gives back all name possibilities.
	 * <p>
	 * Examples, for names:
	 * <blockquote><pre>
	 * "They/(He and she) moved." returns [They moved., He and she moved.]
	 * "/I/You smile." returns [smile., I smile., You smile.]
	 * "(I/You smile.)/(Smile!)" returns [I smile., You smile., Smile!]
	 * "I 'm/am here/there." returns [I'm here., I am here., I'm there., I am there.]
	 * "(We/(May you) 're)/He's /out." returns [We're., May you're., He's., We're out., May you're out., He's out.]
	 * </pre></blockquote>
	 *
	 * @param o the {@link BasicData} its name will be processed
	 * @return all variants of the {@link BasicData#getName() name}
	 */
	public static String[] readName(Object o) {
		String text = o.toString();
		return text.contains("/") ? new NameReader(text).result : new String[]{text};
	}

	public static void main(String[] args) {
//		for(String str : readName("(/(1a/2(1b/2b B)a/3(1b/2(1c/2(1 D)c/3c C)b)a ))A")) System.out.println(str);
//		for(String str : readName("0 1(b/c)a 2a/2b")) System.out.println(str);
	}
	
	private void getParts(List<StringBuilder> ret) {
		List<StringBuilder> parts = null;
		List<StringBuilder> recursiveParts = null;
		StringBuilder sb = new StringBuilder(8);
		int lastSplitter = i - 1;
		char now;
		renderer:
		for (; i < length; i++) switch (now = str.charAt(i)) {
			case '/':
				if (recursiveParts == null) {
					if (sb.length() > 0 || lastSplitter == i - 1) {
						if (parts == null) parts = new ArrayList<>(3);
						parts.add(sb);
						sb = new StringBuilder(8);
					}
				} else {
					if (parts == null) {
						if (sb.length() > 0) for (StringBuilder part : recursiveParts) part.append(sb);
						parts = recursiveParts;
					} else for (StringBuilder part : recursiveParts) parts.add(part.append(sb));
					sb.setLength(0);
					recursiveParts = null;
				}
				lastSplitter = i;
				break;
			case '.':
			case '!':
			case '?':
			case ',':
			case ' ':
				if (parts == null) {
					if (recursiveParts == null) {
						sb.append(now);
						if (ret.isEmpty()) ret.add(sb);
						else for (StringBuilder part : ret) if (sb.length() > 0) part.append(sb);
						sb = new StringBuilder(8);
					} else {
						if (ret.isEmpty()) for (StringBuilder part : recursiveParts)
							ret.add(part.append(sb).append(now));
						else {
							int size = ret.size();
							StringBuilder retPart;
							for (int j = 0; j < size; j++) {
								retPart = ret.remove(0);
								for (StringBuilder part : recursiveParts)
									ret.add(part.length() > 0 ? new StringBuilder(retPart).append(part).append(sb).append(now)
										 : new StringBuilder(retPart).append(sb));
							}
						}
						sb.setLength(0);
						recursiveParts = null;
					}
				} else {
					if (sb.length() > 0) {
						if (recursiveParts == null) parts.add(sb);
						else {
							for (StringBuilder part : recursiveParts) parts.add(part.append(sb));
							recursiveParts = null;
						}
						sb = new StringBuilder(8);
					} else if (recursiveParts != null) {
						for (StringBuilder part : recursiveParts) parts.add(part);
						recursiveParts = null;
					}
					if (ret.isEmpty()) for (StringBuilder part : parts)
						ret.add(part.length() > 0 ? part.append(now) : part);
					else {
						int size = ret.size();
						StringBuilder retPart;
						for (int j = 0; j < size; j++) {
							retPart = ret.remove(0);
							for (StringBuilder part : parts) ret.add(part.length() > 0 ? new StringBuilder(retPart)
								 .append(part).append(now) : new StringBuilder(retPart).append(part));
						}
					}
					parts = null;
				}
				lastSplitter = i;
				break;
			case ')':
				break renderer;
			case '(':
				i++;
				recursiveParts = new ArrayList<>(2);
				if (sb.length() > 0) recursiveParts.add(sb);
				sb = new StringBuilder(8);
				getParts(recursiveParts);
				if (recursiveParts.size() == 1) {
					if (sb.length() > 0) sb.append(recursiveParts.get(0));
					else sb = recursiveParts.get(0);
					recursiveParts = null;
				}
				break;
			case '\\':
				now = str.charAt(++i);
			default:
				sb.append(now);
		}
		if (parts == null) {
			if (recursiveParts == null) {
				if (sb.length() > 0) {
					if (ret.isEmpty()) ret.add(sb);
					else for (StringBuilder part : ret) part.append(sb);
				}
			} else {
				if (ret.isEmpty()) for (StringBuilder part : recursiveParts) ret.add(part.append(sb));
				else {
					int size = ret.size();
					StringBuilder retPart;
					for (int j = 0; j < size; j++) {
						retPart = ret.remove(0);
						for (StringBuilder part : recursiveParts) {
							ret.add(new StringBuilder(retPart).append(part).append(sb));
						}
					}
				}
			}
		} else {
			if (sb.length() > 0) {
				if (recursiveParts == null) parts.add(sb);
				else for (StringBuilder part : recursiveParts) parts.add(part.append(sb));
			} else if (recursiveParts != null) {
				for (StringBuilder part : recursiveParts) parts.add(part);
			} else parts.add(sb);
			if (ret.isEmpty()) for (StringBuilder part : parts) ret.add(part);
			else {
				int size = ret.size();
				StringBuilder retPart;
				for (int j = 0; j < size; j++) {
					retPart = ret.remove(0);
					for (StringBuilder part : parts) {
						ret.add(new StringBuilder(retPart).append(part));
					}
				}
			}
		}
	}
}
