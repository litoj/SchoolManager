/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package IOSystem;

import java.io.File;
import objects.Chapter;
import objects.Element;
import objects.MainChapter;
import objects.SaveChapter;

/**
 * This class provides many methods used in proccess of saving the data of
 * {@link Element} which extend this class.
 *
 * @author Josef Litoš
 */
public abstract class WriteElement {

   /**
    * Saves the given object and its containment into their own files.
    *
    * @param toSave {@link MainChapter} that you want to save
    */
   public static void saveAll(MainChapter toSave) {
      save(toSave);
      for (SaveChapter sch : toSave.getChildren()) {
         if (sch.loaded && sch.hasChild()) {
            save(sch);
         }
      }
   }

   /**
    * Saves the given object into its own file.
    *
    * @param toSave {@link SaveChapter} that you want to save
    */
   public static void save(SaveChapter toSave) {
      Formater.saveFile(toSave.writeElement(new StringBuilder(), 0, null).toString(), new File(toSave.save));
   }

   /**
    * Adds multiple tags to the given {@code sb}.
    *
    * @param sb object containing the data those will be written to the
    * coresponding {@link SaveChapter} file
    * @param e the currently written {@link Element}
    * @param clasS if {@link Formater#CLASS} tag and the coresponding data
    * should be added
    * @param name if {@link Formater#NAME} tag and the coresponding data should
    * be added
    * @param sf if {@link Formater#SUCCESS} and {@link Formater#FAIL} tags and
    * the coresponding data should be added
    * @param desc if {@link Formater#DESC} tag and the coresponding data should
    * be added
    * @param child if {@link Formater#CHILDREN} tag and the coresponding data
    * should be added
    * @return the written form of this object
    */
   public StringBuilder add(StringBuilder sb, Element e, boolean clasS, boolean name, boolean sf, boolean desc, boolean child) {
      if (clasS) {
         sb.append('"').append(Formater.CLASS).append("\": \"").append(e.getClass().getName()).append('"');
      }
      if (name) {
         if (clasS) {
            sb.append(", ");
         }
         sb.append('"').append(Formater.NAME).append("\": \"").append(mkSafe(e)).append('"');
      }
      if (sf) {
         if (e.getSuccess() > 0 || e.getFail() > 0) {
            if (clasS || name) {
               sb.append(", ");
            }
            if (e.getSuccess() > 0) {
               sb.append('"').append(Formater.SUCCESS).append("\": \"").append(e.getSuccess()).append('"');
            }
            if (e.getFail() > 0) {
               if (e.getSuccess() > 0) {
                  sb.append(", ");
               }
               sb.append('"').append(Formater.FAIL).append("\": \"").append(e.getFail()).append('"');
            }
         } else {
            sf = false;
         }
      }
      if (desc && !e.description.equals("")) {
         if (clasS || name || sf) {
            sb.append(", ");
         }
         sb.append('"').append(Formater.DESC).append("\": \"").append(mkSafe(e.description)).append('"');
      }
      if (child) {
         sb.append(", \"").append(Formater.CHILDREN).append("\": [");
      }
      return sb;
   }

   /**
    * Adds to every {@code '\\'} and {@code '"'} chars from the
    * {@link #toString()} method an additional {@code '\\'}.
    *
    * @param obj object whichs name will be made safe
    * @return
    */
   public String mkSafe(Object obj) {
      return obj.toString().replaceAll("\\\\", "\\\\\\\\").replaceAll("\"", "\\\\\"");
   }

   public WriteElement tabs(StringBuilder sb, int tabs, String toWrite) {
      sb.append('\n');
      for (int i = tabs; i > 0; i--) {
         sb.append('\t');
      }
      sb.append(toWrite);
      return this;
   }

   /**
    * This method is for Formater class to writeElement Element's children. For
    * different implementations of Element class can occure different ways of
    * writing.
    *
    * @param sb object containing the data those will be written to the
    * coresponding {@link SaveChapter} file
    * @param tabs current amount of spaces on every new line
    * @param currentParent parent of the object providing this method
    * @return the same object as paramter {@code sb}
    */
   public abstract StringBuilder writeElement(StringBuilder sb, int tabs, Chapter currentParent);
}
