package com.schlmgr.gui.list;

import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.schlmgr.R;
import com.schlmgr.gui.list.AbstractPopupRecyclerAdapter.ViewHolder;
import com.schlmgr.gui.popup.CreatorPopup;

import java.util.ArrayList;
import java.util.List;

import objects.templates.Container;
import objects.templates.TwoSided;

import static com.schlmgr.gui.Controller.dp;
import static com.schlmgr.gui.CurrentData.backLog;

public abstract class AbstractPopupRecyclerAdapter<T, H extends ViewHolder, E extends TwoSided>
		extends AbstractNestAdapter<T, H> implements OnTouchListener {

	final CreatorPopup cp;
	final HierarchyItemModel edited;
	final Container parent;
	final int maxVisibleItemCount;

	public abstract T createItem(TwoSided src);

	@Override
	public void addItem(T item) {
		list.add(0, item);
		container.post(() -> {
			if (list.size() > maxVisibleItemCount)
				container.setLayoutParams(
						new LayoutParams(LayoutParams.MATCH_PARENT, (int) (360 * dp)));
			notifyDataSetChanged();
		});
	}

	@Override
	public void removeItem(int pos) {
		list.remove(pos);
		container.post(() -> {
			if (list.size() < maxVisibleItemCount)
				container.setLayoutParams(
						new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
			notifyItemRemoved(pos);
			notifyItemRangeChanged(pos, list.size());
		});
	}

	public static abstract class ViewHolder extends AbstractNestAdapter.ViewHolder {

		TextView name;
		View remove;

		public ViewHolder(@NonNull View itemView) {
			super(itemView);
			name = itemView.findViewById(R.id.item_name);
			remove = itemView.findViewById(R.id.btn_remove);
		}

		protected abstract void setData(int pos);
	}

	public List<E> toRemove = new ArrayList<>();

	protected AbstractPopupRecyclerAdapter(HierarchyItemModel him,
	                                       CreatorPopup cp, int maxVisibleItems) {
		super(new ArrayList<>());
		this.cp = cp;
		if ((edited = him) != null) {
			parent = him.parent;
			for (TwoSided trl : ((TwoSided) edited.bd).getChildren(parent))
				list.add(0, createItem(trl));
			cp.et_name.setText(edited.bd.getName());
			cp.et_desc.setText(edited.bd.getDesc(parent));
		} else parent = (Container) backLog.path.get(-1);
		maxVisibleItemCount = maxVisibleItems;
	}

	/**
	 * This method must be called when {@code param ll} is inflated.
	 *
	 * @param ll the parent of the adapted RecyclerView
	 * @return the part of the clicking action
	 */
	public Runnable onClick(LinearLayout ll) {
		update(ll.findViewById(R.id.item_adder_list), cp.view.findViewById(R.id.popup_new_scroll));
		if (list.size() > maxVisibleItemCount) {
			container.post(() -> container.setLayoutParams(
					new LayoutParams(LayoutParams.MATCH_PARENT, (int) (360 * dp))));
		}
		return null;
	}
}
