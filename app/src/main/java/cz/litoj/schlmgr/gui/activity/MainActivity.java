package cz.litoj.schlmgr.gui.activity;

import static cz.litoj.schlmgr.gui.AndroidIOSystem.defDir;
import static cz.litoj.schlmgr.gui.Controller.CONTEXT;
import static cz.litoj.schlmgr.gui.Controller.dp;

import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Environment;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.core.content.res.ResourcesCompat;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.navigation.NavigationView;
import cz.litoj.schlmgr.R;
import cz.litoj.schlmgr.gui.AndroidIOSystem;
import cz.litoj.schlmgr.gui.Controller;
import cz.litoj.schlmgr.gui.CurrentData;
import cz.litoj.schlmgr.gui.EdgeToEdge;
import cz.litoj.schlmgr.gui.fragments.MainFragment;
import cz.litoj.schlmgr.gui.list.DirAdapter;
import cz.litoj.schlmgr.gui.list.HierarchyItemModel;
import cz.litoj.schlmgr.gui.popup.AbstractPopup;
import cz.litoj.schlmgr.gui.popup.FullPicture;

import java.io.File;
import java.util.Objects;

import cz.litoj.schlmgr.gui.engine.IOSystem.Formatter;
import cz.litoj.schlmgr.gui.engine.objects.templates.ContainerFile;

public class MainActivity extends PopupCareActivity {

	private AppBarConfiguration mAppBarConfiguration;
	private NavController navController;
	static final Controller c = Controller.getControl();
	private static Thread background;

	public static Drawable ic_check_empty;
	public static Drawable ic_check_filled;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Controller.activity = this;
		CONTEXT = getApplicationContext();
		Controller.defaultBack = this::goBack;
		setContentView(R.layout.nav_menu);
		// The top (status bar) inset is handled by the AppBarLayout itself
		// (fitsSystemWindows), so only the remaining insets are padded here.
		EdgeToEdge.apply(findViewById(R.id.include), false);
		// The drawer's NavigationView can't rely on fitsSystemWindows here: in
		// edge-to-edge mode the insets are consumed by the AppBarLayout sibling before
		// reaching it (dispatch order), so the drawer was clipped by the status bar.
		// Apply the system bar insets to it explicitly, including the top.
		EdgeToEdge.apply(findViewById(R.id.nav_menu), true);
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
		// The main fragment inflated during setContentView above and may already have
		// requested a menu/select button state while the views did not exist yet.
		Controller.applyBarState();
		// Passing each menu ID as a set of Ids because each
		// menu should be considered as top level destinations.
		mAppBarConfiguration = new AppBarConfiguration.Builder(R.id.menu_objects,
				R.id.test, R.id.menu_options, R.id.menu_about)
				.setOpenableLayout(findViewById(R.id.drawer_layout)).build();
		navController = Navigation.findNavController(this, R.id.content_main);
		NavigationUI.setupActionBarWithNavController(this, navController, mAppBarConfiguration);
		NavigationUI.setupWithNavController(
				(NavigationView) findViewById(R.id.nav_menu), navController);
		if (background == null) {
			Rect windowBounds = getWindowManager().getCurrentWindowMetrics().getBounds();
			FullPicture.size = Math.max(windowBounds.height(), windowBounds.width());
			dp = getResources().getDimension(R.dimen.dp);
			(HierarchyItemModel.icPic = Objects.requireNonNull(ResourcesCompat.getDrawable(getResources(), R.drawable.ic_pic, null)))
					.setBounds((int) dp, 0, (int) (dp * 33), (int) (dp * 33));
			(HierarchyItemModel.icWord = Objects.requireNonNull(ResourcesCompat.getDrawable(getResources(), R.drawable.ic_word, null)))
					.setBounds(0, 0, (int) (dp * 30), (int) (dp * 30));
			(HierarchyItemModel.icChap = Objects.requireNonNull(ResourcesCompat.getDrawable(getResources(), R.drawable.ic_chapter, null)))
					.setBounds(0, 0, (int) (dp * 30), (int) (dp * 30));
			(HierarchyItemModel.icMCh = Objects.requireNonNull(ResourcesCompat.getDrawable(getResources(), R.drawable.ic_subject, null)))
					.setBounds(0, 0, (int) (dp * 30), (int) (dp * 30));
			(HierarchyItemModel.icRef = Objects.requireNonNull(ResourcesCompat.getDrawable(getResources(), R.drawable.ic_ref, null)))
					.setBounds(0, 0, (int) (dp * 30), (int) (dp * 30));
			DirAdapter.internal = getString(R.string.storage_internal);
			DirAdapter.external = getString(R.string.storage_external);
			DirAdapter.usbotg = getString(R.string.storage_usbotg);
			ic_check_empty = Objects.requireNonNull(ResourcesCompat.getDrawable(getResources(), R.drawable.ic_check_box_empty, null));
			ic_check_filled = Objects.requireNonNull(ResourcesCompat.getDrawable(getResources(), R.drawable.ic_check_box_filled, null));
			defDir = Environment.getExternalStorageDirectory().getAbsolutePath();
			AndroidIOSystem.storageDir = defDir.substring(0, defDir.lastIndexOf(File.separatorChar +
					(defDir.contains("emulated") ? "emulated/0" : "")));
			new AndroidIOSystem();
			(background = new Thread(() -> {
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
			if (!Formatter.getSubjectsDir().getOriginalName().contains(defDir
					+ "/Android/data/cz.litoj.schlmgr") ||
					!Formatter.getSubjectsDir().exists()) {
				Toast.makeText(this, getString(R.string.fail_permission_write)
						+ AndroidIOSystem.visibleInternalPath(Formatter.getSubjectsDir().getOriginalName())
						+ '\n' + getString(R.string.fail_formatter), Toast.LENGTH_LONG).show();
				Formatter.resetDir();
				CurrentData.createMchs();
				MainFragment.VS.mfInstance.setContent(null, null, 0);
			} else {
				CurrentData.createMchs();
			}
		}
	}

	@Override
	public boolean onSupportNavigateUp() {
		return NavigationUI.navigateUp(navController, mAppBarConfiguration)
				|| super.onSupportNavigateUp();
	}

	@Override
	public void onUserBackPressed() {
		if (!clear()) (c != null ? c.onBackPressed : Controller.defaultBack).run();
	}

	@Override
	public void onDestroy() {
		if (AbstractPopup.isActive && !AbstractPopup.isShowing) AbstractPopup.clear();
		runOnUiThread(AbstractPopup::clean);
		for (ContainerFile cf : CurrentData.changed) cf.save(false);
		CurrentData.changed.clear();
		File[] cacheFiles = CONTEXT.getCacheDir().listFiles();
		if (cacheFiles != null) for (File f : cacheFiles) if (!f.delete()) f.deleteOnExit();
		super.onDestroy();
	}
}
