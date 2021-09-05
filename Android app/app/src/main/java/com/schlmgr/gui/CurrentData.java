package com.schlmgr.gui;

import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.snackbar.Snackbar;
import com.schlmgr.R;
import com.schlmgr.gui.list.HierarchyAdapter;
import com.schlmgr.gui.list.ImageAdapter;
import com.schlmgr.gui.list.OpenListAdapter;
import com.schlmgr.gui.list.SearchAdapter;
import com.schlmgr.gui.popup.TextPopup;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import IOSystem.Formatter;
import IOSystem.Formatter.Data;
import IOSystem.Formatter.IOSystem.GeneralPath;
import objects.MainChapter;
import objects.Picture;
import objects.templates.BasicData;
import objects.templates.Container;
import objects.templates.ContainerFile;

import static IOSystem.Formatter.getIOSystem;
import static com.schlmgr.gui.AndroidIOSystem.getFirstCause;
import static com.schlmgr.gui.Controller.activity;

public class CurrentData {

	public static final BackLog backLog = new BackLog();
	public static final Set<ContainerFile> changed = new HashSet<>();

	public static class BackLog {
		/**
		 * Path to the currently displayed element (inclusive).
		 */
		public EasyList<BasicData> path = new EasyList<>();
		/**
		 * This value is set after {@link #add(Boolean, BasicData, EasyList) adding} an updated path.
		 */
		public OpenListAdapter adapter;
		/**
		 * The previous paths to the displayed elements.
		 */
		private final EasyList<EasyList<BasicData>> prevPaths = new EasyList<>();
		/**
		 * All old states of the main RecyclerView.
		 */
		private final EasyList<OpenListAdapter> prevAdapters = new EasyList<>();
		/**
		 * The amounts of callbacks, before the current paths get replaced with an older ones.
		 */
		public final EasyList<Integer> onePath = new EasyList<>();

		public EasyList<OpenListAdapter> getPrevAdaptersList() {
			//return (EasyList<OpenListAdapter>) prevAdapters.clone();
			return prevAdapters;
		}

		public void add(Boolean newPath, BasicData bd, EasyList<BasicData> currPath) {
			if (newPath == null) if (newPath = currPath.size() == path.size())
				for (int i = path.size() - 1; i >= 0; i--) {
					if (path.get(i) != currPath.get(i)) {
						newPath = false;
						break;
					}
				}
			if (newPath) {
				prevPaths.add(path);
				path = currPath;
				onePath.add(currPath.get(-1) instanceof Picture ? -1 : 0);
			} else {
				if (bd != null) path.add(bd);
				onePath.add((onePath.remove(-1) + 1));
			}
			if (!(adapter instanceof ImageAdapter)) {
				prevAdapters.add(adapter);
				((SearchAdapter) adapter).firstItemPos =
						((LinearLayoutManager) ((SearchAdapter) adapter)
								.container.getLayoutManager()).findFirstVisibleItemPosition();
			}
			adapter = null;
		}

		public void clear() {
			path.clear();
			adapter = null;
			prevPaths.clear();
			prevAdapters.clear();
			onePath.clear();
			onePath.add(0);
		}

		/**
		 * @return if the path list didn't change
		 */
		public boolean remove() {
			boolean ret;
			if (ret = onePath.get(-1) > 0) {
				if (adapter instanceof SearchAdapter == adapter instanceof HierarchyAdapter)
					path.remove(-1);
				onePath.add(onePath.remove(-1) - 1);
			} else {
				onePath.remove(-1);
				if (onePath.isEmpty()) {
					onePath.add(0);
					path = new EasyList<>();
				} else {
					path = prevPaths.remove(-1);
					if (path.get(-1) instanceof Picture) return remove();
				}
			}
			adapter = prevAdapters.isEmpty() ? null : prevAdapters.remove(-1);
			return ret;
		}
	}

	public static class EasyList<T> extends LinkedList<T> {

		public static <E> EasyList<E> convert(E[] source) {
			EasyList<E> ret = new EasyList<>();
			for (E e : source) ret.add(e);
			return ret;
		}

		@Override
		public T get(int index) {
			if (isEmpty()) return null;
			if (index < 0) return size() + index < 0 ? null : super.get(size() + index);
			return size() > index ? super.get(index) : null;
		}

		@Override
		public T remove(int index) {
			if (index < 0) return super.remove(size() + index);
			return super.remove(index);
		}
	}

	private static final LinkedList<MainChapter> toLoad = new LinkedList<>();

	public static void finishLoad() {
		new Thread(() -> {
			try {
				Thread.sleep(500);
			} catch (Exception e) {
			}
			Object uE = Formatter.getSetting("uncaughtException");
			if (uE != null) {
				new TextPopup(activity.getString(R.string.exception_handler)
						+ getFirstCause((Throwable) ((Object[]) uE)[0]), (String) ((Object[]) uE)[1]) {
					@Override
					public void dismiss(boolean forever) {
						super.dismiss(forever);
						if (forever) Formatter.removeSetting("uncaughtException");
					}
				};
			}
		}, "uncaughtShower").start();
		synchronized (toLoad) {
			boolean thread = false;
			for (MainChapter mch : toLoad) {
				if (mch.isEmpty(null)) try {
					mch.load(thread = !thread);
				} catch (Exception e) {
				}
			}
			toLoad.clear();
		}
	}

	public static void createMchs() {
		synchronized (toLoad) {
			if (Formatter.getSubjectsDir().listFiles() != null) for (GeneralPath f : Formatter.getSubjectsDir().listFiles())
				load:{
					for (MainChapter mch : MainChapter.ELEMENTS)
						if (mch.getDir().equals(f)) break load;
					if (f.getChild("setts.dat").exists())
						toLoad.add(new MainChapter(new Data(f.getName(), null)));
				}
			for (GeneralPath f : ImportedMchs.get())
				load:{
					for (MainChapter mch : MainChapter.ELEMENTS)
						if (mch.getDir().getOriginalName().equals(f.getOriginalName())) break load;
					if (f.exists()) toLoad.add(new MainChapter(new Data(f.getName(), null), f));
				}
		}
	}

	public static class ImportedMchs {
		static Set<GeneralPath> importedMchs;

		public static void importMch(GeneralPath mchDir) {
			checkLoaded();
			importedMchs.add(mchDir);
			save();
		}

		public static void removeMch(GeneralPath mchDir) {
			importedMchs.remove(mchDir);
			save();
		}

		private static void checkLoaded() {
			if (importedMchs == null) {
				importedMchs = new HashSet<>();
				backLog.onePath.add(0);
				String imds = (String) Formatter.getSetting("importedMchDirs");
				if (imds != null) for (String s : imds.split(";"))
					try {
						importedMchs.add(getIOSystem().createGeneralPath(s));
					} catch (Exception e) {
						Snackbar.make(Controller.activity.getCurrentFocus(),
								Controller.activity.getString(R.string.subject_not_found) + s,
								Snackbar.LENGTH_LONG).setAction("Action", null).setTextColor(0xFFEEEEEE).show();
					}
			}
		}

		private static void save() {
			if (importedMchs.isEmpty()) Formatter.removeSetting("importedMchDirs");
			else {
				StringBuilder sb = new StringBuilder();
				boolean first = true;
				for (GeneralPath f : get()) {
					if (first) first = false;
					else sb.append(';');
					sb.append(f.getOriginalName());
				}
				Formatter.putSetting("importedMchDirs", sb.toString());
			}
		}

		public static GeneralPath[] get() {
			checkLoaded();
			return importedMchs.toArray(new GeneralPath[0]);
		}
	}

	public static final List<Container> newChapters = new ArrayList<>();

	public static void save(List<? extends BasicData> blPath) {
		new Thread(() -> {
			for (int i = blPath.size() - 1; i >= 0; i--)
				if (blPath.get(i) instanceof ContainerFile) {
					((ContainerFile) blPath.get(i)).save();
					int saveAbove = i + 1;
					for (Container c : newChapters.toArray(new Container[0])) {
						int index = -1;
						for (int j = i; j >= 0; j--) {
							if (blPath.get(j) == c) {
								index = j;
								break;
							}
						}
						if (index != -1) {
							if (index < saveAbove) saveAbove = index;
							newChapters.remove(c);
						}
					}
					if (saveAbove != i + 1) {
						while (!(blPath.get(--saveAbove) instanceof ContainerFile)) ;
						((ContainerFile) blPath.get(saveAbove)).save();
					}
					return;
				}
		}, "CurrentData saver").start();
	}
}
