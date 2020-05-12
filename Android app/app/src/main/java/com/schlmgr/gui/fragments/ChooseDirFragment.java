package com.schlmgr.gui.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.schlmgr.R;
import com.schlmgr.gui.Controller;
import com.schlmgr.gui.activity.SelectDirActivity;

public class ChooseDirFragment extends Fragment {

	boolean on = false;

	public View onCreateView(@NonNull LayoutInflater inflater,
	                         ViewGroup container, Bundle savedInstanceState) {
		SelectDirActivity.importing = false;
		startActivity(new Intent(getContext(), SelectDirActivity.class));
		on = true;
		return null;
	}

	@Override
	public void onResume() {
		super.onResume();
		if (on) on = false;
		else Controller.defaultBack.run();
	}
}