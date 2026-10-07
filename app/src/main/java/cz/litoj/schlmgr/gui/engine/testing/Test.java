package cz.litoj.schlmgr.gui.engine.testing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import cz.litoj.schlmgr.gui.engine.objects.Reference;
import cz.litoj.schlmgr.gui.engine.objects.templates.BasicData;
import cz.litoj.schlmgr.gui.engine.objects.templates.Container;
import cz.litoj.schlmgr.gui.engine.objects.templates.TwoSided;

/**
 * Creates and manages a test for any {@link TwoSided} object. Resets all values
 * using {@link #setTested(int, Timer, int, List) }.
 *
 * @param <T> type of {@link BasicData} to be tested
 * @author Josef Litoš
 */
public class Test<T extends TwoSided> {

	/**
	 * The generic type of this object.
	 */
	private final Class<T> type;

	public Test(Class<T> t) {
		type = t;
	}

	/**
	 * Converts all the content of the given paths to SourcePaths. Searching through the
	 * hierarchy until a TwoSided object is reached.
	 *
	 * @param src the list of paths to be rendered, mustn't contain different TwoSided
	 *            objects than the one of the test's type.
	 * @return the converted content of the given paths
	 */
	public List<SrcPath> convertAll(List<List<Container>> src) {
		List<SrcPath> ret = new ArrayList<>(src.size() * 2);
		ArrayList<List<Container>> paths = src instanceof ArrayList ?
				(ArrayList<List<Container>>) src : new ArrayList<>(src);
		if (src.size() != 1 || src.get(0).size() > 1)
			for (int i = 0; i < paths.size(); i++) resolveReferences(paths, paths.get(i));
		for (List<Container> path : paths) {
			if (path.get(path.size() - 1) instanceof TwoSided) ret.add(new SrcPath(path));
			else ret.addAll(convertContent(path, new Getter()));
		}
		return ret;
	}

	/**
	 * All paths that do not contact with each other. Their possible Reference objects
	 * have been processed.
	 *
	 * @param src  where to put the content
	 * @param path the path to the object to be processed, mustn't contain Reference
	 */
	private void resolveReferences(ArrayList<List<Container>> src, List<Container> path) {
		if (path.get(path.size() - 1) instanceof TwoSided) return;
		for (BasicData bd : path.get(path.size() - 1).getChildren(path.size() > 1 ?
				path.get(path.size() - 2) : null)) {
			if (bd instanceof Container) {
				List<Container> copy = new ArrayList<>(path);
				copy.add((Container) bd);
				if (!(bd instanceof TwoSided)) resolveReferences(src, copy);
			} else if (bd instanceof Reference) {
				try {
					if (!(bd.getThis() instanceof Container) || bd.getThis()
							instanceof TwoSided && !type.isInstance(bd.getThis())) continue;
				} catch (IllegalArgumentException iae) {
					continue;
				}
				List<Container> refPath =
						new ArrayList<>(Arrays.asList(((Reference) bd).getRefPath()));
				refPath.add((Container) bd.getThis());
				ArrayList<List<Container>> result = merge(refPath, src);
				if (result == null) src.add(refPath);
			}
		}
	}

	private ArrayList<List<Container>> merge(
			List<Container> path, ArrayList<List<Container>> paths) {
		boolean noChange = true;
		for (int pos = 0; pos < paths.size(); pos++) {
			Container[] path1 = paths.get(pos).toArray(new Container[0]);
			isOk:
			{
				for (int j = 1; j < path.size() && j < path1.length; j++)
					if (path.get(j) != path1[j]) break isOk;
				if (path.size() < path1.length) {
					paths.remove(pos--);
					noChange = false;
				} else return paths;
			}
		}
		return noChange ? null : paths;
	}

	private class Getter {

		final List<SrcPath> list = new LinkedList<>();
		volatile int threads = 7;
	}

	private List<SrcPath> convertContent(List<Container> path, Getter getter) {
		List<Thread> threads = new LinkedList<>();
		for (BasicData bd : path.get(path.size() - 1).getChildren(path.size() > 1 ?
				path.get(path.size() - 2) : null)) {
			if (bd instanceof Reference) continue;
			if (bd instanceof Container) {
				List<Container> copy = new ArrayList<>(path);
				copy.add((Container) bd);
				if (bd instanceof TwoSided) {
					if (type.isInstance(bd)) synchronized (getter.list) {
						getter.list.add(new SrcPath(copy));
					}
				} else if (getter.threads < 1) convertContent(copy, getter);
				else {
					getter.threads--;
					Thread t = new Thread(() -> {
						convertContent(copy, getter);
						getter.threads++;
					}, "Test converter");
					threads.add(t);
					t.start();
				}
			}
		}
		try {
			for (Thread t : threads) if (t.isAlive()) t.join();
		} catch (InterruptedException ex) {
			return null;
		}
		synchronized (getter.list) {
			return getter.list;
		}
	}

	/**
	 * Default time for one item in a test.
	 */
	private static int DEFAULT_TIME = 18;

	public static int getDefaultTime() {
		return DEFAULT_TIME;
	}

	public static void setDefaultTime(int newTime) {
		if (newTime < 1)
			throw new IllegalArgumentException("Duration of a test must be >= 1 s!");
		DEFAULT_TIME = newTime;
	}

	private static boolean isClever = true;

	/**
	 * @return whether the created tests will prefer less tested and worse result
	 * object, when selecting the content of a test.
	 */
	public static boolean isClever() {
		return isClever;
	}

	public static void setClever(boolean isClever) {
		Test.isClever = isClever;
	}

	private static int amount = 10;

	/**
	 * @return the amount of items in a test
	 */
	public static int getAmount() {
		return amount;
	}

	public static void setAmount(int amount) {
		Test.amount = amount;
	}

	private List<SrcPath> source;
	private boolean[] answered;
	private int time;
	private Timer doOnSec;

	/**
	 * Prepares everything for the next test.
	 *
	 * @param amount  the amount of object to be randomly picked for the test or
	 *                -1 if all of the words are supposed to be used
	 * @param doOnSec action to be done every second of the test, last call on time out
	 * @param timeSec time give per item in test
	 * @param source  List of paths to the tested objects. Don't include MainChapter.
	 */
	public void setTested(int amount, Timer doOnSec, int timeSec, List<SrcPath> source) {
		if (amount == -1 || amount > source.size()) amount = source.size();
		else if (amount < 1)
			throw new IllegalArgumentException("Amount of tested elements must be >= 1!");
		else if (timeSec < 1)
			throw new IllegalArgumentException("Time given for an item must be >= 1 s!");
		else if (source.size() < 2)
			throw new IllegalArgumentException("The source' size must be > 1!");
		this.source = isClever() ? cleverTest(source, amount) : rndTest(source, amount);
		time = timeSec * amount;
		this.doOnSec = doOnSec;
		answered = new boolean[this.source.size()];
	}

	public static class SrcPath {

		public final List<Container> srcPath;
		public final TwoSided t;

		public SrcPath(List<Container> srcPath) {
			this.srcPath = srcPath;
			t = (TwoSided) srcPath.get(srcPath.size() - 1);
		}

		int sfc() {
			return t.getSFCount();
		}

		int rat() {
			return t.getRatio();
		}

		@Override
		public String toString() {
			return t.toString();
		}

		TwoSided[] getChildren() {
			return t.getChildren(srcPath.get(srcPath.size() - 2));
		}
	}

	private List<SrcPath> rndTest(List<SrcPath> source, int amount) {
		Random rnd = new Random();
		for (int i = source.size(); i > amount; i--)
			source.remove(rnd.nextInt(source.size()));
		Collections.shuffle(source);
		return source;
	}

	/**
	 * Size of the success-rate belts (in percents) used to compute how much a word
	 * needs to be tested. Crossing one belt makes a word as urgent as having one
	 * test less done.
	 */
	private static final int RATIO_BELT = 20;

	/**
	 * Ratio belt of words that were never tested — treated as if they always failed.
	 */
	private static final int UNTESTED_BELT = 100 / RATIO_BELT;

	/**
	 * Expresses how much the given word needs to be retested. Computed as the word's
	 * failure rate (split into belts of {@link #RATIO_BELT} percents) minus the amount
	 * of times the word was already tested, so the more tests were run on a word,
	 * the further it slips back in the test queue. This way even words answered
	 * correctly on the first try eventually resurface.
	 *
	 * @return the urgency value, higher means the word should be tested sooner
	 */
	private static int urgency(SrcPath p) {
		int rat = p.rat();
		return (rat < 0 ? UNTESTED_BELT : (100 - rat) / RATIO_BELT) - p.sfc();
	}

	/**
	 * Picks the {@code amount} most {@link #urgency(SrcPath) urgent} words from the
	 * source. All words as urgent as the last word still needed are included and then
	 * trimmed randomly, so equally urgent words take turns. Always returns exactly
	 * {@code amount} words (as long as {@code amount <= source.size()}), regardless of
	 * how the words' urgencies are distributed.
	 */
	private List<SrcPath> cleverTest(List<SrcPath> source, int amount) {
		List<SrcPath> sorted = new ArrayList<>(source);
		sorted.sort((a, b) -> Integer.compare(urgency(b), urgency(a)));
		int cut = urgency(sorted.get(amount - 1)), end = amount;
		while (end < sorted.size() && urgency(sorted.get(end)) == cut) end++;
		return rndTest(new ArrayList<>(sorted.subList(0, end)), amount);
	}

	/**
	 * Starts the test.
	 */
	public void startTest() {
		new Thread((doOnSec == null ? () -> {
			try {
				for (; time >= 0; time--) Thread.sleep(1000);
			} catch (InterruptedException ex) {
			}
		} : () -> {
			try {
				for (; time >= 0; time--) {
					if (!doOnSec.doOnSec(time)) return;
					Thread.sleep(1000);
				}
			} catch (InterruptedException ex) {
			}
		}), "Test timer").start();
	}

	/**
	 * Tests if user answered correctly.
	 *
	 * @param index  index of the answered object
	 * @param answer the answer of the user
	 * @return {@code true} if the user matched the translation for all of the
	 * source Element's children (in most cases if matches its name), otherwise
	 * {@code false}
	 */
	public boolean isAnswer(int index, String answer) {
		if (index >= source.size())
			throw new IllegalArgumentException("Outside of tested size: "
					+ index + " out of " + (source.size() - 1));
		if (answered[index])
			throw new IllegalArgumentException("Can't answer more than once,"
					+ " only one attempt allowed!");
		else answered[index] = true;
		boolean b = false;
		if (answer != null && !answer.isEmpty()) {
			if (answer.equals(source.get(index).t.toString())) b = true;
			else
				for (String s : NameReader.readName(source.get(index).t)) {
					if (s.equals(answer)) {
						b = true;
						break;
					}
				}
			if (!b) {
				for (BasicData bd : source.get(index).t.getChildren()) {
					b = false;
					for (BasicData ch : ((TwoSided) bd).getChildren()) {
						for (String s : NameReader.readName(ch))
							if (s.equals(answer)) {
								b = true;
								break;
							}
						if (b) break;
					}
					if (!b) break;
				}
			}
		}
		for (BasicData bd : source.get(index).srcPath) bd.addSF(b);
		for (BasicData bd : source.get(index).getChildren()) bd.addSF(b);
		return b;
	}

	/**
	 * Gives translates for the picked up testing objects.
	 *
	 * @param index index of the tested object which translates you want to get
	 * @return children of the asked object
	 */
	public TwoSided[] getTested(int index) {
		return index >= source.size() ? null : source.get(index).t.getChildren();
	}

	public List<SrcPath> getTestSrc() {
		return new ArrayList<>(source);
	}
}
