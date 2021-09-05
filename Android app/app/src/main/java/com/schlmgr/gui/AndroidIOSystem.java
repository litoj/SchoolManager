package com.schlmgr.gui;

import android.Manifest.permission;
import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.os.Build.VERSION;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;

import com.google.android.material.snackbar.Snackbar;
import com.schlmgr.BuildConfig;
import com.schlmgr.R;
import com.schlmgr.gui.list.HierarchyItemModel;
import com.schlmgr.gui.popup.TextPopup;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import IOSystem.FilePath;
import IOSystem.Formatter;
import IOSystem.SimpleReader;
import IOSystem.SimpleWriter;
import objects.MainChapter;
import objects.Reference;
import objects.templates.ContainerFile;

import static IOSystem.Formatter.defaultReacts;
import static IOSystem.Formatter.getStackTrace;
import static IOSystem.Formatter.putSetting;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.widget.Toast.makeText;
import static com.schlmgr.gui.Controller.CONTEXT;
import static com.schlmgr.gui.Controller.activity;
import static com.schlmgr.gui.Controller.currentActivity;
import static com.schlmgr.gui.Controller.translate;
import static com.schlmgr.gui.fragments.MainFragment.STORAGE_PERMISSION;

public class AndroidIOSystem extends Formatter.IOSystem {

	public static String defDir;
	public static String storageDir;
	private static boolean canWrite;
	private static AndroidIOSystem ios;

	public static boolean canWrite() {
		if (!canWrite && VERSION.SDK_INT >= 23 && PERMISSION_GRANTED !=
				ContextCompat.checkSelfPermission(CONTEXT, permission.WRITE_EXTERNAL_STORAGE)) {
			activity.requestPermissions
					(new String[]{permission.WRITE_EXTERNAL_STORAGE}, STORAGE_PERMISSION);
			try {
				synchronized (ios) {
					ios.wait();
				}
			} catch (Exception e) {
			}
			return canWrite;
		} else return canWrite = true;
	}

	public static boolean testWrite() {
		if (!canWrite) {
			if (VERSION.SDK_INT >= 23 && PERMISSION_GRANTED != ContextCompat.checkSelfPermission(
					CONTEXT, permission.WRITE_EXTERNAL_STORAGE)) return false;
			else return canWrite = true;
		} else return true;
	}

	/**
	 * @return {@code true} if it already can wrote, otherwise {@code false} and requests permission
	 */
	public static boolean requestWrite() {
		if (!canWrite) {
			if (VERSION.SDK_INT >= 23 && PERMISSION_GRANTED !=
					ContextCompat.checkSelfPermission(CONTEXT, permission.WRITE_EXTERNAL_STORAGE)) {
				activity.requestPermissions(
						new String[]{permission.WRITE_EXTERNAL_STORAGE}, STORAGE_PERMISSION);
				return false;
			} else return canWrite = true;
		} else return true;
	}

	public static void setCanWrite(boolean canWrite) {
		AndroidIOSystem.canWrite = canWrite;
		synchronized (ios) {
			ios.notifyAll();
		}
	}

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
		super(new File(CONTEXT.getFilesDir(), "settings.dat"));
		ios = (AndroidIOSystem) Formatter.getIOSystem();
	}

	@Override
	protected void setDefaults(boolean first) {
		if (first) {
			settings.put("doShowDesc", false);
			settings.put("doChoosePos", false);
			settings.put("defaultTestTypePicture", false);
			settings.put("flipWord", true);
			settings.put("flipAllOnClick", false);
			settings.put("parseNames", true);
			settings.put("version", BuildConfig.VERSION_CODE);
		} else {
			Object value;
			if ((value = settings.get("version")) == null //old version update compatibility ensure
					|| BuildConfig.VERSION_CODE > (Integer) value) {
				int lastVersion = (Integer) value;
				/*0if (lastVersion < 30) {
					defaultReacts.put("removeSchNames", moreInfo -> {
						for (MainChapter mch : MainChapter.ELEMENTS) mch.removeSetting("schNameCount");
					});
				}
				if (lastVersion < 36) settings.put("doChoosePos", false);
				if (lastVersion < 40) settings.put("doShowDesc", false);*/
				settings.put("version", BuildConfig.VERSION_CODE);
			}

			HierarchyItemModel.defFlip = (Boolean) settings.get("flipWord");
			HierarchyItemModel.flipAllOnClick = (Boolean) settings.get("flipAllOnClick");
			HierarchyItemModel.parse = (Boolean) settings.get("parseNames");
			HierarchyItemModel.show_desc = (Boolean) settings.get("doShowDesc");
		}
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
			putSetting("uncaughtException", new Object[]{o[1], fullMsg});
			activity.runOnUiThread(() -> makeText(CONTEXT,
					activity.getString(R.string.exception_warning), Toast.LENGTH_LONG).show());
			if (BuildConfig.DEBUG) Log.e("Unexpected failure", fullMsg);
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
	public OutputStream outputInternal(GeneralPath file) throws IOException {
		return CONTEXT.openFileOutput(file.getName(), Context.MODE_PRIVATE);
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
		return path instanceof Uri ? new UriPath((Uri) path, internal) : path instanceof DocumentFile ?
				new UriPath((DocumentFile) path, internal) : !(path instanceof File) && path.toString().contains(":")
				? new UriPath(path.toString(), internal) : super.createGeneralPath(path, internal);
	}
}
