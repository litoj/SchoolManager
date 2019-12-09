/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package objects.templates;

import objects.MainChapter;

/**
 *
 * @author Josef Litoš
 */
public interface BasicData extends IOSystem.WriteElement {

   public static boolean isCorrect(String name) {
      if (name.length() > 150) {
         throw new IllegalArgumentException("Name can't be longer than 150 characters");
      } else if ("/|\\:\"?*".chars().anyMatch((ch) -> name.contains("" + (char) ch))) {
         throw new IllegalArgumentException("Name can't contain /|\\:\"?*");
      }
      return true;
   }

   boolean destroy(Container parent);

   default boolean isEmpty(Container c) {
      return false;
   }

   MainChapter getIdentifier();

   /**
    * Alteres the current name of this object.
    *
    * @param name the new name for this object
    * @return if the name has been set successfully
    */
   default boolean setName(String name) {
      return isCorrect(name);
   }

   String getName();

   default float getRatio() {
      int[] sf = getSF();
      return sf[0] / (sf[0] + sf[1]) * 100;
   }

   int[] getSF();

   String getDesc(Container c);

   String putDesc(Container c, String desc);
}
