/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package IOSystem;

import static IOSystem.Formater.BasicData;
import static IOSystem.Formater.CHILDREN;
import static IOSystem.Formater.CLASS;
import static IOSystem.Formater.DESC;
import static IOSystem.Formater.FAIL;
import static IOSystem.Formater.NAME;
import static IOSystem.Formater.SUCCESS;
import static IOSystem.Formater.loadFile;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import objects.Chapter;
import objects.MainChapter;

/**
 *
 * @author Josef Litoš
 */
public abstract class ReadElement {

   public static class Source {

      /**
       * Source to be read, starting at position {@link #index}.
       */
      public final String str;
      /**
       * Where the given {@link #str source} should be read from.
       */
      public int index;

      public Source(String s, int index) {
         this.str = s;
         this.index = index;
      }
   }

   public static MainChapter loadMCh(File save) {
      loadSCh(save, null);
      String name = save.getName().split("\\.")[0];
      for (MainChapter mch : MainChapter.ELEMENTS) {
         if (mch.toString().equals(name)) {
            return mch;
         }
      }
      return null;
   }

   public static void loadSCh(File toLoad, MainChapter identifier) {
      Source src = new Source(loadFile(toLoad), 0);

      dumpSpace(src, '{', '\t', '\n');
      read(src, identifier);
   }

   public static void read(Source src, Chapter parent) {
      if (next(src).equals(CLASS)) {
         try {
            Class.forName(next(src)).getDeclaredMethod("readElement", Source.class, Chapter.class).invoke(null, src, parent);
         } catch (IllegalAccessException | IllegalArgumentException
                 | InvocationTargetException | NoSuchMethodException
                 | SecurityException | ClassNotFoundException ex) {
            throw new IllegalArgumentException(ex);
         }
      }
      dumpSpace(src, '}');
   }

   /**
    * Gets values for the basic tags and the given tags
    *
    * @param src contains the reading data
    * @param name if should read {@link #NAME} tag
    * @param identifier the hierarchy the loaded objects belong to
    * @param sf if should read {@link #SUCCESS} and {@link #FAIL} tags
    * @param desc if should read {@link #DESC description} tag
    * @param child if should read {@link #CHILDREN} tag
    * @param tags other tags you want to get value for
    * @return contains all found values for the given {@code tags}
    */
   public static BasicData get(Source src, boolean name, MainChapter identifier, boolean sf, boolean desc, boolean child, String... tags) {
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
               return new BasicData(data[0], identifier, sucfail[0], sucfail[1], data[1], info);

            } else {
               throw iae;
            }
         }
         sorter:
         switch (holder) {
            case NAME:
               data[0] = next(src);
               break;
            case SUCCESS:
               sucfail[0] = Integer.parseInt(next(src));
               break;
            case FAIL:
               sucfail[1] = Integer.parseInt(next(src));
               break;
            case DESC:
               data[1] = next(src);
               break;
            case CHILDREN:
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
               throw new IllegalArgumentException("Unknown field while getting value for " + holder + ", char num: " + src.index);
         }
      }
      return new BasicData(data[0], identifier, sucfail[0], sucfail[1], data[1], info);
   }

   /**
    * Gets values for the basic tags and the given tags for all of the calling
    * object's children. every position in the {@code List} contains all the
    * specified data for each child.
    *
    * @param src contains the reading data
    * @param name if should read {@link #NAME} tag
    * @param identifier the hierarchy the loaded objects belong to
    * @param sf if should read {@link #SUCCESS} and {@link #FAIL} tags
    * @param desc if should read {@link #DESC description} tag
    * @param tags other tags you want to get value for
    * @return data of all children
    * @see #get(java.lang.String, boolean, boolean, boolean, boolean,
    * java.lang.String...)
    */
   public static List<BasicData> readChildren(Source src, boolean name, MainChapter identifier, boolean sf, boolean desc, String... tags) {
      List<BasicData> datas = new ArrayList<>();
      try {
         while (dumpSpace(src, '{', ',', '\n', '\t')) {
            datas.add(get(src, name, identifier, sf, desc, false));
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
    * @param parent the {@link Chapter} object containing the loaded children
    * loading objects belong to
    */
   public static void loadChildren(Source src, Chapter parent) {
      try {
         while (dumpSpace(src, '{', ',', '\n', '\t')) {
            read(src, parent);
         }
      } catch (IllegalArgumentException iae) {
         if (!iae.getMessage().contains("']'")) {
            throw iae;
         }
      }
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

   public abstract void readElement(Source src, Chapter parent);
}
