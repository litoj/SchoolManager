package cz.litoj.schlmgr.gui.fragments;

import static android.widget.Toast.makeText;
import static cz.litoj.schlmgr.gui.Controller.CONTEXT;
import static cz.litoj.schlmgr.gui.Controller.activity;
import static cz.litoj.schlmgr.gui.CurrentData.backLog;
import static cz.litoj.schlmgr.gui.CurrentData.finishLoad;
import static cz.litoj.schlmgr.gui.list.HierarchyItemModel.convert;
import static cz.litoj.schlmgr.gui.engine.IOSystem.Formatter.defaultReacts;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.activity.result.ActivityResultLauncher;

import cz.litoj.schlmgr.R;
import cz.litoj.schlmgr.gui.Controller;
import cz.litoj.schlmgr.gui.CurrentData;
import cz.litoj.schlmgr.gui.CurrentData.EasyList;
import cz.litoj.schlmgr.gui.ExplorerStuff;
import cz.litoj.schlmgr.gui.UriPath;
import cz.litoj.schlmgr.gui.list.HierarchyAdapter;
import cz.litoj.schlmgr.gui.list.HierarchyItemModel;
import cz.litoj.schlmgr.gui.list.ImageAdapter;
import cz.litoj.schlmgr.gui.list.ImageItemModel;
import cz.litoj.schlmgr.gui.list.SearchAdapter;
import cz.litoj.schlmgr.gui.list.SearchAdapter.OnItemActionListener;
import cz.litoj.schlmgr.gui.list.SearchItemModel;
import cz.litoj.schlmgr.gui.popup.ContinuePopup;
import cz.litoj.schlmgr.gui.popup.CreatorContent;
import cz.litoj.schlmgr.gui.popup.CreatorPopup;
import cz.litoj.schlmgr.gui.popup.TranslationRow;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import cz.litoj.schlmgr.gui.engine.IOSystem.Formatter;
import cz.litoj.schlmgr.gui.engine.IOSystem.Formatter.Data;
import cz.litoj.schlmgr.gui.engine.IOSystem.ReadElement.ContentReader;
import cz.litoj.schlmgr.gui.engine.IOSystem.SimpleReader;
import cz.litoj.schlmgr.gui.engine.IOSystem.SimpleWriter;
import cz.litoj.schlmgr.gui.engine.objects.Chapter;
import cz.litoj.schlmgr.gui.engine.objects.MainChapter;
import cz.litoj.schlmgr.gui.engine.objects.Picture;
import cz.litoj.schlmgr.gui.engine.objects.Reference;
import cz.litoj.schlmgr.gui.engine.objects.SaveChapter;
import cz.litoj.schlmgr.gui.engine.objects.Word;
import cz.litoj.schlmgr.gui.engine.objects.templates.BasicData;
import cz.litoj.schlmgr.gui.engine.objects.templates.Container;
import cz.litoj.schlmgr.gui.engine.objects.templates.ContainerFile;
import cz.litoj.schlmgr.gui.engine.objects.templates.SemiElementContainer;
import cz.litoj.schlmgr.gui.engine.objects.templates.TwoSided;

public class MainFragment extends Fragment
    implements Controller.ControlListener, OnItemActionListener {

    private final ActivityResultLauncher<String> createWordFile =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("text/plain"),
                    uri -> {
                        if (uri != null) onWordTargetPicked(uri);
                    });

    private final ActivityResultLauncher<String[]> openWordFile =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(),
                    uri -> {
                        if (uri != null) onWordFilePicked(uri);
                    });

    private final ActivityResultLauncher<String[]> openWordMchFile =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(),
                    uri -> {
                        if (uri != null) onWordMchFilePicked(uri);
                    });

    private final ActivityResultLauncher<String[]> openSchFile =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(),
                    uri -> {
                        if (uri != null) onSchFilePicked(uri);
                    });

    private final ActivityResultLauncher<Uri> openImportDir =
            registerForActivityResult(new ActivityResultContracts.OpenDocumentTree(),
                    uri -> {
                        if (uri != null) onDirPicked(uri, true);
                    });

    private final ActivityResultLauncher<Uri> openChangeDir =
            registerForActivityResult(new ActivityResultContracts.OpenDocumentTree(),
                    uri -> {
                        if (uri != null) onDirPicked(uri, false);
                    });

    @SuppressLint("StaticFieldLeak")
    private static SelectionActions selActs;

    private LinearLayout pasteOpts;
    private TextView paste;

    private static long backTime;
    @SuppressLint("StaticFieldLeak")
    public static ExplorerStuff es;
    public static ViewState VS = new ViewState();

    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle oldState) {
        VS.mfInstance = this;
        View root = inflater.inflate(R.layout.fragment_main, container, false);
        selActs = new SelectionActions(root);
        pasteOpts = root.findViewById(R.id.objects_paster);
        root.findViewById(R.id.objects_cancel).setOnClickListener(v -> {
            VS.pasteData = null;
            Controller.toggleSelectBtn(true);
            pasteOpts.setVisibility(View.GONE);
        });
        paste = root.findViewById(R.id.objects_paste);

        new Thread(() -> {
            selActs.onClick(SelectionActions.Action.REFERENCE, v -> {
                int search = 0;
                EasyList<Container> list = null;
                for (HierarchyItemModel him : VS.contentAdapter.list)
                    if (him.isSelected()) {
                        if (search == 0 && VS.contentAdapter.search) {
                            list = ((SearchItemModel) him).path;
                            search++;
                        } else if (him.bd instanceof Reference ref) {
                            list = EasyList.convert(ref.getRefPath());
                            break;
                        } else {
                            search += 10;
                            break;
                        }
                    }
                if (search < 2) {
                    backLog.add(true, null, list);
                    setContent(list.get(-1), list.get(-2), 10);
                    es.onChange(true);
                    VS.contentAdapter.selected = -1;
                    setSelectOpts(false);
                } else move(true);
            });
            selActs.onClick(SelectionActions.Action.CUT, v -> move(false));
            selActs.onClick(SelectionActions.Action.EDIT, none -> {
                HierarchyItemModel him, item1 = null;
                for (HierarchyItemModel item : VS.contentAdapter.list)
                    if (item.isSelected()) {
                        item1 = item;
                        break;
                    }
                him = item1;
                if (him.bd instanceof Container && !(him.bd instanceof TwoSided)) {
                    boolean isMch = him.bd instanceof MainChapter;
                    boolean isSch = him.bd instanceof SaveChapter;
                    int maxPos = isMch ? him.position
                        : VS.contentAdapter.list.size();
                    CreatorContent content = isMch ? CreatorContent.SIMPLE
                        : new CreatorContent.Chapter(isSch);
                    activity.runOnUiThread(() -> {
                        CreatorPopup cp = new CreatorPopup(getString(R.string.edit), him,
                            him.position, maxPos, content);
                        cp.setOkListener(() -> {
                            int position = him.position;
                            String name = cp.getName();
                            if (name.isEmpty()) return;
                            cp.dismiss();
                            try {
                                him.bd.putDesc(him.parent,
                                    cp.getDesc().replace("\\t", "\t"));
                                boolean sch = isMch || cp.isChapterFile();
                                if (!Objects.equals(name, him.bd.getName())) {
                                    if (him.bd instanceof ContainerFile
                                        || sch) ContainerFile.isCorrect(name);
                                    BasicData newBD = him.bd.setName(him.parent, name);
                                    if (newBD != him.bd) {
                                        if (him.bd instanceof ContainerFile)
                                            CurrentData.changed.remove(him.bd);
                                        him.bd = newBD;
                                    }
                                }
                                if (!isMch) {
                                    if (sch != isSch)
                                        him.bd = ((SemiElementContainer) him.bd).convert();
                                    position = cp.getPosition();
                                    if (position != him.position)
                                        him.parent.putChild(him.parent.removeChild(him.bd),
                                            him.bd, position - 1);
                                    CurrentData.save(backLog.path);
                                } else ((MainChapter) him.bd).save();
                                VS.contentAdapter.selected = -1;
                                setSelectOpts(false);
                                him.update();
                                if (position == him.position)
                                    backLog.adapter.notifyItemChanged(position - 1);
                                else {
                                    backLog.adapter.list.add(position - 1, backLog.adapter.list.remove(him.position - 1));
                                    backLog.adapter.notifyItemMoved(him.position - 1, position - 1);
                                    him.position = position;
                                }
                            } catch (IllegalArgumentException iae) {
                                if (!iae.getMessage().contains("Name can't"))
                                    throw iae;
                                defaultReacts.get(ContainerFile.class + ":name")
                                    .react(iae.getMessage().contains("longer"));
                            }
                        });
                    });
                } else if (him.bd instanceof Word) {
                    int maxPos = VS.contentAdapter.list.size();
                    List<TranslationRow> rows = new ArrayList<>();
                    for (Object child : ((Word) him.bd).getChildren(him.parent)) {
                        Word trl = (Word) child;
                        rows.add(new TranslationRow(trl.getName(), trl.getDesc(him.parent), trl));
                    }
                    activity.runOnUiThread(() -> {
                        CreatorPopup cp = new CreatorPopup(getString(R.string.edit), him,
                            him.position, maxPos, new CreatorContent.Word(rows));
                        cp.setOkListener(() -> {
                            boolean ok = applyWord(cp, him);
                            int position = cp.getPosition();
                            if (position != him.position) {
                                him.parent.putChild(him.parent.removeChild(him.bd),
                                    him.bd, position - 1);
                                if (!ok) CurrentData.save(backLog.path);
                            }
                            if (!ok) return;
                            CurrentData.save(backLog.path);
                            VS.contentAdapter.selected = -1;
                            setSelectOpts(false);
                            him.update();
                            if (position == him.position)
                                backLog.adapter.notifyItemChanged(position - 1);
                            else {
                                backLog.adapter.list.add(position - 1,
                                    backLog.adapter.list.remove(him.position - 1));
                                backLog.adapter.notifyItemMoved(him.position - 1, position - 1);
                                him.position = position;
                            }
                            cp.dismiss();
                        });
                    });
                }
            });
            selActs.onClick(SelectionActions.Action.DELETE, v -> {
                if (((SearchAdapter) backLog.adapter).selected > 0)
                    new ContinuePopup(getString(R.string.continue_delete), () -> root.post(() -> {
                        boolean left = false;
                        for (int i = VS.contentAdapter.list.size() - 1; i >= 0; i--) {
                            HierarchyItemModel him = VS.contentAdapter.list.get(i);
                            if (him.isSelected()) {
                                if (him.bd.destroy(him.parent)) {
                                    VS.contentAdapter.list.remove(i);
                                    VS.contentAdapter.notifyItemRemoved(i);
                                    if (him.bd instanceof ContainerFile)
                                        CurrentData.changed.remove(him.bd);
                                } else left = true;
                            }
                        }
                        if (!backLog.path.isEmpty())
                            CurrentData.save(backLog.path);
                        if (VS.contentAdapter instanceof HierarchyAdapter && !backLog.path.isEmpty())
                            es.setInfo(backLog.path.get(-1), backLog.path.get(-2));
                        if (!left) {
                            VS.contentAdapter.selected = -1;
                            es.rv.postDelayed(VS.contentAdapter::notifyDataSetChanged, 200);
                            selActs.setBarVisible(false);
                        } else
                            Toast.makeText(CONTEXT, R.string.popup_delete_fail, Toast.LENGTH_SHORT).show();
                    }));
            });
            if (backLog.adapter instanceof SearchAdapter && VS.contentAdapter.selected > -1) {
                selActs.setBarVisible(true);
                selActs.render(VS.contentAdapter.selected, VS.contentAdapter.ref,
                    !backLog.path.isEmpty());
            }
        }, "select options setter").start();

        es = new ExplorerStuff(this, this::setContent, () -> {
            VS.contentAdapter.selected = -1;
            setSelectOpts(false);
        }, this::setVisibleOpts, VS, backLog, getContext(),
            root.findViewById(R.id.explorer_path_handler), root.findViewById(R.id.explorer_list),
            root.findViewById(R.id.explorer_path), root.findViewById(R.id.explorer_info_handler),
            root.findViewById(R.id.explorer_info), root.findViewById(R.id.explorer_search),
            root.findViewById(R.id.touch_outside), root.findViewById(R.id.search_collapser),
            this::updateBackContent);
        Formatter.defaultReacts.put("MChLoaded", (o) -> {
            if (backLog.path.isEmpty())
                root.post(backLog.adapter::notifyDataSetChanged);
        });
        if (backLog.adapter == null) {
            setContent(backLog.path.get(-1), backLog.path.get(-2), backLog.path.size());
            es.setInfo(backLog.path.get(-1), backLog.path.get(-2));
        } else {
            es.rv.setAdapter(backLog.adapter);
            if (!VS.sv_visible) es.searchView.setVisibility(View.GONE);
            es.onChange(true);
        }
        setVisibleOpts();
        pasteOpts.setVisibility(VS.pasteData != null ? View.VISIBLE : View.GONE);
        return root;
    }

    /**
     * Sets up move-mode.
     *
     * @param ref if the selected items will be referenced or moved.
     */
    private void move(boolean ref) {
        VS.pasteData = new ViewState.PasteData(ref, VS.contentAdapter);
        Controller.toggleSelectBtn(false);
        pasteOpts.setVisibility(View.VISIBLE);
        selActs.setEnabled(SelectionActions.Action.PASTE, false);
        boolean search = VS.contentAdapter.search;
        for (HierarchyItemModel him : VS.contentAdapter.list)
            if (him.isSelected()) VS.pasteData.src.add(him);
        VS.pasteData.srcPath.addAll(backLog.path);
        paste.setOnClickListener(v -> {
            Container npp = backLog.path.get(-2);
            Container np = backLog.path.get(-1);
            boolean searchNow = VS.contentAdapter.search;
            for (BasicData bd : np.getChildren(npp))
                VS.pasteData.src.remove(bd);
            if (ref) {
                List<Container> cp = new ArrayList<>(backLog.path.size());
                cp.addAll(backLog.path);
                for (HierarchyItemModel him : VS.pasteData.src) {
                    Reference r;
                    try {
                        r = Reference.mkElement(him.bd, cp,
                            (search ? ((SearchItemModel) him).path : VS.pasteData.srcPath)
                                .toArray(new Container[0]));
                    } catch (IllegalArgumentException iae) {
                        continue;
                    }
                    np.putChild(npp, r);
                    if (!searchNow)
                        backLog.adapter.addItem(new HierarchyItemModel(r, np, backLog.adapter.list.size()));
                }
            } else {
                List<ContainerFile> toSave = new LinkedList<>();
                for (HierarchyItemModel him : VS.pasteData.src) {
                    him.bd.move(him.parent, VS.pasteData.srcPath.size() == 1 ? null :
                        search ? ((SearchItemModel) him).path.get(-2)
                        : VS.pasteData.srcPath.get(VS.pasteData.srcPath.size() - 2), np, npp);
                    if (search) {
                        List<? extends BasicData> path = ((SearchItemModel) him).path;
                        for (int i = path.size() - 1; i >= 0; i--)
                            if (path.get(i) instanceof ContainerFile) {
                                if (!toSave.contains(path.get(i)))
                                    toSave.add((ContainerFile) path.get(i));
                                break;
                            }
                    }
                    if (!searchNow) {
                        if (search)
                            backLog.adapter.addItem(new HierarchyItemModel(him.bd, np, backLog.adapter.list.size()));
                        else {
                            backLog.adapter.addItem(him);
                            him.parent = np;
                            him.position = backLog.adapter.getItemCount();
                        }
                    }
                }
                if (!search) {
                    for (int i = VS.pasteData.srcPath.size() - 1; i >= 0; i--)
                        if (VS.pasteData.srcPath.get(i) instanceof ContainerFile) {
                            toSave.add((ContainerFile) VS.pasteData.srcPath.get(i));
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
            CurrentData.save(backLog.path);
            VS.pasteData.srcView.list.removeAll(VS.pasteData.src);
            int i = 0;
            for (Object him :
                VS.pasteData.srcView.list) {
                ((HierarchyItemModel) him).position = i++;
            }
            VS.pasteData.srcView.notifyDataSetChanged();
            VS.pasteData = null;
            Controller.toggleSelectBtn(true);
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
        selActs.setBarVisible(VS.contentAdapter.selected > -1);
        setVisibleOpts();
        if (VS.contentAdapter.selected == -1 && !change) {
            VS.contentAdapter.ref = 0;
            for (HierarchyItemModel him : VS.contentAdapter.list)
                him.setSelected(false);
            VS.contentAdapter.notifyDataSetChanged();
        }
    }

    /**
     * Controls the usability of the selection bar buttons by re-rendering it from state.
     */
    private void setVisibleOpts() {
        if (VS.contentAdapter == null) return;
        selActs.render(VS.contentAdapter.selected, VS.contentAdapter.ref, !backLog.path.isEmpty());
    }

    @Override
    public void run() {
        VS.sv_focused = false;
        es.searchView.clearFocus();
        if (Controller.isActive(this) && VS.contentAdapter != null
            && VS.contentAdapter.selected > -1) {
            VS.contentAdapter.selected = -1;
            setSelectOpts(false);
        } else if (Controller.isActive(this) && !backLog.path.isEmpty()) {
            if (VS.pasteData != null && backLog.path.size() == 1 && !VS.contentAdapter.search) {
                pasteOpts.setVisibility(View.GONE);
                VS.pasteData = null;
            } else updateBackContent(1);
        } else {
            if (!Controller.isActive(this)) {
                Controller.defaultBack.run();
                backTime = 0;
            } else if (System.currentTimeMillis() - backTime > 3000) {
                backTime = System.currentTimeMillis();
                Toast.makeText(getContext(), R.string.press_exit, Toast.LENGTH_SHORT).show();
            } else Controller.defaultBack.run();
            if (Controller.isActive(this)) {
                if (!backLog.path.isEmpty() && Controller.activity.getSupportActionBar() != null)
                    Controller.activity.getSupportActionBar().setTitle(backLog.path.get(-1).toString());
                Controller.setMenuRes(VS.menuRes);
                if (!VS.sv_visible) es.searchView.setVisibility(View.GONE);
                else es.updateSearch(false);
            }
        }
    }

    public void setContent(BasicData bd, Container parent, int size) {
        boolean container = false;
        if (size == 0) {
            new Thread(() -> {
                try {
                    Thread.sleep(100);
                } catch (Exception e) {
                }
                finishLoad();
                if (defaultReacts.get("removeSchNames") != null)
                    defaultReacts.get("removeSchNames").react();
            }, "MCh finishLoad").start();
            es.rv.setAdapter(backLog.adapter = VS.contentAdapter = new HierarchyAdapter(es.rv,
                this, convert(new ArrayList<>(MainChapter.ELEMENTS), null),
                this::setVisibleOpts));
            prepareObjectsContent();
        } else if (bd instanceof Container)
            // If opened item is a Picture holder
            if (!(container = !(bd instanceof TwoSided)) && bd instanceof Picture pic && VS.pasteData == null) {
                Controller.setMenuRes(VS.menuRes = 0);
                Controller.toggleSelectBtn(false);
                es.updateSearch(true);
                ArrayList<ImageItemModel> list = new ArrayList<>();
                BasicData[] pics = pic.getChildren(parent);
                for (int i = 1; i < pics.length; i += 2)
                    list.add(new ImageItemModel((Picture) pics[i - 1], (Picture) pics[i]));
                if (pics.length % 2 == 1)
                    list.add(new ImageItemModel((Picture) pics[pics.length - 1], null));
                es.rv.setAdapter(backLog.adapter = new ImageAdapter(es.rv, list));
                VS.contentAdapter = null;
                if (Controller.activity.getSupportActionBar() != null)
                    Controller.activity.getSupportActionBar().setTitle(bd.toString());
            }
        if (container) {
            if (VS.contentAdapter.list != null)
                for (HierarchyItemModel him : VS.contentAdapter.list)
                    him.setSelected(false);
            es.rv.setAdapter(backLog.adapter = VS.contentAdapter = new HierarchyAdapter(es.rv,
                this, convert(((Container) bd).getChildren(parent), (Container) bd),
                this::setVisibleOpts));
            prepareContainerContent((Container) bd);
        }
    }

    public void updateBackContent(int skip) {
        es.updateBackPath(skip);
        if (backLog.path.isEmpty()) prepareObjectsContent();
        else prepareContainerContent(backLog.path.get(-1));
    }

    private void prepareObjectsContent() {
        Controller.setMenuRes(VS.menuRes = R.menu.more_main);
        es.updateSearch(true);
        if (Controller.activity.getSupportActionBar() != null)
            Controller.activity.getSupportActionBar().setTitle(getString(R.string.menu_objects));
    }

    private void prepareContainerContent(Container bd) {
        if (VS.pasteData != null) test:{
            if (VS.pasteData.referencing) {
                if (VS.pasteData.src.get(0) instanceof SearchItemModel) {
                    for (SearchItemModel source
                        : (List<SearchItemModel>) (List<? extends HierarchyItemModel>) VS.pasteData.src) {
                        if (source.bd instanceof Container)
                            for (BasicData currentParent : backLog.path)
                                if (currentParent == source.bd) {
                                    selActs.setEnabled(SelectionActions.Action.PASTE, false);
                                    break test;
                                }
                        boolean match;
                        for (BasicData currentParent : backLog.path) {
                            match = false;
                            for (Container parent : source.path)
                                if (currentParent == parent) {
                                    match = true;
                                    break;
                                }
                            if (!match) {
                                selActs.setEnabled(SelectionActions.Action.PASTE, true);
                                break test;
                            }
                        }
                        selActs.setEnabled(SelectionActions.Action.PASTE, false);
                    }
                } else {
                    boolean match;
                    for (BasicData currentParent : backLog.path) {
                        match = false;
                        for (HierarchyItemModel source : VS.pasteData.src)
                            if (currentParent == source.bd) {
                                match = true;
                                break;
                            }
                        if (!match)
                            for (Container parent : VS.pasteData.srcPath)
                                if (currentParent == parent) {
                                    match = true;
                                    break;
                                }
                        if (!match) {
                            selActs.setEnabled(SelectionActions.Action.PASTE, true);
                            break test;
                        }
                    }
                    selActs.setEnabled(SelectionActions.Action.PASTE, false);
                }
            } else {
                for (BasicData parent : backLog.path) {
                    if (parent instanceof MainChapter) continue;
                    for (HierarchyItemModel source : VS.pasteData.src) {
                        try {
                            if (parent == source.bd.getThis()) {
                                selActs.setEnabled(SelectionActions.Action.PASTE, false);
                                break test;
                            }
                        } catch (IllegalArgumentException ex) {
                        }
                    }
                }
                selActs.setEnabled(SelectionActions.Action.PASTE, true);
            }
        }
        //else selActs.setEnabled(SelectionActions.Action.PASTE, false);
        Controller.setMenuRes(VS.menuRes = bd instanceof MainChapter
            ? R.menu.more_mch : R.menu.more_container);
        if (VS.pasteData == null) Controller.toggleSelectBtn(true);
        es.updateSearch(false);
        if (Controller.activity.getSupportActionBar() != null)
            Controller.activity.getSupportActionBar().setTitle(bd.toString());
    }

    @Override
    public void onClick(View v) { //control of the 'select' button
        if (backLog.adapter instanceof SearchAdapter) {
            if (VS.contentAdapter.selected > -1) {
                if (VS.contentAdapter.selected < VS.contentAdapter.list.size()) {
                    VS.contentAdapter.selected = VS.contentAdapter.list.size();
                    for (HierarchyItemModel him : VS.contentAdapter.list)
                        if (!him.isSelected()) {
                            if (him.bd instanceof Reference)
                                VS.contentAdapter.ref++;
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
    public void onItemClick(HierarchyItemModel item) {
        BasicData bd = item.bd;
        if (bd instanceof Word) {
            if (HierarchyItemModel.flipAllOnClick) {
                boolean flip = !item.flipped;
                for (HierarchyItemModel item1 : VS.contentAdapter.list)
                    if (item1.flipped != flip) item1.flip();
            } else item.flip();
            VS.contentAdapter.notifyDataSetChanged();
        } else {
            boolean ref;
            if (ref = bd instanceof Reference) {
                try {
                    if (item instanceof SearchItemModel sim)
                        sim.setNew(
                            bd.getThis(), Arrays.asList(((Reference) bd).getRefPath()));
                    else
                        item.setNew(bd.getThis(), ((Reference) bd).getRefPathAt(-1));
                    if (bd.getThis() instanceof Word) {
                        VS.contentAdapter.notifyDataSetChanged();
                        return;
                    } else {
                        backLog.add(true, null, EasyList.convert(((Reference) bd).getRefPath()));
                        backLog.path.add((Container) (bd = bd.getThis()));
                    }
                } catch (Exception e) {
                    return;
                }
            } else if (ref = VS.contentAdapter.search) {
                EasyList<Container> list = new EasyList<>();
                list.addAll(((SearchItemModel) item).path);
                list.add((Container) item.bd);
                backLog.add(true, null, list);
            } else backLog.add(false, (Container) bd, null);
            VS.contentAdapter.selected = -1;
            setSelectOpts(true);
            setContent(bd, item.parent, backLog.path.size());
            es.onChange(ref);
        }
    }

    @Override
    public boolean onItemLongClick(HierarchyItemModel item) {
        if (VS.pasteData == null) {
            boolean selected = !item.isSelected();
            if (VS.contentAdapter.selected == -1)
                VS.contentAdapter.selected = 0;
            if (item.bd instanceof Reference)
                VS.contentAdapter.ref += selected ? 1 : -1;
            VS.contentAdapter.selected += selected ? 1 : -1;
            setSelectOpts(false);
            item.setSelected(selected);
            VS.contentAdapter.notifyDataSetChanged();
        } else return false;
        return true;
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        new Thread(() -> {
            int itemId = item.getItemId();
            if (itemId == R.id.more_new_mch) {
                activity.runOnUiThread(() -> {
                    CreatorPopup cp = new CreatorPopup(getString(R.string.new_mch), null,
                        0, 0, CreatorContent.SIMPLE);
                    cp.setOkListener(() -> {
                        String name = cp.getName();
                        if (name.isEmpty()) return;
                        try {
                            ContainerFile.isCorrect(name);
                        } catch (IllegalArgumentException iae) {
                            if (!iae.getMessage().contains("Name can't"))
                                throw iae;
                            defaultReacts.get(ContainerFile.class + ":name")
                                .react(iae.getMessage().contains("longer"));
                            return;
                        }
                        backLog.adapter.addItem(new HierarchyItemModel(new MainChapter(
                            new Data(name, null).addDesc(cp.getDesc()
                                .replace("\\t", "\t"))), null, backLog.adapter.list.size() + 1));
                        cp.dismiss();
                    });
                });
            } else if (itemId == R.id.more_new_container) {
                int pos = VS.contentAdapter.list.size() + 1;
                activity.runOnUiThread(() -> {
                    CreatorPopup cp = new CreatorPopup(getString(R.string.new_chapter), null,
                        pos, pos, new CreatorContent.Chapter(false));
                    cp.setOkListener(() -> {
                        String name = cp.getName();
                        if (name.isEmpty()) return;
                        Container par = backLog.path.get(-1);
                        try {
                            Data d = new Data(name, (MainChapter) backLog.path.get(0))
                                .addDesc(cp.getDesc().replace("\\t", "\t"))
                                .addPar(par);
                            Container ch = cp.isChapterFile()
                                ? SaveChapter.mkElement(d) : new Chapter(d);
                            backLog.adapter.addItem(cp.getPosition() - 1,
                                new HierarchyItemModel(ch, par, cp.getPosition()));
                            par.putChild(backLog.path.get(-2), ch, cp.getPosition() - 1);
                            cp.dismiss();
                        } catch (IllegalArgumentException iae) {
                            if (!iae.getMessage().contains("Name can't"))
                                throw iae;
                            defaultReacts.get(ContainerFile.class + ":name")
                                .react(iae.getMessage().contains("longer"));
                        }
                    });
                });
            } else if (itemId == R.id.more_new_word) {
                int pos = VS.contentAdapter.list.size() + 1;
                activity.runOnUiThread(() -> {
                    CreatorPopup cp = new CreatorPopup(getString(R.string.new_word), null,
                        pos, pos, new CreatorContent.Word(Collections.emptyList()));
                    cp.setOkListener(() -> {
                        if (!applyWord(cp, null)) return;
                        backLog.adapter.notifyDataSetChanged();
                        CurrentData.save(backLog.path);
                        cp.dismiss();
                    });
                });
            } else if (itemId == R.id.sort_alpha_AZ) {
                sort(1, true);
            } else if (itemId == R.id.sort_alpha_ZA) {
                sort(1, false);
            } else if (itemId == R.id.sort_sf_01) {
                sort(2, true);
            } else if (itemId == R.id.sort_sf_10) {
                sort(2, false);
            } else if (itemId == R.id.sort_length_01) {
                sort(3, true);
            } else if (itemId == R.id.sort_length_10) {
                sort(3, false);
            } else if (itemId == R.id.sort_default) {
                sort(4, true);
            } else if (itemId == R.id.more_import_sch) {
                openSchFile.launch(new String[]{"*/*"});
            } else if (itemId == R.id.more_import_word) {
                openWordFile.launch(new String[]{"text/plain"});
            } else if (itemId == R.id.more_import_word_mch) {
                openWordMchFile.launch(new String[]{"text/plain"});
            } else if (itemId == R.id.more_export_word) {
                createWordFile.launch(backLog.path.get(-1).getName() + ".txt");
            } else if (itemId == R.id.more_import_mch) {
                openImportDir.launch(null);
            } else if (itemId == R.id.more_change_dir) {
                openChangeDir.launch(null);
            } else if (itemId == R.id.more_sf_revaluate) {
                List<Container> currentPath = (List<Container>) backLog.path.clone();
                Container opened = backLog.path.get(-1);
                int[] sf = opened.getSF();
                int[] refreshSF = opened.refreshSF();
                if (sf[0] != refreshSF[0] || sf[1] != refreshSF[1]) {
                    if (backLog.path.get(-1) == opened) es.rv.post(() -> {
                        backLog.adapter.notifyDataSetChanged();
                        es.setInfo(opened, currentPath.get(currentPath.size() - 2));
                    });
                    CurrentData.save(currentPath);
                }
            } else if (itemId == R.id.more_clean) {
                MainChapter mch = (MainChapter) backLog.path.get(0);
                try {
                    Picture.clean(mch);
                    SaveChapter.clean(mch);
                    mch.save(false);
                    activity.runOnUiThread(() -> makeText(CONTEXT, mch.getName()
                        + activity.getString(R.string.action_sw_success), Toast.LENGTH_SHORT).show());
                } catch (IllegalArgumentException iae) {
                }
            }
        }, "MFrag onMenuItemClick").start();
        return true;
    }

    private void sort(int type, boolean rising) {
        List<HierarchyItemModel> list = (List<HierarchyItemModel>) VS.contentAdapter.list;
        if (type != 4) list.sort(type == 1 ?
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
        es.rv.post(() -> backLog.adapter.notifyDataSetChanged());
    }

    /**
     * A word list (.txt) file was picked for import. Runs on a background thread,
     * as the original onActivityResult() did.
     */
    private void onWordFilePicked(Uri uri) {
        new Thread(() -> {
            try {
                List<BasicData> currentPath = (List<BasicData>) backLog.path.clone();
                Container self = backLog.path.get(-1),
                    par = backLog.path.get(-2);
                SimpleReader content = new SimpleReader(new UriPath(uri, false).load(), self, par);
                if (backLog.adapter instanceof HierarchyAdapter ha) {
                    int pos = ha.list.size();
                    for (BasicData bd : content.added)
                        ha.addItem(new HierarchyItemModel(bd, self, pos++));
                    es.rv.post(() -> es.setInfo(self, par));
                }
                defaultReacts.get(SimpleReader.class + ":success").react(content.result);
                CurrentData.save(currentPath);
            } catch (Exception e) {
                if (e instanceof IllegalArgumentException) return;
                defaultReacts.get(ContainerFile.class + ":load")
                    .react(e, uri, backLog.path.get(-1));
            }
        }, "MFrag word import").start();
    }

    /**
     * A word file (.txt) containing exactly one subject was picked on the main screen.
     * The whole file is parsed by the default {@link SimpleReader} with a new
     * {@link MainChapter} as the container, so the subject's name is taken from
     * the file's name (without the suffix) and every top-level chapter/word lands
     * directly inside it.
     */
    private void onWordMchFilePicked(Uri uri) {
        new Thread(() -> {
            UriPath file = new UriPath(uri, false);
            String name = file.getName();
            if (name == null || name.isEmpty()) name = "subject";
            else {
                int dot = name.lastIndexOf('.');
                if (dot > 0) name = name.substring(0, dot);
            }
            boolean exists = false;
            for (MainChapter mch : MainChapter.ELEMENTS)
                if (mch.getName().equalsIgnoreCase(name)) { exists = true; break; }
            if (exists) {
                String msg = getString(R.string.fail_import_mch_word_exists) + name;
                activity.runOnUiThread(() -> makeText(CONTEXT, msg, Toast.LENGTH_LONG).show());
                return;
            }
            try {
                ContainerFile.isCorrect(name);
            } catch (IllegalArgumentException iae) {
                return;
            }
            MainChapter mch = new MainChapter(new Data(name, null));
            try {
                SimpleReader content = new SimpleReader(file.load(), mch, null);
                mch.save(false);
                es.rv.post(() -> {
                    if (backLog.adapter instanceof HierarchyAdapter ha)
                        ha.addItem(new HierarchyItemModel(mch, null, ha.list.size() + 1));
                });
                defaultReacts.get(SimpleReader.class + ":success").react(content.result);
                defaultReacts.get("MChLoaded").react();
            } catch (IllegalArgumentException iae) {
                mch.destroy(null);
            } catch (Exception e) {
                mch.destroy(null);
                defaultReacts.get(ContainerFile.class + ":load")
                    .react(e, file, name);
            }
        }, "MFrag mch word import").start();
    }

    /**
     * A target location was picked for exporting a word list. Runs on a background thread,
     * as the original onActivityResult() did.
     */
    private void onWordTargetPicked(Uri uri) {
        new Thread(() -> {
            if (VS.contentAdapter.selected < 1) {
                new SimpleWriter(new UriPath(uri, false), new Container[]{
                    backLog.path.get(-1), backLog.path.get(-2)});
                return;
            }
            Container[][] toExport = new Container[VS.contentAdapter.selected][2];
            int i = 0;
            for (HierarchyItemModel him : VS.contentAdapter.list)
                if (him.isSelected() && him.bd instanceof Container c) {
                    toExport[i][0] = c;
                    toExport[i++][1] = him.parent;
                }
            VS.contentAdapter.selected = -1;
            setSelectOpts(false);
            new SimpleWriter(new UriPath(uri, false), toExport);
        }, "MFrag word export").start();
    }

    /**
     * Applies the word editor results (name, description, translations) to the model.
     * Mirrors the previous translations-adapter save behaviour.
     *
     * @param him the edited word's item model, or {@code null} when creating a new word
     * @return {@code false} when the input is incomplete and nothing should be committed
     */
    private boolean applyWord(CreatorPopup cp, HierarchyItemModel him) {
        String name = cp.getName();
        List<TranslationRow> rows = cp.getTranslations();
        if (name.isEmpty() || rows.isEmpty()) return false;
        MainChapter mch = (MainChapter) backLog.path.get(0);
        Container parent = him != null ? him.parent : backLog.path.get(-1);
        LinkedList<Data> translates = new LinkedList<>();
        for (TranslationRow row : rows) {
            String[] trls = SimpleReader.nameResolver(row.getName());
            if (trls[0].isEmpty()) return false;
            String[] trlDescs = SimpleReader.nameResolver(row.getDesc());
            if (him == null) {
                for (int i = 0; i < trls.length; i++)
                    translates.add(new Data(trls[i], mch).addDesc(i < trlDescs.length
                        ? trlDescs[i].replace("\\t", "\t") : null).addPar(parent));
            } else if (row.getSource() != null && trls.length == 1) {
                row.getSource().putDesc(parent, trlDescs[0]);
                row.getSource().setName(parent, trls[0]);
            } else {
                for (int i = 0; i < trls.length; i++)
                    Word.mkTranslate(new Data(trls[i], mch).addDesc(i < trlDescs.length
                        ? trlDescs[i] : null).addPar(parent), (Word) him.bd);
            }
        }
        if (him == null) {
            String[] names = SimpleReader.nameResolver(name);
            String[] descs = SimpleReader.nameResolver(cp.getDesc());
            Data d = new Data(null, mch).addPar(parent);
            int pos = cp.getPosition();
            for (int i = 0; i < names.length; i++) {
                d.name = names[i];
                d.description = i < descs.length ? descs[i].replace("\\t", "\t") : null;
                Word w = Word.mkElement(d, translates);
                backLog.adapter.addItem(pos + i - 1, new HierarchyItemModel(w, parent, pos + i));
                parent.putChild(backLog.path.get(-2), w, pos + i - 1);
            }
        } else {
            for (Word w : cp.getRemovedTranslations())
                ((Word) him.bd).removeChild(parent, w);
            him.bd.putDesc(parent, cp.getDesc().replace("\\t", "\t"));
            him.bd = him.bd.setName(parent, name);
        }
        return true;
    }

    /**
     * A school (.sch) file was picked for import. Runs on a background thread,
     * as the original onActivityResult() did.
     */
    private void onSchFilePicked(Uri uri) {
        new Thread(() -> {
            try {
                BasicData bd = new ContentReader(new UriPath(uri, false).load(),
                    (MainChapter) backLog.path.get(0)).mContent.getItem(backLog.path.get(-1));
                backLog.path.get(-1).putChild(backLog.path.get(-2), bd);
                if (backLog.adapter instanceof HierarchyAdapter ha) {
                    ha.addItem(new HierarchyItemModel(bd, backLog.path.get(-1), ha.list.size()));
                    es.rv.post(() -> es.setInfo(backLog.path.get(-1), backLog.path.get(-2)));
                }
                CurrentData.save(backLog.path);
            } catch (Exception e) {
                defaultReacts.get("uncaught").react(Thread.currentThread(), e);
            }
        }, "MFrag sch import").start();
    }

    /**
     * A directory was picked (either to import a .mch from, or to change the subjects dir).
     * Runs on a background thread, as the original onActivityResult() did.
     */
    private void onDirPicked(Uri uri, boolean importDir) {
        new Thread(() -> {
            CONTEXT.getContentResolver().takePersistableUriPermission(uri, Intent
                .FLAG_GRANT_READ_URI_PERMISSION | Intent
                .FLAG_GRANT_WRITE_URI_PERMISSION);
            UriPath file = new UriPath(uri, true);
            if (importDir) {
                if (file.getChild("main.json") != null && file.getChild("setts.dat") != null)
                    CurrentData.ImportedMchs.importMch(file);
                CurrentData.createMchs();
                if (backLog.path.isEmpty())
                    activity.runOnUiThread(() -> MainFragment.VS.mfInstance.setContent(null, null, 0));
            } else {
                if (Formatter.changeDir(file)) {
                    VS.pasteData = null;
                    backLog.clear();
                    CurrentData.createMchs();
                    activity.runOnUiThread(() -> {
                        MainFragment.VS.mfInstance.setContent(null, null, 0);
                        Toast.makeText(
                            CONTEXT, getString(R.string.choose_dir), Toast.LENGTH_SHORT).show();
                    });
                } else activity.runOnUiThread(
                    () -> Toast.makeText(CONTEXT, "Error", Toast.LENGTH_SHORT).show());
            }
        }, "MFrag dir picked").start();
    }

    @Override
    public void onResume() {
        Controller.setCurrentControl(this, VS.menuRes, VS.pasteData == null, true);
        if (!backLog.path.isEmpty() && Controller.activity.getSupportActionBar() != null)
            Controller.activity.getSupportActionBar().setTitle(backLog.path.get(-1).toString());
        super.onResume();
        backLog.adapter.update(es.rv);
    }

    public static class ViewState extends ExplorerStuff.ViewState {
        private int menuRes;
        public MainFragment mfInstance;
        private PasteData pasteData;

        private static class PasteData {
            final boolean referencing;
            final List<Container> srcPath = new ArrayList<>();
            final List<HierarchyItemModel> src = new ArrayList<>();
            final SearchAdapter srcView;

            private PasteData(boolean referencing, SearchAdapter currentAdapter) {
                this.referencing = referencing;
                srcView = currentAdapter;
            }
        }
    }
}
