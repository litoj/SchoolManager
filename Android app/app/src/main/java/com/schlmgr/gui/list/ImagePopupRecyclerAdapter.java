package com.schlmgr.gui.list;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build.VERSION;
import android.provider.MediaStore.Images.Media;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.schlmgr.R;
import com.schlmgr.gui.list.ImagePopupRecyclerAdapter.Image;
import com.schlmgr.gui.list.ImagePopupRecyclerAdapter.ImageHolder;
import com.schlmgr.gui.popup.CreatorPopup;
import com.schlmgr.gui.popup.FullPicture;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import IOSystem.Formatter.Data;
import objects.MainChapter;
import objects.Picture;
import objects.templates.Container;
import objects.templates.TwoSided;

import static IOSystem.Formatter.defaultReacts;
import static com.schlmgr.gui.Controller.dp;
import static com.schlmgr.gui.CurrentData.backLog;
import static com.schlmgr.gui.fragments.MainFragment.IMAGE_PICK;
import static com.schlmgr.gui.fragments.MainFragment.VS;
import static com.schlmgr.gui.list.ImageItemModel.getScaledBitmap;

/**
 * Used for creating and editing images in {@link CreatorPopup}.
 */
public class ImagePopupRecyclerAdapter
		extends AbstractPopupRecyclerAdapter<Image, ImageHolder, Picture> {

	public static class Image {

		public final Picture twosided;
		public final File f;
		public final Bitmap bm;

		public Image(File file) {
			f = file;
			bm = getScaledBitmap(f.getAbsolutePath(), 150 * dp);
			twosided = null;
		}

		public Image(Picture pic) {
			f = (twosided = pic).getFile();
			bm = getScaledBitmap(pic, 150 * dp);
		}
	}

	@Override
	public Image createItem(TwoSided src) {
		return new Image((Picture) src);
	}

	class ImageHolder extends AbstractPopupRecyclerAdapter.ViewHolder {

		ImageView image;
		Image item;

		public ImageHolder(@NonNull View itemView) {
			super(itemView);
			image = itemView.findViewById(R.id.item_img);
			itemView.setOnClickListener(v -> new FullPicture(item.f));
			if (VERSION.SDK_INT < 21) itemView.setOnTouchListener(ImagePopupRecyclerAdapter.this);
		}

		@Override
		public void setData(int pos) {
			item = list.get(pos);
			name.setText(item.f.getName());
			image.setImageBitmap(item.bm);
			remove.setOnClickListener(v -> {
				int index = list.indexOf(item);
				if (index < 0 || index >= list.size()) return;
				if (item.twosided != null) toRemove.add(item.twosided);
				removeItem(index);
			});
		}
	}

	public ImagePopupRecyclerAdapter(HierarchyItemModel edited, CreatorPopup cp) {
		super(edited, cp, 2);
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
			String desc = cp.et_desc.getText().toString().replace("\\t", "\t");
			MainChapter mch = (MainChapter) backLog.path.get(0);
			LinkedList<Data> images = new LinkedList<>();
			for (Image i : list)
				if (i.twosided == null) {
					Map<String, Object> map = new HashMap<>();
					map.put("imageRender", i.bm);
					if (edited != null)
						Picture.mkImage(new Data(i.f.getPath(), mch).addPar(parent).addExtra(map),
								(Picture) edited.bd);
					else images.add(new Data(i.f.getAbsolutePath(), mch).addPar(parent).addExtra(map));
				}
			if (edited == null) {
				Picture p = Picture.mkElement(new Data(name, mch).addDesc(desc).addPar(parent), images);
				backLog.adapter.addItem(new HierarchyItemModel(p, parent, backLog.adapter.list.size() + 1));
				parent.putChild((Container) backLog.path.get(-2), p);
			} else {
				for (Picture p : toRemove) ((Picture) edited.bd).removeChild(parent, p);
				edited.bd.putDesc(parent, desc);
				edited.bd = edited.bd.setName(parent, name);
			}
			toRemove = null;
		};
	}
}
