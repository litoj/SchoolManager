/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package objects;

import IOSystem.Formatter.Data;
import static IOSystem.Formatter.createDir;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import objects.templates.BasicData;
import objects.templates.Container;

/**
 * This class creates objects containing images under the given name.
 *
 * @author Josef Litoš
 */
public class Picture extends TwoSided<Picture> {

   /**
    * read-only data
    */
   public static final Map<MainChapter, List<Picture>> IMAGES = new HashMap<>();
   public static final Map<MainChapter, List<Picture>> ELEMENTS = new HashMap<>();

   /**
    * The only allowed way to create Picture objects. Automaticaly controls its
    * existence and returns the proper Picture.
    *
    * @param bd all the necessary data to create new {@link Picture} object
    * @param images each must contain its image file path as its
    * {@link Data#name name}
    * @return new
    * {@linkplain #Picture(IOSystem.Formatter.Data, java.util.List, boolean) Picture object}
    * if the name doesn't exist yet, otherwise returns the picture object with
    * the same name and adds the new images.
    */
   public static Picture mkElement(Data bd, List<Data> images) {
      return mkElement(bd, images, true);
   }

   /**
    * The only allowed way to create Picture objects. Automaticaly controls its
    * existence and returns the proper Picture.
    *
    * @param d all the necessary data to create new {@link Picture} object
    * @param images each must contain its image file path as its
    * {@link Data#name name}
    * @param isNew if the object to be created is new or just being loaded,
    * usually {@code true} when called outside this class
    * @return new
    * {@linkplain #Picture(IOSystem.Formatter.Data, java.util.List, boolean) Picture object}
    * if the name doesn't exist yet, otherwise returns the picture object with
    * the same name and adds the new images.
    */
   public static Picture mkElement(Data d, List<Data> images, boolean isNew) {
      BasicData.isCorrect(d.name);
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
            p.addImages(images, d.par, isNew);
            return p;
         }
      }
      return new Picture(d, images, isNew);
   }

   /**
    * Creates and adds all children to this object. This method doesn't control
    * potencial doubling of an image.
    *
    * @param images all the necessary data for every new image reference created
    * @param parent Chapter containing this picture
    * @param isNew if this object is being created by the user or is already
    * saved
    */
   private void addImages(List<Data> images, Container parent, boolean isNew) {
      int serialINum = -1;
      String front = identifier.getDir().getPath() + "\\Pictures\\" + testing.NameReader.readName(this)[0] + ' ';
      for (int i = 0; i < images.size(); i++) {
         File source = new File(images.get(i).name);
         if (isNew) {
            while (new File(front + ++serialINum + ".jpg").exists());
            images.get(i).name = testing.NameReader.readName(this)[0] + ' ' + serialINum;
         }
         putChild(parent, new Picture(this, source, images.get(i), isNew));
      }
   }

   /**
    * This constructor is used only to create an image part.
    */
   private Picture(Picture pic, File save, Data bd, boolean isNew) {
      super(bd, false, IMAGES);
      BasicData.isCorrect(name);
      children.put(bd.par, new ArrayList<>(Arrays.asList(new Picture[]{pic})));
      picParentCount(isNew);
      if (isNew) {
         File dest = new File(identifier.getDir().getPath() + "\\Pictures\\" + name + ".jpg");
         if (!dest.exists()) {
            try (BufferedOutputStream bos = new BufferedOutputStream(
                    new java.io.FileOutputStream(dest)); BufferedInputStream bis
                    = new BufferedInputStream(new java.io.FileInputStream(save))) {
               byte[] buffer = new byte[8192];
               int amount;
               while ((amount = bis.read(buffer)) != -1) {
                  bos.write(buffer, 0, amount);
               }
            } catch (java.io.IOException ex) {
               throw new IllegalArgumentException(ex);
            }
         }
      }
   }

   /**
    * This constructor is used only to create main instance of this class.
    */
   private Picture(Data bd, List<Data> images, boolean isNew) {
      super(bd, true, ELEMENTS);
      createDir(identifier.getDir().getPath() + "\\Pictures");
      picParentCount(isNew);
      children.put(bd.par, new ArrayList<>(images.size()));
      addImages(images, bd.par, isNew);
   }

   public static void clean(MainChapter mch) {
      if (!isCleanable(mch)) {
         return;
      }
      mch.loadAll();
      String exceptions = "";
      String dir = mch.getDir().getPath() + "\\Pictures\\";
      int size = ELEMENTS.get(mch).size();
      for (Picture p : ELEMENTS.get(mch)) {
         int serialINum = -1;
         String front = dir + p.name + ' ';
         for (BasicData img : p.getChildren()) {
            File pic = new File(dir + img.getName() + ".jpg");
            while (!pic.renameTo(new File(front + ++serialINum + ".jpg"))) {
               if (serialINum > size) {
                  exceptions += "\nFile '" + pic + "' can't be renamed!";
                  break;
               }
            }
            ((Map<String, Integer>) mch.getSetting("picParCount")).remove(img.getName());
            ((Map<String, Integer>) mch.getSetting("picParCount")).put(((Picture) img).name = (p.name + ' ' + serialINum), ((Picture) img).parentCount);
         }
      }
      if (!exceptions.isEmpty()) {
         throw new IllegalArgumentException(exceptions);
      }
      mch.putSetting("imgRemoved", false);
   }

   public static boolean isCleanable(MainChapter mch) {
      return (boolean) mch.getSetting("imgRemoved");
   }

   @Override
   public boolean setName(String name) {
      BasicData.isCorrect(name);
      if (this.name.equals(name) || children.isEmpty()) {
         return false;
      }
      if (!isMain) {
         this.name = name;
         return true;
      }
      ((Map<String, Integer>) identifier.getSetting("picParCount")).remove(this.name);
      String path = identifier.getDir().getPath() + "\\Pictures\\";
      for (Picture p : ELEMENTS.get(identifier)) {
         if (p.name.equals(name)) {//Umožňuje splynutí obrázků v případě shody názvu
            for (Container c : description.keySet()) {
               if (p.getDesc(c) == null || p.getDesc(c).equals("")) {
                  p.putDesc(c, getDesc(c));
               }
            }
            for (Container ch : children.keySet()) {
               Container parpar = ch.removeChild(this);
               if (!ch.hasChild(p)) {
                  ch.putChild(parpar, p);
                  p.children.put(ch, children.get(ch));
                  p.parentCount++;
               } else {
                  p.children.get(ch).addAll(Arrays.asList(getChildren(ch)));
               }
            }
            ELEMENTS.get(identifier).remove(this);
            int serialINum = -1;
            String front = path + name + ' ';
            for (BasicData img : getChildren()) {
               File pic = new File(path + img.getName() + ".jpg");
               while (!pic.renameTo(new File(front + ++serialINum + ".jpg")));
               ((Map<String, Integer>) identifier.getSetting("picParCount")).put(name + ' ' + serialINum,
                       ((Map<String, Integer>) identifier.getSetting("picParCount")).remove(img.getName()));
               img.setName(name + ' ' + serialINum);
            }
            children.clear();
            ((Map<String, Integer>) identifier.getSetting("picParCount")).put(p.name, p.parentCount);
            return true;
         }
      }
      ((Map<String, Integer>) identifier.getSetting("picParCount")).put(name,
              ((Map<String, Integer>) identifier.getSetting("picParCount")).remove(this.name));
      for (BasicData p : getChildren()) {
         String newName = p.getName().replaceFirst(this.name, name);
         ((Map<String, Integer>) identifier.getSetting("picParCount")).put(newName,
                 ((Map<String, Integer>) identifier.getSetting("picParCount")).remove(p.getName()));
         new File(path + p.getName() + ".jpg").renameTo(new File(path + newName + ".jpg"));
         p.setName(newName);
      }
      ((Map<String, Integer>) identifier.getSetting("picParCount")).put(this.name = name, parentCount);
      return true;
   }

   private void picParentCount(boolean isNew) {
      if (isNew) {
         Integer i = ((Map<String, Integer>) identifier.getSetting("picParCount")).get(name);
         ((Map<String, Integer>) identifier.getSetting("picParCount")).put(name, parentCount = (i == null ? 1 : (i + 1)));
      }
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
      }
      if (--parentCount == 0) {
         identifier.putSetting("imgRemoved", true);
         ((Map<String, Integer>) identifier.getSetting("picParCount")).remove(name);
         if (isMain) {
            return ELEMENTS.get(identifier).remove(this);
         }
         IMAGES.get(identifier).remove(this);
         return new File(identifier.getDir().getPath() + "\\Pictures\\" + name + ".jpg").delete();
      }
      ((Map<String, Integer>) identifier.getSetting("picParCount")).replace(name, parentCount);
      return true;
   }

   public static void readData(IOSystem.ReadElement.Source src, Container parent) {
      Data data = IOSystem.ReadElement
              .get(src, true, true, true, true, parent);
      List<Data> children = IOSystem.ReadElement
              .readChildren(src, true, true, true, parent);
      mkElement(data, children, false);
   }
}
