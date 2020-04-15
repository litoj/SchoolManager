package com.schlmgr.gui.fragments;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.provider.MediaStore.Images.Media;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
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
import com.schlmgr.gui.list.HierarchyAdapter;
import com.schlmgr.gui.list.HierarchyItemModel;
import com.schlmgr.gui.list.ImageItemModel;
import com.schlmgr.gui.list.OpenListAdapter;
import com.schlmgr.gui.list.SearchAdapter;
import com.schlmgr.gui.list.SearchItemModel;
import com.schlmgr.gui.popup.ContinuePopup;
import com.schlmgr.gui.popup.CreatorPopup;
import com.schlmgr.gui.popup.CreatorPopup.Includer;
import com.schlmgr.gui.popup.FullPicture;

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

public class MainFragment extends Fragment implements Controller.ControlListener, OnItemClickListener, OnItemLongClickListener {

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
	private static ExplorerStuff es;
	public static ViewState VS = new ViewState();

	public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		VS.mfInstance = this;
		View root = inflater.inflate(R.layout.fragment_main, container, false);
		selectOpts = root.findViewById(R.id.objects_select);
		delete = root.findViewById(R.id.select_delete);
		reference = root.findViewById(R.id.select_reference);
		cut = root.findViewById(R.id.select_cut);
		edit = root.findViewById(R.id.select_rename);
		pasteOpts = root.findViewById(R.id.objects_paster);
		root.findViewById(R.id.objects_cancel).setOnClickListener(v -> {
			VS.paster = false;
			pasteOpts.setVisibility(View.GONE);
		});
		paste = root.findViewById(R.id.objects_paste);

		if (icCut == null) {
			Resources res = activity.getResources();
			(icDelete = res.getDrawable(R.drawable.ic_delete))
					.setBounds((int) dp, 0, (int) (dp * 40), (int) (dp * 40));
			(icDelete_disabled = res.getDrawable(R.drawable.ic_delete_disabled))
					.setBounds((int) dp, 0, (int) (dp * 40), (int) (dp * 40));
			(icReference = res.getDrawable(R.drawable.ic_reference))
					.setBounds((int) dp, 0, (int) (dp * 40), (int) (dp * 40));
			(icReference_disabled = res.getDrawable(R.drawable.ic_reference_disabled))
					.setBounds((int) dp, 0, (int) (dp * 40), (int) (dp * 40));
			(icCut = res.getDrawable(R.drawable.ic_cut))
					.setBounds((int) dp, 0, (int) (dp * 40), (int) (dp * 40));
			(icCut_disabled = res.getDrawable(R.drawable.ic_cut_disabled))
					.setBounds((int) dp, 0, (int) (dp * 40), (int) (dp * 40));
			(icEdit = res.getDrawable(R.drawable.ic_edit))
					.setBounds((int) dp, 0, (int) (dp * 35), (int) (dp * 35));
			(icEdit_disabled = res.getDrawable(R.drawable.ic_edit_disabled))
					.setBounds((int) dp, 0, (int) (dp * 35), (int) (dp * 35));
			(icPaste = res.getDrawable(R.drawable.ic_paste))
					.setBounds((int) dp, 0, (int) (dp * 33), (int) (dp * 33));
			(icPaste_disabled = res.getDrawable(R.drawable.ic_paste_disabled))
					.setBounds((int) dp, 0, (int) (dp * 33), (int) (dp * 33));
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
				if (((OpenListAdapter) VS.aa).selected > 0)
					new ContinuePopup(getString(R.string.continue_delete), () -> {
						for (int i = VS.ola.list.size() - 1; i >= 0; i--) {
							HierarchyItemModel him = VS.ola.list.get(i);
							if (him.isSelected()) {
								if (backLog.path.isEmpty()) him.bd.destroy(null);
								else {
									him.parent.removeChild((Container) (him instanceof SearchItemModel ?
											((SearchItemModel) him).path.get(-2)
											: backLog.path.get(-2)), him.bd);
								}
								VS.ola.list.remove(i);
							}
						}
						if (!backLog.path.isEmpty()) CurrentData.save();
						root.post(() -> {
							VS.ola.notifyDataSetChanged();
							VS.ola.selected = -1;
							selectOpts.setVisibility(View.GONE);
						});
					});
			});
			reference.setOnClickListener(v -> {
				for (HierarchyItemModel him : VS.ola.list)
					if (him.isSelected()) {
						EasyList<? extends BasicData> list;
						if (VS.ola instanceof SearchAdapter) list = ((SearchItemModel) him).path;
						else if (him.bd instanceof Reference)
							list = EasyList.convert(((Reference) him.bd).getRefPath());
						else break;
						backLog.add(true, null, (EasyList<BasicData>) list);
						setContainerContent(((EasyList<Container>) list).get(-1), ((EasyList<Container>) list).get(-2));
						es.onChange(true);
						VS.ola.selected = -1;
						setSelectOpts(false);
						return;
					}
				move(true);
			});
			cut.setOnClickListener(v -> move(false));
			edit.setOnClickListener(none -> {
				for (HierarchyItemModel him : VS.ola.list) {
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
										VS.ola.selected = -1;
										setSelectOpts(false);
										him.toShow = him.bd.toString();
										VS.aa.notifyDataSetChanged();
										cp.dismiss();
									} catch (IllegalArgumentException iae) {
										defaultReacts.get(ContainerFile.class + ":name").react(iae.getMessage().contains("longer"));
									}
								});
								return null;
							}));
						} else if (him.bd instanceof Word) {
							activity.runOnUiThread(() -> new CreatorPopup(getString(R.string.edit), new Includer() {
								class Translate {
									final Word trl;
									View v;

									Translate(Word trl) {
										this.trl = trl;
									}
								}

								List<Translate> list;
								List<Word> toRemove = new ArrayList<>();

								void addView(int index, LayoutInflater li, LinearLayout ll) {
									View view = li.inflate(R.layout.item_add_translate, ll, false);
									Translate item = list.get(index);
									((TextView) view.findViewById(R.id.item_trl_header)).setText("" + (list.size() - index));
									if (item.v != null || item.trl != null) {
										((TextView) view.findViewById(R.id.item_adder_name)).setText(
												item.v != null ? ((TextView) item.v.findViewById(R.id.item_adder_name))
														.getText().toString() : item.trl.getName());
										((TextView) view.findViewById(R.id.item_adder_desc)).setText(
												item.v != null ? ((TextView) item.v.findViewById(R.id.item_adder_desc))
														.getText().toString() : item.trl.getDesc(him.parent));
									}
									item.v = view;
									view.findViewById(R.id.item_adder_remove).setOnClickListener(v -> {
										Translate t = list.remove(index);
										if (t.trl != null) toRemove.add(t.trl);
										ll.removeView(view);
									});
									ll.addView(view, 2);
								}

								@Override
								public View onInclude(LayoutInflater li, CreatorPopup cp) {
									LinearLayout ll = (LinearLayout) li.inflate(R.layout.new_twosided, null);
									if (list == null) {
										list = new ArrayList<>();
										for (TwoSided trl : ((Word) him.bd).getChildren(him.parent)) {
											list.add(0, new Translate((Word) trl));
											addView(0, li, ll);
										}
										cp.et_name.setText(him.bd.getName());
										cp.et_desc.setText(him.bd.getDesc(him.parent));
									} else for (int i = list.size() - 1; i >= 0; i--) addView(i, li, ll);
									TextView tv = ll.findViewById(R.id.new_add);
									tv.setText(R.string.add_word);
									tv.setOnClickListener(v -> {
										list.add(0, new Translate(null));
										addView(0, li, ll);
									});
									cp.ok.setOnClickListener(v -> {
										String name = cp.et_name.getText().toString();
										if (name.isEmpty() || list.isEmpty()) return;
										MainChapter mch = (MainChapter) backLog.path.get(0);
										try {
											for (Translate item : list) {
												String[] trls = SimpleReader.nameResolver(((TextView)
														item.v.findViewById(R.id.item_adder_name)).getText().toString());
												String[] trlDescs = SimpleReader.nameResolver(((TextView)
														item.v.findViewById(R.id.item_adder_desc)).getText().toString());
												if (item.trl != null && trls.length == 1) {
													item.trl.putDesc(him.parent, trlDescs[0]);
													item.trl.setName(him.parent, trls[0]);
												} else for (int i = 0; i < trls.length; i++)
													Word.mkTranslate(new Data(trls[i], mch, i < trlDescs.length
															? trlDescs[i] : null, him.parent), (Word) him.bd);
											}
											for (Word w : toRemove)
												((Word) him.bd).removeChild(him.parent, w);
											him.bd.putDesc(him.parent, cp.et_desc.getText().toString());
											if (!him.bd.getName().equals(name)) {
												him.bd.setName(him.parent, name);
												for (Word w : Word.ELEMENTS.get(mch))
													if (name.equals(w.getName())) {
														him.bd = w;
														break;
													}
											}
											CurrentData.save();
											VS.ola.selected = -1;
											setSelectOpts(false);
											him.flipped = !him.flipped;
											him.flip();
											VS.aa.notifyDataSetChanged();
											cp.dismiss();
										} catch (IllegalArgumentException iae) {
											System.out.println("An Exception occurred:\n" + iae.getMessage() + "\n" + Formatter.getStackTrace(iae));
										}
									});
									return ll;
								}
							}));
						} else if (him.bd instanceof Picture) {
							if (!AndroidIOSystem.requestWrite()) return;
							activity.runOnUiThread(() -> new CreatorPopup(getString(R.string.edit), new Includer() {
								class Image {

									final Picture img;
									final File f;
									final Bitmap bm;

									Image(File file) {
										f = file;
										bm = BitmapFactory.decodeFile(f.toString());
										img = null;
									}

									Image(TwoSided pic) {
										img = (Picture) pic;
										f = img.getFile();
										bm = BitmapFactory.decodeFile(f.toString());
									}
								}

								List<Image> list;
								List<Picture> toRemove = new ArrayList<>();

								void addView(int index, LayoutInflater li, LinearLayout ll) {
									View view = li.inflate(R.layout.item_add_image, ll, false);
									Image item = list.get(index);
									((TextView) view.findViewById(R.id.item_adder_name)).setText(item.f.getName());
									ImageView iv = view.findViewById(R.id.item_img);
									iv.setImageBitmap(item.bm);
									iv.setOnClickListener(v -> new FullPicture(item.bm));
									view.findViewById(R.id.item_adder_remove).setOnClickListener(v -> {
										Image i = list.remove(index);
										if (i.img != null) toRemove.add(i.img);
										ll.removeView(view);
									});
									ll.addView(view, 2);
								}

								@Override
								public View onInclude(LayoutInflater li, CreatorPopup cp) {
									LinearLayout ll = (LinearLayout) li.inflate(R.layout.new_twosided, null);
									TextView tv = ll.findViewById(R.id.new_add);
									tv.setText(R.string.add_picture);
									tv.setOnClickListener(v -> VS.mfInstance.startActivityForResult(
											new Intent(Intent.ACTION_PICK, Media.EXTERNAL_CONTENT_URI), IMAGE_PICK));
									if (list == null) {
										list = new ArrayList<>();
										for (TwoSided img : ((Picture) him.bd).getChildren(him.parent)) {
											list.add(0, new Image(img));
											addView(0, li, ll);
										}
										cp.et_name.setText(him.bd.getName());
										cp.et_desc.setText(him.bd.getDesc(him.parent));
									} else for (int i = list.size() - 1; i >= 0; i--) addView(i, li, ll);
									defaultReacts.put("NotifyNewImage", (o) -> {
										File file = ((File) o[0]);
										if (!file.exists()) return;
										for (Image img : list) if (img.f.equals(file)) return;
										Image img = new Image(file);
										list.add(0, img);
										ll.post(() -> addView(0, li, ll));
									});
									cp.ok.setOnClickListener(v -> {
										String name = cp.et_name.getText().toString();
										if (name.isEmpty() || list.isEmpty()) return;
										MainChapter mch = (MainChapter) backLog.path.get(0);
										for (Image i : list)
											if (i.img == null)
												Picture.mkImage(new Data(i.f.getAbsolutePath(), mch, him.parent), (Picture) him.bd);
										try {
											for (Picture p : toRemove)
												((Picture) him.bd).removeChild(him.parent, p);
											him.bd.putDesc(him.parent, cp.et_desc.getText().toString());
											if (!him.bd.getName().equals(name)) {
												him.bd.setName(him.parent, name);
												for (Picture p : Picture.ELEMENTS.get(mch))
													if (name.equals(p.getName())) {
														him.bd = p;
														break;
													}
											}
											CurrentData.save();
											VS.ola.selected = -1;
											setSelectOpts(false);
											;
											him.toShow = him.bd.toString();
											VS.aa.notifyDataSetChanged();
											cp.dismiss();
										} catch (IllegalArgumentException iae) {
											System.out.println("An Exception occurred:\n" + iae.getMessage() + "\n" + Formatter.getStackTrace(iae));
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
			if (VS.aa instanceof OpenListAdapter && VS.ola.selected > -1) {
				selectOpts.setVisibility(View.VISIBLE);
				for (HierarchyItemModel him : ((OpenListAdapter<? extends HierarchyItemModel>) VS.aa).list)
					if (him.isSelected() && him.bd instanceof Reference) {
						tglEnabled(edit, false);
						return;
					}
				if (VS.ola.selected > 1) tglEnabled(edit, false);
			}
		}).start();

		es = new ExplorerStuff(false, this::setContent, () -> {
			VS.ola.selected = -1;
			setSelectOpts(false);
		}, this::setVisibleOpts, VS, backLog, getContext(),
				root.findViewById(R.id.objects_path_handler),
				root.findViewById(R.id.objects_list), root.findViewById(R.id.objects_path),
				root.findViewById(R.id.objects_info_handler), root.findViewById(R.id.objects_info),
				root.findViewById(R.id.objects_search), root.findViewById(R.id.touch_outside));
		es.lv.setOnItemClickListener(this);
		es.lv.setOnItemLongClickListener(this);
		Formatter.defaultReacts.put("MChLoaded", (o) -> {
			if (backLog.path.isEmpty()) root.post(() -> VS.aa.notifyDataSetChanged());
		});
		if (VS.aa == null) {
			setContent(backLog.path.get(-1), (Container) backLog.path.get(-2), backLog.path.size());
			es.setInfo(backLog.path.get(-1), (Container) backLog.path.get(-2));
		} else {
			es.lv.setAdapter(VS.aa);
			if (!VS.sv_visible) es.sv.setVisibility(View.GONE);
			es.onChange(true);
		}
		return root;
	}

	private void move(boolean ref) {
		VS.paster = true;
		Controller.toggleSelectBtn(false);
		pasteOpts.setVisibility(View.VISIBLE);
		tglEnabled(paste, true);
		boolean search = VS.aa instanceof SearchAdapter;
		List<HierarchyItemModel> list = new ArrayList<>();
		for (HierarchyItemModel him : VS.ola.list)
			if (him.isSelected()) list.add(him);
		List<Container> original = new ArrayList<>();
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
			boolean searchNow = VS.aa instanceof SearchAdapter;
			for (BasicData bd : np.getChildren(npp)) list.remove(bd);
			if (ref) {
				List<Container> cp = new ArrayList<>(backLog.path.size());
				for (BasicData bd : backLog.path) cp.add((Container) bd);
				for (HierarchyItemModel him : list) {
					Reference r;
					try {
						r = Reference.mkElement(him.bd, cp,
								(search ? (List<Container>) ((SearchItemModel) him).path : original).toArray(new Container[0]));
					} catch (IllegalArgumentException iae) {
						continue;
					}
					np.putChild(npp, r);
					if (!searchNow) VS.aa.add(new HierarchyItemModel(r, np, VS.aa.getCount()));
				}
			} else {
				List<ContainerFile> toSave = new LinkedList<>();
				for (HierarchyItemModel him : list) {
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
						if (search) VS.aa.add(new HierarchyItemModel(him.bd, np, VS.aa.getCount()));
						else {
							VS.aa.add(him);
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
			VS.paster = false;
			pasteOpts.setVisibility(View.GONE);
		});
		VS.ola.selected = -1;
		setSelectOpts(false);
		;
	}

	/**
	 * Controls the visibility of selecting options.
	 *
	 * @param change if an item has been clicked
	 */
	private void setSelectOpts(boolean change) {
		selectOpts.setVisibility(VS.ola.selected > -1 ? View.VISIBLE : View.GONE);
		setVisibleOpts();
		if (VS.ola.selected == -1 && !change) {
			VS.ola.ref = 0;
			for (HierarchyItemModel him : VS.ola.list) him.setSelected(false);
		}
	}

	private void setVisibleOpts() {
		boolean notObj = backLog.path.size() > 0;
		int selected = VS.ola.selected;
		tglEnabled(delete, selected > 0);
		tglEnabled(reference, selected > 0 && notObj && VS.ola.ref < 2 &&
				(!(VS.ola instanceof SearchAdapter) || selected < 2));
		tglEnabled(cut, selected > 0 && notObj && VS.ola.ref < 1);
		tglEnabled(edit, selected == 1 && VS.ola.ref < 1);
	}

	private void tglEnabled(TextView tv, boolean enabled) {
		if (tv.isClickable() == enabled) return;
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
		tv.setClickable(enabled);
	}

	@Override
	public void run() {
		VS.sv_focused = false;
		es.sv.clearFocus();
		if (Controller.isActive(this) && VS.aa instanceof OpenListAdapter && VS.ola.selected > -1) {
			VS.ola.selected = -1;
			setSelectOpts(false);
		} else if (Controller.isActive(this) && !backLog.path.isEmpty()) {
			if (VS.paster && backLog.path.size() == 1) {
				pasteOpts.setVisibility(View.GONE);
				VS.paster = false;
				return;
			}
			if (VS.aa instanceof SearchAdapter) {
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
				if (!VS.sv_visible) es.sv.setVisibility(View.GONE);
				else es.svc.update(false);
			}
		}
	}

	@Override
	public void onClick(View v) {
		if (VS.aa instanceof OpenListAdapter) {
			if (VS.ola.selected > -1) {
				if (VS.ola.selected < VS.ola.list.size()) {
					VS.ola.selected = VS.ola.list.size();
					for (HierarchyItemModel him : VS.ola.list)
						if (!him.isSelected()) {
							if (him.bd instanceof Reference) VS.ola.ref++;
							him.setSelected(true);
						}
					setSelectOpts(true);
				} else {
					VS.ola.selected = -1;
					setSelectOpts(false);
				}
			} else {
				VS.ola.selected = 0;
				setSelectOpts(false);
			}
			VS.ola.notifyDataSetChanged();
		}
	}

	@Override
	public void onItemClick(AdapterView<?> par, View v, int pos, long id) {
		HierarchyItemModel him = (HierarchyItemModel) par.getItemAtPosition(pos);
		BasicData bd = him.bd;
		if (bd instanceof Word) {
			if (HierarchyItemModel.flipAllOnClick) {
				boolean flip = !him.flipped;
				for (HierarchyItemModel item : VS.ola.list)
					if (item.flipped != flip) item.flip();
			} else him.flip();
			VS.ola.notifyDataSetChanged();
			return;
		} else {
			boolean ref;
			if (ref = bd instanceof Reference) {
				try {
					him.setNew(bd.getThis(), ((Reference) bd).getRefPathAt(-1));
					if (bd.getThis() instanceof Word) {
						VS.ola.notifyDataSetChanged();
						return;
					} else {
						backLog.add(true, null, EasyList.convert(((Reference) bd).getRefPath()));
						backLog.path.add(bd = bd.getThis());
					}
				} catch (Exception e) {
					return;
				}
			} else if (ref = VS.ola instanceof SearchAdapter) {
				backLog.add(true, null, (EasyList<BasicData>) ((SearchItemModel) him).path);
				backLog.path.add(him.bd);
			} else backLog.add(false, bd, null);
			VS.ola.selected = -1;
			setSelectOpts(true);
			setContent(bd, him.parent, backLog.path.size());
			es.onChange(ref);
		}
	}

	@Override
	public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
		if (VS.aa instanceof OpenListAdapter && !VS.paster) {
			boolean selected = !VS.ola.list.get(position).isSelected();
			if (VS.ola.selected == -1) VS.ola.selected = 0;
			if (VS.ola.list.get(position).bd instanceof Reference) VS.ola.ref += selected ? 1 : -1;
			VS.ola.selected += selected ? 1 : -1;
			setSelectOpts(false);
			VS.ola.list.get(position).setSelected(selected);
			VS.ola.notifyDataSetChanged();
		} else return false;
		return true;
	}

	public void setContent(BasicData bd, Container parent, int size) {
		if (size == 0) setObjectsContent();
		else if (size == 1) setContainerContent((Container) bd, null);
		else if (bd instanceof Container)
			if (bd instanceof TwoSided) {
				if (bd instanceof Picture && !VS.paster) setPictureContent(bd, parent);
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
		es.svc.update(true);
		es.lv.setAdapter(VS.aa = VS.ola = new HierarchyAdapter(getContext(),
				convert(new ArrayList<>(MainChapter.ELEMENTS), null), this::setVisibleOpts, false));
		Controller.activity.getSupportActionBar().setTitle(getString(R.string.menu_objects));
	}

	private void setContainerContent(Container bd, Container parent) {
		Controller.setMenuRes(VS.menuRes = R.menu.more_container);
		if (!VS.paster) Controller.toggleSelectBtn(true);
		es.svc.update(false);
		es.lv.setAdapter(VS.aa = VS.ola = new HierarchyAdapter(getContext(),
				convert(bd.getChildren(parent), bd), this::setVisibleOpts, false));
		Controller.activity.getSupportActionBar().setTitle(bd.toString());
	}

	private void setPictureContent(BasicData bd, Container parent) {
		Controller.setMenuRes(VS.menuRes = 0);
		Controller.toggleSelectBtn(false);
		es.svc.update(true);
		class ImageAdapter extends ArrayAdapter<ImageItemModel> {
			private final LayoutInflater li;
			public final List<ImageItemModel> list;
			private final Container parent;

			public ImageAdapter(@NonNull Context context, @NonNull List<ImageItemModel> objects, Container parent) {
				super(context, R.layout.item_image, R.id.item_img_ics, objects);
				li = LayoutInflater.from(context);
				list = objects;
				this.parent = parent;
			}

			public View getView(int pos, View view, ViewGroup parent) {
				if (view == null) view = li.inflate(R.layout.item_image, parent, false);
				ImageItemModel iim = list.get(pos);
				ImageView iv = view.findViewById(R.id.item_img_1);
				iv.setOnClickListener(v -> new FullPicture(iim.bm1));
				iv.setImageBitmap(iim.bm1);
				iv.setContentDescription(iim.pic1.toString());
				TextView tv = view.findViewById(R.id.item_img_d1);
				String desc = iim.pic1.getDesc(this.parent);
				if (desc.isEmpty()) tv.setVisibility(View.GONE);
				else tv.setText(desc);
				if (iim.bm2 != null) {
					(iv = view.findViewById(R.id.item_img_2)).setImageBitmap(iim.bm2);
					iv.setContentDescription(iim.pic2.toString());
					iv.setOnClickListener(v -> new FullPicture(iim.bm2));
					tv = view.findViewById(R.id.item_img_d2);
					desc = iim.pic2.getDesc(this.parent);
					if (desc.isEmpty()) tv.setVisibility(View.GONE);
					else tv.setText(desc);
				} else view.findViewById(R.id.item_img_l2).setVisibility(View.GONE);
				return view;
			}
		}
		ArrayList<ImageItemModel> list = new ArrayList<>();
		BasicData[] pics = ((Picture) bd).getChildren(parent);
		for (int i = 1; i < pics.length; i += 2)
			list.add(new ImageItemModel((Picture) pics[i - 1], (Picture) pics[i]));
		if (pics.length % 2 == 1) list.add(new ImageItemModel((Picture) pics[pics.length - 1], null));
		es.lv.setAdapter(VS.aa = new ImageAdapter(getContext(), list, parent));
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
								defaultReacts.get(ContainerFile.class + ":name").react(iae.getMessage().contains("longer"));
								return;
							}
							try {
								VS.aa.add(new HierarchyItemModel(new MainChapter(new Data(
										name, null, cp.et_desc.getText().toString())), null, es.lv.getCount() + 1));
								VS.aa.notifyDataSetChanged();
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
								Data d = new Data(name, (MainChapter) backLog.path.get(0), cp.et_desc.getText().toString(), par);
								Container ch = ((CheckBox) cp.view.findViewById(R.id.chapter_file))
										.isChecked() ? SaveChapter.mkElement(d) : new Chapter(d);
								VS.aa.add(new HierarchyItemModel(ch, par, es.lv.getCount() + 1));
								par.putChild((Container) backLog.path.get(-2), ch);
								cp.dismiss();
							} catch (IllegalArgumentException iae) {
								if (iae.getMessage().contains("Name can't"))
									defaultReacts.get(ContainerFile.class + ":name").react(iae.getMessage().contains("longer"));
							}
						});
						return li.inflate(R.layout.new_chapter, null);
					}));
					break;
				case R.id.more_new_word:
					activity.runOnUiThread(() -> new CreatorPopup(getString(R.string.new_word), new Includer() {

						List<View> list;

						void addView(int index, LayoutInflater li, LinearLayout ll) {
							View view = li.inflate(R.layout.item_add_translate, ll, false);
							View item = list.get(index);
							((TextView) view.findViewById(R.id.item_trl_header)).setText("" + (list.size() - index));
							if (item != null) {
								((TextView) view.findViewById(R.id.item_adder_name)).setText(((TextView)
										item.findViewById(R.id.item_adder_name)).getText().toString());
								((TextView) view.findViewById(R.id.item_adder_desc)).setText(((TextView)
										item.findViewById(R.id.item_adder_desc)).getText().toString());
							}
							list.set(index, view);
							view.findViewById(R.id.item_adder_remove).setOnClickListener(v -> {
								list.remove(index);
								ll.removeView(view);
							});
							ll.addView(view, 2);
						}

						@Override
						public View onInclude(LayoutInflater li, CreatorPopup cp) {
							LinearLayout ll = (LinearLayout) li.inflate(R.layout.new_twosided, null);
							if (list == null) list = new ArrayList<>();
							else for (int i = list.size() - 1; i >= 0; i--) addView(i, li, ll);
							TextView tv = ll.findViewById(R.id.new_add);
							tv.setText(R.string.add_word);
							tv.setOnClickListener(v -> {
								list.add(0, null);
								addView(0, li, ll);
							});
							cp.ok.setOnClickListener(v -> {
								String name = cp.et_name.getText().toString();
								if (name.isEmpty() || list.isEmpty()) return;
								Container par = (Container) backLog.path.get(-1);
								MainChapter mch = (MainChapter) backLog.path.get(0);
								LinkedList<Data> translates = new LinkedList<>();
								try {
									for (View view : list) {
										String[] trls = SimpleReader.nameResolver(((TextView)
												view.findViewById(R.id.item_adder_name)).getText().toString());
										String[] trlDescs = SimpleReader.nameResolver(((TextView)
												view.findViewById(R.id.item_adder_desc)).getText().toString());
										for (int i = 0; i < trls.length; i++)
											translates.add(new Data(trls[i], mch, i < trlDescs.length ? trlDescs[i] : null, par));
									}
									String[] names = SimpleReader.nameResolver(name);
									String[] descs = SimpleReader.nameResolver(cp.et_desc.getText().toString());
									Data d = new Data(null, mch, par);
									for (int i = 0; i < names.length; i++) {
										d.name = names[i];
										d.description = i < descs.length ? descs[i] : null;
										Word w = Word.mkElement(d, translates);
										VS.aa.add(new HierarchyItemModel(w, par, es.lv.getCount() + 1));
										par.putChild((Container) backLog.path.get(-2), w);
									}
									CurrentData.save();
									cp.dismiss();
								} catch (IllegalArgumentException iae) {
									System.out.println("An Exception occurred:\n" + iae.getMessage() + "\n" + Formatter.getStackTrace(iae));
								}
							});
							return ll;
						}
					}));
					break;
				case R.id.more_new_picture:
					if (!AndroidIOSystem.requestWrite()) return;
					activity.runOnUiThread(() -> new CreatorPopup(getString(R.string.new_picture), new Includer() {
						class Image {
							final File f;
							final Bitmap bm;

							Image(File file) {
								f = file;
								bm = BitmapFactory.decodeFile(f.toString());
							}
						}

						List<Image> list;

						void addView(int index, LayoutInflater li, LinearLayout ll) {
							View view = li.inflate(R.layout.item_add_image, ll, false);
							Image item = list.get(index);
							((TextView) view.findViewById(R.id.item_adder_name)).setText(item.f.getName());
							ImageView iv = view.findViewById(R.id.item_img);
							iv.setImageBitmap(item.bm);
							iv.setOnClickListener(v -> new FullPicture(item.bm));
							view.findViewById(R.id.item_adder_remove).setOnClickListener(v -> {
								list.remove(index);
								ll.removeView(view);
							});
							ll.addView(view, 2);
						}

						@Override
						public View onInclude(LayoutInflater li, CreatorPopup cp) {
							LinearLayout ll = (LinearLayout) li.inflate(R.layout.new_twosided, null);
							TextView tv = ll.findViewById(R.id.new_add);
							tv.setText(R.string.add_picture);
							tv.setOnClickListener(v -> VS.mfInstance.startActivityForResult(
									new Intent(Intent.ACTION_PICK, Media.EXTERNAL_CONTENT_URI), IMAGE_PICK));
							if (list == null) list = new ArrayList<>();
							else for (int i = list.size() - 1; i >= 0; i--) addView(i, li, ll);
							defaultReacts.put("NotifyNewImage", (o) -> {
								File file = ((File) o[0]);
								if (!file.exists()) return;
								for (Image img : list) if (img.f.equals(file)) return;
								Image img = new Image(file);
								list.add(0, img);
								ll.post(() -> addView(0, li, ll));
							});
							cp.ok.setOnClickListener(v -> {
								String name = cp.et_name.getText().toString();
								if (name.isEmpty() || list.isEmpty()) return;
								String desc = cp.et_desc.getText().toString();
								Container par = (Container) backLog.path.get(-1);
								MainChapter mch = (MainChapter) backLog.path.get(0);
								LinkedList<Data> images = new LinkedList<>();
								for (Image i : list)
									images.add(new Data(i.f.getAbsolutePath(), mch, par));
								try {
									Picture p = Picture.mkElement(new Data(name, mch, desc, par), images);
									VS.aa.add(new HierarchyItemModel(p, par, es.lv.getCount() + 1));
									par.putChild((Container) backLog.path.get(-2), p);
									CurrentData.save();
									cp.dismiss();
								} catch (IllegalArgumentException iae) {
									System.out.println("An Exception occurred:\n" + iae.getMessage() + "\n" + Formatter.getStackTrace(iae));
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
					SelectDirActivity.titleID = R.string.select_dir_import;
					startActivity(new Intent(getContext(), SelectDirActivity.class));
			}
		}).start();
		return true;
	}

	private void sort(int type, boolean rising) {
		List<HierarchyItemModel> list = (List<HierarchyItemModel>) VS.ola.list;
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
		es.lv.post(() -> VS.aa.notifyDataSetChanged());
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (resultCode == Activity.RESULT_OK) {
			new Thread(() -> {
				switch (requestCode) {
					case FILE_READ:
						try {
							defaultReacts.get(SimpleReader.class + ":success").react(
									SimpleReader.simpleLoad(Formatter.loadFile(activity.getContentResolver()
													.openInputStream(data.getData())), (Container) backLog.path.get(-1),
											(Container) backLog.path.get(-2), 0, -1, -1));
							CurrentData.save();
						} catch (Exception e) {
							if (e instanceof IllegalArgumentException) return;
							defaultReacts.get(ContainerFile.class + ":load").react(e,
									data.getData().getPath(), backLog.path.get(-1));
						}
						break;
					case FILE_WRITE:
						try {
							SimpleWriter.saveWords(activity.getContentResolver()
											.openOutputStream(data.getData(), "wa"),
									(Container) backLog.path.get(-2), (Container) backLog.path.get(-1));
						} catch (Exception e) {
							defaultReacts.get(ContainerFile.class + ":save").react(e,
									data.getData().getPath(), backLog.path.get(-1));
						}
						break;
					case IMAGE_PICK:
						defaultReacts.get("NotifyNewImage").react(Controller.getFileFromUri(data.getData()));
				}
				super.onActivityResult(requestCode, resultCode, data);
			}).start();
		}
	}

	@Override
	public void onResume() {
		Controller.setCurrentControl(this, VS.menuRes, !VS.paster, true);
		super.onResume();
	}

	public static final int FILE_WRITE = 1;
	public static final int FILE_READ = 2;
	public static final int STORAGE_PERMISSION = 3;
	public static final int IMAGE_PICK = 4;

	public static class ViewState extends ExplorerStuff.ViewState {
		private int menuRes;
		private boolean paster;
		public MainFragment mfInstance;
	}
}
