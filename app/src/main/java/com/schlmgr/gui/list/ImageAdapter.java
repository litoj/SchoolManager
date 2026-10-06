package com.schlmgr.gui.list;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.schlmgr.R;
import com.schlmgr.gui.popup.FullPicture;

import java.util.List;

public class ImageAdapter extends OpenListAdapter<ImageItemModel, ImageAdapter.ImageItemHolder> {

	public ImageAdapter(RecyclerView rv, @NonNull List<ImageItemModel> objects) {
		super(objects);
		update(rv);
	}

	@NonNull
	@Override
	public ImageItemHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		return new ImageItemHolder(LayoutInflater.from(
				parent.getContext()).inflate(R.layout.item_image, parent, false));
	}

	public static class ImageItemHolder extends RecyclerView.ViewHolder {

		private final ImageView iv1, iv2;
		private ImageItemModel item;

		public ImageItemHolder(@NonNull View itemView) {
			super(itemView);
			iv1 = itemView.findViewById(R.id.item_img_1);
			iv1.setOnClickListener((v) -> new FullPicture(item.pic1));
			iv2 = itemView.findViewById(R.id.item_img_2);
			iv2.setOnClickListener((v) -> new FullPicture(item.pic2));
		}
	}

	@Override
	public void onBindViewHolder(@NonNull ImageItemHolder holder, int pos) {
		holder.item = list.get(pos);
		(holder.item.iv1 = holder.iv1).setImageBitmap(holder.item.bm1);
		if (holder.item.pic2 != null) {
			holder.iv2.setVisibility(View.VISIBLE);
			(holder.item.iv2 = holder.iv2).setImageBitmap(holder.item.bm2);
		} else holder.iv2.setVisibility(View.GONE);
	}
}

