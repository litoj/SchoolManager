package com.schlmgr.gui.activity;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Environment;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
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
import com.schlmgr.gui.popup.AbstractPopup;

import java.io.File;

import IOSystem.Formatter;
import objects.templates.ContainerFile;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static com.schlmgr.gui.AndroidIOSystem.defDir;
import static com.schlmgr.gui.fragments.MainFragment.STORAGE_PERMISSION;

public class MainActivity extends AppCompatActivity {

	private AppBarConfiguration mAppBarConfiguration;
	private NavController navController;
	private static final Controller c = Controller.getControl();
	private static boolean loaded;
	private static Thread background;
	private boolean exists;

	public static Drawable ic_check_empty;
	public static Drawable ic_check_filled;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Controller.activity = this;
		Controller.CONTEXT = getApplicationContext();
		Controller.defaultBack = super::onBackPressed;
		setContentView(R.layout.nav_menu);
		setSupportActionBar(findViewById(R.id.bar));
		(c.moreButton = findViewById(R.id.bar_more)).setOnClickListener(v -> {
			if (c.menuRes == 0) return;
			PopupMenu pm = new PopupMenu(Controller.CONTEXT, v);
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
		NavigationUI.setupWithNavController((NavigationView) findViewById(R.id.nav_menu), navController);
		if (background == null) {
			ic_check_empty=getResources().getDrawable(R.drawable.ic_check_box_empty);
			ic_check_filled=getResources().getDrawable(R.drawable.ic_check_box_filled);
			Controller.dp = getResources().getDimension(R.dimen.dp);
			defDir = Environment.getExternalStorageDirectory().getAbsolutePath();
			AndroidIOSystem.storageDir = defDir.substring(0, defDir.lastIndexOf(File.separatorChar +
					(defDir.contains("emulated") ? "emulated" : "")));
			new AndroidIOSystem().testWrite();
			(background = new Thread(() -> {
				if (!Formatter.getPath().getAbsolutePath().contains(defDir + "/Android/data/com.schlmgr")
						&& !AndroidIOSystem.canWrite()) {
					runOnUiThread(() -> {
						Toast.makeText(this, getString(R.string.fail_permission_write)
								+ AndroidIOSystem.visibleFilePath(Formatter.getPath().getAbsolutePath()) + '\n' +
								getString(R.string.fail_formatter), Toast.LENGTH_LONG).show();
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
			})).start();
		}
	}

	@Override
	protected void onPostResume() {
		super.onPostResume();
		if (!c.popupRepaint.isEmpty() && !exists)
			for (Runnable r : c.popupRepaint) new Thread(r).start();
		exists = true;
	}

	@Override
	public boolean onSupportNavigateUp() {
		return NavigationUI.navigateUp(navController, mAppBarConfiguration)
				|| super.onSupportNavigateUp();
	}

	@Override
	public void onBackPressed() {
		if (AbstractPopup.isActive) AbstractPopup.clear();
		else (c != null ? c.onBackPressed : Controller.defaultBack).run();
	}

	@Override
	public void onDestroy() {
		if (AbstractPopup.isActive && !AbstractPopup.isShowing) AbstractPopup.clear();
		runOnUiThread(() -> AbstractPopup.clean());
		for (ContainerFile cf : CurrentData.changed) cf.save(false);
		CurrentData.changed.clear();
		super.onDestroy();
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
		if (requestCode == STORAGE_PERMISSION) {
			if (grantResults[0] != PERMISSION_GRANTED) {
				AndroidIOSystem.setCanWrite(false);
				if (loaded) super.onBackPressed();
			} else AndroidIOSystem.setCanWrite(true);
		} else super.onRequestPermissionsResult(requestCode, permissions, grantResults);
	}
}
