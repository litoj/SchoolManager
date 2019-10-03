/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package objects;

import IOSystem.Formater.BasicData;
import static IOSystem.ReadElement.get;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author Josef Litoš
 */
public class Picture extends TwoSided<Picture> {

   /**
    * @see Element#ELEMENTS
    */
   public static final Map<MainChapter, List<Picture>> IMAGES = new HashMap<>();
   /**
    * @see Element#ELEMENTS
    */
   public static final Map<MainChapter, List<Picture>> ELEMENTS = new HashMap<>();

   /**
    * The only allowed way to create Picture objects. Automaticaly controls its
    * existence and returns the proper Picture.
    *
    * @param bd all the needed data to create new {@link Picture} object
    * @param images the files containing an image each
    * @param parent the Chapter which this Picture belongs to
    * @param isNew if the object to be created is new or just being loaded
    * @return new
    * {@linkplain #Picture(java.lang.String, objects.Chapter, objects.MainChapter, int, int, boolean) Picture object}
    * if the name doesn't exist yet, otherwise returns the picture object with
    * the same name and adds the new images.
    */
   public static final Picture mkElement(BasicData bd, List<BasicData> images, Chapter parent, boolean isNew) {
      if (ELEMENTS.get(bd.identifier) == null) {
         ELEMENTS.put(bd.identifier, new ArrayList<>());
         IMAGES.put(bd.identifier, new ArrayList<>());
      }
      for (Picture p : ELEMENTS.get(bd.identifier)) {
         if (bd.name.equals(p.toString())) {
            condition:
            {
               for (Chapter prnt : p.children.keySet()) {
                  if (prnt == parent) {
                     break condition;
                  }
               }
               if (isNew) {
                  bd.identifier.pictures.put(bd.name, ++p.parentCount);
               }
               parent.children.add(p);
            }
            p.addImages(images, parent, isNew);
            return p;
         }
      }
      return new Picture(bd, images, parent, isNew);
   }

   /**
    * This method allows you for potencial doubling of an image.
    *
    * @param images names of imgs for this picture in the specified Chapter
    * @param parent Chapter containing this picture
    * @param sfs the successes and fails for every image
    */
   private void addImages(List<BasicData> images, Chapter parent, boolean isNew) {
      if (children.get(parent) == null) {
         children.put(parent, new Picture[0]);
      }
      Picture[] imgs = Arrays.copyOf(children.get(parent), images.size() + children.get(parent).length);
      int differ = imgs.length - images.size();
      int serialINum = -1;
      String front = identifier.dir.getPath() + "\\Pictures\\" + this.name + ' ';
      for (int i = 0; i < images.size(); i++) {
         File pic;
         if (isNew) {
            String back = images.get(i).name.substring(images.get(i).name.lastIndexOf('.'));
            do {
               pic = new File(front + ++serialINum + back);
            } while (pic.exists());
         } else {
            pic = new File(images.get(i).name);
         }
         File source = new File(images.get(i).name);
         images.get(i).name = pic.getName();
         imgs[i + differ] = new Picture(this, parent, source, images.get(i), pic, isNew);
      }
      children.put(parent, imgs);
   }

   /**
    * This constructor is used only to create images.
    */
   private Picture(Picture pic, Chapter parent, File save, BasicData bd, File dest, boolean isNew) {
      super(bd, false, IMAGES);
      children.put(parent, new Picture[]{pic});
      picParentCount(isNew);
      if (isNew) {
         if (!dest.exists()) {
            try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(dest));
                    BufferedInputStream bis = new BufferedInputStream(new FileInputStream(save))) {
               byte[] buffer = new byte[8192];
               int amount;
               while ((amount = bis.read(buffer)) != -1) {
                  bos.write(buffer, 0, amount);
               }
            } catch (IOException ex) {
               throw new IllegalArgumentException(ex);
            }
         }
      }
   }

   /**
    * This constructor is used only to create Pictures.
    */
   private Picture(BasicData bd, List<BasicData> images, Chapter parent, boolean isNew) {
      super(bd, true, ELEMENTS);
      picParentCount(isNew);
      addImages(images, parent, isNew);
      parent.children.add(this);
   }

   @Override
   public void setName(String name) {
      if (this.name.equals(name)) {
         return;
      }
      if (isMain) {
         String path = identifier.dir.getPath() + "\\Pictures\\";
         for (Picture p : ELEMENTS.get(identifier)) {
            if (p.name.equals(name)) {//Umožňuje splynutí obrázků v případě shody názvu
               for (Chapter ch : children.keySet()) {
                  ch.children.remove(this);
                  if (!ch.children.contains(p)) {
                     ch.children.add(p);
                     p.children.put(ch, children.remove(ch));
                  } else {
                     Picture[] allImgs = p.children.get(ch);
                     int length = allImgs.length;
                     Picture[] oldImgs = children.get(ch);
                     allImgs = Arrays.copyOf(allImgs, length + children.get(ch).length);
                     for (int i = oldImgs.length - 1; i >= 0; i--) {
                        allImgs[length + i] = oldImgs[i];
                     }
                     p.children.put(ch, allImgs);
                  }
               }
               ELEMENTS.get(identifier).remove(this);
               int serialINum = -1;
               String front = path + name + ' ';
               for (Picture img : getChildren()) {
                  String back = img.name.substring(img.name.lastIndexOf('.'));
                  File pic = new File(path + img.name);
                  while (!pic.renameTo(new File(front + ++serialINum + back)));
                  img.name = pic.getName();
               }
               return;
            }
         }
         identifier.pictures.put(name, identifier.pictures.remove(this.name));
         for (Picture p : getChildren()) {
            String newName = p.name.replaceFirst(this.name, name);
            identifier.pictures.put(newName, identifier.pictures.remove(p.name));
            new File(path + p.name).renameTo(new File(path + newName));
            p.name = newName;
         }
         this.name = name;
      } else {
         throw new IllegalArgumentException("Name of an image file can't be changed.");
      }
   }

   private void picParentCount(boolean isNew) {
      if (isNew) {
         Integer i = identifier.pictures.get(name);
         identifier.pictures.put(name, parentCount = i == null ? 1 : (i + 1));
      }
   }

   @Override
   public void destroy(Chapter parent) {
      if (isMain) {
         for (Picture child : children.get(parent)) {
            child.remove(parent, this);
            child.destroy(parent);
         }
         children.remove(parent);
         parent.children.remove(this);
      }
      if (--parentCount == 0) {
         identifier.pictures.remove(name);
         if (isMain) {
            ELEMENTS.get(identifier).remove(this);
         } else {
            IMAGES.get(identifier).remove(this);
            new File(identifier.dir.getPath() + "\\Pictures\\" + name).delete();
         }
      } else {
         identifier.pictures.replace(name, parentCount);
      }
   }

   @Override
   void remove(Chapter parent, Picture toRem) {
      Picture[] prev = children.get(parent);
      Picture[] chdrn = new Picture[prev.length - 1];
      for (int i = 0; i < chdrn.length; i++) {
         if (prev[i] == toRem) {
            chdrn[i] = prev[chdrn.length];
         } else {
            chdrn[i] = prev[i];
         }
      }
      children.put(parent, chdrn);
   }

   @Override
   Picture[] mkArray(int size) {
      return new Picture[size];
   }

   public static void readElement(IOSystem.ReadElement.Source src, Chapter parent) {
      BasicData data = get(src, true, parent.identifier, true, true, true);
      List<BasicData> children = IOSystem.ReadElement.readChildren(src, true, data.identifier, true, true);
      mkElement(data, children, parent, false);
   }
}
