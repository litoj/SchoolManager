package com.schlmgr.gui.activity;

import android.graphics.drawable.Drawable;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.graphics.drawable.DrawableCompat;

import com.schlmgr.R;
import com.schlmgr.gui.Controller;
import com.schlmgr.gui.CurrentData.BackLog;
import com.schlmgr.gui.CurrentData.EasyList;
import com.schlmgr.gui.ExplorerStuff;
import com.schlmgr.gui.ExplorerStuff.ViewState;
import com.schlmgr.gui.fragments.TestFragment;
import com.schlmgr.gui.list.HierarchyAdapter;
import com.schlmgr.gui.list.HierarchyItemModel;
import com.schlmgr.gui.list.SearchAdapter.OnItemActionListener;
import com.schlmgr.gui.list.SearchItemModel;

import java.util.ArrayList;
import java.util.Arrays;

import objects.MainChapter;
import objects.Picture;
import objects.Reference;
import objects.Word;
import objects.templates.BasicData;
import objects.templates.Container;
import objects.templates.TwoSided;

import static com.schlmgr.gui.Controller.dp;
import static com.schlmgr.gui.fragments.TestFragment.list;
import static com.schlmgr.gui.list.HierarchyItemModel.convert;

public class SelectItemsActivity extends PopupCareActivity
		implements OnItemActionListener {

	private static long backTime;
	public static BackLog backLog;
	private static ExplorerStuff es;
	private static ViewState VS = new ViewState();

	private TextView select;

	private static Drawable icSelect;
	private static Drawable icSelect_disabled;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_select_item);
		findViewById(R.id.objects_cancel).setOnClickListener(v -> super.onBackPressed());
		findViewById(R.id.select_all).setOnClickListener(v -> {
			boolean all = VS.contentAdapter.list.size() > VS.contentAdapter.selected;
			for (HierarchyItemModel him : VS.contentAdapter.list) him.setSelected(all);
			VS.contentAdapter.selected = all ? VS.contentAdapter.list.size() : 0;
			checkSelectUsability();
			VS.contentAdapter.notifyDataSetChanged();
		});
		(select = findViewById(R.id.objects_select)).setOnClickListener(v -> {
			EasyList<Container> src = new EasyList<>();
			boolean ha = VS.contentAdapter instanceof HierarchyAdapter;
			if (ha) for (BasicData bd : backLog.path) src.add((Container) bd);
			for (HierarchyItemModel him : VS.contentAdapter.list) {
				if (him.isSelected())
					if (him.bd instanceof Reference) {
						try {
							him.bd.getThis();
						} catch (IllegalArgumentException iae) {
							continue;
						}
						EasyList<Container> path = new EasyList<>();
						path.addAll(Arrays.asList(((Reference) him.bd).getRefPath()));
						list.add(0, new SearchItemModel(him.bd.getThis(), path, -1));
					} else if (!(him.bd instanceof TwoSided)
							|| him.bd instanceof Picture == TestFragment.picTest)
						list.add(0, ha ? new SearchItemModel(him.bd, src, -1) : (SearchItemModel) him);
			}
			(TestFragment.control = new Thread(() -> {
				for (int pos = 0; pos < list.size() - 1; pos++) {
					Container[] path = list.get(pos).path.toArray(new Container[0]);
					for (int i = list.size() - 1; i > pos; i--) {
						isOk:
						{
							Container[] path2 = list.get(i).path.toArray(new Container[0]);
							for (int j = 1; j < path2.length && j < path.length; j++)
								if (path[j] != path2[j]) break isOk;
							if (list.get(i).path.size() > path.length) {
								if (list.get(pos).bd == path2[path.length]) list.remove(i);
							} else if (list.get(i).path.size() < path.length) {
								if (list.get(i).bd == path[path2.length]) {
									list.remove(pos--);
									break;
								}
							} else if (list.get(i).bd == list.get(pos).bd) {
								list.remove(pos--);
								break;
							}
						}
					}
				}
				runOnUiThread(() -> TestFragment.adapter.notifyDataSetChanged());
			}, "TFrag test item control")).start();
			super.onBackPressed();
		});
		boolean none;
		if (none = backLog == null) {
			(backLog = new BackLog()).clear();
			VS = new ViewState();
		}
		es = new ExplorerStuff(this, this::setContent, this::checkSelectUsability,
				this::checkSelectUsability, VS, backLog, Controller.CONTEXT = getApplicationContext(),
				findViewById(R.id.explorer_path_handler), findViewById(R.id.explorer_list),
				findViewById(R.id.explorer_path), findViewById(R.id.explorer_info_handler),
				findViewById(R.id.explorer_info), findViewById(R.id.explorer_search),
				findViewById(R.id.touch_outside), findViewById(R.id.search_collapser),
				this::updateBackPath);
		if (none) {
			setContent(backLog.path.get(-1), (Container) backLog.path.get(-2), backLog.path.size());
			es.setInfo(backLog.path.get(-1), (Container) backLog.path.get(-2));
		} else {
			VS.contentAdapter.update(es.rv);
			VS.contentAdapter.occ = this::checkSelectUsability;
			if (!VS.sv_visible) es.searchView.setVisibility(View.GONE);
			es.onChange(true);
		}
		if (icSelect == null) {
			(icSelect = getResources().getDrawable(R.drawable.ic_check))
					.setBounds((int) dp, 0, (int) (dp * 35), (int) (dp * 35));
			(icSelect_disabled = getResources().getDrawable(R.drawable.ic_check_disabled))
					.setBounds((int) dp, 0, (int) (dp * 35), (int) (dp * 35));
			if (VERSION.SDK_INT < 21)
				DrawableCompat.setTint(DrawableCompat.wrap(icSelect_disabled), 0x55FFFFFF);
		}
		checkSelectUsability();
	}

	public void setContent(BasicData bd, Container parent, int size) {
		es.updateSearch(size == 0);
		es.rv.setAdapter(backLog.adapter = VS.contentAdapter = new HierarchyAdapter(es.rv,
				this, size == 0 ? convert(new ArrayList<>(MainChapter.ELEMENTS), null) :
				convert(((Container) bd).getChildren(parent), (Container) bd),
				this::checkSelectUsability));
		checkSelectUsability();
	}

	public void updateBackPath(int skip) {
		es.updateBackPath(skip);
		es.updateSearch(backLog.path.isEmpty());
		VS.contentAdapter.occ = this::checkSelectUsability;
		checkSelectUsability();
	}


	/**
	 * Checks if the {@link #select} button is enabled. Changes the state if necessary.
	 */
	private void checkSelectUsability() {
		boolean selectOn = VS.contentAdapter.selected > 0
				&& (!backLog.path.isEmpty() || VS.contentAdapter.selected < 2);
		select.setCompoundDrawables(null, selectOn ? icSelect : icSelect_disabled, null, null);
		select.setTextColor(selectOn ? 0xFFFFFFFF : 0x66FFFFFF);
		select.setClickable(selectOn);
	}

	@Override
	public void onBackPressed() {
		if (clear()) return;
		VS.sv_focused = false;
		es.searchView.clearFocus();
		if (!backLog.path.isEmpty() && list.isEmpty() || backLog.path.size() > 1
				|| !(VS.contentAdapter instanceof HierarchyAdapter)) updateBackPath(1);
		else {
			if (System.currentTimeMillis() - backTime > 3000) {
				backTime = System.currentTimeMillis();
				Toast.makeText(getApplicationContext(), R.string.press_exit, Toast.LENGTH_SHORT).show();
			} else super.onBackPressed();
		}
	}

	@Override
	public void onItemClick(HierarchyItemModel item) {
		BasicData bd = item.bd;
		if (bd instanceof Picture) return;
		if (bd instanceof Word) {
			if (HierarchyItemModel.flipAllOnClick) {
				boolean flip = !item.flipped;
				for (HierarchyItemModel item1 : VS.contentAdapter.list)
					if (item1.flipped != flip) item1.flip();
			} else item.flip();
			VS.contentAdapter.notifyDataSetChanged();
			return;
		} else {
			boolean ref;
			if (ref = bd instanceof Reference) {
				try {
					item.setNew(bd.getThis(), ((Reference) bd).getRefPathAt(-1));
					if (bd.getThis() instanceof Word) {
						VS.contentAdapter.notifyDataSetChanged();
						return;
					} else {
						if (bd.getThis() instanceof Picture) return;
						backLog.add(true, null, EasyList.convert(((Reference) bd).getRefPath()));
						backLog.path.add((Container) (bd = bd.getThis()));
					}
				} catch (Exception e) {
					return;
				}
			} else if (!(ref = VS.contentAdapter.search))
				backLog.add(false, (Container) bd, null);
			else {
				EasyList<Container> list = new EasyList<>();
				list.addAll(((SearchItemModel) item).path);
				list.add((Container) item.bd);
				backLog.add(true, null, list);
			}
			setContent(bd, item.parent, backLog.path.size());
			es.onChange(ref);
		}
	}

	@Override
	public boolean onItemLongClick(HierarchyItemModel item) {
		if (VS.contentAdapter.search) {
			SearchItemModel sim = (SearchItemModel) item;
			EasyList<Container> list = new EasyList<>();
			list.addAll(sim.path);
			list.add((Container) item.bd);
			backLog.add(true, null, list);
			setContent(sim.path.get(-1), sim.path.get(-2), 1);
			es.onChange(true);
		} else if (VS.contentAdapter instanceof HierarchyAdapter) {
			boolean selected = !item.isSelected();
			item.setSelected(selected);
			checkSelectUsability();
			VS.contentAdapter.notifyDataSetChanged();
		} else return false;
		return true;
	}
}
