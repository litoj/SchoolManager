package testing;

import IOSystem.Formatter;
import objects.MainChapter;
import objects.templates.BasicData;
import objects.templates.Container;
import objects.templates.TwoSided;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import objects.Reference;

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
	final Class<T> t;

	public Test(Class<T> t) {
		this.t = t;
	}

	/**
	 * Converts all the content of the given paths to SourcePaths. Searching throught the
	 * hierarchy until a TwoSided object is reached.
	 * 
	 * @param src the list of paths to be rendered
	 * @return the converted content of the given paths
	 */
	public List<SrcPath<T>> convertAll(List<List<Container>> src) {
		List<SrcPath<T>> ret = new ArrayList<>(src.size() * 2);
		ArrayList<List<Container>> paths = src instanceof ArrayList ?
				(ArrayList<List<Container>>) src : new ArrayList<>(src);
		if (src.size() != 1 || src.get(0).size() > 1)
			for (int i = 0; i < paths.size(); i++) createUniquePaths(paths, paths.get(i));
		for (List<Container> path : paths) {
			if (path.get(path.size() - 1) instanceof TwoSided) ret.add(new SrcPath(path));
			else ret.addAll(getC0(path, new Getter()));
		}
		return ret;
	}

	/**
	 * All paths that do not contact with each other. Their possible Reference objects
	 * have been processed.
	 * 
	 * @param src where to put the content
	 * @param path the path to the object to be processed, mustn't contain Reference
	 */
	private void createUniquePaths(ArrayList<List<Container>> src, List<Container> path) {
		if (path.get(path.size() - 1) instanceof TwoSided) return;
		for (BasicData bd : path.get(path.size() - 1).getChildren(path.size() > 1 ?
				path.get(path.size() - 2) : null)) {
			if (bd instanceof Container) {
				List<Container> copy = new ArrayList<>(path);
				copy.add((Container) bd);
				if (!(bd instanceof TwoSided)) createUniquePaths(src, copy);
			} else if (bd instanceof Reference) {
				try {
					if (!(bd.getThis() instanceof Container) || bd.getThis() instanceof TwoSided &&
							!t.isInstance(bd.getThis())) continue;
				} catch (IllegalArgumentException iae) {
					continue;
				}
				List<Container> refPath = new ArrayList<>(Arrays.asList(((Reference) bd).getRefPath()));
				refPath.add((Container) bd.getThis());
				ArrayList<List<Container>> result = merge(refPath, src);
				if (result == null) src.add(refPath);
			}
		}
	}

	private static ArrayList<List<Container>> merge(List<Container> path, ArrayList<List<Container>> paths) {
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
		
		final List<SrcPath<T>> list = new LinkedList<>();
		int threads = 7;
		
		private synchronized int get() {
			return threads;
		}
		
		private synchronized void set(boolean add) {
			threads += add ? 1 : -1;
		}
	}

	private List<SrcPath<T>> getC0(List<Container> path, Getter getter) {
		List<Thread> threads = new LinkedList<>();
		for (BasicData bd : path.get(path.size() - 1).getChildren(path.size() > 1 ?
				path.get(path.size() - 2) : null)) {
			if (bd instanceof Reference) continue;
			if (bd instanceof Container) {
				List<Container> copy = new ArrayList<>(path);
				copy.add((Container) bd);
				if (bd instanceof TwoSided) {
					if (t.isInstance(bd)) synchronized (getter.list) {
						getter.list.add(new SrcPath<>(copy));
					}
				} else if (getter.get() < 1) getC0(copy, getter);
				else {
					getter.set(false);
					Thread t = new Thread(() -> {
						getC0(copy, getter);
						getter.set(true);
					});
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
	 * Default duration of a test in seconds.
	 */
	private static int DEFAULT_TIME = 180;

	public static int getDefaultTime() {
		return DEFAULT_TIME;
	}

	public static void setDefaultTime(int newTime) {
		if (newTime < 1)
			throw new IllegalArgumentException("Duration of any test can't be less than 1 second!");
		Formatter.putSetting("defaultTestTime", DEFAULT_TIME = newTime);
	}

	/**
	 * {@code true} - clever choosing when creating a test
	 */
	private static boolean CLEVER_RND = true;

	/**
	 * @return whether the created tests will prefer less tested and worse result
	 * object, when selecting the content of a test.
	 */
	public static boolean isClever() {
		return CLEVER_RND;
	}

	public static void setClever(boolean isClever) {
		Formatter.putSetting("isClever", CLEVER_RND = isClever);
	}

	/**
	 * Default amount items to be tested.
	 */
	private static int AMOUNT = 10;

	public static int getAmount() {
		return AMOUNT;
	}

	public static void setAmount(int amount) {
		Formatter.putSetting("testAmount", AMOUNT = amount);
	}

	private List<SrcPath<T>> source;
	private MainChapter mch;
	private boolean[] answered;
	private int time;
	private Timer doOnSec;

	/**
	 * Prepares everything for the next test.
	 *
	 * @param amount  the amount of object to be randomly picked for the test or
	 *                -1 if all of the words are supposed to be used
	 * @param doOnSec action to be done every second of the test, last call on time out
	 * @param timeSec duration of a test
	 * @param source  List of paths to the tested objects. Don't include MainChapter.
	 */
	public void setTested(int amount, Timer doOnSec, int timeSec, List<SrcPath<T>> source) {
		if (amount == -1 || amount > source.size()) amount = source.size();
		else if (amount < 1)
			throw new IllegalArgumentException("Amount of tested elements can't be less than 1!");
		else if (timeSec < 1)
			throw new IllegalArgumentException("Duration of the test can't be less than 1 second!");
		else if (source.size() < 2)
			throw new IllegalArgumentException("The source must contain more than one element");
		mch = source.get(0).t.getIdentifier();
		this.source = isClever() ? cleverTest(source, amount) : rndTest(source, amount);
		time = timeSec;
		this.doOnSec = doOnSec;
		answered = new boolean[this.source.size()];
	}

	public static class SrcPath<T extends TwoSided> {

		public final List<Container> srcPath;
		public final T t;

		public SrcPath(List<Container> srcPath) {
			this.srcPath = srcPath;
			t = (T) srcPath.get(srcPath.size() - 1);
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

	private List<SrcPath<T>> rndTest(List<SrcPath<T>> source, int amount) {
		Random rnd = new Random();
		for (int i = source.size(); i > amount; i--) source.remove(rnd.nextInt(source.size()));
		Collections.shuffle(source);
		return source;
	}

	private List<SrcPath<T>> cleverTest(List<SrcPath<T>> source, int amount) {
		int i = -1;
		LinkedList<Object[]> tested = new LinkedList<>();
		List<Object> part = new LinkedList<>();
		Object[] toSort = source.toArray();
		Arrays.sort(toSort, (a, b) -> Integer.compare(((SrcPath<T>) a).sfc(), ((SrcPath<T>) b).sfc()));
		for (Object path : toSort) {
			if (((SrcPath<T>) path).sfc() == i) part.add(path);
			else {
				tested.add(part.toArray());
				i = ((SrcPath<T>) path).sfc();
				part = new LinkedList<>();
				part.add(path);
			}
		}
		tested.add(part.toArray());
		tested.remove(0);
		source = new ArrayList<>();
		for (Object[] x : tested) {
			if (source.size() + x.length < amount) {
				for (Object o : Arrays.asList(x)) source.add((SrcPath<T>) o);
			} else {
				Arrays.sort(x, (a, b) -> Integer.compare(((SrcPath<T>) a).rat(), ((SrcPath<T>) b).rat()));
				for (Object o : Arrays.asList(Arrays.copyOf(x, amount - source.size())))
					source.add((SrcPath<T>) o);
				break;
			}
		}
		return rndTest(source, amount);
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
		})).start();
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
			throw new IllegalArgumentException("Outside of tested size: " + index + " out of " + (source.size() - 1));
		if (answered[index])
			throw new IllegalArgumentException("Can't answer more than once, only one try allowed!");
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
		mch.addSF(b);
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
	public T[] getTested(int index) {
		return index >= source.size() ? null : (T[]) source.get(index).t.getChildren();
	}

	public List<SrcPath<T>> getTestSrc() {
		return new ArrayList<>(source);
	}
}
