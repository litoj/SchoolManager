/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package IOSystem;

import objects.templates.Element;
import objects.SaveChapter;
import objects.templates.BasicData;
import objects.templates.Container;

/**
 * This class provides many methods used in proccess of saving the data of
 * {@link Element} which extend this class.
 *
 * @author Josef Litoš
 */
public interface WriteElement {

   /**
    * Saves the given object into its respective file.
    *
    * @param toSave {@link SaveChapter} that you want to saveSch
    */
   public static void saveSch(SaveChapter toSave) {
      Formatter.saveFile(toSave.writeData(new StringBuilder(), 0, null).toString(),
              new java.io.File(toSave.getIdentifier().getDir() + "\\Chapters\\" + toSave + ".json"));
   }

   /**
    * Adds multiple tags to the given {@code sb}.
    *
    * @param sb object containing the data those will be written to the
    * coresponding {@link SaveChapter} file
    * @param e the currently written {@link Element}
    * @param parent parent of param {
    * @conde e}
    * @param clasS if {@link Formatter#CLASS class} tag and the coresponding
    * data should be added
    * @param name if {@link Formatter#NAME name} tag and the coresponding data
    * should be added
    * @param sf if {@link Formatter#SUCCESS success} and
    * {@link Formatter#FAIL fail} tags and the coresponding data should be added
    * @param desc if {@link Formatter#DESC decription} tag and the coresponding
    * data should be added
    * @param child if {@link Formatter#CHILDREN children} tag and the
    * coresponding data should be added
    * @return the written form of this object
    */
   default StringBuilder add(StringBuilder sb, BasicData e, Container parent, boolean clasS, boolean name, boolean sf, boolean desc, boolean child) {
      if (clasS) {
         sb.append('"').append(Formatter.CLASS).append("\": \"")
                 .append(e.getClass().getName()).append('"');
      }
      if (name) {
         if (clasS) {
            sb.append(", ");
         }
         sb.append('"').append(Formatter.NAME).append("\": \"")
                 .append(mkSafe(e)).append('"');
      }
      if (sf) {
         if (e.getSF()[0] > 0 || e.getSF()[1] > 0) {
            if (clasS || name) {
               sb.append(", ");
            }
            if (e.getSF()[0] > 0) {
               sb.append('"').append(Formatter.SUCCESS).append("\": \"")
                       .append(e.getSF()[0]).append('"');
            }
            if (e.getSF()[1] > 0) {
               if (e.getSF()[0] > 0) {
                  sb.append(", ");
               }
               sb.append('"').append(Formatter.FAIL).append("\": \"")
                       .append(e.getSF()[1]).append('"');
            }
         } else {
            sf = false;
         }
      }
      if (desc && e.getDesc(parent) != null && !e.getDesc(parent).equals("")) {
         if (clasS || name || sf) {
            sb.append(", ");
         }
         sb.append('"').append(Formatter.DESC).append("\": \"")
                 .append(mkSafe(e.getDesc(parent))).append('"');
      }
      if (child) {
         sb.append(", \"").append(Formatter.CHILDREN).append("\": [");
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
   default String mkSafe(Object obj) {
      return obj.toString().replaceAll("\\\\", "\\\\\\\\").replaceAll("\"", "\\\\\"");
   }

   default WriteElement tabs(StringBuilder sb, int tabs, String toWrite) {
      sb.append('\n');
      for (int i = tabs; i > 0; i--) {
         sb.append('\t');
      }
      sb.append(toWrite);
      return this;
   }

   /**
    * This method is for Formatter class to writeData Element's children. For
    * different implementations of Element class can occure different ways of
    * writing.
    *
    * @param sb object containing the data those will be written to the
    * coresponding {@link SaveChapter} file
    * @param tabs current amount of spaces on every new line
    * @param currentParent parent of the object providing this method
    * @return the same object as paramter {@code sb} or null, if nothing has
    * been added
    */
   StringBuilder writeData(StringBuilder sb, int tabs, objects.templates.Container currentParent);
}
