/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package IOSystem;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Map;
import objects.MainChapter;
import objects.templates.Container;

/**
 * This class is used to operate with text files. The format of all files
 * containing necessary data of every {@link objects.none} is supposed to look
 * like json, but the code is all thought and writen by the author.
 *
 * @author Josef Litoš
 */
public class Formatter {

   static String objDir;
//   static final String DIR = "SchlMgr\\";
   public static final String CLASS = "class", NAME = "name", SUCCESS = "s",
           FAIL = "f", CHILDREN = "cdrn", DESC = "desc";
   static Map<String, String> settings = new java.util.HashMap<>();

   public static String getPath() {
      return objDir;
   }

   /**
    * @see Map#get(java.lang.Object)
    */
   public static String getSetting(String key) {
      return settings.get(key);
   }

   /**
    * @see Map#put(java.lang.Object, java.lang.Object)
    */
   public static void putSetting(String key, String value) {
      settings.put(key, value);
      deserializeTo("SchlMgr\\settings.dat", settings);
   }

   /**
    * Removes the settings value for the given key.
    *
    * @param key the tag that will be removed
    */
   public static void removeSetting(String key) {
      settings.remove(key);
      deserializeTo("SchlMgr\\settings.dat", settings);
   }

   /**
    * This method changes the directory of saves of hierarchies.
    *
    * @param path the directory for this application
    */
   public static void changeDir(File path) {
      objDir = ("".equals(path.getPath()) ? "" : path + "\\")
              + (path.getPath().equals("School objects") ? "" : "School objects\\");
      putSetting("objdir", objDir);
      createDir(objDir);
   }

   public static File createDir(String dirStr) {
      File dir = new File(dirStr);
      if (!dir.exists()) {
         dir.mkdir();
         try {
            dir.createNewFile();
         } catch (IOException ex) {
            throw new IllegalArgumentException(ex);
         }
      }
      return dir;
   }

   /**
    * This method has to be called at the start of the program.
    */
   public static void loadSettings() {
      File setts = new File("settings.dat");
      if (setts.exists()) {
         settings = (Map<String, String>) serialize("settings.dat");
         createDir(objDir = settings.get("objdir"));
      } else {
         createDir(objDir = "School objects\\");
         settings.put("objdir", "School objects\\");
         settings.put("language", "cz");
         deserializeTo("settings.dat", settings);
      }
   }

   public static void deserializeTo(String destination, Object toSave) {
      try (ObjectOutputStream oos = new ObjectOutputStream(
              new java.io.FileOutputStream(destination))) {
         oos.writeObject(toSave);
      } catch (IOException ex) {
         throw new IllegalArgumentException(ex);
      }
   }

   public static Object serialize(String filePath) {
      try (ObjectInputStream ois = new ObjectInputStream(
              new java.io.FileInputStream(filePath))) {
         return ois.readObject();
      } catch (IOException | ClassNotFoundException ex) {
         throw new IllegalArgumentException(ex);
      }
   }

   public static String loadFile(File toLoad) {
      StringBuilder sb = new StringBuilder();
      try (FileReader fr = new FileReader(toLoad)) {
         char[] buffer = new char[1024];
         int amount;
         while ((amount = fr.read(buffer)) != -1) {
            sb.append(buffer, 0, amount);
         }
      } catch (IOException ex) {
         throw new IllegalArgumentException(ex);
      }
      return sb.toString();
   }

   public static void saveFile(String toSave, File save) {
      try (FileWriter fw = new FileWriter(save)) {
         fw.write(toSave);
      } catch (IOException ex) {
         throw new IllegalArgumentException(ex);
      }
   }

   public static class Data {

      public String name;
      public MainChapter identifier;
      public int[] sf;
      public String description = "";
      public String[] tagVals;
      public Container par;

      Data(String name, MainChapter identifier, int s, int f, String description, Container parent, String... tagValues) {
         this(name, identifier, s, f, description, parent);
         tagVals = tagValues;
      }

      public Data(String name, MainChapter identifier, int s, int f, String description, Container parent) {
         this(name, identifier, s, f, description);
         par = parent;
      }

      public Data(String name, MainChapter identifier, String description, Container parent) {
         this(name, identifier, description);
         par = parent;
      }

      public Data(String name, MainChapter identifier, int s, int f, Container parent) {
         this(name, identifier, s, f);
         par = parent;
      }

      public Data(String name, MainChapter identifier, int s, int f, String description) {
         this(name, identifier, description);
         this.sf = new int[]{s, f};
      }

      public Data(String name, MainChapter identifier, int s, int f) {
         this(name, identifier);
         this.sf = new int[]{s, f};
      }

      public Data(String name, MainChapter identifier, String description) {
         this(name, identifier);
         this.description = description == null ? "" : description;
      }

      public Data(String name, MainChapter identifier, Container parent) {
         this(name, identifier);
         par = parent;
      }

      public Data(String name, MainChapter identifier) {
         this.name = name;
         this.identifier = identifier;
      }

      @Override
      public String toString() {
         return name;
      }

   }

   public static void main(String[] args) {
      loadSettings();
      long x = System.currentTimeMillis();
      MainChapter mch = ReadElement.loadAll(new File(objDir + "AJ"));
      System.out.println(System.currentTimeMillis() - x);
//      for (BasicData sch : mch.getChildren()) {
//         int i = 0;
//         for (BasicData ch : ((Container)sch).getChildren()) {
//            i += ((Container)ch).getChildren().length;
//            System.out.println(" " + ch.toString() + "\t" + ((Container)ch).getChildren().length + "\t" + i);
//         }
//         System.out.println(sch + "\t" + i + '\n');
//      }
      mch.save(null);
   }
}
