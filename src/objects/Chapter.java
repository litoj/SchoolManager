/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package objects;

import IOSystem.Formater.BasicData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Instances of this class can contain any other {@link Element} instances.
 *
 * @author Josef Litoš
 */
public class Chapter extends Element {

   /**
    * @see Element#ELEMENTS
    */
   public static final Map<MainChapter, List<Chapter>> ELEMENTS = new HashMap<>();

   protected final List<Element> children = new ArrayList<>();

   /**
    *
    * @param bd doesn't have to have {@link #sf} or {@link #description}
    * @param parent the parent of this Chapter
    */
   public Chapter(BasicData bd, Chapter parent) {
      super(bd);
      parent.children.add(this);
      if (ELEMENTS.get(identifier) == null) {
         ELEMENTS.put(identifier, new LinkedList<>());
      }
      ELEMENTS.get(identifier).add(this);
   }

   /**
    * This constructor can be used only to create {@link SaveChapter} and its
    * extensions.
    */
   Chapter(BasicData bd) {
      super(bd);
   }

   public boolean hasChild() {
      return !children.isEmpty();
   }

   @Override
   public void destroy(Chapter parent) {
      parent.children.remove(this);
      children.forEach((E) -> {
         E.destroy(this);
      });
      ELEMENTS.get(identifier).remove(this);
   }

   @Override
   public Element[] getChildren() {
      return children.toArray(new Element[children.size()]);
   }

   @Override
   public StringBuilder writeElement(StringBuilder sb, int tabs, Chapter currentParent) {
      tabs(sb, tabs++, "{ ").add(sb, this, true, true, true, true, true);
      boolean first = true;
      for (Element e : children) {
         if (first) {
            first = false;
         } else {
            sb.append(',');
         }
         e.writeElement(sb, tabs, this);
      }
      return sb.append(" ] }");
   }

   public static void readElement(IOSystem.ReadElement.Source src, Chapter parent) {
      IOSystem.ReadElement.loadChildren(src, new Chapter(IOSystem.ReadElement.get(
              src, true, parent.identifier, true, true, true), parent));
   }
}
