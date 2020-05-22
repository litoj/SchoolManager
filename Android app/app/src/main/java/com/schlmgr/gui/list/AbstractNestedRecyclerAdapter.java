package com.schlmgr.gui.list;

import android.os.Build.VERSION;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public abstract class AbstractNestedRecyclerAdapter<I,
		H extends AbstractNestedRecyclerAdapter.ViewHolder>
		extends RecyclerView.Adapter<H> implements OnTouchListener {
	public final List<I> list;
	public final RecyclerView container;
	public final ScrollView sv;

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

	public static abstract class ViewHolder extends RecyclerView.ViewHolder {

		public final View view;

		public ViewHolder(@NonNull View itemView) {
			super(itemView);
			view = itemView;
		}

		protected abstract void setData(int pos);
	}

	protected AbstractNestedRecyclerAdapter(List<I> items, RecyclerView rv, ScrollView firstScroll) {
		list = items;
		container = rv;
		sv = firstScroll;
		if (VERSION.SDK_INT < 21) rv.setOnTouchListener(this);
		rv.setAdapter(this);
		rv.setLayoutManager(new LinearLayoutManager(rv.getContext()));
	}

	float y;
	boolean first;

	/**
	 * Use on API < 21 to simulate nested scrolling.
	 */
	@Override
	public boolean onTouch(View v, MotionEvent event) {
		switch (event.getAction()) {
			case MotionEvent.ACTION_UP:
				sv.requestDisallowInterceptTouchEvent(false);
				break;
			case MotionEvent.ACTION_DOWN:
				sv.requestDisallowInterceptTouchEvent(true);
				first = true;
				break;
			case MotionEvent.ACTION_MOVE:
				if (!first) return false;
				if (y - event.getY() == 0) return true;
				first = false;
				if (y - event.getY() < 0 ? container.computeVerticalScrollOffset() <= 0
						: (container.computeVerticalScrollRange() <=
						container.computeVerticalScrollOffset()
								+ container.computeVerticalScrollExtent())) {
					sv.requestDisallowInterceptTouchEvent(false);
					y = event.getY();
					return true;
				} else sv.requestDisallowInterceptTouchEvent(true);
		}
		y = event.getY();
		if (v != container) {
			v.onTouchEvent(event);
			return false;
		}
		container.onTouchEvent(event);
		return true;
	}

	@Override
	public void onBindViewHolder(@NonNull H holder, int position) {
		holder.setData(position);
	}

	@Override
	public int getItemCount() {
		return list.size();
	}
}
