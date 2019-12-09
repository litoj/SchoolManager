/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package objects;

import java.util.List;
import java.util.Map;
import objects.templates.BasicData;
import objects.templates.Container;
import objects.templates.ElementContainer;

/**
 * Defines the system for objects used in a {@link testing.Test}. They have
 * their name and their other side, both in its own instance of the same class.
 *
 * @author Josef Litoš
 * @param <T> object which has two versions, but only one is manipulatable with
 */
public abstract class TwoSided<T extends TwoSided> extends ElementContainer {

   /**
    * {@code true} if and only if the object is the main one (all its methods
    * can be used).
    */
   protected final boolean isMain;
   protected int parentCount;

   protected TwoSided(IOSystem.Formatter.Data bd, boolean isMain, Map<MainChapter, List<T>> NET) {
      super(bd);
      this.isMain = isMain;
      NET.get(identifier).add((T) this);
      parentCount = 1;
   }

   @Override
   public boolean removeChild(Container parent, BasicData child) {
      if (isMain) {
         child.destroy(parent);
         remove1(parent, (T) child);
         return true;
      }
      throw new IllegalArgumentException("Child can be removed only by main object");
   }

   @Override
   public Container removeChild(BasicData e) {
      if (isMain) {
         for (Container c : children.keySet()) {
            if (children.get(c).remove(e)) {
               e.destroy(c);
               remove1(c, (T) e);
               return c;
            }
         }
      }
      throw new IllegalArgumentException("Child can be removed only by main object");
   }

   /**
    * Called to end the proccess of removing a child from the main object.
    *
    * @param parent where the object is located
    * @param toRem the object to be removed
    */
   protected void remove1(Container parent, T toRem) {
      children.get(parent).remove(toRem);
      if (children.get(parent).isEmpty()) {
         children.remove(parent);
         parent.removeChild(this);
      }
   }

   @Override
   public boolean isEmpty(Container c) {
      return children.get(c) == null || children.get(c).isEmpty();
   }

   @Override
   public StringBuilder writeData(StringBuilder sb, int tabs, Container cp) {
      tabs(sb, tabs++, "{ ").add(sb, this, cp, true, true, true, true, true);
      boolean first = true;
      for (BasicData e : getChildren(cp)) {
         if (first) {
            first = false;
         } else {
            sb.append(',');
         }
         tabs(sb, tabs, "{ ").add(sb, e, cp, false, true, true, true, false).append(" }");
      }
      return sb.append(" ] }");
   }
}
