package com.schlmgr.gui.list;

import android.os.Build.VERSION;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.schlmgr.R;
import com.schlmgr.gui.list.NestedRecyclerAdapter.ViewHolder;
import com.schlmgr.gui.popup.CreatorPopup;

import java.util.ArrayList;
import java.util.List;

import objects.templates.Container;
import objects.templates.TwoSided;

import static com.schlmgr.gui.CurrentData.backLog;

public abstract class NestedRecyclerAdapter<T, H extends ViewHolder> extends RecyclerView.Adapter<H>
		implements OnTouchListener {
	public List<T> list;
	RecyclerView container;
	ScrollView sv;

	final CreatorPopup cp;
	final HierarchyItemModel edited;
	final Container parent;

	public abstract T createItem(TwoSided src);

	public static abstract class ViewHolder extends RecyclerView.ViewHolder {

		View view;
		TextView name;
		View remove;

		public ViewHolder(@NonNull View itemView) {
			super(itemView);
			view = itemView;
			name = view.findViewById(R.id.item_name);
			remove = view.findViewById(R.id.btn_remove);
		}

		protected abstract void setData(int pos);
	}

	public NestedRecyclerAdapter(HierarchyItemModel him, CreatorPopup cp) {
		list = new ArrayList<>();
		this.cp = cp;
		if ((edited = him) != null) {
			parent = him.parent;
			for (TwoSided trl : ((TwoSided) edited.bd).getChildren(parent))
				list.add(0, createItem(trl));
			cp.et_name.setText(edited.bd.getName());
			cp.et_desc.setText(edited.bd.getDesc(parent));
		} else parent = (Container) backLog.path.get(-1);
	}

	public void addItem(T item) {
		list.add(0, item);
		container.post(() -> {
			notifyDataSetChanged();
		});
	}

	public Runnable onClick(LinearLayout ll) {
		RecyclerView rv = ll.findViewById(R.id.item_adder_list);
		sv = cp.view.findViewById(R.id.popup_new_scroll);
		container = rv;
		if (VERSION.SDK_INT < 21) rv.setOnTouchListener(this);
		rv.setAdapter(this);
		rv.setLayoutManager(new LinearLayoutManager(rv.getContext()));
		return null;
	}

	float y;
	boolean first;

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
						container.computeVerticalScrollOffset() + container.computeVerticalScrollExtent())) {
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

	void removeItem(int pos) {
		list.remove(pos);
		notifyItemRemoved(pos);
		notifyItemRangeChanged(pos, list.size());
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
