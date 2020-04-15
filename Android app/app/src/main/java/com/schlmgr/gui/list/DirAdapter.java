package com.schlmgr.gui.list;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.schlmgr.R;
import com.schlmgr.gui.AndroidIOSystem;
import com.schlmgr.gui.list.DirAdapter.DirItemModel;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static com.schlmgr.gui.Controller.activity;
import static com.schlmgr.gui.activity.MainActivity.ic_check_empty;
import static com.schlmgr.gui.activity.MainActivity.ic_check_filled;

public class DirAdapter extends ArrayAdapter<DirItemModel> {
	private LayoutInflater li;
	public int selected;
	public List<DirItemModel> list;
	private final Runnable occ;

	public static String external;
	public static String internal;
	public static String usbotg;

	public static List<DirItemModel> convert(List<File> dirs) {
		List<DirItemModel> list = new ArrayList<>();
		if (dirs != null) {
			for (File f : dirs) list.add(new DirItemModel(f, f.getName()));
			return list;
		}
		File[] files = new File(AndroidIOSystem.storageDir).listFiles();
		if (files.length == 1) {
			list.add(new DirItemModel(files[0], internal));
		} else {
			DirItemModel suspicious = null;
			boolean foundInternal = false;
			for (File f : files) {
				DirItemModel item = new DirItemModel(f, f.getName());
				try {
					if (f.listFiles() == null) continue;
					if (f.getName().equals("emulated") && f.listFiles().length > 0)
						item.f = f.listFiles()[0];
					list.add(item);
				} catch (Exception e) {
					continue;
				}
				switch (f.getName()) {
					case "usbotg":
						item.name = usbotg;
						break;
					case "emulated":
					case "sdcard0":
					case "external0":
					case "0":
						if (foundInternal) {
							list.remove(f);
							break;
						}
						foundInternal = true;
						item.name = internal;
						break;
					case "sdcard1":
					case "external1":
					case "ext":
						item.name = external;
						break;
					case "sdcard":
					case "external":
						suspicious = item;
						list.remove(item);
				}
			}
			if (suspicious != null)
				if (!foundInternal) {
					suspicious.name = internal;
					list.add(suspicious);
				} else {
					test:
					{
						for (DirItemModel dim : list) if (dim.name.equals(external)) break test;
						suspicious.name = external;
						list.add(suspicious);
					}
				}
		}
		for (DirItemModel dim : list) if (dim.name.equals(internal)) return list;
		list.add(new DirItemModel(new File(AndroidIOSystem.defDir), internal));
		return list;
	}

	public DirAdapter(@NonNull Context context, @NonNull List<DirItemModel> objects, boolean storage, Runnable onClickCheck) {
		super(context, R.layout.item_dir, objects);
		occ = onClickCheck;
		li = LayoutInflater.from(context);
		list = objects;
	}

	public static String storageName(File f) {
		if (external == null) {
			external = activity.getString(R.string.storage_external);
			internal = activity.getString(R.string.storage_internal);
			usbotg = activity.getString(R.string.storage_usbotg);
		}
		File[] files = new File(AndroidIOSystem.storageDir).listFiles();
		if (files.length == 1 || f.getName().equals("0"))
			return internal;
		Boolean ext = null;
		File suspicious = null;
		for (File file : files) {
			switch (file.getName()) {
				case "usbotg":
					if (file.equals(f)) return usbotg;
					break;
				case "sdcard0":
				case "external0":
				case "emulated":
					if (file.equals(f)) return internal;
					if (ext == null) ext = false;
					break;
				case "sdcard1":
				case "external1":
				case "ext":
					if (file.equals(f)) return external;
					ext = true;
					break;
				case "sdcard":
				case "external":
					if (file.equals(f)) {
						if (ext == null) suspicious = file;
						else return ext ? internal : external;
					}
					break;
				default:
					if (file.equals(f)) return file.getName();
			}
		}
		if (suspicious != null) return ext ? internal : external;
		return f.getName();
	}

	public View getView(int index, View view, ViewGroup parent) {
		if (view == null) view = li.inflate(R.layout.item_dir, parent, false);
		DirItemModel item = list.get(index);
		((TextView) view.findViewById(R.id.item_name)).setText(item.name);
		if (item.v == null) (item.v = view.findViewById(R.id.item_selected)).setOnClickListener(v -> {
			item.setSelected(!item.selected);
			selected += item.selected ? 1 : -1;
			occ.run();
		});
		return view;
	}

	public static class DirItemModel {
		public File f;
		public String name;
		public boolean selected;
		public ImageView v;

		DirItemModel(File file, String name) {
			f = file;
			this.name = name;
		}

		public void setSelected(boolean checked) {
			v.setImageDrawable((selected = checked) ? ic_check_filled : ic_check_empty);
		}
	}
}
