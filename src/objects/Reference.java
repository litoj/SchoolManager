/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package objects;

import static IOSystem.ReadElement.get;
import static IOSystem.ReadElement.loadSCh;
import static IOSystem.ReadElement.next;
import IOSystem.Formater.BasicData;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * References to another {@link Element} instance named after the reference.
 *
 * @author Josef Litoš
 */
public class Reference extends Element {

   /**
    * @see Element#ELEMENTS
    */
   public static final Map<MainChapter, List<Reference>> ELEMENTS = new HashMap<>();

   public final String originalContainer;
   public final Element reference;
   int parentCount;

   /**
    *
    * @param ref referenced element
    * @param parent parent of this object
    * @param origin name of the {@link SaveChapter} the argument {@code ref} is
    * saved in
    * @return
    */
   public static final Reference mkElement(Element ref, Chapter parent, String origin) {
      if (ref instanceof MainChapter) {
         throw new IllegalArgumentException("Hierarchy can't be referenced!");
      }
      if (ELEMENTS.get(ref.identifier) == null) {
         ELEMENTS.put(ref.identifier, new ArrayList<>());
      }
      for (Reference r : ELEMENTS.get(ref.identifier)) {
         if (ref == r.reference) {
            r.parentCount++;
            parent.children.add(r);
            return r;
         }
      }
      return new Reference(ref, parent, origin);
   }

   private Reference(Element ref, Chapter parent, String origin) {
      super(new BasicData(ref.name, ref.identifier));
      this.reference = ref;
      parent.children.add(this);
      originalContainer = origin;
      parentCount = 1;
      ELEMENTS.get(identifier).add(this);
   }

   @Override
   public void destroy(Chapter parent) {
      parent.children.remove(this);
      if (--parentCount == 0) {
         ELEMENTS.get(identifier).remove(this);
      }
   }

   @Override
   public Element[] getChildren() {
      return new Element[]{reference};
   }

   @Override
   public StringBuilder writeElement(StringBuilder sb, int tabs, Chapter cp) {
      tabs(sb, tabs, "{ ").add(sb, this, true, true, false, false, false).append(", \"refCls\": \"")
              .append(reference.getClass().getName());
      if (!(reference instanceof SaveChapter)) {
         sb.append("\", \"origin\": \"").append(mkSafe(originalContainer));
      }
      return sb.append("\" }");
   }

   public static void readElement(IOSystem.ReadElement.Source src, Chapter parent) {
      BasicData data = get(src, true, parent.identifier, false, false, false, "refCls");
      String org = null;
      if (data.tagVals[0].equals("objects.SaveChapter")) {
         org = data.name;
      } else {
         if (next(src, ',').equals("origin")) {
            org = next(src);
         }
      }
      try {
         SaveChapter origin;
         for (SaveChapter sch : SaveChapter.ELEMENTS.get(parent.identifier)) {
            if (org.equals(sch.toString())) {
               origin = sch;
               if (!sch.loaded) {
                  loadSCh(new File(origin.save), parent.identifier);
                  break;
               }
            }
         }
      } catch (IllegalArgumentException iae) {
         if (!iae.getMessage().contains("']'")) {
            throw iae;
         }
      }
      Map<MainChapter, List<Element>> elements = null;
      try {
         elements = (Map<MainChapter, List<Element>>) Class.forName(data.tagVals[0]).getDeclaredField("ELEMENTS").get(null);
      } catch (ClassNotFoundException | NoSuchFieldException | SecurityException
              | IllegalArgumentException | IllegalAccessException ex) {
         Logger.getLogger(Reference.class.getName()).log(Level.SEVERE, null, ex);
      }
      for (Element e : elements.get(parent.identifier)) {
         if (data.name.equals(e.toString())) {
            mkElement(e, parent, org);
            return;
         }
      }
      throw new IllegalArgumentException("Reference to " + data.name + " of type " + data.tagVals[0] + "doesn't exist in file " + org);
   }

}
