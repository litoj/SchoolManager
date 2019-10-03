/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package objects;

import IOSystem.Formater.BasicData;
import java.io.File;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Heading chapter belonging to a specified {@link #identifier hierarchy}. Every
 * instance of this class saves into its own file.
 *
 * @see Chapter
 *
 * @author InvisibleManCZ
 */
public class SaveChapter extends Chapter {

   public static final Map<MainChapter, List<SaveChapter>> ELEMENTS = new HashMap<>();

   public boolean loaded;
   public final String save;

   public static final SaveChapter mkElement(BasicData bd) {
      if (ELEMENTS.get(bd.identifier) == null) {
         ELEMENTS.put(bd.identifier, new LinkedList<>());
      }
      for (SaveChapter sch : ELEMENTS.get(bd.identifier)) {
         if (bd.name.equals(sch.toString())) {
            sch.loaded = true;
            return sch;
         }
      }
      return new SaveChapter(bd);
   }

   private SaveChapter(BasicData bd) {
      super(bd);
      loaded = true;
      ELEMENTS.get(identifier).add(this);
      identifier.children.add(this);
      save = identifier.dir.getAbsolutePath() + "\\Chapters\\" + name + ".json";
   }

   /**
    * This constructor can be used only to create {@link MainChapter}.
    *
    * @param save {@link #save}
    */
   SaveChapter(BasicData bd, String save) {
      super(bd);
      this.save = save;
   }

   @Override
   public void destroy(Chapter parent) {
      if (parent != null && parent != identifier) {
         throw new IllegalArgumentException("This chapter can't have different parent than identifier, but got: " + parent);
      }
      identifier.children.remove(this);
      ELEMENTS.get(identifier).remove(this);
      new File(save).delete();
   }

   @Override
   public StringBuilder writeElement(StringBuilder sb, int tabs, Chapter currentParent) {
      tabs(sb, tabs++, "{ ").add(sb, this, true, true, false, true, true);
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
      IOSystem.ReadElement.loadChildren(src, mkElement(IOSystem.ReadElement.get(
              src, true, (MainChapter) parent, false, true, true)));
   }
}
