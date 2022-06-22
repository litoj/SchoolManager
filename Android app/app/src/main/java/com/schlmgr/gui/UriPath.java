package com.schlmgr.gui;

import static com.schlmgr.gui.Controller.CONTEXT;

import android.content.ContentResolver;
import android.net.Uri;
import android.webkit.MimeTypeMap;

import androidx.documentfile.provider.DocumentFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import IOSystem.Formatter.IOSystem.GeneralPath;

public class UriPath implements GeneralPath {

	protected DocumentFile file;
	protected Uri uri;
	protected String original;
	private final boolean internal;

	public UriPath(String original) {
		this(Uri.parse(original), true);
	}

	public UriPath(DocumentFile file) {
		this(file, false);
	}

	public UriPath(DocumentFile file, boolean internal) {
		this.file = file;
		original = (uri = file.getUri()).toString();
		this.internal = internal;
	}

	/**
	 * Use only for folders obtained using Intent.ACTION_OPEN_DOCUMENT_TREE
	 */
	public UriPath(Uri uri, boolean folder) {
		this.uri = uri;
		original = uri.toString();
		file = folder ? DocumentFile.fromTreeUri(CONTEXT, uri) : DocumentFile.fromSingleUri(CONTEXT, uri);
		this.internal = false;
	}

	@Override
	public String getOriginalName() {
		return original;
	}

	@Override
	public String getName() {
		return file.getName();
	}

	public DocumentFile getDocumentFile() {
		return file;
	}

	@Override
	public OutputStream createOutputStream(boolean append) throws IOException {
		return CONTEXT.getContentResolver().openOutputStream(uri, append ? "wa" : "wt");
	}

	@Override
	public InputStream createInputStream() throws IOException {
		return CONTEXT.getContentResolver().openInputStream(uri);
	}

	@Override
	public UriPath getChild(String name) {
		DocumentFile child = file.findFile(name);
		if (name.matches(".*\\..{3,4}$")) {
			String mimetype = MimeTypeMap.getSingleton().getMimeTypeFromExtension(
					name.substring(name.lastIndexOf('.')));
			return child != null ? new UriPath(child) : new UriPath(file.createFile(
					mimetype == null ? "application/dat" : mimetype, name));
		}
		return child != null ? new UriPath(child) : new UriPath(file.createDirectory(name));
	}

	@Override
	public boolean hasChild(String name) {
		DocumentFile child = file.findFile(name);
		return child != null;
	}

	@Override
	public UriPath getParentDir() {
		return new UriPath(file.getParentFile());
	}

	// TODO: Find out why it creates one file and then renames to 'name (1).ext'
	@Override
	public boolean renameTo(String newName) {
		if (newName.equals(getName())) return true;
		if (file.renameTo(newName)) {
			uri = file.getUri();
			original = uri.toString();
			return true;
		} else return false;
	}

	@Override
	public GeneralPath moveTo(GeneralPath newPath) {
		if (newPath.getParentDir() == getParentDir()) {
			renameTo(newPath.getName());
			return newPath;
		} else if (!newPath.exists() || copyTo(newPath)) {
			delete();
			return newPath;
		} else return null;
	}

	@Override
	public boolean copyTo(GeneralPath newPath) {
		if (!newPath.isDir() && !file.isDirectory()) {
			return GeneralPath.super.copyTo(newPath);
		} else if (copy(file, newPath, CONTEXT.getContentResolver())) return true;
		else {
			newPath.delete();
			return false;
		}
	}

	private static boolean copy(DocumentFile file, GeneralPath mirror, ContentResolver cr) {
		for (DocumentFile f : file.listFiles()) {
			if (f.isFile()) {
				try (OutputStream os = mirror.getChild(f.getName()).createOutputStream();
						 InputStream is = cr.openInputStream(f.getUri())) {
					byte[] buffer = new byte[32768];
					int amount;
					while ((amount = is.read(buffer)) != -1) {
						os.write(buffer, 0, amount);
					}
				} catch (Exception e) {
					return false;
				}
			} else if (!copy(f, mirror.getChild(f.getName()), cr)) return false;
		}
		return true;
	}

	@Override
	public boolean delete() {
		return deleteFile(file);
	}

	protected boolean deleteFile(DocumentFile src) {
		if (src.isDirectory()) {
			for (DocumentFile f : src.listFiles()) {
				deleteFile(f);
			}
		}
		return src.delete();
	}

	@Override
	public boolean exists() {
		return file.exists() && (file.isDirectory() || file.length() > 0);
	}

	@Override
	public boolean isDir() {
		return file.isDirectory();
	}

	@Override
	public boolean isEmpty() {
		return !file.exists() || file.isDirectory() && file.listFiles().length == 0;
	}

	@Override
	public boolean equals(GeneralPath filePath) {
		return file.getUri().compareTo(filePath instanceof UriPath ? ((UriPath) filePath).uri
				: Uri.fromFile(new File(filePath.getOriginalName()))) == 0;
	}

	@Override
	public UriPath[] listFiles() {
		if (file.isDirectory()) {
			DocumentFile[] files = file.listFiles();
			UriPath[] paths = new UriPath[files.length];
			for (int i = 0; i < files.length; i++) paths[i] = new UriPath(files[i]);
			return paths;
		} else return null;
	}

	@Override
	public String toString() {
		return original;
	}
}
