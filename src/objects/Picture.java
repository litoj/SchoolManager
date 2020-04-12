package objects;

import IOSystem.Formatter.Data;
import objects.templates.BasicData;
import objects.templates.Container;
import objects.templates.ContainerFile;
import objects.templates.TwoSided;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static testing.NameReader.readName;

/**
 * Creates objects containing images associated with a given
 * {@link Data#name name}.
 *
 * @author Josef Litoš
 */
public class Picture extends TwoSided<Picture> {

	/**
	 * Contains all instances of this class created as the {@link #isMain more_main} version. All Pictures
	 * are sorted by the {@link MainChapter hierarchy} they belong to. read-only data
	 */
	public static final Map<MainChapter, List<Picture>> IMAGES = new HashMap<>();

	/**
	 * Contains all instances of this class created as the {@link #isMain non-more_main} version. All
	 * Images are sorted by the {@link MainChapter hierarchy} they belong to. read-only data
	 */
	public static final Map<MainChapter, List<Picture>> ELEMENTS = new HashMap<>();

	/**
	 * The only allowed way to create Picture objects. Automatically controls its
	 * existence and returns the proper Picture.
	 *
	 * @param bd     all the necessary data to create new {@link Picture} object
	 * @param images each must contain its image file path as its {@link Data#name name}, the list can lose its content
	 * @return new
	 * {@linkplain #Picture(IOSystem.Formatter.Data, java.util.List, boolean) Picture object}
	 * if the name doesn't exist yet, otherwise returns the picture object with the same name and adds the new images.
	 */
	public static Picture mkElement(Data bd, List<Data> images) {
		return mkElement(bd, images, true);
	}

	private static boolean creating = false;

	/**
	 * The only allowed way to create Picture objects. Automatically controls its
	 * existence and returns the proper Picture.
	 *
	 * @param d      all the necessary data to create new {@link Picture} object
	 * @param images each must contain its image file path as its
	 *               {@link Data#name name}
	 * @param isNew  if the object to be created is new or just being loaded,
	 *               usually {@code true} when called outside this class
	 * @return new
	 * {@link #Picture(Data, java.util.List, boolean) Picture object}
	 * if the name doesn't exist yet, otherwise returns the picture object with
	 * the same name and adds the new images.
	 */
	public static Picture mkElement(Data d, List<Data> images, boolean isNew) {
		synchronized (ELEMENTS) {
			if (creating) try {
				ELEMENTS.wait();
			} catch (InterruptedException ex) {
			}
			if (images == null || images.isEmpty()) {
				throw new NullPointerException();
			}
			ContainerFile.isCorrect(d.name);
			if (ELEMENTS.get(d.identifier) == null) {
				ELEMENTS.put(d.identifier, new ArrayList<>());
				IMAGES.put(d.identifier, new ArrayList<>());
				if (d.identifier.getSetting("picParCount") == null) {
					d.identifier.putSetting("picParCount", new HashMap<String, Integer>());
					d.identifier.putSetting("imgRemoved", false);
				}
			}
			for (Picture p : ELEMENTS.get(d.identifier)) {
				if (d.name.equals(p.name)) {
					if (p.children.get(d.par) == null) {
						p.children.put(d.par, new ArrayList<>(images.size()));
						((Map<String, Integer>) d.identifier.getSetting("picParCount")).put(d.name, ++p.parentCount);
					}
					if (d.description != null && !d.description.isEmpty())
						p.putDesc(d.par, d.description);
					p.addImages(images, d.par, isNew);
					creating = false;
					ELEMENTS.notify();
					return p;
				}
			}
			creating = false;
			ELEMENTS.notify();
			return new Picture(d, images, isNew);
		}
	}
	
	/**
	 * Creates an image part of Picture class. Connects to the given Picture.
	 * 
	 * @param d		data necessary for creating the image, {@link Data#name name} is the
	 *					source-file name, must contain parent object
	 * @param main	the picture object this image will be connected to
	 * @return		the created image part of Picture class
	 */
	public static Picture mkImage(Data d, Picture main) {
		int serialINum = -1;
		File par = new File(d.identifier.getDir().getPath(), "Pictures");
		String front = readName(main)[0] + ' ';
		File source = new File(d.name);
		while (new File(par, front + ++serialINum + ".jpg").exists());
		d.name = front + serialINum;
		Picture img = new Picture(main, source,d, true);
		main.putChild(d.par,img);
		return img;
	}

	/**
	 * Creates and adds all children to this object. This method
	 * doesn't control potential doubling of an image.
	 *
	 * @param images all the necessary data for every new image reference created
	 * @param parent Chapter containing this picture
	 * @param isNew  if this object is being created by the user or is already saved
	 */
	private void addImages(List<Data> images, Container parent, boolean isNew) {
		int serialINum = -1;
		File par = new File(identifier.getDir().getPath(), "Pictures");
		String front = readName(this)[0] + ' ';
		for (int i = 0; i < images.size(); i++) {
			File source = new File(images.get(i).name);
			if (isNew) {
				while (new File(par, front + ++serialINum + ".jpg").exists()) ;
				images.get(i).name = readName(this)[0] + ' ' + serialINum;
			}
			putChild(parent, new Picture(this, source, images.get(i), isNew));
		}
	}

	/**
	 * This constructor is used only to create an image part.
	 */
	private Picture(Picture pic, File save, Data bd, boolean isNew) {
		super(bd, false, IMAGES);
		children.put(bd.par, new ArrayList<>(Arrays.asList(new Picture[]{pic})));
		parentCount = 1;
		if (isNew) {
			File dest = new File(new File(identifier.getDir().getPath(), "Pictures"), name + ".jpg");
			if (!dest.exists()) try (BufferedOutputStream bos = new BufferedOutputStream(
					new java.io.FileOutputStream(dest)); BufferedInputStream bis
					                         = new BufferedInputStream(new java.io.FileInputStream(save))) {
				byte[] buffer = new byte[8192];
				int amount;
				while ((amount = bis.read(buffer)) != -1) bos.write(buffer, 0, amount);
			} catch (java.io.IOException ex) {
				throw new IllegalArgumentException(ex);
			}
		}
	}

	/**
	 * This constructor is used only to create the main instance of this class.
	 */
	private Picture(Data bd, List<Data> images, boolean isNew) {
		super(bd, true, ELEMENTS);
		new File(identifier.getDir(), "Pictures").mkdirs();
		picParentCount(isNew);
		children.put(bd.par, new ArrayList<>(images.size()));
		addImages(images, bd.par, isNew);
	}

	/**
	 * Cleans the database numbering of images. This is needed, when images were
	 * removed and they weren't last of the given name.
	 *
	 * @param mch the hierarchy to be cleaned
	 */
	public static void clean(MainChapter mch) {
		if (!isCleanable(mch)) return;
		mch.load(false);
		String exceptions = "";
		File dir = new File(mch.getDir(), "Pictures");
		for (Picture p : ELEMENTS.get(mch)) {
			int serialINum = -1;
			String front = p.name + ' ';
			for (BasicData img : p.getChildren()) {
				String[] name = img.getName().split(" ");
				int srnum = Integer.parseInt(name[name.length - 1]);
				if (serialINum < srnum) {
					File pic = new File(dir, img.getName() + ".jpg");
					while (serialINum < srnum && !pic.renameTo(new File(dir, front + ++serialINum + ".jpg")));
					((Picture) img).name = p.name + ' ' + serialINum;
				}
			}
		}
		if (!exceptions.isEmpty()) throw new IllegalArgumentException(exceptions);
		mch.putSetting("imgRemoved", false);
	}

	/**
	 * Tells, if the given hierarchy can get cleaned of image numbers.
	 *
	 * @param mch source
	 * @return {@code true} if an image has been deleted from the hierarchy
	 */
	public static boolean isCleanable(MainChapter mch) {
		return (boolean) mch.getSetting("imgRemoved");
	}

	@Override
	public boolean setName(Container ch, String name) {
		ContainerFile.isCorrect(name);
		if (this.name.equals(name) || children.isEmpty() || !isMain) return false;
		Container parpar = ch.removeChild(this);
		Map<String, Integer> map = (Map<String, Integer>) identifier.getSetting("picParCount");
		for (Picture p : ELEMENTS.get(identifier))
			if (p.name.equals(name)) {//Umožňuje splynutí obrázků v případě shody názvu
				if (p.getDesc(ch) == null || p.getDesc(ch).equals("")) p.putDesc(ch, getDesc(ch));
				if (parentCount <= 1) {
					ELEMENTS.get(identifier).remove(this);
					map.remove(this.name);
				} else map.put(this.name, --parentCount);
				setName0(parpar, ch, p);
				return true;
			}
		if (parentCount <= 1) {
			this.name = name;
			fileName(ch, false, this);
		} else {
			parentCount--;
			setName0(parpar, ch, new Picture(this, ch, name));
		}
		return true;
	}

	private Picture(Picture src, Container par, String newName) {
		super(new Data(newName, src.identifier, 0, 0, src.description.get(par), par), true, ELEMENTS);
		picParentCount(true);
		parentCount--;
	}

	private void fileName(Container ch, boolean newPic, Picture p) {
		Map<String, Integer> map = (Map<String, Integer>) identifier.getSetting("picParCount");
		File path = new File(identifier.getDir(), "Pictures");
		for (Picture img : children.remove(ch)) {
			int serialINum = -1;
			File pic = new File(path, img.getName() + ".jpg");
			while (!pic.renameTo(new File(path, p.name + ' ' + ++serialINum + ".jpg"))) ;
			img.name = p.name + ' ' + serialINum;
			if (newPic) {
				img.children.get(ch).remove(this);
				img.children.get(ch).add(p);
			}
		}
		map.put(p.name, p.parentCount);
	}

	private void setName0(Container parpar, Container ch, Picture p) {
		if (!ch.hasChild(p)) {
			ch.putChild(parpar, p);
			p.children.put(ch, children.get(ch));
			p.parentCount++;
		} else p.children.get(ch).addAll(children.get(ch));
		fileName(ch, true, p);
	}

	private void picParentCount(boolean isNew) {
		if (isNew) {
			Map<String, Integer> map = (Map<String, Integer>) identifier.getSetting("picParCount");
			map.put(name, parentCount = (map.get(name) == null ? 1 : (map.get(name) + 1)));
		}
	}

	@Override
	public boolean putChild(Container c, BasicData e) {
		if (!(e instanceof TwoSided)) return false;
		if (children.get(c) == null) {
			if (isMain) ((Map<String, Integer>) identifier.getSetting("picParCount"))
					.put(name, ++parentCount);
			children.put(c, new LinkedList<>());
		} else if (children.get(c).contains((Picture) e)) return false;
		return children.get(c).add((Picture) e);
	}

	@Override
	public boolean destroy(Container parent) {
		if (isMain) {
			for (BasicData child : children.get(parent)) {
				((Picture) child).remove1(parent, this);
				child.destroy(parent);
			}
			children.remove(parent);
			parent.removeChild(this);
			if (--parentCount != 0) {
				((Map<String, Integer>) identifier.getSetting("picParCount")).put(name, parentCount);
				return true;
			}
		}
		identifier.putSetting("imgRemoved", true);
		if (isMain) {
			((Map<String, Integer>) identifier.getSetting("picParCount")).remove(name);
			return ELEMENTS.get(identifier).remove(this);
		}
		IMAGES.get(identifier).remove(this);
		return new File(new File(identifier.getDir().getPath(), "Pictures"), name + ".jpg").delete();
	}

	/**
	 * Implementation of
	 * {@link IOSystem.ReadElement#readData(IOSystem.ReadElement.Source, objects.templates.Container) loading from String}.
	 */
	public static BasicData readData(IOSystem.ReadElement.Source src, Container parent) {
		Data data = IOSystem.ReadElement.get(src, true, true, true, true, parent);
		List<Data> children = IOSystem.ReadElement.readChildren(src, true, true, true, parent);
		return mkElement(data, children, false);
	}
}
