/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package IOSystem;

import static IOSystem.Formatter.Data;
import java.io.File;
import java.util.LinkedList;
import java.util.List;
import objects.MainChapter;
import objects.SaveChapter;
import objects.templates.BasicData;
import objects.templates.Container;

/**
 * This class provides methods used for reading the data of any
 * {@link objects.Element} from its file.
 *
 * @author Josef Litoš
 */
public abstract class ReadElement {

   /**
    * Used for any text reading to keep track of the current position of
    * reading.
    */
   public static class Source {

      /**
       * Source to be read, from position {@link #index index}.
       */
      public final String str;
      /**
       * Where the given {@link #str source} should be read from.
       */
      public int index;

      public final MainChapter i;

      public Source(String s, int index, MainChapter identifier) {
         this.str = s;
         this.index = index;
         i = identifier;
      }
   }

   /**
    * Loads the full hierarchy fro, its folder.
    *
    * @param mchDir the hierarchy folder to be read
    * @return returns the loaded {@link MainChapter}
    */
   public static MainChapter loadAll(File mchDir) {
      MainChapter mch = loadMch(new File(mchDir + "\\main.json"));
//      Arrays.asList(mch.getChildren()).forEach((sch)
//              -> loadSch(new File(mchDir + "\\Chapters\\" + sch + ".json"), mch,mch));
      return mch;
   }

   /**
    * Creates {@link MainChapter} object with information from the given file.
    *
    * @param save the file the returned object is saved in
    * @return created head-object of the hierarchy containing basic information
    * about its content
    */
   public static MainChapter loadMch(File save) {
      loadSch(save, null, null);
      String[] str = save.toString().split("\\\\");
      String name = str[str.length - 2];
      for (MainChapter mch : MainChapter.ELEMENTS) {
         if (mch.toString().equals(name)) {
            return mch;
         }
      }
      return null;
   }

   /**
    * Loads all data into the respective {@link SaveChapter} using {@link #readElement(IOSystem.ReadElement.Source, objects.Chapter)
    * readData} method which all objects have to implement in static form.
    *
    * @param toLoad should contain the data of the loaded object
    * @param identifier the parent of the loaded object
    */
   public static void loadSch(File toLoad, MainChapter identifier, Container parent) {
      Source src = new Source(Formatter.loadFile(toLoad), 0, identifier);
      dumpSpace(src, '{', '\t', '\n');
      read(src, parent);
   }

   /**
    * Calls the coresponding {@link #readElement(IOSystem.ReadElement.Source, objects.Chapter)
    * readData} method implementation to create its instance. Middle–step
    * between every loaded {@link objects.Element}.
    *
    * @param src {@link Source}
    * @param cp parent of the read object
    * @return the loaded object
    */
   public static BasicData read(Source src, Container cp) {
      BasicData bd = null;
      if (next(src).equals(Formatter.CLASS)) {
         try {
            bd = (BasicData) Class.forName(next(src)).getDeclaredMethod("readData",
                    Source.class, Container.class).invoke(null, src, cp);
         } catch (IllegalAccessException | java.lang.reflect.InvocationTargetException
                 | NoSuchMethodException | SecurityException | ClassNotFoundException ex) {
            throw new IllegalArgumentException(ex);
         }
      }
      dumpSpace(src, '}');
      return bd;
   }

   /**
    * Gets values for the basic tags and the given tags.
    *
    * @param src contains the reading data
    * @param name if should read {@link Formatter#NAME name} tag
    * @param sf if should read {@link Formatter#SUCCESS success} and
    * {@link Formatter#FAIL fail} tags
    * @param desc if should read {@link Formatter#DESC description} tag
    * @param child if should read {@link Formatter#CHILDREN children} tag
    * @param parent parent of this object
    * @param tags other tags you want to get value for
    * @return contains all found values for the given {@code tags}
    */
   public static Data get(Source src, boolean name, boolean sf, boolean desc, boolean child, Container parent, String... tags) {
      String[] data = new String[2];
      int[] sucfail = {0, 0};
      String[] info = new String[tags.length];
      String holder = null;
      int bolRes = (name ? 1 : 0) + (sf ? 2 : 0) + (desc ? 1 : 0) + (child ? 1 : 0);
      for (int i = tags.length + bolRes; i > 0; i--) {
         try {
            holder = next(src, ',');
         } catch (IllegalArgumentException iae) {
            if (iae.getMessage().contains("'}'")) {
               src.index--;
               return new Data(data[0], src.i, sucfail[0], sucfail[1], data[1], parent, info);

            } else {
               throw iae;
            }
         }
         sorter:
         switch (holder) {
            case Formatter.NAME:
               data[0] = next(src);
               break;
            case Formatter.SUCCESS:
               sucfail[0] = Integer.parseInt(next(src));
               break;
            case Formatter.FAIL:
               sucfail[1] = Integer.parseInt(next(src));
               break;
            case Formatter.DESC:
               data[1] = next(src);
               break;
            case Formatter.CHILDREN:
               dumpSpace(src, '[');
               i = 0;
               break;
            default:
               for (int j = tags.length - 1; j >= 0; j--) {
                  if (holder.equals(tags[j])) {
                     info[j] = next(src);
                     break sorter;
                  }
               }
               throw new IllegalArgumentException("Unknown field while getting value for "
                       + holder + ", char num: " + src.index);
         }
      }
      return new Data(data[0], src.i, sucfail[0], sucfail[1], data[1], parent, info);
   }

   /**
    * Gets values for the basic tags and the given tags for all of the calling
    * object's children. every position in the {@code List} contains all the
    * specified data for each child.
    *
    * @param src contains the reading data
    * @param name if should read {@link Formater#NAME name} tag
    * @param sf if should read {@link Formater#SUCCESS success} and
    * {@link Formater#FAIL fail} tags
    * @param desc if should read {@link Formater#DESC description} tag
    * @param parent parent of the read children
    * @param tags other tags you want to get value for
    * @return data of all children
    * @see #get(java.lang.String, boolean, boolean, boolean, boolean,
    * java.lang.String...)
    */
   public static List<Data> readChildren(Source src, boolean name, boolean sf, boolean desc, Container parent, String... tags) {
      List<Data> datas = new java.util.ArrayList<>();
      try {
         while (dumpSpace(src, '{', ',', '\n', '\t')) {
            datas.add(get(src, name, sf, desc, false, parent));
            dumpSpace(src, '}');
         }
      } catch (IllegalArgumentException iae) {
         if (!iae.getMessage().contains("']'")) {
            throw iae;
         }
      }
      return datas;
   }

   /**
    * Loads all the children from the calling object.
    *
    * @param src contains the reading data
    * @param cp parent of the loaded children
    * @return the loaded children
    */
   public static List<BasicData> loadChildren(Source src, Container cp) {
      List<BasicData> bds = new LinkedList<>();
      try {
         while (dumpSpace(src, '{', ',', '\n', '\t')) {
            bds.add(read(src, cp));
         }
      } catch (IllegalArgumentException iae) {
         if (!iae.getMessage().contains("']'")) {
            throw iae;
         }
      }
      return bds;
   }

   /**
    * Dumps all chars from the argument {@code s}, until it reaches a char that
    * is not defined in the argument {@code ignore} and is not ' ' or ':'.
    *
    * @param src contains the reading data
    * @param end the char this method is supposed to find
    * @param ignore list of ignored chars
    * @return {@code true} if the found char matches the argument {@code end}.
    * @throws IllegalArgumentException if the unknown char doesn't match the
    * argument {@code end}.
    */
   public static boolean dumpSpace(Source src, char end, char... ignore) {
      char ch;
      boolean ctn;
      do {
         ch = src.str.charAt(src.index++);
         if (ctn = (ch == ' ' || ch == ':')) {
            continue;
         }
         for (char c : ignore) {
            if (ctn = (c == ch)) {
               break;
            }
         }
      } while (ctn);
      if (ch != end) {
         throw new IllegalArgumentException("Unknown field, char  '" + ch + "', num: " + src.index);
      } else {
         return true;
      }
   }

   /**
    * Reads the next value contained in "", ignoring chars defined by
    * {@code ignore} parameter using {@link #dumpSpace(IOSystem.ReadElement.Source, char, char...)
    * } method.
    *
    * @param src {@link Source}
    * @param ignore chars whose will be ignored
    * @return the found {@link String}
    */
   public static String next(Source src, char... ignore) {
      StringBuilder sb = new StringBuilder();
      dumpSpace(src, '"', ignore);
      char ch;
      while ((ch = src.str.charAt(src.index++)) != '"') {
         if (ch == '\\') {
            ch = src.str.charAt(src.index++);
         }
         sb.append(ch);
      }
      return sb.toString();
   }

   /**
    * Creates an {@link objects.templates.BasicData} from the loaded data.
    *
    * @param src {@link Source}
    * @param cp parent of the created object
    * @return the loaded object
    */
   public abstract BasicData readData(Source src, Container cp);
}
