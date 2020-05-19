package com.schlmgr.gui.activity;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.graphics.drawable.DrawableCompat;

import com.schlmgr.R;
import com.schlmgr.gui.AndroidIOSystem;
import com.schlmgr.gui.Controller;
import com.schlmgr.gui.CurrentData;
import com.schlmgr.gui.CurrentData.EasyList;
import com.schlmgr.gui.fragments.MainFragment;
import com.schlmgr.gui.list.DirAdapter;
import com.schlmgr.gui.list.DirAdapter.DirItemModel;

import java.io.File;
import java.util.LinkedList;

import IOSystem.Formatter;

import static com.schlmgr.gui.Controller.dp;
import static com.schlmgr.gui.CurrentData.backLog;

public class SelectDirActivity extends PopupCareActivity
		implements OnItemClickListener, OnItemLongClickListener {

	private static long backTime;
	private HorizontalScrollView path_handler;
	private LinearLayout path;
	private TextView set_subjdir;
	private ListView list;
	private Context context;
	public static boolean importing;
	private static ViewState VS;

	private static Drawable icSetDir;
	private static Drawable icSetDir_disabled;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Controller.currentActivity = this;
		setContentView(R.layout.activity_select_dir);
		((TextView) findViewById(R.id.bar)).setText(importing ? R.string.select_dir_import : R.string.select_dir_src);
		findViewById(R.id.bar_more).setOnClickListener(v -> {
			PopupMenu pm = new PopupMenu(Controller.CONTEXT, v);
			pm.inflate(importing ? R.menu.more_current_dir : R.menu.more_choose_dir);
			pm.setOnMenuItemClickListener(this::onMenuItemClick);
			pm.show();
		});
		findViewById(R.id.bar_select).setOnClickListener(v -> allCheck());
		findViewById(R.id.cancel).setOnClickListener(v -> super.onBackPressed());
		findViewById(R.id.select_all).setOnClickListener(v -> allCheck());
		set_subjdir = findViewById(R.id.dir_set_subjdir);
		TextView mch_import = findViewById(R.id.dir_import);
		if (importing) {
			set_subjdir.setVisibility(View.GONE);
			mch_import.setOnClickListener(v -> {
				if (VS.path.get(-1).equals(Formatter.getPath()) || VS.da.list.size() < 1) return;
				for (DirItemModel item : VS.da.list)
					if (item.selected) for (File f : item.f.listFiles())
						if (f.getName().equals("main.json")) {
							CurrentData.ImportedMchs.importMch(item.f);
							break;
						}
				CurrentData.createMchs();
				if (backLog.path.isEmpty()) MainFragment.VS.mfInstance.setContent(null, null, 0);
				super.onBackPressed();
			});
		} else {
			mch_import.setVisibility(View.GONE);
			set_subjdir.setOnClickListener(v -> {
				for (DirItemModel item : VS.da.list) {
					if (item.selected) {
						if (Formatter.changeDir(item.f.getAbsolutePath())) {
							MainFragment.VS = new MainFragment.ViewState();
							backLog.clear();
							CurrentData.createMchs();
							runOnUiThread(() -> Toast.makeText(
									context, getString(R.string.choose_dir), Toast.LENGTH_SHORT).show());
						}
						super.onBackPressed();
						break;
					}
				}
			});
		}
		context = getApplicationContext();
		path_handler = findViewById(R.id.dir_path_handler);
		path = findViewById(R.id.dir_path);
		list = findViewById(R.id.dir_list);
		path_handler.setHorizontalScrollBarEnabled(false);
		list.setOnItemClickListener(this);
		list.setOnItemLongClickListener(this);
		File f;
		if (VS == null) {
			(icSetDir = getResources().getDrawable(R.drawable.ic_set))
					.setBounds((int) dp, 0, (int) (dp * 35), (int) (dp * 35));
			(icSetDir_disabled = getResources().getDrawable(R.drawable.ic_subjdir_disabled))
					.setBounds((int) dp, 0, (int) (dp * 35), (int) (dp * 35));
			if (VERSION.SDK_INT < 21)
				DrawableCompat.setTint(DrawableCompat.wrap(icSetDir_disabled), 0x55FFFFFF);
			VS = new ViewState();
			f = resetView();
		} else if (VS.path.isEmpty()) f = null;
		else {
			f = VS.path.get(-1);
			File[] dirPath = VS.path.toArray(new File[0]);
			VS.path.clear();
			for (int i = 0; i < dirPath.length; i++)
				addPathButton(dirPath[i], i == 0 ?
						DirAdapter.storageName(dirPath[i]) : dirPath[i].getName());
		}
		makeFiles(f);
		checkSelected();
	}

	private File resetView() {
		File f = Formatter.getPath();
		String path;
		if (f.getAbsolutePath().contains("/0")) {
			path = AndroidIOSystem.visibleFilePath(f.getAbsolutePath());
		} else {
			path = f.getAbsolutePath().substring(AndroidIOSystem.storageDir.length());
			path = path.substring(path.indexOf(File.separatorChar, 1));
		}
		String[] s = path.split(File.separator);
		f = new File(f.getAbsolutePath().substring(0, f.getAbsolutePath().indexOf(path)));
		addPathButton(f, DirAdapter.storageName(f));
		for (int i = 1; i < s.length; i++) addPathButton(f = new File(f, s[i]), f.getName());
		return f;
	}

	@Override
	public void onItemClick(AdapterView<?> par, View v, int index, long id) {
		DirItemModel item = VS.da.list.get(index);
		addPathButton(item.f, item.name);
		makeFiles(item.f);
	}

	@Override
	public boolean onItemLongClick(AdapterView<?> par, View v, int index, long id) {
		DirItemModel item = VS.da.list.get(index);
		item.setSelected(!item.selected);
		VS.da.selected += item.selected ? 1 : -1;
		checkSelected();
		return true;
	}

	public void checkSelected() {
		if (importing) return;
		boolean enabled = VS.da == null || VS.da.selected == 1;
		set_subjdir.setCompoundDrawables(null, enabled ? icSetDir : icSetDir_disabled, null, null);
		set_subjdir.setTextColor(enabled ? 0xFFFFFFFF : 0x66FFFFFF);
		set_subjdir.setClickable(enabled);
	}

	public void allCheck() {
		if (VS.da.selected < VS.da.list.size()) {
			for (DirItemModel dim : VS.da.list) if (!dim.selected) dim.setSelected(true);
			VS.da.selected = VS.da.list.size();
		} else {
			for (DirItemModel dim : VS.da.list) dim.setSelected(false);
			VS.da.selected = 0;
		}
		checkSelected();
	}

	@Override
	public void onBackPressed() {
		if (clear()) return;
		if (!VS.path.isEmpty()) {
			if (makeFiles(VS.path.get(-2))) {
				VS.path.remove(-1);
				this.path.removeViews(VS.path.size() * 2, 2);
			}
		} else if (System.currentTimeMillis() - backTime > 3000) {
			backTime = System.currentTimeMillis();
			Toast.makeText(getApplicationContext(), R.string.press_exit, Toast.LENGTH_SHORT).show();
		} else super.onBackPressed();
	}

	private boolean makeFiles(File parent) {
		if (!AndroidIOSystem.requestWrite()) return false;
		LinkedList<File> files = new LinkedList<>();
		if (parent == null)
			list.setAdapter(VS.da = new DirAdapter(context,
					DirAdapter.convert(null), true, this::checkSelected));
		else if (parent.listFiles() != null) {
			for (File f : parent.listFiles())
				if (f.isDirectory()) {
					String name = f.getName();
					int i = files.size() - 1;
					for (; i >= 0 && files.get(i).getName().compareToIgnoreCase(name) > 0; i--) ;
					files.add(i + 1, f);
				}
			list.setAdapter(VS.da = new DirAdapter(context,
					DirAdapter.convert(files), false, this::checkSelected));
		}
		return true;
	}

	private void addPathButton(File file, String name) {
		TextView btn = new Button(context);
		btn.setTextSize(17);
		btn.setAllCaps(false);
		btn.setBackground(null);
		btn.setTextColor(0xFFFFFFFF);
		btn.setPadding(0, 0, 0, 0);
		btn.setText(name);
		btn.setTypeface(null);
		btn.setLayoutParams(new LayoutParams((int)
				(btn.getPaint().measureText(name) + dp * 8), LayoutParams.MATCH_PARENT));
		btn.setOnClickListener((v) -> {
			if (VS.path.get(-1) == file || !makeFiles(file)) return;
			int diff = 0;
			for (; file != VS.path.get(-1); diff++) VS.path.remove(-1);
			try {
				this.path.removeViews(VS.path.size() * 2, diff * 2);
			} catch (NullPointerException e) {
				this.path.removeViews(VS.path.size() * 2, diff * 2);
			}
		});
		path.addView(btn);
		VS.path.add(file);
		btn = new TextView(context);
		btn.setBackground(getResources().getDrawable(R.drawable.ic_bread_crumbs));
		btn.setLayoutParams(new LayoutParams((int) (dp * 7), LayoutParams.MATCH_PARENT));
		btn.setScaleX(2.4f);
		path.addView(btn);
		path_handler.post(() -> path_handler.fullScroll(View.FOCUS_RIGHT));
	}

	public static class ViewState {
		final EasyList<File> path = new EasyList<>();
		DirAdapter da;
	}

	public boolean onMenuItemClick(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.more_dir_default:
				Formatter.resetDir();
				MainFragment.VS = new MainFragment.ViewState();
				backLog.clear();
				CurrentData.createMchs();
				runOnUiThread(() -> Toast.makeText(
						context, getString(R.string.choose_dir), Toast.LENGTH_SHORT).show());
				super.onBackPressed();
				break;
			case R.id.more_dir_view:
				VS.path.clear();
				path.removeAllViews();
				makeFiles(resetView());
		}
		return true;
	}
}
