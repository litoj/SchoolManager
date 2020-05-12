package com.schlmgr.gui.list;

import android.content.Intent;
import android.graphics.Bitmap;
import android.provider.MediaStore.Images.Media;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.schlmgr.R;
import com.schlmgr.gui.list.ImageRecyclerAdapter.Image;
import com.schlmgr.gui.list.ImageRecyclerAdapter.ImageHolder;
import com.schlmgr.gui.popup.CreatorPopup;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import IOSystem.Formatter.Data;
import objects.MainChapter;
import objects.Picture;
import objects.templates.Container;
import objects.templates.TwoSided;

import static IOSystem.Formatter.defaultReacts;
import static com.schlmgr.gui.CurrentData.backLog;
import static com.schlmgr.gui.fragments.MainFragment.IMAGE_PICK;
import static com.schlmgr.gui.fragments.MainFragment.VS;
import static com.schlmgr.gui.fragments.MainFragment.es;
import static com.schlmgr.gui.list.ImageItemModel.getScaledBitmap;

public class ImageRecyclerAdapter extends NestedRecyclerAdapter<Image, ImageHolder> {

	public static class Image {

		public final Picture twosided;
		public final File f;
		public final Bitmap bm;

		public Image(File file) {
			f = file;
			bm = getScaledBitmap(f.getAbsolutePath());
			twosided = null;
		}

		public Image(Picture pic) {
			f = (twosided = pic).getFile();
			bm = getScaledBitmap(pic);
		}
	}

	@Override
	public Image createItem(TwoSided src) {
		return new Image((Picture) src);
	}

	class ImageHolder extends NestedRecyclerAdapter.ViewHolder {

		ImageView image;

		public ImageHolder(@NonNull View itemView) {
			super(itemView);
			image = view.findViewById(R.id.item_img);
		}

		@Override
		public void setData(int pos) {
			Image item = list.get(pos);
			name.setText(item.f.getName());
			image.setImageBitmap(item.bm);
			remove.setOnClickListener(v -> {
				if (item.twosided != null) toRemove.add(item.twosided);
				removeItem(list.indexOf(item));
			});
		}
	}

	public List<Picture> toRemove = new ArrayList<>();

	public ImageRecyclerAdapter(HierarchyItemModel edited, CreatorPopup cp) {
		super(edited, cp);
		defaultReacts.put("NotifyNewImage", (o) -> {
			File file = ((File) o[0]);
			if (!file.exists()) return;
			for (Image img : list) if (img.f.equals(file)) return;
			addItem(new Image(file));
		});
	}

	@NonNull
	@Override
	public ImageHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		return new ImageHolder(LayoutInflater.from(parent.getContext())
				.inflate(R.layout.item_add_image, parent, false));
	}

	public Runnable onClick(LinearLayout ll) {
		super.onClick(ll);
		TextView tv = ll.findViewById(R.id.new_add);
		tv.setText(R.string.add_picture);
		tv.setOnClickListener(v -> VS.mfInstance.startActivityForResult(
				new Intent(Intent.ACTION_PICK, Media.EXTERNAL_CONTENT_URI), IMAGE_PICK));
		return () -> {
			String name = cp.et_name.getText().toString();
			if (name.isEmpty() || list.isEmpty()) return;
			String desc = cp.et_desc.getText().toString();
			MainChapter mch = (MainChapter) backLog.path.get(0);
			LinkedList<Data> images = new LinkedList<>();
			for (Image i : list)
				if (i.twosided == null) {
					if (edited != null)
						Picture.mkImage(new Data(i.f.getPath(), mch).addPar(parent).addExtra(i.bm),
								(Picture) edited.bd);
					else images.add(new Data(i.f.getAbsolutePath(), mch).addPar(parent).addExtra(i.bm));
				}
			if (edited == null) {
				Picture p = Picture.mkElement(new Data(name, mch).addDesc(desc).addPar(parent), images);
				VS.aa.add(new HierarchyItemModel(p, parent, es.lv.getCount() + 1));
				parent.putChild((Container) backLog.path.get(-2), p);
			} else {
				for (Picture p : toRemove) ((Picture) edited.bd).removeChild(parent, p);
				edited.bd.putDesc(parent, cp.et_desc.getText().toString());
				if (!edited.bd.getName().equals(name) && edited.bd.setName(parent, name)) {
					for (Picture p : Picture.ELEMENTS.get(mch))
						if (name.equals(p.getName())) {
							edited.bd = p;
							break;
						}
				}
			}
			toRemove = null;
		};
	}
}
