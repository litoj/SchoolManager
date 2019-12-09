/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package objects.templates;

import java.util.Arrays;

/**
 *
 * @author Josef Litoš
 */
public interface Container extends BasicData {

   BasicData[] getChildren();

   BasicData[] getChildren(Container c);

   void putChild(Container c, BasicData e);

   boolean removeChild(Container c, BasicData e);

   Container removeChild(BasicData e);

   default boolean hasChild(BasicData e) {
      return Arrays.asList(getChildren()).stream().anyMatch((bd) -> e.equals(bd));
   }

   default boolean hasChild(Container par, BasicData e) {
      return getChildren(par) == null ? false : Arrays.asList(getChildren(par)).stream().anyMatch((bd) -> e.equals(bd));
   }

   @Override
   default boolean destroy(Container c) {
      Arrays.stream(getChildren(c)).forEach((e) -> e.destroy(this));
      return c.removeChild(this) != null;
   }

   @Override
   default boolean isEmpty(Container c) {
      for (BasicData bd : getChildren(c)) {
         if (!bd.isEmpty(this)) {
            return false;
         }
      }
      return true;
//      return Arrays.stream(getChildren(c)).allMatch((e) -> e.isEmpty(this));
   }

   default StringBuilder writeData0(StringBuilder sb, int tabs, Container cp) {
      //tabs(sb, tabs++, "{ ").add(sb, this, cp, true, true, true, true, true); this has to be altered with what is needed to be written, than call this method
      boolean first = true;
      for (BasicData bd : getChildren(cp)) {
         if (!bd.isEmpty(this)) {
            if (first) {
               first = false;
            } else {
               sb.append(',');
            }
            bd.writeData(sb, tabs, this);
         }
      }
      return sb.append(" ] }");
   }
}
