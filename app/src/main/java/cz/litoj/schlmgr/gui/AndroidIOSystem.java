package cz.litoj.schlmgr.gui;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import androidx.documentfile.provider.DocumentFile;

import com.google.android.material.snackbar.Snackbar;
import cz.litoj.schlmgr.R;
import cz.litoj.schlmgr.gui.list.HierarchyItemModel;
import cz.litoj.schlmgr.gui.popup.TextPopup;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import cz.litoj.schlmgr.gui.engine.IOSystem.FilePath;
import cz.litoj.schlmgr.gui.engine.IOSystem.Formatter;
import cz.litoj.schlmgr.gui.engine.IOSystem.SimpleReader;
import cz.litoj.schlmgr.gui.engine.IOSystem.SimpleWriter;
import cz.litoj.schlmgr.gui.engine.objects.MainChapter;
import cz.litoj.schlmgr.gui.engine.objects.Reference;
import cz.litoj.schlmgr.gui.engine.objects.templates.ContainerFile;
import cz.litoj.schlmgr.gui.engine.testing.Test;

import static cz.litoj.schlmgr.gui.engine.IOSystem.Formatter.defaultReacts;
import static cz.litoj.schlmgr.gui.engine.IOSystem.Formatter.getStackTrace;
import static android.widget.Toast.makeText;
import static cz.litoj.schlmgr.gui.Controller.CONTEXT;
import static cz.litoj.schlmgr.gui.Controller.activity;
import static cz.litoj.schlmgr.gui.Controller.currentActivity;
import static cz.litoj.schlmgr.gui.Controller.translate;

/**
 * The Android platform layer of the engine. It keeps the proven file-saving implementation:
 * the objects storage directory, per-hierarchy files and streams and the error reactions.
 * The engine's own tiny settings cache lives in the private {@code settings.dat} file;
 * the durable store for application options is Android's {@code "settings"} SharedPreferences.
 */
public class AndroidIOSystem extends Formatter.IOSystem {

	public static String defDir;
	public static String storageDir;

	/**
	 * This method hides the keyboard from the screen when called.
	 * Code used from
	 * https://medium.com/@rmirabelle/close-hide-the-soft-keyboard-in-android-db1da22b09d2
	 *
	 * @param view the view, that initially used the keyboard
	 */
	public static void hideKeyboardFrom(View view) {
		InputMethodManager imm = (InputMethodManager)
				view.getContext().getSystemService(Activity.INPUT_METHOD_SERVICE);
		imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
	}

	public static String visibleInternalPath(String path) {
		if (!path.contains(defDir)) return path;
		return path.substring(defDir.length());
	}

	public AndroidIOSystem() {
		// No persisted subjects directory: nothing ever stores one (Formatter.onDirChanged
		// is not hooked up), so always start with the default. Previously the settings file
		// was passed here by mistake, which made the engine treat 'settings.dat' as the
		// subjects directory and tripped the startup validation toast on every launch.
		super(null);
	}

	@Override
	protected void setDefaults(boolean first) {
		// The engine passes "subjects dir is the default" as `first`, which says nothing
		// about first launch (the app always starts on the default dir now). The reliable
		// first-launch signal is that no app version has been persisted yet.
		if (AppSettings.getInt("version", 0) == 0) {
			AppSettings.set("doShowDesc", false);
			AppSettings.set("defaultTestTypePicture", false);
			AppSettings.set("flipWord", true);
			AppSettings.set("flipAllOnClick", false);
			AppSettings.set("parseNames", true);
		}
		if (AppSettings.getInt("version", 0) < appVersionCode()) {
			AppSettings.set("version", appVersionCode());
		}

		HierarchyItemModel.defFlip = AppSettings.getBool("flipWord", true);
		HierarchyItemModel.flipAllOnClick = AppSettings.getBool("flipAllOnClick", false);
		HierarchyItemModel.parse = AppSettings.getBool("parseNames", true);
		HierarchyItemModel.show_desc = AppSettings.getBool("doShowDesc", false);

		// Engine options live in system SharedPreferences; apply them to the engine's
		// (in-memory) statics once at startup.
		Test.setAmount(AppSettings.getInt("testAmount", 10));
		Test.setDefaultTime(AppSettings.getInt("defaultTestTime", 18));
		Test.setClever(AppSettings.getBool("isClever", true));
		SimpleWriter.setWordSplitter(AppSettings.getString("exportWordSplit", ";"));
	}

	/**
	 * @return the app's version code, as declared in the build script
	 */
	private static int appVersionCode() {
		try {
			return (int) CONTEXT.getPackageManager()
					.getPackageInfo(CONTEXT.getPackageName(), 0).getLongVersionCode();
		} catch (android.content.pm.PackageManager.NameNotFoundException e) {
			throw new IllegalStateException("Own package not found", e);
		}
	}

	/**
	 * @return {@code true} for debug builds (replaces the old {@code BuildConfig.DEBUG})
	 */
	private static boolean isDebugBuild() {
		return (CONTEXT.getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0;
	}

	/**
	 * Creates the default handlers for various actions.<p>
	 * This method is completely platform dependant.
	 */
	@Override
	protected void mkDefaultReacts() {
		Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
			defaultReacts.get("uncaught").react(t, e);
			System.exit(0);
		});
		defaultReacts.put("uncaught", (o) -> {
			String fullMsg = o[0].toString() + '\n' + getStackTrace((Throwable) o[1]);
			// Keep only the rendered message, stored in the app's SharedPreferences
			// (no filesystem), so it can be shown on the next launch.
			CONTEXT.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
					.putString("uncaughtException", activity.getString(R.string.exception_handler)
							+ getFirstCause((Throwable) o[1]) + "\n\n" + fullMsg)
					.apply();
			activity.runOnUiThread(() -> makeText(CONTEXT,
					activity.getString(R.string.exception_warning), Toast.LENGTH_LONG).show());
			if (isDebugBuild()) Log.e("Unexpected failure", fullMsg);
		});
		defaultReacts.put(Formatter.class + ":newSrcDir", (o) -> {
			Exception e = (Exception) o[0];
			String msg = activity.getString(R.string.fail_find) + '\n' + o[1].toString() + '\n' +
					activity.getString(R.string.fail_formatter) + '\n';
			showMsg(msg + getFirstCause(e), msg + e.getMessage()
					+ '\n' + getStackTrace(e));
		});
		defaultReacts.put(ContainerFile.class + ":name", (o) -> activity.runOnUiThread(() ->
				makeText(CONTEXT, activity.getString(((boolean) o[0]) ? R.string.fail_cf_name_length :
						R.string.fail_cf_name_typo), Toast.LENGTH_LONG).show()));
		defaultReacts.put(ContainerFile.class + ":load", (o) -> {
			String msg = activity.getString(R.string.fail_load) + '\n' + o[1].toString() + '\n' +
					activity.getString(R.string.fail_load_src) + o[2] + activity.getString(R.string.fail_type)
					+ translate(o[2].getClass()) + ":\n";
			showMsg(msg + getFirstCause((Exception) o[0]), msg + ((Exception) o[0]).getMessage()
					+ '\n' + getStackTrace((Exception) o[0]));
		});
		defaultReacts.put(ContainerFile.class + ":save", (o) -> {
			String msg = activity.getString(R.string.fail_save) + '\n' + o[1].toString() + '\n' +
					activity.getString(R.string.fail_save_src) + o[2] + activity.getString(R.string.fail_type)
					+ translate(o[2].getClass()) + ":\n";
			showMsg(msg + getFirstCause((Exception) o[0]), msg + ((Exception) o[0]).getMessage()
					+ '\n' + getStackTrace((Exception) o[0]));
		});
		defaultReacts.put(SimpleReader.class + ":fail", (o) -> {
			String msg = activity.getString(R.string.fail_sr_msg) + '\n' + o[0];
			showMsg(msg, msg);
		});
		defaultReacts.put(SimpleWriter.class + ":success", (o) -> activity.runOnUiThread(() ->
				makeText(CONTEXT, visibleInternalPath(o[0].toString())
						+ activity.getString(R.string.action_sw_success), Toast.LENGTH_SHORT).show()));
		defaultReacts.put(SimpleReader.class + ":success", (o) -> {
			int[] i = (int[]) o[0];
			String msg = activity.getString(R.string.loaded)
					+ '\n' + activity.getString(R.string.loaded_chaps) + ": " + i[0] + '\n'
					+ activity.getString(R.string.help_create_word) + ": " + i[1] + '\n'
					+ activity.getString(R.string.data_translations) + ": " + i[2];
			showMsg(msg, msg);
		});
		defaultReacts.put(Reference.class + ":not_found", (o) -> {
			String[] desc = new String[]{activity.getString(R.string.fail_ref_msg),
					activity.getString(R.string.fail_parent), activity.getString(R.string.fail_type)};
			String msg = desc[0] + o[0] + "\n  " + desc[1] + o[1] + desc[2] +
					translate(o[1].getClass()) + (o[1].getClass() == MainChapter.class ? '!' :
					("\n  " + desc[1] + o[2] + desc[2] + translate(o[2].getClass())));
			showMsg(msg, msg);
		});
	}

	public static String getFirstCause(Throwable e) {
		while (true) {
			if (e.getCause() != null) e = e.getCause();
			else return e.getMessage();
		}
	}

	/**
	 * @return the crash report of an unexpected failure from a previous session,
	 * or {@code null} when there is none
	 */
	public static String getCrashReport() {
		return CONTEXT.getSharedPreferences("settings", Context.MODE_PRIVATE)
				.getString("uncaughtException", null);
	}

	public static void showMsg(String msg, String fullMsg) {
		new Thread(() -> {
			try {
				while (Controller.isActive(null)) Thread.sleep(200);
			} catch (Exception e) {
			}
			Snackbar.make(currentActivity.getWindow().getDecorView().getRootView(),
					msg, Snackbar.LENGTH_LONG).setAction(activity.getString(R.string.action_full_text),
					v -> new TextPopup(msg, fullMsg)).setTextColor(0xFFEEEEEE).show();
		}, "Showing msg").start();
	}

	@Override
	public GeneralPath getDefaultSubjectsDir() {
		return new FilePath(new File(CONTEXT.getExternalFilesDir(
				null).getAbsolutePath(), "School objects"));
	}

	@Override
	public OutputStream outputInternal(GeneralPath file, boolean append) throws IOException {
		return CONTEXT.openFileOutput(file.getName(),
				append ? Context.MODE_APPEND : Context.MODE_PRIVATE);
	}

	@Override
	public InputStream inputInternal(GeneralPath file) throws IOException {
		return CONTEXT.openFileInput(file.getName());
	}

	@Override
	public GeneralPath createGeneralPath(Object path) {
		return createGeneralPath(path, false);
	}

	@Override
	public GeneralPath createGeneralPath(Object path, boolean internal) {
		return path instanceof Uri ? null : path instanceof DocumentFile documentFile ?
				new UriPath(documentFile, internal) : !(path instanceof File) && path.toString().contains(":")
				? new UriPath(path.toString()) : super.createGeneralPath(path, internal);
	}
}
