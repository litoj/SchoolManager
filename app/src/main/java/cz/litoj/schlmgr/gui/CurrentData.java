package cz.litoj.schlmgr.gui;

import android.view.View;

import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.snackbar.Snackbar;
import cz.litoj.schlmgr.R;
import cz.litoj.schlmgr.gui.list.HierarchyAdapter;
import cz.litoj.schlmgr.gui.list.ImageAdapter;
import cz.litoj.schlmgr.gui.list.OpenListAdapter;
import cz.litoj.schlmgr.gui.list.SearchAdapter;
import cz.litoj.schlmgr.gui.popup.CrashReportPopup;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import cz.litoj.schlmgr.gui.engine.IOSystem.Formatter;
import cz.litoj.schlmgr.gui.engine.IOSystem.Formatter.Data;
import cz.litoj.schlmgr.gui.engine.IOSystem.Formatter.IOSystem.GeneralPath;
import cz.litoj.schlmgr.gui.engine.objects.MainChapter;
import cz.litoj.schlmgr.gui.engine.objects.Picture;
import cz.litoj.schlmgr.gui.engine.objects.templates.BasicData;
import cz.litoj.schlmgr.gui.engine.objects.templates.Container;
import cz.litoj.schlmgr.gui.engine.objects.templates.ContainerFile;

import static cz.litoj.schlmgr.gui.engine.IOSystem.Formatter.getIOSystem;

public class CurrentData {

	public static final BackLog backLog = new BackLog();
	public static final Set<ContainerFile> changed = new HashSet<>();

	public static class BackLog {
		/**
		 * Path to the currently displayed element (inclusive).
		 */
		public EasyList<Container> path = new EasyList<>();
		/**
		 * This value is set after {@link #add(Boolean, Container, EasyList) adding} an updated path.
		 */
		public OpenListAdapter adapter;
		/**
		 * The previous paths to the displayed elements.
		 */
		private final EasyList<EasyList<Container>> prevPaths = new EasyList<>();
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

		public void add(Boolean newPath, Container bd, EasyList<Container> currPath) {
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
			ret.addAll(Arrays.asList(source));
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
		String crash = AndroidIOSystem.getCrashReport();
		if (crash != null) new CrashReportPopup(crash);
		synchronized (toLoad) {
			boolean thread = false;
			for (MainChapter mch : toLoad) mch.load(thread = !thread);
			toLoad.clear();
		}
	}

	public static void createMchs() {
		synchronized (toLoad) {
			if (Formatter.getSubjectsDir().listFiles() != null)
				for (GeneralPath f : Formatter.getSubjectsDir().listFiles())
					load:{
						for (MainChapter mch : MainChapter.ELEMENTS)
							if (mch.getDir().equals(f)) break load;
						if (f.hasChild("setts.dat"))
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
				String imds = AppSettings.getString("importedMchDirs");
				if (imds != null) for (String s : imds.split(";"))
					try {
						importedMchs.add(getIOSystem().createGeneralPath(s));
					} catch (Exception e) {
						View focus = Controller.activity.getCurrentFocus();
						if (focus != null)
							Snackbar.make(focus,
									Controller.activity.getString(R.string.subject_not_found) + s,
									Snackbar.LENGTH_LONG).setAction("Action", null).setTextColor(0xFFEEEEEE).show();
					}
			}
		}

		private static void save() {
			if (importedMchs.isEmpty()) AppSettings.remove("importedMchDirs");
			else {
				StringBuilder sb = new StringBuilder();
				boolean first = true;
				for (GeneralPath f : get()) {
					if (first) first = false;
					else sb.append(';');
					sb.append(f.getOriginalName());
				}
				AppSettings.set("importedMchDirs", sb.toString());
			}
		}

		public static GeneralPath[] get() {
			checkLoaded();
			return importedMchs.toArray(new GeneralPath[0]);
		}
	}

	public static void save(List<? extends BasicData> blPath) {
		for (int i = blPath.size() - 1; i >= 0; i--) {
			if (blPath.get(i) instanceof ContainerFile) {
				changed.add((ContainerFile) blPath.get(i));
				return;
			}
		}
	}
}
