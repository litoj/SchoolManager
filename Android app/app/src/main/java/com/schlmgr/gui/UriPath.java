package com.schlmgr.gui;

import android.content.Intent;
import android.net.Uri;
import android.webkit.MimeTypeMap;

import androidx.documentfile.provider.DocumentFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.Arrays;

import IOSystem.FilePath;
import IOSystem.Formatter.IOSystem.GeneralPath;

import static com.schlmgr.gui.Controller.CONTEXT;

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
		return CONTEXT.getContentResolver().openOutputStream(uri, "wa");
	}

	@Override
	public InputStream createInputStream() throws IOException {
		return CONTEXT.getContentResolver().openInputStream(uri);
	}

	//Why does this damn thing not let me create a directory, I resign from trying to fix it.
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
	public UriPath getParentDir() {
		return new UriPath(file.getParentFile());
	}

	@Override
	public boolean renameTo(String newName) {
		DocumentFile newFile = file.getParentFile().findFile(newName);
		if (newFile == null || newFile.isDirectory() && newFile.listFiles().length == 0) {
			newFile.delete();
			if (file.renameTo(newName)) {
				uri = file.getUri();
				original = uri.toString();
			}
		}
		return false;
	}

	@Override
	public GeneralPath moveTo(GeneralPath newPath) {
		if (!newPath.exists() && copyTo(newPath)) {
			delete();
			return newPath;
		} else return null;
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
		return file.exists();
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
