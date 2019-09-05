/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package objects;

import IOSystem.Formater.BasicData;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author InvisibleManCZ
 * @param <T> object which has two versions, but only one is supposed to
 * manipulate with
 */
public abstract class TwoSided<T extends TwoSided> extends Element {

   protected final Map<Chapter, T[]> children = new HashMap<>();
   protected final boolean isMain;
   protected int parentCount;

   protected TwoSided(BasicData bd, boolean isMain, Map<MainChapter, List<T>> NET) {
      super(bd);
      this.isMain = isMain;
      NET.get(identifier).add((T) this);
      parentCount = 1;
   }

   @Override
   public T[] getChildren() {
      List<T> chdrn = new ArrayList<>();
      children.values().forEach((t) -> chdrn.addAll(Arrays.asList(t)));
      return chdrn.toArray(mkArray(chdrn.size()));
   }

   abstract T[] mkArray(int size);

   public void removeChild(T child, Chapter parent) {
      if (isMain) {
         child.destroy(parent);
         remove(parent, child);
      } else {
         throw new IllegalArgumentException("Child can be removed only by main Picture");
      }
   }

   abstract void remove(Chapter parent, T child);

   @Override
   public StringBuilder writeElement(StringBuilder sb, int tabs, Element cp) {
      tabs(sb, tabs++, "{ ").add(sb, this, true, true, true, true, true);
      boolean first = true;
      for (T e : children.get((Chapter) cp)) {
         if (first) {
            first = false;
         } else {
            sb.append(',');
         }
         tabs(sb, tabs, "{ ").add(sb, e, false, true, true, true, false).append(" }");
      }
      return sb.append(" ] }");
   }
}
