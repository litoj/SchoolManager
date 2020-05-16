package com.schlmgr.gui.activity;

import android.graphics.drawable.Drawable;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
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
import com.schlmgr.gui.list.SearchAdapter;
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

import static com.schlmgr.gui.Controller.currentActivity;
import static com.schlmgr.gui.Controller.dp;
import static com.schlmgr.gui.fragments.TestFragment.list;
import static com.schlmgr.gui.list.HierarchyItemModel.convert;

public class SelectItemsActivity extends PopupCareActivity
		implements OnItemClickListener, OnItemLongClickListener {

	private static long backTime;
	public static BackLog backLog;
	private static ExplorerStuff es;
	private static ViewState VS = new ViewState();

	private TextView select;

	private static Drawable icSelect;
	private static Drawable icSelect_disabled;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		currentActivity = this;
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
			})).start();
			super.onBackPressed();
		});
		boolean none;
		if (none = backLog == null) {
			(backLog = new BackLog()).clear();
			VS = new ViewState();
		}
		es = new ExplorerStuff(true, this::setContent, this::checkSelectUsability,
				this::checkSelectUsability, VS, backLog,
				Controller.CONTEXT = getApplicationContext(), findViewById(R.id.objects_path_handler),
				findViewById(R.id.objects_list), findViewById(R.id.objects_path),
				findViewById(R.id.objects_info_handler), findViewById(R.id.objects_info),
				findViewById(R.id.objects_search), findViewById(R.id.touch_outside));
		es.lv.setOnItemClickListener(this);
		es.lv.setOnItemLongClickListener(this);
		if (none) {
			setContent(backLog.path.get(-1), (Container) backLog.path.get(-2), backLog.path.size());
			es.setInfo(backLog.path.get(-1), (Container) backLog.path.get(-2));
		} else {
			es.lv.setAdapter(VS.contentAdapter);
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
		if (!backLog.path.isEmpty() && list.isEmpty() || backLog.path.size() > 1) {
			if (VS.contentAdapter instanceof SearchAdapter) {
				setContent(backLog.path.get(-1), (Container) backLog.path.get(-2), backLog.path.size());
				return;
			}
			boolean change = !backLog.remove();
			setContent(backLog.path.get(-1), (Container) backLog.path.get(-2), backLog.path.size());
			if (change) es.onChange(true);
			else {
				VS.breadCrumbs--;
				es.path.removeViews(VS.breadCrumbs * 2, 2);
				es.setInfo(backLog.path.get(-1), (Container) backLog.path.get(-2));
			}
			checkSelectUsability();
		} else {
			if (System.currentTimeMillis() - backTime > 3000) {
				backTime = System.currentTimeMillis();
				Toast.makeText(getApplicationContext(), R.string.press_exit, Toast.LENGTH_SHORT).show();
			} else super.onBackPressed();
		}
	}

	@Override
	public void onItemClick(AdapterView<?> par, View v, int pos, long id) {
		HierarchyItemModel him = VS.contentAdapter.list.get(pos);
		BasicData bd = him.bd;
		if (bd instanceof Picture) return;
		if (bd instanceof Word) {
			if (HierarchyItemModel.flipAllOnClick) {
				boolean flip = !him.flipped;
				for (HierarchyItemModel item : VS.contentAdapter.list)
					if (item.flipped != flip) item.flip();
			} else him.flip();
			VS.contentAdapter.notifyDataSetChanged();
			return;
		} else {
			boolean ref;
			if (ref = bd instanceof Reference) {
				try {
					him.setNew(bd.getThis(), ((Reference) bd).getRefPathAt(-1));
					if (bd.getThis() instanceof Word) {
						VS.contentAdapter.notifyDataSetChanged();
						return;
					} else {
						if (bd.getThis() instanceof Picture) return;
						backLog.add(true, null, EasyList.convert(((Reference) bd).getRefPath()));
						backLog.path.add(bd = bd.getThis());
					}
				} catch (Exception e) {
					return;
				}
			} else if (!(ref = VS.contentAdapter instanceof SearchAdapter)) backLog.add(false, bd, null);
			else {
				backLog.add(true, null, (EasyList<BasicData>) ((SearchItemModel) him).path);
				backLog.path.add(him.bd);
			}
			setContent(bd, him.parent, backLog.path.size());
			es.onChange(ref);
		}
	}

	@Override
	public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
		if (VS.contentAdapter instanceof SearchAdapter) {
			SearchItemModel sim = (SearchItemModel) parent.getItemAtPosition(position);
			backLog.add(true, null, (EasyList<BasicData>) sim.path);
			EasyList<Container> c = (EasyList<Container>) sim.path;
			setContent(c.get(-1), c.get(-2), 1);
			es.onChange(true);
		} else if (VS.contentAdapter instanceof HierarchyAdapter) {
			boolean selected = !VS.contentAdapter.list.get(position).isSelected();
			VS.contentAdapter.list.get(position).setSelected(selected);
			VS.contentAdapter.notifyDataSetChanged();
		} else return false;
		return true;
	}

	public void setContent(BasicData bd, Container parent, int size) {
		es.searchControl.update(size == 0);
		es.lv.setAdapter(VS.mAdapter = VS.contentAdapter = new HierarchyAdapter(es.context, size == 0 ?
				convert(new ArrayList<>(MainChapter.ELEMENTS), null) : convert(((Container) bd)
				.getChildren(parent), (Container) bd), this::checkSelectUsability, true));
		checkSelectUsability();
	}
}
