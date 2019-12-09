/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package objects;

import IOSystem.Formatter.Data;
import java.util.List;
import objects.templates.BasicData;
import objects.templates.Container;

/**
 * Instances of this class can contain any other hierarchy objects.
 *
 * @author Josef Litoš
 */
public class Chapter extends objects.templates.SemiElementContainer {

   /**
    * read-only data
    */
   public static final java.util.Map<MainChapter, List<Chapter>> ELEMENTS = new java.util.HashMap<>();

   protected Container parent;

   @Override
   public Container removeChild(BasicData e) {
      children.remove(e);
      return parent;
   }

   /**
    * The head hierarchy object which this object belongs to.
    */
   protected final MainChapter identifier;

   @Override
   public MainChapter getIdentifier() {
      return identifier;
   }

   /**
    *
    * @param d must contain {@link #name name} and
    * {@link #identifier identifier} and mainly the parent of this object
    */
   public Chapter(Data d) {
      super(d);
      parent = d.par;
      identifier = d.identifier;
      if (ELEMENTS.get(identifier) == null) {
         ELEMENTS.put(identifier, new java.util.LinkedList<>());
      }
      ELEMENTS.get(identifier).add(this);
   }

   @Override
   public boolean destroy(Container parent) {
      ELEMENTS.get(identifier).remove(this);
      return super.destroy(parent);
   }

   @Override
   public StringBuilder writeData(StringBuilder sb, int tabs, Container cp) {
      tabs(sb, tabs++, "{ ").add(sb, this, cp, true, true, true, true, true);
      return writeData0(sb, tabs, cp);
   }

   public static BasicData readData(IOSystem.ReadElement.Source src, Container parent) {
      Chapter ch = new Chapter(IOSystem.ReadElement.get(src, true, true, true, true, parent));
      IOSystem.ReadElement.loadChildren(src, ch).forEach((e) -> ch.putChild(parent, e));
      return ch;
   }
}
