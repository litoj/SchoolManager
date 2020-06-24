package com.schlmgr.gui.list;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/**
 * The set of items in this adapter can be freely accessed.
 */
public abstract class OpenListAdapter<I, H extends RecyclerView.ViewHolder>
		extends RecyclerView.Adapter<H> {
	public final List<I> list;
	public RecyclerView container;

	public OpenListAdapter(List<I> items) {
		list = items;
	}

	public void update(RecyclerView parent) {
		container = parent;
		parent.setAdapter(this);
		parent.setLayoutManager(new LinearLayoutManager(parent.getContext()));
	}

	@Override
	public int getItemCount() {
		return list.size();
	}

	public void addItem(I item) {
		list.add(0, item);
		container.post(() -> notifyDataSetChanged());
	}
	
	public void removeItem(int pos) {
		list.remove(pos);
		container.post(() -> {
			notifyItemRemoved(pos);
			notifyItemRangeChanged(pos, list.size());
		});
	}
}
