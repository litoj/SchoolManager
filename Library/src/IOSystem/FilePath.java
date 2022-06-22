/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package IOSystem;

import IOSystem.Formatter.IOSystem.GeneralPath;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 *
 * @author Josef Litoš
 */
public class FilePath implements GeneralPath {

	private File file;
	private String original;
	final private boolean internal;

	public FilePath(String original) {
		this(original, false);
	}

	public FilePath(String original, boolean internal) {
		this.original = original;
		file = new File(original);
		if (!file.getName().contains(".") && !file.exists()) {
			file.mkdirs();
		}
		this.internal = internal;
	}

	public FilePath(File src) {
		this(src, false);
	}

	public FilePath(File src, boolean internal) {
		this.original = src.getAbsolutePath();
		file = src;
		if (!file.getName().contains(".") && !src.exists()) {
			src.mkdirs();
		}
		this.internal = internal;
	}

	@Override
	public String getOriginalName() {
		return original;
	}

	@Override
	public String getName() {
		return file.getName();
	}

	@Override
	public OutputStream createOutputStream(boolean append) throws IOException {
		return internal ? Formatter.getIOSystem().outputInternal(this, append)
			 : new FileOutputStream(file, append);
	}

	@Override
	public InputStream createInputStream() throws IOException {
		return internal ? Formatter.getIOSystem().inputInternal(this) : new FileInputStream(file);
	}

	@Override
	public FilePath getChild(String name) {
		return new FilePath(new File(original, name));
	}
	
	@Override
	public boolean hasChild(String name) {
		File f = new File(original, name);
		return f.exists();
	}

	@Override
	public FilePath getParentDir() {
		return new FilePath(file.getParentFile().getPath());
	}

	@Override
	public boolean renameTo(String newName) {
		File newFile = new File(file.getParentFile(), newName);
		if (!newFile.exists() || newFile.isDirectory() && newFile.listFiles().length == 0) {
			newFile.delete();
			if (file.renameTo(newFile)) {
				file = newFile;
				original = file.getPath();
				return true;
			}
		}
		return false;
	}

	@Override
	public GeneralPath moveTo(GeneralPath newPath) {
		if (!newPath.isEmpty()) {
			return null;
		}
		if (newPath instanceof FilePath && !internal) {
			if (file.renameTo(((FilePath) newPath).file)) {
				file = ((FilePath) newPath).file;
				original = ((FilePath) newPath).original;
				return this;
			} else {
				return null;
			}
		}
		copyTo(newPath);
		delete();
		return newPath;
	}

	@Override
	public boolean delete() {
		return deleteFile(file);
	}

	protected boolean deleteFile(File src) {
		if (src.isDirectory()) {
			for (File f : src.listFiles()) {
				deleteFile(f);
			}
		}
		return src.delete();
	}

	@Override
	public boolean exists() {
		return file.exists();
	}

	@Override
	public boolean isDir() {
		return file.isDirectory();
	}

	@Override
	public boolean isEmpty() {
		return !file.exists() || file.isDirectory() && (file.listFiles() == null
			 || file.listFiles().length == 0);
	}

	@Override
	public boolean equals(GeneralPath comparison) {
		return file.getAbsolutePath().equals(comparison.getOriginalName());
	}

	@Override
	public FilePath[] listFiles() {
		File[] files = file.listFiles();
		if (files == null) return new FilePath[0];
		FilePath[] paths = new FilePath[files.length];
		for (int i = 0; i < files.length; i++) {
			paths[i] = new FilePath(files[i].getPath());
		}
		return paths;
	}

	@Override
	public String toString() {
		return file.getPath();
	}
}
