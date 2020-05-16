package com.schlmgr.gui.fragments;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.fragment.app.Fragment;

import com.schlmgr.R;
import com.schlmgr.gui.AndroidIOSystem;
import com.schlmgr.gui.Controller;
import com.schlmgr.gui.CurrentData;
import com.schlmgr.gui.CurrentData.EasyList;
import com.schlmgr.gui.ExplorerStuff;
import com.schlmgr.gui.activity.SelectDirActivity;
import com.schlmgr.gui.list.AbstractContainerAdapter;
import com.schlmgr.gui.list.HierarchyAdapter;
import com.schlmgr.gui.list.HierarchyItemModel;
import com.schlmgr.gui.list.ImageAdapter;
import com.schlmgr.gui.list.ImageItemModel;
import com.schlmgr.gui.list.ImageRecyclerAdapter;
import com.schlmgr.gui.list.SearchAdapter;
import com.schlmgr.gui.list.SearchItemModel;
import com.schlmgr.gui.list.TranslateRecyclerAdapter;
import com.schlmgr.gui.popup.ContinuePopup;
import com.schlmgr.gui.popup.CreatorPopup;
import com.schlmgr.gui.popup.CreatorPopup.Includer;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import IOSystem.Formatter;
import IOSystem.Formatter.Data;
import IOSystem.SimpleReader;
import IOSystem.SimpleWriter;
import objects.Chapter;
import objects.MainChapter;
import objects.Picture;
import objects.Reference;
import objects.SaveChapter;
import objects.Word;
import objects.templates.BasicData;
import objects.templates.Container;
import objects.templates.ContainerFile;
import objects.templates.TwoSided;

import static IOSystem.Formatter.defaultReacts;
import static com.schlmgr.gui.Controller.activity;
import static com.schlmgr.gui.Controller.dp;
import static com.schlmgr.gui.CurrentData.backLog;
import static com.schlmgr.gui.CurrentData.finishLoad;
import static com.schlmgr.gui.list.HierarchyItemModel.convert;

public class MainFragment extends Fragment
		implements Controller.ControlListener, OnItemClickListener, OnItemLongClickListener {

	private static LinearLayout selectOpts;
	private static TextView edit;
	private static TextView delete;
	private static TextView reference;
	private static TextView cut;

	private LinearLayout pasteOpts;
	private TextView paste;

	private static Drawable icDelete;
	private static Drawable icDelete_disabled;
	private static Drawable icReference;
	private static Drawable icReference_disabled;
	private static Drawable icCut;
	private static Drawable icCut_disabled;
	private static Drawable icEdit;
	private static Drawable icEdit_disabled;
	private static Drawable icPaste;
	private static Drawable icPaste_disabled;

	private static long backTime;
	public static ExplorerStuff es;
	public static ViewState VS = new ViewState();

	public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle oldState) {
		VS.mfInstance = this;
		View root = inflater.inflate(R.layout.fragment_main, container, false);
		selectOpts = root.findViewById(R.id.objects_select);
		delete = root.findViewById(R.id.select_delete);
		reference = root.findViewById(R.id.select_reference);
		cut = root.findViewById(R.id.select_cut);
		edit = root.findViewById(R.id.select_rename);
		pasteOpts = root.findViewById(R.id.objects_paster);
		root.findViewById(R.id.objects_cancel).setOnClickListener(v -> {
			VS.pasteMode = false;
			pasteOpts.setVisibility(View.GONE);
		});
		paste = root.findViewById(R.id.objects_paste);

		if (icCut == null) {
			Resources res = activity.getResources();
			(icDelete = res.getDrawable(R.drawable.ic_delete))
					.setBounds(0, 0, (int) (dp * 40), (int) (dp * 40));
			(icDelete_disabled = res.getDrawable(R.drawable.ic_delete_disabled))
					.setBounds(0, 0, (int) (dp * 40), (int) (dp * 40));
			(icReference = res.getDrawable(R.drawable.ic_reference))
					.setBounds(0, 0, (int) (dp * 40), (int) (dp * 40));
			(icReference_disabled = res.getDrawable(R.drawable.ic_reference_disabled))
					.setBounds(0, 0, (int) (dp * 40), (int) (dp * 40));
			(icCut = res.getDrawable(R.drawable.ic_cut))
					.setBounds(0, 0, (int) (dp * 40), (int) (dp * 40));
			(icCut_disabled = res.getDrawable(R.drawable.ic_cut_disabled))
					.setBounds(0, 0, (int) (dp * 40), (int) (dp * 40));
			(icEdit = res.getDrawable(R.drawable.ic_edit))
					.setBounds(0, 0, (int) (dp * 35), (int) (dp * 35));
			(icEdit_disabled = res.getDrawable(R.drawable.ic_edit_disabled))
					.setBounds(0, 0, (int) (dp * 35), (int) (dp * 35));
			(icPaste = res.getDrawable(R.drawable.ic_paste))
					.setBounds(0, 0, (int) (dp * 33), (int) (dp * 33));
			(icPaste_disabled = res.getDrawable(R.drawable.ic_paste_disabled))
					.setBounds(0, 0, (int) (dp * 33), (int) (dp * 33));
			if (VERSION.SDK_INT < 21) {
				DrawableCompat.setTint(DrawableCompat.wrap(icDelete_disabled), 0x55FFFFFF);
				DrawableCompat.setTint(DrawableCompat.wrap(icReference_disabled), 0x55FFFFFF);
				DrawableCompat.setTint(DrawableCompat.wrap(icCut_disabled), 0x55FFFFFF);
				DrawableCompat.setTint(DrawableCompat.wrap(icEdit_disabled), 0x55FFFFFF);
				DrawableCompat.setTint(DrawableCompat.wrap(icPaste_disabled), 0x55FFFFFF);
			}
		}

		new Thread(() -> {
			delete.setOnClickListener(v -> {
				if (((AbstractContainerAdapter) VS.mAdapter).selected > 0)
					new ContinuePopup(getString(R.string.continue_delete), () -> {
						for (int i = VS.contentAdapter.list.size() - 1; i >= 0; i--) {
							HierarchyItemModel him = VS.contentAdapter.list.get(i);
							if (him.isSelected()) {
								if (backLog.path.isEmpty()) him.bd.destroy(null);
								else {
									him.parent.removeChild((Container) (him instanceof SearchItemModel ?
											((SearchItemModel) him).path.get(-2)
											: backLog.path.get(-2)), him.bd);
								}
								VS.contentAdapter.list.remove(i);
							}
						}
						if (!backLog.path.isEmpty()) CurrentData.save();
						root.post(() -> {
							VS.contentAdapter.notifyDataSetChanged();
							VS.contentAdapter.selected = -1;
							selectOpts.setVisibility(View.GONE);
						});
					});
			});
			reference.setOnClickListener(v -> {
				for (HierarchyItemModel him : VS.contentAdapter.list)
					if (him.isSelected()) {
						EasyList<? extends BasicData> list;
						if (VS.contentAdapter instanceof SearchAdapter)
							list = ((SearchItemModel) him).path;
						else if (him.bd instanceof Reference)
							list = EasyList.convert(((Reference) him.bd).getRefPath());
						else break;
						backLog.add(true, null, (EasyList<BasicData>) list);
						setContainerContent(((EasyList<Container>) list).get(-1),
								((EasyList<Container>) list).get(-2));
						es.onChange(true);
						VS.contentAdapter.selected = -1;
						setSelectOpts(false);
						return;
					}
				move(true);
			});
			cut.setOnClickListener(v -> move(false));
			edit.setOnClickListener(none -> {
				for (HierarchyItemModel him : VS.contentAdapter.list) {
					if (him.isSelected()) {
						if (him.bd instanceof Container && !(him.bd instanceof TwoSided)) {
							activity.runOnUiThread(() -> new CreatorPopup(getString(R.string.edit), (x, cp) -> {
								if (cp.et_name.getText().toString().isEmpty()) {
									cp.et_name.setText(him.bd.getName());
									cp.et_desc.setText(him.bd.getDesc(him.parent));
								}
								cp.ok.setOnClickListener(v -> {
									String name = cp.et_name.getText().toString();
									if (name.isEmpty()) return;
									try {
										if (!name.equals(him.bd.getName())) {
											if (him.bd instanceof ContainerFile) ContainerFile.isCorrect(name);
											him.bd.setName(him.parent, name);
										}
										him.bd.putDesc(him.parent, cp.et_desc.getText().toString());
										if (him.bd instanceof MainChapter) ((MainChapter) him.bd).save();
										else CurrentData.save();
										VS.contentAdapter.selected = -1;
										setSelectOpts(false);
										him.toShow = him.bd.toString();
										VS.mAdapter.notifyDataSetChanged();
										cp.dismiss();
									} catch (IllegalArgumentException iae) {
										defaultReacts.get(ContainerFile.class + ":name")
												.react(iae.getMessage().contains("longer"));
									}
								});
								return null;
							}));
						} else if (him.bd instanceof Word) {
							activity.runOnUiThread(() -> new CreatorPopup(getString(R.string.edit), new Includer() {
								TranslateRecyclerAdapter content;

								@Override
								public View onInclude(LayoutInflater li, CreatorPopup cp) {
									LinearLayout ll = (LinearLayout) li.inflate(R.layout.new_twosided, null);
									if (content == null) content = new TranslateRecyclerAdapter(him, cp);
									Runnable onClick = content.onClick(ll);
									cp.ok.setOnClickListener(v -> {
										try {
											onClick.run();
											if (content.toRemove != null) return;
											CurrentData.save();
											VS.contentAdapter.selected = -1;
											setSelectOpts(false);
											him.flipped = !him.flipped;
											him.flip();
											VS.mAdapter.notifyDataSetChanged();
											cp.dismiss();
										} catch (IllegalArgumentException iae) {
											System.out.println("An Unexpected Exception occurred:\n"
													+ iae.getMessage() + "\n" + Formatter.getStackTrace(iae));
										}
									});
									return ll;
								}
							}));
						} else if (him.bd instanceof Picture) {
							if (!AndroidIOSystem.requestWrite()) return;
							activity.runOnUiThread(() -> new CreatorPopup(getString(R.string.edit), new Includer() {
								ImageRecyclerAdapter content;

								@Override
								public View onInclude(LayoutInflater li, CreatorPopup cp) {
									LinearLayout ll = (LinearLayout) li.inflate(R.layout.new_twosided, null);
									if (content == null) content = new ImageRecyclerAdapter(him, cp);
									Runnable onClick = content.onClick(ll);
									cp.ok.setOnClickListener(v -> {
										try {
											onClick.run();
											if (content.toRemove != null) return;
											CurrentData.save();
											VS.contentAdapter.selected = -1;
											setSelectOpts(false);
											him.toShow = him.bd.toString();
											VS.mAdapter.notifyDataSetChanged();
											cp.dismiss();
										} catch (IllegalArgumentException iae) {
											System.out.println("An Unexpected Exception occurred:\n"
													+ iae.getMessage() + "\n" + Formatter.getStackTrace(iae));
										}
									});
									return ll;
								}
							}));
						}
						break;
					}
				}
			});
			if (VS.mAdapter instanceof AbstractContainerAdapter && VS.contentAdapter.selected > -1) {
				selectOpts.setVisibility(View.VISIBLE);
				for (HierarchyItemModel him : ((AbstractContainerAdapter<? extends HierarchyItemModel>) VS.mAdapter).list)
					if (him.isSelected() && him.bd instanceof Reference) {
						tglEnabled(edit, false);
						return;
					}
				if (VS.contentAdapter.selected > 1) tglEnabled(edit, false);
			}
		}).start();

		es = new ExplorerStuff(false, this::setContent, () -> {
			VS.contentAdapter.selected = -1;
			setSelectOpts(false);
		}, this::setVisibleOpts, VS, backLog, getContext(),
				root.findViewById(R.id.objects_path_handler),
				root.findViewById(R.id.objects_list), root.findViewById(R.id.objects_path),
				root.findViewById(R.id.objects_info_handler), root.findViewById(R.id.objects_info),
				root.findViewById(R.id.objects_search), root.findViewById(R.id.touch_outside));
		es.lv.setOnItemClickListener(this);
		es.lv.setOnItemLongClickListener(this);
		Formatter.defaultReacts.put("MChLoaded", (o) -> {
			if (backLog.path.isEmpty()) root.post(() -> VS.mAdapter.notifyDataSetChanged());
		});
		if (VS.mAdapter == null) {
			setContent(backLog.path.get(-1), (Container) backLog.path.get(-2), backLog.path.size());
			es.setInfo(backLog.path.get(-1), (Container) backLog.path.get(-2));
		} else {
			es.lv.setAdapter(VS.mAdapter);
			if (!VS.sv_visible) es.searchView.setVisibility(View.GONE);
			es.onChange(true);
		}
		setVisibleOpts();
		pasteOpts.setVisibility(VS.pasteMode ? View.VISIBLE : View.GONE);
		return root;
	}

	private static List<Container> original = new ArrayList<>();
	private static List<HierarchyItemModel> toMove = new ArrayList<>();

	/**
	 * Sets up move-mode.
	 *
	 * @param ref if the selected items will be referenced or moved.
	 */
	private void move(boolean ref) {
		VS.pasteMode = true;
		Controller.toggleSelectBtn(false);
		pasteOpts.setVisibility(View.VISIBLE);
		tglEnabled(paste, false);
		boolean search = VS.mAdapter instanceof SearchAdapter;
		toMove = new ArrayList<>();
		for (HierarchyItemModel him : VS.contentAdapter.list)
			if (him.isSelected()) toMove.add(him);
		original = new ArrayList<>();
		for (BasicData bd : backLog.path) original.add((Container) bd);
		paste.setOnClickListener(v -> {
			if (backLog.path.size() == original.size()) {
				int i = 0;
				boolean ok = false;
				for (BasicData bd : backLog.path) if (ok = bd != original.get(i++)) break;
				if (!ok) return;
			}
			Container npp = (Container) backLog.path.get(-2);
			Container np = (Container) backLog.path.get(-1);
			boolean searchNow = VS.mAdapter instanceof SearchAdapter;
			for (BasicData bd : np.getChildren(npp)) toMove.remove(bd);
			if (ref) {
				List<Container> cp = new ArrayList<>(backLog.path.size());
				for (BasicData bd : backLog.path) cp.add((Container) bd);
				for (HierarchyItemModel him : toMove) {
					Reference r;
					try {
						r = Reference.mkElement(him.bd, cp,
								(search ? (List<Container>) ((SearchItemModel) him).path : original)
										.toArray(new Container[0]));
					} catch (IllegalArgumentException iae) {
						continue;
					}
					np.putChild(npp, r);
					if (!searchNow)
						VS.mAdapter.add(new HierarchyItemModel(r, np, VS.mAdapter.getCount()));
				}
			} else {
				List<ContainerFile> toSave = new LinkedList<>();
				for (HierarchyItemModel him : toMove) {
					him.bd.move(him.parent, search ? (Container) ((SearchItemModel) him).path.get(-2)
							: original.get(original.size() - 2), np, npp);
					if (search) {
						List<? extends BasicData> path = ((SearchItemModel) him).path;
						for (int i = path.size() - 1; i >= 0; i--)
							if (path.get(i) instanceof ContainerFile) {
								if (!toSave.contains(path.get(i))) toSave.add((ContainerFile) path.get(i));
								break;
							}
					}
					if (!searchNow) {
						if (search)
							VS.mAdapter.add(new HierarchyItemModel(him.bd, np, VS.mAdapter.getCount()));
						else {
							VS.mAdapter.add(him);
							him.parent = np;
						}
					}
				}
				if (!search) {
					for (int i = original.size() - 1; i >= 0; i--)
						if (original.get(i) instanceof ContainerFile) {
							toSave.add((ContainerFile) original.get(i));
							break;
						}
				}
				for (ContainerFile cf : toSave)
					try {
						cf.save();
					} catch (Exception e) {
						defaultReacts.get(ContainerFile.class + ":save").react(e, cf.getSaveFile(), cf);
						CurrentData.changed.add(cf);
					}
			}
			CurrentData.save();
			VS.pasteMode = false;
			pasteOpts.setVisibility(View.GONE);
		});
		VS.contentAdapter.selected = -1;
		setSelectOpts(false);
	}

	/**
	 * Controls the visibility of selecting options.
	 *
	 * @param change if an item has been clicked
	 */
	private void setSelectOpts(boolean change) {
		selectOpts.setVisibility(VS.contentAdapter.selected > -1 ? View.VISIBLE : View.GONE);
		setVisibleOpts();
		if (VS.contentAdapter.selected == -1 && !change) {
			VS.contentAdapter.ref = 0;
			for (HierarchyItemModel him : VS.contentAdapter.list) him.setSelected(false);
		}
	}

	/**
	 * Controls the usability of the {@link #selectOpts} buttons.
	 */
	private void setVisibleOpts() {
		boolean notObj = backLog.path.size() > 0;
		int selected = VS.contentAdapter.selected;
		tglEnabled(delete, selected > 0);
		tglEnabled(reference, selected > 0 && notObj && VS.contentAdapter.ref < 2 &&
				(!(VS.contentAdapter instanceof SearchAdapter) || selected < 2));
		tglEnabled(cut, selected > 0 && notObj && VS.contentAdapter.ref < 1);
		tglEnabled(edit, selected == 1 && VS.contentAdapter.ref < 1);
	}

	/**
	 * Toggles the usability of the given button.
	 */
	private void tglEnabled(TextView tv, boolean enabled) {
		if (tv.isEnabled() == enabled) return;
		if (tv == delete)
			tv.setCompoundDrawables(null, enabled ? icDelete : icDelete_disabled, null, null);
		else if (tv == reference)
			tv.setCompoundDrawables(null, enabled ? icReference : icReference_disabled, null, null);
		else if (tv == cut)
			tv.setCompoundDrawables(null, enabled ? icCut : icCut_disabled, null, null);
		else if (tv == edit)
			tv.setCompoundDrawables(null, enabled ? icEdit : icEdit_disabled, null, null);
		else if (tv == paste)
			tv.setCompoundDrawables(null, enabled ? icPaste : icPaste_disabled, null, null);
		tv.setTextColor(enabled ? 0xFFFFFFFF : 0x66FFFFFF);
		tv.setEnabled(enabled);
	}

	@Override
	public void run() {
		VS.sv_focused = false;
		es.searchView.clearFocus();
		if (Controller.isActive(this) && VS.mAdapter instanceof AbstractContainerAdapter
				&& VS.contentAdapter.selected > -1) {
			VS.contentAdapter.selected = -1;
			setSelectOpts(false);
		} else if (Controller.isActive(this) && !backLog.path.isEmpty()) {
			if (VS.pasteMode && backLog.path.size() == 1) {
				pasteOpts.setVisibility(View.GONE);
				VS.pasteMode = false;
				return;
			}
			if (VS.mAdapter instanceof SearchAdapter) {
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
		} else {
			if (!Controller.isActive(this)) {
				Controller.defaultBack.run();
				backTime = 0;
			} else if (System.currentTimeMillis() - backTime > 3000) {
				backTime = System.currentTimeMillis();
				Toast.makeText(getContext(), R.string.press_exit, Toast.LENGTH_SHORT).show();
			} else Controller.defaultBack.run();
			if (Controller.isActive(this)) {
				if (!backLog.path.isEmpty())
					Controller.activity.getSupportActionBar().setTitle(backLog.path.get(-1).toString());
				Controller.setMenuRes(VS.menuRes);
				if (!VS.sv_visible) es.searchView.setVisibility(View.GONE);
				else es.searchControl.update(false);
			}
		}
	}

	@Override
	public void onClick(View v) {
		if (VS.mAdapter instanceof AbstractContainerAdapter) {
			if (VS.contentAdapter.selected > -1) {
				if (VS.contentAdapter.selected < VS.contentAdapter.list.size()) {
					VS.contentAdapter.selected = VS.contentAdapter.list.size();
					for (HierarchyItemModel him : VS.contentAdapter.list)
						if (!him.isSelected()) {
							if (him.bd instanceof Reference) VS.contentAdapter.ref++;
							him.setSelected(true);
						}
					setSelectOpts(true);
				} else {
					VS.contentAdapter.selected = -1;
					setSelectOpts(false);
				}
			} else {
				VS.contentAdapter.selected = 0;
				setSelectOpts(false);
			}
			VS.contentAdapter.notifyDataSetChanged();
		}
	}

	@Override
	public void onItemClick(AdapterView<?> par, View v, int pos, long id) {
		if (VS.mAdapter instanceof ImageAdapter) return;
		HierarchyItemModel him = (HierarchyItemModel) par.getItemAtPosition(pos);
		BasicData bd = him.bd;
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
						backLog.add(true, null, EasyList.convert(((Reference) bd).getRefPath()));
						backLog.path.add(bd = bd.getThis());
					}
				} catch (Exception e) {
					return;
				}
			} else if (ref = VS.contentAdapter instanceof SearchAdapter) {
				backLog.add(true, null, (EasyList<BasicData>) ((SearchItemModel) him).path);
				backLog.path.add(him.bd);
			} else backLog.add(false, bd, null);
			VS.contentAdapter.selected = -1;
			setSelectOpts(true);
			setContent(bd, him.parent, backLog.path.size());
			es.onChange(ref);
		}
	}

	@Override
	public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
		if (VS.mAdapter instanceof AbstractContainerAdapter && !VS.pasteMode) {
			boolean selected = !VS.contentAdapter.list.get(position).isSelected();
			if (VS.contentAdapter.selected == -1) VS.contentAdapter.selected = 0;
			if (VS.contentAdapter.list.get(position).bd instanceof Reference)
				VS.contentAdapter.ref += selected ? 1 : -1;
			VS.contentAdapter.selected += selected ? 1 : -1;
			setSelectOpts(false);
			VS.contentAdapter.list.get(position).setSelected(selected);
			VS.contentAdapter.notifyDataSetChanged();
		} else return false;
		return true;
	}

	public void setContent(BasicData bd, Container parent, int size) {
		if (size == 0) setObjectsContent();
		else if (size == 1) setContainerContent((Container) bd, null);
		else if (bd instanceof Container)
			if (bd instanceof TwoSided) {
				if (bd instanceof Picture && !VS.pasteMode) setPictureContent(bd, parent);
			} else setContainerContent((Container) bd, parent);
	}

	private void setObjectsContent() {
		new Thread(() -> {
			try {
				Thread.sleep(20);
			} catch (Exception e) {
			}
			finishLoad();
		}).start();
		Controller.setMenuRes(VS.menuRes = R.menu.more_main);
		es.searchControl.update(true);
		es.lv.setAdapter(VS.mAdapter = VS.contentAdapter = new HierarchyAdapter(getContext(),
				convert(new ArrayList<>(MainChapter.ELEMENTS), null), this::setVisibleOpts, false));
		Controller.activity.getSupportActionBar().setTitle(getString(R.string.menu_objects));
	}

	private void setContainerContent(Container bd, Container parent) {
		if (VS.pasteMode) test:{
			BasicData holder = original.get(original.size() - 1);
			int i = 0;
			for (BasicData c : backLog.path) {
				i++;
				if (c == holder) {
					for (; i < backLog.path.size(); i++) {
						holder = backLog.path.get(i);
						for (HierarchyItemModel him : toMove) {
							if (holder == him.bd) {
								tglEnabled(paste, false);
								break test;
							}
						}
					}
					tglEnabled(paste, c != holder);
					break test;
				}
			}
			tglEnabled(paste, true);
		}
		Controller.setMenuRes(VS.menuRes = R.menu.more_container);
		if (!VS.pasteMode) Controller.toggleSelectBtn(true);
		es.searchControl.update(false);
		es.lv.setAdapter(VS.mAdapter = VS.contentAdapter = new HierarchyAdapter(getContext(),
				convert(bd.getChildren(parent), bd), this::setVisibleOpts, false));
		Controller.activity.getSupportActionBar().setTitle(bd.toString());
	}

	private void setPictureContent(BasicData bd, Container parent) {
		Controller.setMenuRes(VS.menuRes = 0);
		Controller.toggleSelectBtn(false);
		es.searchControl.update(true);
		ArrayList<ImageItemModel> list = new ArrayList<>();
		BasicData[] pics = ((Picture) bd).getChildren(parent);
		for (int i = 1; i < pics.length; i += 2)
			list.add(new ImageItemModel((Picture) pics[i - 1], (Picture) pics[i]));
		if (pics.length % 2 == 1) list.add(new ImageItemModel((Picture) pics[pics.length - 1], null));
		es.lv.setAdapter(VS.mAdapter = new ImageAdapter(getContext(), list, parent));
		Controller.activity.getSupportActionBar().setTitle(bd.toString());
	}

	@Override
	public boolean onMenuItemClick(MenuItem item) {
		new Thread(() -> {
			switch (item.getItemId()) {
				case R.id.more_new_mch:
					activity.runOnUiThread(() -> new CreatorPopup(getString(R.string.new_mch), (x, cp) -> {
						cp.ok.setOnClickListener(v -> {
							String name = cp.et_name.getText().toString();
							try {
								if (name.isEmpty()) return;
								ContainerFile.isCorrect(name);
							} catch (IllegalArgumentException iae) {
								defaultReacts.get(ContainerFile.class + ":name")
										.react(iae.getMessage().contains("longer"));
								return;
							}
							try {
								VS.mAdapter.add(new HierarchyItemModel(new MainChapter(new Data(name, null)
										.addDesc(cp.et_desc.getText().toString())), null, es.lv.getCount() + 1));
								VS.mAdapter.notifyDataSetChanged();
								cp.dismiss();
							} catch (IllegalArgumentException iae) {
								defaultReacts.get(ContainerFile.class + ":save").react(iae, name, name);
							}
						});
						return null;
					}));
					break;
				case R.id.more_new_container:
					activity.runOnUiThread(() -> new CreatorPopup(getString(R.string.new_chapter), (li, cp) -> {
						cp.ok.setOnClickListener(v -> {
							String name = cp.et_name.getText().toString();
							if (name.isEmpty()) return;
							Container par = (Container) backLog.path.get(-1);
							try {
								Data d = new Data(name, (MainChapter) backLog.path.get(0))
										.addDesc(cp.et_desc.getText().toString()).addPar(par);
								Container ch = ((CheckBox) cp.view.findViewById(R.id.chapter_file))
										.isChecked() ? SaveChapter.mkElement(d) : new Chapter(d);
								VS.mAdapter.add(new HierarchyItemModel(ch, par, es.lv.getCount() + 1));
								par.putChild((Container) backLog.path.get(-2), ch);
								CurrentData.newChapters.add(ch);
								cp.dismiss();
							} catch (IllegalArgumentException iae) {
								if (iae.getMessage().contains("Name can't"))
									defaultReacts.get(ContainerFile.class + ":name")
											.react(iae.getMessage().contains("longer"));
							}
						});
						return li.inflate(R.layout.new_chapter, null);
					}));
					break;
				case R.id.more_new_word:
					activity.runOnUiThread(() -> new CreatorPopup(getString(R.string.new_word), new Includer() {

						TranslateRecyclerAdapter content;

						@Override
						public View onInclude(LayoutInflater li, CreatorPopup cp) {
							LinearLayout ll = (LinearLayout) li.inflate(R.layout.new_twosided, null);
							if (content == null) content = new TranslateRecyclerAdapter(null, cp);
							Runnable onClick = content.onClick(ll);
							cp.ok.setOnClickListener(v -> {
								try {
									onClick.run();
									if (content.toRemove != null) return;
									CurrentData.save();
									cp.dismiss();
								} catch (IllegalArgumentException iae) {
									System.out.println("An Exception occurred:\n" + iae.getMessage()
											+ "\n" + Formatter.getStackTrace(iae));
								}
							});
							return ll;
						}
					}));
					break;
				case R.id.more_new_picture:
					if (!AndroidIOSystem.requestWrite()) return;
					activity.runOnUiThread(() -> new CreatorPopup(getString(R.string.new_picture), new Includer() {
						ImageRecyclerAdapter content;

						@Override
						public View onInclude(LayoutInflater li, CreatorPopup cp) {
							LinearLayout ll = (LinearLayout) li.inflate(R.layout.new_twosided, null);
							if (content == null) content = new ImageRecyclerAdapter(null, cp);
							Runnable onClick = content.onClick(ll);
							cp.ok.setOnClickListener(v -> {
								try {
									onClick.run();
									if (content.toRemove != null) return;
									CurrentData.save();
									cp.dismiss();
								} catch (IllegalArgumentException iae) {
									System.out.println("An Exception occurred:\n" + iae.getMessage()
											+ "\n" + Formatter.getStackTrace(iae));
								}
							});
							return ll;
						}
					}));
					break;
				case R.id.sort_alpha_AZ:
					sort(1, true);
					break;
				case R.id.sort_alpha_ZA:
					sort(1, false);
					break;
				case R.id.sort_sf_01:
					sort(2, true);
					break;
				case R.id.sort_sf_10:
					sort(2, false);
					break;
				case R.id.sort_length_01:
					sort(3, true);
					break;
				case R.id.sort_length_10:
					sort(3, false);
					break;
				case R.id.sort_default:
					sort(4, true);
					break;
				case R.id.more_import_word:
					Intent i = new Intent(Intent.ACTION_GET_CONTENT);
					i.setType("*/*");
					i.addCategory(Intent.CATEGORY_OPENABLE);
					startActivityForResult(Intent.createChooser(i,
							activity.getString(R.string.action_chooser_file)), FILE_READ);
					break;
				case R.id.more_export_word:
					i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
					i.setType("*/*");
					i.addCategory(Intent.CATEGORY_OPENABLE);
					startActivityForResult(Intent.createChooser(i,
							activity.getString(R.string.action_chooser_file)), FILE_WRITE);
					break;
				case R.id.more_import_mch:
					SelectDirActivity.importing = true;
					startActivity(new Intent(getContext(), SelectDirActivity.class));
			}
		}).start();
		return true;
	}

	private void sort(int type, boolean rising) {
		List<HierarchyItemModel> list = (List<HierarchyItemModel>) VS.contentAdapter.list;
		if (type != 4) Collections.sort(list, type == 1 ?
				(a, b) -> (rising ? 1 : -1) * (a.bd.getName().compareToIgnoreCase(b.bd.getName()))
				: type == 2 ? (a, b) -> a.bd.getRatio() == b.bd.getRatio() ? 0 :
				(a.bd.getRatio() > b.bd.getRatio() == rising) ? 1 : -1
				: (a, b) -> a.bd.getName().length() == b.bd.getName().length() ? 0 :
				(a.bd.getName().length() > b.bd.getName().length() == rising) ? 1 : -1);
		else {
			HierarchyItemModel[] hims = new HierarchyItemModel[list.size()];
			for (HierarchyItemModel him : list) hims[him.position - 1] = him;
			list.clear();
			Collections.addAll(list, hims);
		}
		es.lv.post(() -> VS.mAdapter.notifyDataSetChanged());
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (resultCode == Activity.RESULT_OK) {
			new Thread(() -> {
				File f;
				switch (requestCode) {
					case FILE_READ:
						f = Controller.getFileFromUri(data.getData());
						try {
							defaultReacts.get(SimpleReader.class + ":success").react(
									SimpleReader.simpleLoad(Formatter.loadFile(f),
											(Container) backLog.path.get(-1),
											(Container) backLog.path.get(-2), 0, -1, -1));
							CurrentData.save();
						} catch (Exception e) {
							if (e instanceof IllegalArgumentException) return;
							defaultReacts.get(ContainerFile.class + ":load").react(e,
									f.getPath(), backLog.path.get(-1));
						}
						break;
					case FILE_WRITE:
						f = Controller.getFileFromUri(data.getData());
						try {
							SimpleWriter.saveWords(f, (Container) backLog.path.get(-2),
									(Container) backLog.path.get(-1));
						} catch (Exception e) {
							defaultReacts.get(ContainerFile.class + ":save").react(e,
									f.getPath(), backLog.path.get(-1));
						}
						break;
					case IMAGE_PICK:
						defaultReacts.get("NotifyNewImage")
								.react(Controller.getFileFromUri(data.getData()));
				}
				super.onActivityResult(requestCode, resultCode, data);
			}).start();
		}
	}

	@Override
	public void onResume() {
		Controller.setCurrentControl(this, VS.menuRes, !VS.pasteMode, true);
		super.onResume();
	}

	public static final int FILE_WRITE = 1;
	public static final int FILE_READ = 2;
	public static final int STORAGE_PERMISSION = 3;
	public static final int IMAGE_PICK = 4;

	public static class ViewState extends ExplorerStuff.ViewState {
		private int menuRes;
		private boolean pasteMode;
		public MainFragment mfInstance;
	}
}
