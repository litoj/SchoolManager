package com.schlmgr.gui.list;

import android.os.Build.VERSION;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.schlmgr.R;
import com.schlmgr.gui.list.TranslateRecyclerAdapter.Translate;
import com.schlmgr.gui.list.TranslateRecyclerAdapter.TranslateHolder;
import com.schlmgr.gui.popup.CreatorPopup;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import IOSystem.Formatter.Data;
import IOSystem.SimpleReader;
import objects.MainChapter;
import objects.Word;
import objects.templates.Container;
import objects.templates.TwoSided;

import static com.schlmgr.gui.CurrentData.backLog;
import static com.schlmgr.gui.fragments.MainFragment.VS;
import static com.schlmgr.gui.fragments.MainFragment.es;

/**
 * Used for creating and editing translates in {@link CreatorPopup}.
 */
public class TranslateRecyclerAdapter extends AbstractRecyclerAdapter<Translate, TranslateHolder> {
	public static class Translate {
		public final Word twosided;
		public TextView tvName, tvDesc;
		public String name, desc;

		public Translate() {
			twosided = null;
			name = "";
			desc = "";
		}

		public Translate(Word trl, String desc) {
			twosided = trl;
			name = trl.getName();
			this.desc = desc;
		}
	}

	@Override
	public Translate createItem(TwoSided src) {
		return new Translate((Word) src, src.getDesc(parent));
	}

	class TranslateHolder extends AbstractRecyclerAdapter.ViewHolder {

		TextView desc;
		Translate currentItem;

		public TranslateHolder(@NonNull View itemView) {
			super(itemView);
			desc = view.findViewById(R.id.item_desc);
			if (VERSION.SDK_INT < 21) {
				name.setOnTouchListener(TranslateRecyclerAdapter.this);
				desc.setOnTouchListener(TranslateRecyclerAdapter.this);
			}
		}

		@Override
		public void setData(int pos) {
			if (currentItem != null) {
				currentItem.name = name.getText().toString();
				currentItem.desc = desc.getText().toString();
				currentItem.tvName = null;
				currentItem.tvDesc = null;
			}
			currentItem = list.get(pos);
			name.setText(currentItem.tvName == null ? currentItem.name : currentItem.tvName.getText());
			desc.setText(currentItem.tvDesc == null ? currentItem.desc : currentItem.tvDesc.getText());
			currentItem.tvName = name;
			currentItem.tvDesc = desc;
			remove.setOnClickListener(v -> {
				if (currentItem.twosided != null) toRemove.add(currentItem.twosided);
				removeItem(list.indexOf(currentItem));
			});
		}
	}

	public List<Word> toRemove = new ArrayList<>();

	public TranslateRecyclerAdapter(HierarchyItemModel edited, CreatorPopup cp) {
		super(edited, cp);
	}

	@NonNull
	@Override
	public TranslateHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		return new TranslateHolder(LayoutInflater.from(parent.getContext())
				.inflate(R.layout.item_add_translate, parent, false));
	}

	public Runnable onClick(LinearLayout ll) {
		super.onClick(ll);
		TextView tv = ll.findViewById(R.id.new_add);
		tv.setText(R.string.add_word);
		tv.setOnClickListener(v -> addItem(new Translate()));
		return () -> {
			String name = cp.et_name.getText().toString();
			if (name.isEmpty() || list.isEmpty()) return;
			MainChapter mch = (MainChapter) backLog.path.get(0);
			LinkedList<Data> translates = new LinkedList<>();
			for (Translate item : list) {
				String[] trls = SimpleReader.nameResolver(item.tvName == null
						? item.name : item.tvName.getText().toString());
				String[] trlDescs = SimpleReader.nameResolver(item.tvDesc == null
						? item.desc : item.tvDesc.getText().toString());
				if (edited == null) {
					for (int i = 0; i < trls.length; i++)
						translates.add(new Data(trls[i], mch)
								.addDesc(i < trlDescs.length ? trlDescs[i] : null).addPar(parent));
				} else if (item.twosided != null && trls.length == 1) {
					item.twosided.putDesc(parent, trlDescs[0]);
					item.twosided.setName(parent, trls[0]);
				} else for (int i = 0; i < trls.length; i++)
					Word.mkTranslate(new Data(trls[i], mch).addDesc(i < trlDescs.length
							? trlDescs[i] : null).addPar(parent), (Word) edited.bd);
			}
			if (edited == null) {
				String[] names = SimpleReader.nameResolver(name);
				String[] descs = SimpleReader.nameResolver(cp.et_desc.getText().toString());
				Data d = new Data(null, mch).addPar(parent);
				for (int i = 0; i < names.length; i++) {
					d.name = names[i];
					d.description = i < descs.length ? descs[i] : null;
					Word w = Word.mkElement(d, translates);
					VS.mAdapter.add(new HierarchyItemModel(w, parent, es.lv.getCount() + 1));
					parent.putChild((Container) backLog.path.get(-2), w);
				}
			} else {
				for (Word w : toRemove) ((Word) edited.bd).removeChild(parent, w);
				edited.bd.putDesc(parent, cp.et_desc.getText().toString());
				edited.bd = edited.bd.setName(parent, name);
			}
			toRemove = null;
		};
	}
}
