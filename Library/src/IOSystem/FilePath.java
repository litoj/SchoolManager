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
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;

/**
 *
 * @author Josef Litoš
 */
public class FilePath implements GeneralPath {

	private File file;
	private String original;

	public FilePath(String original) {
		this.original = original;
		file = new File(Formatter.getIOSystem().mkRealPath(original));
		file.mkdirs();
		if (!original.contains(".") && !file.exists()) {
			file.mkdir();
		}
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
	public OutputStream createOutputStream() throws IOException {
		return new FileOutputStream(file);
	}

	@Override
	public InputStream createInputStream() throws IOException {
		return new FileInputStream(file);
	}

	@Override
	public void deserialize(Object toSave) {
		try ( ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file))) {
			oos.writeObject(toSave);
		} catch (IOException ex) {
			throw new IllegalArgumentException(ex);
		}
	}

	@Override
	public Object serialize() {
		try ( ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
			return ois.readObject();
		} catch (IOException | ClassNotFoundException ex) {
			throw new IllegalArgumentException(ex);
		}
	}

	@Override
	public FilePath getChild(String name) {
		return new FilePath(original + File.pathSeparatorChar + name);
	}

	@Override
	public FilePath getParentDir() {
		return new FilePath(file.getParentFile().getPath());
	}

	@Override
	public boolean renameTo(String newName) {
		File newFile = new File(file.getParentFile(), newName);
		if (file.renameTo(newFile)) {
			file = newFile;
			original = file.getPath();
			return true;
		} else {
			return false;
		}
	}

	@Override
	public GeneralPath moveTo(GeneralPath newPath) {
		if (newPath.exists()) {
			return null;
		}
		if (newPath instanceof FilePath) {
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
	public boolean isDir() {
		return file.isDirectory();
	}

	@Override
	public boolean exists() {
		return file.exists();
	}

	@Override
	public FilePath[] listFiles() {
		File[] files = file.listFiles();
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
