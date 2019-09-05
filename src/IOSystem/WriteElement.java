/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package IOSystem;

import java.io.File;
import objects.Element;
import objects.MainChapter;
import objects.SaveChapter;

/**
 *
 * @author Josef Litoš
 */
public abstract class WriteElement {

   public static void saveAll(MainChapter toSave) {
      save(toSave);
      for (SaveChapter sch : toSave.getChildren()) {
         if (sch.loaded && sch.hasChild()) {
            save(sch);
         }
      }
   }

   /**
    *
    * @param toSave MainChapter that you want to save to file
    */
   public static void save(SaveChapter toSave) {
      Formater.saveFile(toSave.writeElement(new StringBuilder(), 0, null).toString(), new File(toSave.save));
   }

   /**
    *
    * @param e this object
    * @param clasS
    * @param name
    * @param sf
    * @param desc
    * @param child
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
    * This method is for Formater class to writeElement Element's children, for
    * different implementations of Element class can occure different ways of
    * writing
    *
    * @param tabs current amount of spaces on every new line
    * @param currentParent parent of the object providing this method
    * @return the same object as paramter sb
    */
   public abstract StringBuilder writeElement(StringBuilder sb, int tabs, Element currentParent);
}
