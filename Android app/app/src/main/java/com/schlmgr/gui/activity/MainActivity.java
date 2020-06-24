package com.schlmgr.gui.activity;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Environment;
import android.util.DisplayMetrics;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.navigation.NavigationView;
import com.schlmgr.R;
import com.schlmgr.gui.AndroidIOSystem;
import com.schlmgr.gui.Controller;
import com.schlmgr.gui.CurrentData;
import com.schlmgr.gui.fragments.MainFragment;
import com.schlmgr.gui.list.DirAdapter;
import com.schlmgr.gui.list.HierarchyItemModel;
import com.schlmgr.gui.popup.AbstractPopup;
import com.schlmgr.gui.popup.FullPicture;

import java.io.File;

import IOSystem.Formatter;
import objects.templates.ContainerFile;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static com.schlmgr.gui.AndroidIOSystem.defDir;
import static com.schlmgr.gui.Controller.CONTEXT;
import static com.schlmgr.gui.Controller.dp;
import static com.schlmgr.gui.fragments.MainFragment.STORAGE_PERMISSION;

public class MainActivity extends PopupCareActivity {

	private AppBarConfiguration mAppBarConfiguration;
	private NavController navController;
	static final Controller c = Controller.getControl();
	private static boolean loaded;
	private static Thread background;

	public static Drawable ic_check_empty;
	public static Drawable ic_check_filled;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Controller.activity = this;
		CONTEXT = getApplicationContext();
		Controller.defaultBack = super::onBackPressed;
		setContentView(R.layout.nav_menu);
		setSupportActionBar(findViewById(R.id.bar));
		(c.moreButton = findViewById(R.id.bar_more)).setOnClickListener(v -> {
			if (c.menuRes == 0) return;
			PopupMenu pm = new PopupMenu(CONTEXT, v);
			pm.inflate(c.menuRes);
			pm.setOnMenuItemClickListener(c.currentControl);
			pm.show();
		});
		(c.selectButton = findViewById(R.id.bar_select)).setOnClickListener(
				v -> c.currentControl.onClick(v));
		// Passing each menu ID as a set of Ids because each
		// menu should be considered as top level destinations.
		mAppBarConfiguration = new AppBarConfiguration.Builder(R.id.menu_objects, R.id.test,
				R.id.menu_choose_dir, R.id.menu_options, R.id.menu_about)
				.setDrawerLayout(findViewById(R.id.drawer_layout)).build();
		navController = Navigation.findNavController(this, R.id.content_main);
		NavigationUI.setupActionBarWithNavController(this, navController, mAppBarConfiguration);
		NavigationUI.setupWithNavController(
				(NavigationView) findViewById(R.id.nav_menu), navController);
		if (background == null) {
			DisplayMetrics dm = new DisplayMetrics();
			getWindowManager().getDefaultDisplay().getMetrics(dm);
			FullPicture.size = dm.heightPixels > dm.widthPixels ? dm.heightPixels : dm.widthPixels;
			dp = getResources().getDimension(R.dimen.dp);
			(HierarchyItemModel.icPic = getResources().getDrawable(R.drawable.ic_pic))
					.setBounds((int) dp, 0, (int) (dp * 33), (int) (dp * 33));
			(HierarchyItemModel.icWord = getResources().getDrawable(R.drawable.ic_word))
					.setBounds(0, 0, (int) (dp * 30), (int) (dp * 30));
			(HierarchyItemModel.icChap = getResources().getDrawable(R.drawable.ic_chapter))
					.setBounds(0, 0, (int) (dp * 30), (int) (dp * 30));
			(HierarchyItemModel.icMCh = getResources().getDrawable(R.drawable.ic_subject))
					.setBounds(0, 0, (int) (dp * 30), (int) (dp * 30));
			(HierarchyItemModel.icRef = getResources().getDrawable(R.drawable.ic_ref))
					.setBounds(0, 0, (int) (dp * 30), (int) (dp * 30));
			DirAdapter.internal = getString(R.string.storage_internal);
			DirAdapter.external = getString(R.string.storage_external);
			DirAdapter.usbotg = getString(R.string.storage_usbotg);
			ic_check_empty = getResources().getDrawable(R.drawable.ic_check_box_empty);
			ic_check_filled = getResources().getDrawable(R.drawable.ic_check_box_filled);
			defDir = Environment.getExternalStorageDirectory().getAbsolutePath();
			AndroidIOSystem.storageDir = defDir.substring(0, defDir.lastIndexOf(File.separatorChar +
					(defDir.contains("emulated") ? "emulated" : "")));
			new AndroidIOSystem();
			AndroidIOSystem.testWrite();
			(background = new Thread(() -> {
				if (!Formatter.getPath().getAbsolutePath().contains(defDir
						+ "/Android/data/com.schlmgr") && !AndroidIOSystem.canWrite()) {
					runOnUiThread(() -> {
						Toast.makeText(this, getString(R.string.fail_permission_write)
								+ AndroidIOSystem.visibleFilePath(Formatter.getPath().getAbsolutePath())
								+ '\n' + getString(R.string.fail_formatter), Toast.LENGTH_LONG).show();
						Formatter.resetDir();
						CurrentData.createMchs();
						MainFragment.VS.mfInstance.setContent(null, null, 0);
						loaded = true;
					});
				} else {
					loaded = true;
					CurrentData.createMchs();
				}
				try {
					while (true) {
						Thread.sleep(100_000);
						for (ContainerFile cf : CurrentData.changed) cf.save();
						CurrentData.changed.clear();
						if (Thread.interrupted()) break;
					}
				} catch (Exception e) {
				}
			}, "MA background")).start();
		}
	}

	@Override
	public boolean onSupportNavigateUp() {
		return NavigationUI.navigateUp(navController, mAppBarConfiguration)
				|| super.onSupportNavigateUp();
	}

	@Override
	public void onBackPressed() {
		if (!clear()) (c != null ? c.onBackPressed : Controller.defaultBack).run();
	}

	@Override
	public void onDestroy() {
		if (AbstractPopup.isActive && !AbstractPopup.isShowing) AbstractPopup.clear();
		runOnUiThread(() -> AbstractPopup.clean());
		for (ContainerFile cf : CurrentData.changed) cf.save(false);
		CurrentData.changed.clear();
		for (File f : CONTEXT.getCacheDir().listFiles()) if (!f.delete()) f.deleteOnExit();
		super.onDestroy();
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
	                                       @NonNull int[] grantResults) {
		if (requestCode == STORAGE_PERMISSION) {
			if (grantResults[0] != PERMISSION_GRANTED) {
				AndroidIOSystem.setCanWrite(false);
				if (loaded) super.onBackPressed();
			} else AndroidIOSystem.setCanWrite(true);
		} else super.onRequestPermissionsResult(requestCode, permissions, grantResults);
	}
}
