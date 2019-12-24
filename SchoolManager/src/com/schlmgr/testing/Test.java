package com.schlmgr.testing;

import com.schlmgr.objects.MainChapter;
import com.schlmgr.objects.templates.BasicData;
import com.schlmgr.objects.templates.Container;
import com.schlmgr.objects.templates.TwoSided;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

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
	 * Converts the given list of paths to list of SourcePaths.
	 *
	 * @param path the list of paths to be converted
	 * @return the converted {@code paths}
	 */
	public List<SrcPath<T>> mkSrcPath(List<List<Container>> path) {
		List<SrcPath<T>> p = new LinkedList<>();
		for (List<Container> t : path) p.add(new SrcPath<>(t));
		return p;
	}

	/**
	 * @param path the path to the object which content will be processed and returned
	 * @return the list of paths to every element of class {@link #t T} contained
	 * in the last object of the param {@code path}
	 */
	public List<SrcPath<T>> getContent(List<Container> path) {
		if (!t.isInstance(path.get(path.size() - 1))) return getC0(path);
		throw new IllegalArgumentException("Only normal Containers can be read, the TwoSided is the result!");
	}

	private List<SrcPath<T>> getC0(List<Container> path) {
		List<SrcPath<T>> list = new LinkedList<>();
		for (BasicData bd : path.get(path.size() - 1).getChildren(path.get(path.size() - 2))) {
			bd = bd.getThis();
			if (bd instanceof Container) {
				if (bd instanceof TwoSided)
					if (t.isInstance(bd)) {
						List<Container> x = new LinkedList<>(path);
						x.add((Container) bd);
						list.add(new SrcPath<>(x));
					}
				} else {
					path.add((Container) bd);
					list.addAll(getC0(path));
				}
		}
		path.remove(path.size() - 1);
		return list;
	}

	/**
	 * Default duration of a test in seconds.
	 */
	private static int DEFAULT_TIME = -1;

	public static int getDefaultTime() {
		if (DEFAULT_TIME <= 0) {
			try {
				return DEFAULT_TIME = Integer.parseInt(com.schlmgr.IOSystem.Formatter.getSetting("defaultTestTime"));
			} catch (NumberFormatException | NullPointerException e) {
				setDefaultTime(180);
				return 180;
			}
		}
		return DEFAULT_TIME;
	}

	public static void setDefaultTime(int newTime) {
		if (newTime < 1)
			throw new IllegalArgumentException("Duration of any test can't be less than 1 second!");
		com.schlmgr.IOSystem.Formatter.putSetting("defaultTestTime", "" + (DEFAULT_TIME = newTime));
	}

	/**
	 * {@code true} - clever choosing when creating a test
	 */
	private static boolean CLEVER_RND;

	/**
	 * @return whether the created tests will prefer less tested and worse result
	 * object, when selecting the content of a test.
	 */
	public static boolean isClever() {
		return CLEVER_RND ? true : (CLEVER_RND = com.schlmgr.IOSystem.Formatter.getSetting("randomType").contains("t"));
	}

	public static void setClever(boolean isClever) {
		com.schlmgr.IOSystem.Formatter.putSetting("randomType", "" + (CLEVER_RND = isClever));
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
		if (amount == -1) amount = source.size();
		else if (amount < 1 || amount > source.size())
			throw new IllegalArgumentException("Amount of tested elements can't be less than 1\nnor above the size of the source!");
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

		final List<Container> srcPath;
		final T t;

		SrcPath(List<Container> srcPath) {
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
			return sfc() + "  " + rat() + "  " + t.getName();
		}

		TwoSided[] getChildren() {
			return t.getChildren(srcPath.get(srcPath.size() - 2));
		}
	}

	private List<SrcPath<T>> rndTest(List<SrcPath<T>> source, int amount) {
		for (int i = source.size() - 1; i > amount; i--) source.remove(i);
		Random rd = new Random(System.nanoTime());
		int pos = rd.nextInt(source.size());
		SrcPath current = source.get(pos);
		for (int i = 0; i < source.size(); i++)
			current = source.set(rd.nextInt(source.size()), current);
		source.set(pos, current);
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
		(doOnSec == null ? new Thread(() -> {
			try {
				for (; time >= 0; time--) Thread.sleep(1000);
			} catch (InterruptedException ex) {
			}
		}) : new Thread(() -> {
			try {
				for (; time >= 0; time--) {
					if (doOnSec != null) doOnSec.doOnSec(time);
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
	 * Gives translates for the piced up testing com.schlmgr.objects.
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
