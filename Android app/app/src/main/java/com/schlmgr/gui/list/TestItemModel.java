package com.schlmgr.gui.list;

import android.view.View;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import objects.Picture;
import objects.templates.Container;
import objects.templates.TwoSided;
import testing.Test;
import testing.Test.SrcPath;

import static com.schlmgr.gui.Controller.dp;
import static com.schlmgr.gui.fragments.TestFragment.picTest;

public class TestItemModel {
	public View v;
	public final Test.SrcPath sp;
	public final Container par;
	public List<TwoSided> children;
	public ImageItemModel iim; //for images only

	public boolean correct;
	public String answer;

	public TestItemModel(SrcPath srcPath) {
		sp = srcPath;
		par = (Container) sp.srcPath.get(sp.srcPath.size() - 2);
		children = new ArrayList<>(Arrays.asList(sp.t.getChildren(par)));
		if (picTest) {
			if (children.size() > 2) {
				Collections.shuffle(children);
				children = children.subList(0, 2);
				iim = new ImageItemModel((Picture) children.get(0),
						(Picture) children.get(1), 50 * dp);
			} else iim = new ImageItemModel((Picture) children.get(0), null, 50 * dp);
		}
	}

	@NonNull
	@Override
	public String toString() {
		return sp.t.toString();
	}
}