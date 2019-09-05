/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package objects;

import IOSystem.Formater.BasicData;
import static IOSystem.Formater.getData;
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
    * @param images the files containing an image each
    * @param parent the Chapter which this Picture belongs to
    * @param isNew if the object to be created is new or just loading hierarchy
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
                  String search = bd.name + ":parCount:";
                  bd.identifier.pictures = bd.identifier.pictures.replace(search + p.parentCount,
                          search + ++p.parentCount);
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

   private void picParentCount(boolean isNew) {
      String search = name + ":parCount:";
      try {
         parentCount = Integer.parseInt(getData(identifier.pictures, search));
      } catch (ArrayIndexOutOfBoundsException ex) {
         parentCount = 0;
         identifier.pictures += search + "0\n";//kam s tim
      }
      if (isNew) {
         identifier.pictures = identifier.pictures.replace(search + parentCount,
                 search + ++parentCount);
      }
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
      for (int i = 0; i < images.size(); i++) {
         File pic;
         if (isNew) {
            int serialINum = -1;
            String[] fix = images.get(i).name.split("\\.");
            fix[1] = '.' + fix[fix.length - 1];
            fix[0] = identifier.dir.getPath() + "\\Pictures\\" + this.name + ' ';
            do {
               serialINum++;
               pic = new File(fix[0] + serialINum + fix[1]);
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

   @Override
   public void destroy(Chapter parent) {
      String search = name + ":parCount:";
      identifier.pictures = identifier.pictures.replace(search + parentCount + '\n',
              (--parentCount == 0 ? "" : (search + parentCount + '\n')));
      if (isMain) {
         for (Picture child : children.get(parent)) {
            child.remove(parent, this);
            child.destroy(parent);
         }
         children.remove(parent);
         parent.children.remove(this);
      }
      if (parentCount == 0) {
         if (isMain) {
            ELEMENTS.get(identifier).remove(this);
         } else {
            IMAGES.get(identifier).remove(this);
            new File(identifier.dir.getPath() + "\\Pictures\\" + name).delete();
         }
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
