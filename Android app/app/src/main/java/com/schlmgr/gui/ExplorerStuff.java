package com.schlmgr.gui;

import android.content.Context;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.ScrollView;
import android.widget.SearchView;
import android.widget.SearchView.OnQueryTextListener;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.AppBarLayout;
import com.schlmgr.R;
import com.schlmgr.gui.CurrentData.BackLog;
import com.schlmgr.gui.CurrentData.EasyList;
import com.schlmgr.gui.activity.SelectItemsActivity;
import com.schlmgr.gui.list.HierarchyItemModel;
import com.schlmgr.gui.list.OpenListAdapter;
import com.schlmgr.gui.list.SearchAdapter;
import com.schlmgr.gui.list.SearchAdapter.OnItemActionListener;
import com.schlmgr.gui.list.SearchItemModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import objects.Picture;
import objects.Reference;
import objects.Word;
import objects.templates.BasicData;
import objects.templates.Container;
import objects.templates.TwoSided;
import testing.NameReader;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static com.schlmgr.gui.Controller.activity;
import static com.schlmgr.gui.Controller.dp;

/**
 * This class contains all data that are identical for both {@link com.schlmgr.gui.fragments.MainFragment}
 * and {@link SelectItemsActivity}.
 */
public class ExplorerStuff {

	public final Content content;
	public final Runnable onSearchSubmit;
	private final OnItemActionListener itemListener;

	private final HorizontalScrollView hsv;
	public final LinearLayout path;
	private final ScrollView infoScroll;
	private final TextView info;
	public final SearchView searchView;
	public final RecyclerView rv;
	private boolean opened;
	private int height_def;
	public final Context context;
	private final Runnable onCheckChange;
	private final BackLog backLog;
	private final ViewState VS;
	public final AppBarLayout searchHide;
	private final BackUpdater bu;

	public interface BackUpdater {
		void changedPath(int skip);
	}

	public ExplorerStuff(OnItemActionListener l, Content onItemClick, Runnable oss, Runnable onCheck,
	                     ViewState vs, BackLog bl, Context c, HorizontalScrollView hsv,
	                     RecyclerView rv, LinearLayout path, ScrollView infoScroll, TextView info,
	                     SearchView searchView, View touchOutside, AppBarLayout searchCollapser,
	                     BackUpdater bu) {
		this.itemListener = l;
		this.bu = bu;
		content = onItemClick;
		backLog = bl;
		VS = vs;
		context = c;
		onSearchSubmit = oss;
		onCheckChange = onCheck;
		this.hsv = hsv;
		this.rv = rv;
		this.path = path;
		this.infoScroll = infoScroll;
		this.info = info;
		(this.searchView = searchView).setOnFocusChangeListener((v, hasFocus) -> {
			if (!hasFocus) AndroidIOSystem.hideKeyboardFrom(v);
		});
		searchHide = searchCollapser;
		touchOutside.setOnTouchListener((v, event) -> {
			if (event.getAction() == MotionEvent.ACTION_DOWN
					&& searchView.getVisibility() == View.VISIBLE) {
				Rect outRect = new Rect();
				searchView.getGlobalVisibleRect(outRect);
				if (!outRect.contains((int) event.getRawX(), (int) event.getRawY())) {
					if (VS.sv_focused) {
						VS.sv_focused = false;
						VS.query = searchView.getQuery().toString();
						searchView.onActionViewCollapsed();
					}
				} else if (!VS.sv_focused) {
					v.performClick();
					VS.sv_focused = true;
					searchView.onActionViewExpanded();
					searchView.setQuery(VS.query, false);
					return true;
				}
			}
			return false;
		});
		hsv.setHorizontalScrollBarEnabled(false);
		searchView.setOnQueryTextListener(new SearchControl());
		info.setOnClickListener((v) -> infoScroll.setLayoutParams(
				new LayoutParams(MATCH_PARENT, realHeight(opened = !opened))));
	}

	public void updateSearch(boolean gone) {
		if (!gone) {
			searchHide.setExpanded(true, false);
		}
		searchView.setVisibility((VS.sv_visible = !gone) ? View.VISIBLE : View.GONE);
	}

	public class SearchControl implements OnQueryTextListener {

		@Override
		public boolean onQueryTextSubmit(String query) {
			searchView.onActionViewCollapsed();
			VS.query = query;
			VS.sv_focused = false;
			if (onSearchSubmit != null) onSearchSubmit.run();
			EasyList<Container> c = new EasyList<>();
			for (BasicData bd : backLog.path) c.add((Container) bd);
			new SearchEngine(c, query);
			return true;
		}

		@Override
		public boolean onQueryTextChange(String newText) {
			return false;
		}

		/**
		 * Searches for all occurrences matching the compare sequence.
		 */
		class SearchEngine {

			String comp;
			volatile int threads = 1;
			Correct correct;
			long start;
			Set<BasicData> set = new HashSet<>();

			/**
			 * Prepares and starts the searching, resolves the comparing method by prefix.
			 *
			 * @param path    path to the element who's content will be searched
			 * @param compare the sequence with optional prefix to be used as comparator
			 */
			SearchEngine(EasyList<Container> path, String compare) {
				Correct oldCor = null;
				SFManipulation sfm = null;
				StringFinder strCor = (name) -> {
					name = name.toLowerCase();
					if (name.contains(comp)) return true;
					for (String parsed : NameReader.readName(comp))
						if (name.contains(parsed)) return true;
					return false;
				};
				int start = 0;
				boolean desc = false;
				resolver:
				if (compare.charAt(start) == '\\') {
					switch (compare.charAt(++start)) { //select the type of searched object
						case 'W': //if object is the main Word
							oldCor = (bd, par) -> bd instanceof Word && ((Word) bd).isMain;
							break;
						case 'T': //if object is the other Word part - translate
							oldCor = (bd, par) -> bd instanceof Word && !((Word) bd).isMain;
							break;
						case 'P': //if object is Picture
							oldCor = (bd, par) -> bd instanceof Picture && ((Picture) bd).isMain;
							break;
						case 'C': //if object is any type of chapter
							oldCor = (bd, par) -> bd instanceof Container && !(bd instanceof TwoSided);
							break;
						default: //no type selection prefix found
							start--;
					}
					if (start + 1 >= compare.length()) {
						comp = "";
						break resolver;
					}
					switch (compare.charAt(++start)) { //select the source of comparing
						case 'D': //the tested value is the object's description
							desc = true;
							break;
						//for selection by success rate
						case 'S': //the tested value is amount of successes
							sfm = (bd) -> bd.getSF()[0];
							break;
						case 'F': //the tested value is amount of fails
							sfm = (bd) -> bd.getSF()[1];
							break;
						case 'N': //the tested value is the number of times, the object was tested
							sfm = (bd) -> bd.getSFCount();
							break;
						case 'R': //the tested value is the success rate ratio (S/F*100)
							sfm = (bd) -> bd.getRatio();
							break;
						default: //the tested value is the name of the object
							start--;
					}
					if (start + 1 >= compare.length()) {
						comp = "";
						break resolver;
					}
					int count1, count;
					try {
						count1 = Integer.parseInt(compare.substring(start + 2));
					} catch (NumberFormatException nfe) {
						count1 = -10;
					}
					count = count1;
					Correct copyOC = oldCor;
					SFManipulation copySFM = sfm;
					comp = compare.substring(start + 2);
					switch (compare.charAt(start + 1)) { //the main select operation prefix selector
						//number operations
						case '>': //if the object's value is higher (than the remaining text)
							oldCor = copyOC == null ? (bd, par) -> copySFM.value(bd) > count :
									(bd, par) -> copyOC.verify(bd, par) && copySFM.value(bd) > count;
							break;
						case '<': //if the object's value is lower
							oldCor = copyOC == null ? (bd, par) -> copySFM.value(bd) < count :
									(bd, par) -> copyOC.verify(bd, par) && copySFM.value(bd) < count;
							break;
						case '=': //if the object's value is equal
							oldCor = copyOC == null ? (bd, par) -> copySFM.value(bd) == count :
									(bd, par) -> copyOC.verify(bd, par) && copySFM.value(bd) == count;
							break;
						//text operations
						case 'r': //if the object's name (or description) matches given regex
							Pattern p;
							try {
								p = Pattern.compile(comp);
							} catch (Exception e) {
								String msg = activity.getString(R.string.pattern_err) + '\n' + e.getMessage();
								AndroidIOSystem.showMsg(msg, msg);
								return;
							}
							strCor = (name) -> p.matcher(name).matches();
							break;
						case 's': //if the object's value starts with the given text
							strCor = (name) -> {
								if (name.startsWith(comp)) return true;
								for (String parsed : NameReader.readName(comp))
									if (name.startsWith(parsed)) return true;
								return false;
							};
							break;
						case 'e': //if the object's value ends with the given text
							strCor = (name) -> {
								if (name.endsWith(comp)) return true;
								for (String parsed : NameReader.readName(comp))
									if (name.endsWith(parsed)) return true;
								return false;
							};
							break;
						case 'c': //if the object's value contains the given text
							strCor = (name) -> {
								if (name.contains(comp)) return true;
								for (String parsed : NameReader.readName(comp))
									if (name.contains(parsed)) return true;
								return false;
							};
							break;
						case '\\': //the object's value must contain the written text, ignores case
							comp = comp.toLowerCase();
						default:
							comp = compare.toLowerCase().substring(start + 1);
					}
				} else comp = compare.toLowerCase();
				//constructs the final search comparator
				Correct copyOC = oldCor;
				StringFinder copySC = strCor;
				correct = sfm != null ? oldCor : desc ?
						(bd, par) -> {
							if (copyOC != null && !copyOC.verify(bd, par)) return false;
							String d = bd.getDesc(par);
							return !d.isEmpty() && copySC.verify(d);
						} :
						(bd, par) -> {
							if (copyOC != null && !copyOC.verify(bd, par)) return false;
							if (copySC.verify(bd.getName())) return true;
							for (String name : NameReader.readName(bd))
								if (copySC.verify(name)) return true;
							return false;
						};

				backLog.add(false, null, null);
				rv.setAdapter(backLog.adapter = VS.contentAdapter = new SearchAdapter<SearchItemModel>(
						rv, itemListener, new ArrayList<>(), onCheckChange));
				updateSearch(false);
				info.setText(activity.getString(R.string.data_child_count) + 0);
				infoScroll.setLayoutParams(new LayoutParams(MATCH_PARENT, (int) (18 * dp)));
				this.start = System.currentTimeMillis();
				visited = new ArrayList<>();
				new Thread(() -> {
					search(path.get(-1).getChildren(path.get(-2)), path, false);
					threads--;
				}, "Search engine").start();
			}

			private List<Reference> visited;

			/**
			 * Searches the given content for matching elements.
			 */
			private void search(BasicData[] src, EasyList<Container> path, boolean threaded) {
				if (src == null) return;
				EasyList<Container> path2 = new EasyList<>();
				path2.addAll(path);
				boolean ref = false;
				for (BasicData bd : src) {
					if (bd instanceof Reference) try {
						if (visited.contains(bd)) continue;
						else visited.add((Reference) bd);
						bd.getThis();
					} catch (Exception e) {
						continue;
					}
					boolean found;
					if (backLog.adapter instanceof SearchAdapter) found = correct(bd, path);
					else return;
					if (bd instanceof Reference) {
						ref = true;
						path2.clear();
						Collections.addAll(path2, ((Reference) bd).getRefPath());
						bd = bd.getThis();
					} else if (ref) {
						path2.clear();
						path2.addAll(path);
						ref = false;
					}
					if (bd instanceof Container) {
						if (bd instanceof TwoSided) {
							if (bd instanceof Word && !found)
								for (BasicData trl : ((Word) bd).getChildren(path2.get(-1)))
									correct(trl, path2);
						} else {
							EasyList<Container> list = new EasyList<>();
							list.addAll(path2);
							list.add((Container) bd);
							if (!threaded && threads < 3) {
								threads++;
								Container c = (Container) bd;
								new Thread(() -> {
									search(c.getChildren(path2.get(-1)), list, true);
									threads--;
								}, "Searcher").start();
							} else {
								search(((Container) bd).getChildren(path2.get(-1)), list, threaded);
							}
						}
					}
				}
			}

			/**
			 * Validates the given element by the selected {@link #correct comparing} method.
			 */
			boolean correct(BasicData bd, EasyList<Container> path) {
				boolean yes = correct.verify(bd, path.get(-1));
				if (yes && set.add(bd)) {
					EasyList<Container> copy = new EasyList<>();
					copy.addAll(path);
					rv.post(() -> {
						backLog.adapter.addItem(new SearchItemModel(bd, copy,
								VS.contentAdapter.list.size() + 1));
						info.setText(activity.getString(R.string.data_child_count) + set.size() + ";\t" +
								activity.getString(R.string.time) + (System.currentTimeMillis() - start) + "ms");
					});
				}
				return yes;
			}
		}
	}

	private interface Correct {
		boolean verify(BasicData bd, Container par);
	}

	private interface SFManipulation {
		int value(BasicData bd);
	}

	private interface StringFinder {
		boolean verify(String str);
	}

	/**
	 * When the {@link #rv ListView's} content changes.
	 *
	 * @param allChanged if the currently displayed path to the element has to be fully altered.
	 */
	public void onChange(boolean allChanged) {
		if (allChanged) {
			path.removeAllViews();
			VS.breadCrumbs = 0;
			for (BasicData bd : backLog.path) addPathButton(bd);
		} else addPathButton(backLog.path.get(-1));
		setInfo(backLog.path.get(-1), (Container) backLog.path.get(-2));
	}

	/**
	 * Adds another item to the {@link #path breadCrumbs} tracker.
	 *
	 * @param bd the item to be added
	 */
	private void addPathButton(BasicData bd) {
		TextView btn = new Button(context);
		btn.setTextSize(17);
		btn.setAllCaps(false);
		btn.setBackground(null);
		btn.setTextColor(0xFFFFFFFF);
		btn.setPadding(0, 0, 0, 0);
		btn.setText(bd.toString());
		btn.setTypeface(null);
		btn.setLayoutParams(new LayoutParams((int)
				(btn.getPaint().measureText(bd.toString()) + dp * 8), MATCH_PARENT));
		btn.setOnClickListener((v) -> {
			if (backLog.path.get(-1) == bd) return;
			EasyList<BasicData> path = new EasyList<>();
			boolean noChange;
			for (BasicData e : backLog.path) {
				path.add(e);
				if (e == bd) break;
			}
			int diff = backLog.path.size() - path.size();
			EasyList<OpenListAdapter> adapters = backLog.copyPrevAdapters();
			for (int i = 1; i < diff && diff <= adapters.size(); i++)
				if (((SearchAdapter) adapters.get(-i)).search) diff++;
			if (diff >= adapters.size()) diff *= 5;
			if (noChange = diff <= backLog.onePath.get(-1)) {
				bu.changedPath(diff);
			} else backLog.add(true, null, path);
			content.setContent(bd, (Container) backLog.path.get(-2), backLog.path.size());
			if (noChange) setInfo(backLog.path.get(-1), (Container) backLog.path.get(-2));
			else onChange(true);
		});
		path.addView(btn);
		VS.breadCrumbs++;
		btn = new TextView(context);
		btn.setBackground(activity.getResources().getDrawable(R.drawable.ic_bread_crumbs));
		btn.setLayoutParams(new LayoutParams((int) (dp * 7), MATCH_PARENT));
		btn.setScaleX(2.4f);
		path.addView(btn);
	}

	public void updateBackPath(int skip) {
		for (; skip > 0; skip--) backLog.remove();
		(VS.contentAdapter = (SearchAdapter) backLog.adapter).update(rv);
		onChange(true);
	}

	/**
	 * Setts the {@link #infoScroll}
	 */
	public void setInfo(BasicData bd, Container parent) {
		String txt;
		if (!backLog.path.isEmpty()) {
			String desc = bd.getDesc(parent);
			info.setText(txt = (activity.getString(R.string.data_child_count) + backLog.adapter.list.size()
					+ ", " + activity.getString(R.string.success_rate) + ": " + bd.getRatio()
					+ (desc.isEmpty() ? '%' : "%\n" + desc)));
		} else
			info.setText(txt = (activity.getString(R.string.data_child_count) + backLog.adapter.list.size()));
		hsv.post(() -> hsv.fullScroll(View.FOCUS_RIGHT));
		info.setClickable((height_def = height(txt)) >= 3);
		infoScroll.setLayoutParams(new LayoutParams(MATCH_PARENT, realHeight(opened)));
		infoScroll.post(() -> infoScroll.fullScroll(View.FOCUS_UP));
	}

	/**
	 * Calculates the height parameter for {@link android.view.ViewGroup.LayoutParams#LayoutParams(int, int)}.
	 *
	 * @param opened toggles the unlimited height option - opened means maximum height
	 */
	private int realHeight(boolean opened) {
		return height_def >= 3 && opened ? LayoutParams.WRAP_CONTENT :
				(int) (activity.getResources().getDimension(R.dimen.dp) * (height_def == 1 ? 19 : 36));
	}

	/**
	 * Returns the height code for the given String, height code represents lines of the view.
	 * 1 when no '\n' chars, 2 for 1 '\n' char and 3 for more than 1 '\n' char.
	 */
	public int height(String src) {
		int i = 1;
		for (char ch : src.toCharArray()) if (ch == '\n' && ++i > 2) break;
		return i;
	}

	public static class ViewState {
		/**
		 * This adapter is the {@link BackLog#adapter} if not an {@link com.schlmgr.gui.list.ImageAdapter}.
		 */
		public SearchAdapter<? extends HierarchyItemModel> contentAdapter;
		public int breadCrumbs = 0;
		public boolean sv_focused;
		public boolean sv_visible;
		private String query;
	}

	public interface Content {
		/**
		 * What to do with the selected item.
		 *
		 * @param bd         the selected item
		 * @param parent     the item's parent
		 * @param pathLength length of the path to the item
		 */
		void setContent(BasicData bd, Container parent, int pathLength);
	}
}
