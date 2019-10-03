/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package IOSystem;

import static IOSystem.Formater.settings;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;
import objects.MainChapter;
import objects.SaveChapter;

/**
 * This class is used to operate with text files. The format of all files
 * containing necessary data of every {@link objects.Element} is supposed to
 * look like json, but the code is all thought and writen by the author.
 *
 * @author Josef Litoš
 */
public class Formater {

   static String objDir;
   static final String DIR = "SchlMgr\\";
   public static final String CLASS = "class", NAME = "name", SUCCESS = "s", FAIL = "f", CHILDREN = "cdrn", DESC = "desc";
   static Map<String, String> settings = new HashMap<>();

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
      deserializeTo("SchlMgr\\settings.txt", settings);
   }

   /**
    * This method changes the directory of saves of hierarchies.
    *
    * @param path the directory for this application
    */
   public static void changeDir(File path) {
      String s = ("".equals(path.getPath()) ? "" : path.getPath() + "\\")
              + (path.getName().equals("School objects") ? "" : "School objects\\");
      putSetting("objdir", s);
      createDir(s);
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
      createDir(DIR);
      File setts = new File(DIR + "options.txt");
      if (setts.exists()) {
         settings = (Map<String, String>) serialize("SchlMgr\\settings.txt");
         createDir(objDir = settings.get("objdir"));
      } else {
         createDir(objDir = (DIR + "School objects\\"));
         settings.put("objdir", "SchlMgr\\School objects");
         settings.put("language", "cz");
         deserializeTo("SchlMgr\\settings.txt", settings);
      }
   }

   public static void deserializeTo(String destination, Object toSave) {
      try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(destination))) {
         oos.writeObject(toSave);
      } catch (IOException ex) {
         throw new IllegalArgumentException(ex);
      }
   }

   public static Object serialize(String filePath) {
      try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(filePath))) {
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

   public static class BasicData {

      public String name;
      public MainChapter identifier;
      public int[] sf;
      public String description;
      public String[] tagVals;

      BasicData(String name, MainChapter identifier, int s, int f, String description, String... tagValues) {
         this(name, identifier, s, f, description);
         tagVals = tagValues;
      }

      public BasicData(String name, MainChapter identifier, int s, int f, String description) {
         this(name, identifier, s, f);
         this.description = description;
      }

      public BasicData(String name, MainChapter identifier, int s, int f) {
         this(name, identifier);
         this.sf = new int[]{s, f};
      }

      public BasicData(String name, MainChapter identifier, String description) {
         this(name, identifier);
         this.description = description;
      }

      public BasicData(String name, MainChapter identifier) {
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
      MainChapter mch = ReadElement.loadMCh(new File(objDir + "newSetts\\newSetts.json"));
      SaveChapter sch = mch.getChildren()[0];
      ReadElement.loadSCh(new File(sch.save), sch.identifier);
      WriteElement.saveAll(mch);
   }
}
