package com.schlmgr.gui.list;

import android.os.Build.VERSION;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.schlmgr.gui.list.AbstractNestAdapter.ViewHolder;

import java.util.List;

public abstract class AbstractNestAdapter<I, H extends ViewHolder>
		extends OpenListAdapter<I, H> implements OnTouchListener {
	RecyclerView container;
	ScrollView sv;

	protected AbstractNestAdapter(List<I> items) {
		super(items);
	}

	protected void update(RecyclerView parent, ScrollView layout) {
		container = parent;
		sv = layout;
		if (VERSION.SDK_INT < 21) parent.setOnTouchListener(this);
		parent.setAdapter(this);
		parent.setLayoutManager(new LinearLayoutManager(parent.getContext()));
	}

	public static abstract class ViewHolder extends RecyclerView.ViewHolder {

		public ViewHolder(@NonNull View itemView) {
			super(itemView);
		}

		protected abstract void setData(int pos);
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
}
