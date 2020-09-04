package com.schlmgr.gui.list;

import android.os.Build.VERSION;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.schlmgr.R;
import com.schlmgr.gui.list.TranslatePopupRecyclerAdapter.Translate;
import com.schlmgr.gui.list.TranslatePopupRecyclerAdapter.TranslateHolder;
import com.schlmgr.gui.popup.CreatorPopup;

import java.util.LinkedList;

import IOSystem.Formatter.Data;
import IOSystem.SimpleReader;
import objects.MainChapter;
import objects.Word;
import objects.templates.Container;
import objects.templates.TwoSided;

import static com.schlmgr.gui.CurrentData.backLog;

/**
 * Used for creating and editing translates in {@link CreatorPopup}.
 */
public class TranslatePopupRecyclerAdapter
		extends AbstractPopupRecyclerAdapter<Translate, TranslateHolder, Word> {
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

	class TranslateHolder extends AbstractPopupRecyclerAdapter.ViewHolder {

		TextView desc;
		Translate item;

		public TranslateHolder(@NonNull View itemView) {
			super(itemView);
			desc = itemView.findViewById(R.id.item_desc);
			if (VERSION.SDK_INT < 21) {
				name.setOnTouchListener(TranslatePopupRecyclerAdapter.this);
				desc.setOnTouchListener(TranslatePopupRecyclerAdapter.this);
			}
		}

		@Override
		public void setData(int pos) {
			if (item != null) {
				item.name = name.getText().toString();
				item.desc = desc.getText().toString();
				item.tvName = null;
				item.tvDesc = null;
			}
			item = list.get(pos);
			name.setText(item.tvName == null ? item.name : item.tvName.getText());
			desc.setText(item.tvDesc == null ? item.desc : item.tvDesc.getText());
			item.tvName = name;
			item.tvDesc = desc;
			remove.setOnClickListener(v -> {
				name.setFocusable(false);
				desc.setFocusable(false);
				int index = list.indexOf(item);
				if (index < 0 || index >= list.size()) return;
				if (item.twosided != null) toRemove.add(item.twosided);
				removeItem(index);
			});
		}
	}

	public TranslatePopupRecyclerAdapter(HierarchyItemModel edited, CreatorPopup cp) {
		super(edited, cp, 5);
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
				if (trls[0].length() == 0) return;
				String[] trlDescs = SimpleReader.nameResolver(item.tvDesc == null
						? item.desc : item.tvDesc.getText().toString());
				if (edited == null) {
					for (int i = 0; i < trls.length; i++)
						translates.add(new Data(trls[i], mch).addDesc(i < trlDescs.length
								? trlDescs[i].replace("\\t", "\t") : null).addPar(parent));
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
				int pos = cp.np.getValue();
				for (int i = 0; i < names.length; i++) {
					d.name = names[i];
					d.description = i < descs.length ? descs[i].replace("\\t", "\t") : null;
					Word w = Word.mkElement(d, translates);
					backLog.adapter.addItem(pos + i - 1,
							new HierarchyItemModel(w, parent, pos + i));
					parent.putChild((Container) backLog.path.get(-2), w, pos + i - 1);
				}
			} else {
				for (Word w : toRemove) ((Word) edited.bd).removeChild(parent, w);
				edited.bd.putDesc(parent, cp.et_desc.getText().toString().replace("\\t", "\t"));
				edited.bd = edited.bd.setName(parent, name);
			}
			toRemove = null;
		};
	}
}
