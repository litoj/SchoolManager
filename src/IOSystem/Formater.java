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
import objects.MainChapter;
import objects.SaveChapter;

/**
 * This class is used to operate with text files. Mainly to writeElement and get
 MainChapters and their content. The format of the saved files is supposed to
 look like json, but the code is all thought and writen by the author of this
 project. Noones code has been used as an example to make json format.
 *
 * @author Josef Litoš
 */
public class Formater {

   static String objDir;
   static final String DIR = "SchlMgr\\";
   public static final String CLASS = "class", NAME = "name", SUCCESS = "s", FAIL = "f", CHILDREN = "cdrn", DESC = "desc";
   static String settings;

   public static String getPath() {
      return objDir;
   }

   public static String getSettings() {
      return settings;
   }

   public static void setSettings(String newSetts) {
      saveFile(settings = newSetts, new File(DIR + "options.txt"));
   }

   /**
    * This method changes the directory of saves of hierarchies.
    *
    * @param path the directory for this application
    */
   public static void changeDir(File path) {
      String s = ("".equals(path.getPath()) ? "" : path.getPath() + "\\")
              + (path.getName().equals("School objects") ? "" : "School objects\\");
      saveFile(settings = settings.replace("objdir:" + objDir, "objdir:" + s), new File(DIR + "options.txt"));
      setDir(s);
   }

   private static void setDir(String path) {
      createDir(objDir = path);
   }

   /**
    * This method has to be called at the start of the program.
    */
   public static void loadSettings() {
      createDir(DIR);
      File setts = new File(DIR + "options.txt");
      if (setts.exists()) {
         settings = loadFile(setts);
         setDir(getData(settings, "objdir:"));
      } else {
         setDir(DIR + "School objects\\");
         saveFile(settings = new StringBuilder().append("objdir:").append(objDir).append("\nlanguage:cz\n").toString(), new File(DIR + "options.txt"));
      }
   }

   public static String getData(String source, String objective) {
      return getData(source, objective, "\n");
   }

   public static String getData(String source, String objective, String end) {
      return source.split(objective)[1].split(end)[0];
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
      MainChapter mch = ReadElement.loadMCh(new File(objDir + "newIOSystem\\newIOSystem.json"));
      SaveChapter sch = mch.getChildren()[0];
      ReadElement.loadSCh(new File(sch.save), sch.identifier);
      WriteElement.saveAll(mch);
   }

}
