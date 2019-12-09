/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package objects;

import static IOSystem.Formatter.createDir;
import static IOSystem.Formatter.deserializeTo;
import static IOSystem.Formatter.getPath;
import static IOSystem.Formatter.serialize;
import java.io.File;
import java.util.Map;
import objects.templates.Container;
import objects.templates.BasicData;
import objects.templates.ContainerFile;

/**
 * Head object of hierarchy of all {@link BasicData elemetary} objects. Saves
 * this hierarchy into folder under name of this object in the specified
 * {@link IOSystem.Formatter#objDir directory}. Should be {@link #name named}
 * after the school object this hierarchy represents.
 *
 * @author Josef Litoš
 */
public class MainChapter extends objects.templates.SemiElementContainer implements ContainerFile {

   /**
    * read-only data
    */
   public static final java.util.Set<MainChapter> ELEMENTS = new java.util.HashSet<>();
   /**
    * This file contains everything about this object and its
    * {@link #children children}.
    */
   File dir;

   public File getDir() {
      return dir;
   }

   protected Map<String, Object> settings = new java.util.HashMap<>();

   public Object getSetting(String key) {
      return settings.get(key);
   }

   public void putSetting(String key, Object value) {
      settings.put(key, value);
      deserializeTo(dir + "\\setts.dat", settings);
   }

   public void removeSetting(String key) {
      settings.remove(key);
      deserializeTo(dir + "\\setts.dat", settings);
   }

   @Override
   public File getSaveFile(Container c) {
      return new File(dir + "\\main.json");
   }

   /**
    * Only this constructor creates the head object of the hierarchy. The
    * hierarchy files are saved in its {@link #dir directory}.
    *
    * @param d must contain {@link #name name}
    */
   public MainChapter(IOSystem.Formatter.Data d) {
      super(d);
      BasicData.isCorrect(name);
      dir = createDir(getPath() + name);
      createDir(dir.getPath() + "\\Chapters");
      File setts = new File(dir + "\\setts.dat");
      if (setts.exists()) {
         settings = (Map<String, Object>) serialize(dir + "\\setts.dat");
      } else {
         deserializeTo(dir + "\\setts.dat", settings);
      }
      ELEMENTS.add(this);
   }

   @Override
   public boolean destroy(Container parent) {
      children.forEach((bd) -> bd.destroy(this));
      children.clear();
      ELEMENTS.remove(this);
      return remFiles(dir);
   }

   private boolean remFiles(File src) {
      if (src.isDirectory()) {
         for (File f : src.listFiles()) {
            remFiles(f);
         }
      }
      return src.delete();
   }

   @Override
   public boolean setName(String name) {
      BasicData.isCorrect(name);
      File newDir = new File(getPath() + name);
      for (byte i = 0; i < 5; i++) {
         if (dir.renameTo(newDir)) {
            dir = newDir;
            this.name = name;
            save(null);
            return true;
         }
      }
      return false;
   }

   /**
    * This method saves this object and then removes itself from the
    * {@link #ELEMENTS list}.
    */
   public void close() {
      save(null);
      ELEMENTS.remove(this);
   }

   @Override
   public MainChapter getIdentifier() {
      return this;
   }

   public void loadAll() {
      do {
         for (SaveChapter sch : SaveChapter.ELEMENTS.get(this)) {
            if (!sch.loaded) {
               sch.load(this);
            }
         }
      } while (SaveChapter.ELEMENTS.get(this).stream().anyMatch((sch) -> !sch.loaded));
   }

   @Override
   public boolean isLoaded(Container parent) {
      return true;
   }

   @Override
   public StringBuilder writeData(StringBuilder sb, int tabs, Container cp) {
      tabs(sb, tabs++, "{ ").add(sb, this, null, true, true, true, true, true);
      deserializeTo(dir + "\\setts.dat", settings);
      return writeData0(sb, tabs, cp);
   }

   public static BasicData readData(IOSystem.ReadElement.Source src, Container parent) {
      MainChapter mch = new MainChapter(IOSystem.ReadElement.get(src, true, true, true, true, null));
      IOSystem.ReadElement.loadChildren(src, null).forEach((e) -> mch.putChild(parent, e));
      return mch;
   }
}
