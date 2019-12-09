/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package objects.templates;

/**
 *
 * @author Josef Litoš
 */
public abstract class BasicElement implements BasicData {

   public BasicElement(IOSystem.Formatter.Data d) {
      name = d.name;
      sf = d.sf == null ? new int[]{0, 0} : d.sf;
   }

   /**
    * Name of this object.
    */
   protected String name;

   @Override
   public boolean setName(String name) {
      BasicData.isCorrect(name);
      this.name = name;
      return true;
   }

   @Override
   public String getName() {
      return name;
   }

   /**
    * Success and Fail for this object.
    */
   protected int[] sf;

   @Override
   public int[] getSF() {
      return sf;
   }

   @Override
   public String toString() {
      return getName();
   }
}
