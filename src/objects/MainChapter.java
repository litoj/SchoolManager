/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package objects;

import static IOSystem.Formater.getPath;
import static IOSystem.Formater.createDir;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Head object of hierarchy of all {@link Element} objects. Saves this hierarchy
 * into folder under name of this object in the specified
 * {@link IOSystem.Formater#objDir directory}. Should be {@link #name}d after
 * the school object this hierarchy represents.
 *
 * @author Josef Litoš
 */
public final class MainChapter extends SaveChapter {

   /**
    * @see Element#ELEMENTS
    */
   public static final Set<MainChapter> ELEMENTS = new HashSet<>();
   /**
    * Contains all {@link SaveChapter} objects, which are under this object.
    */
   protected final List<SaveChapter> children = new ArrayList<>();
   /**
    * This file contains everything about this object and its {@link #children}.
    */
   public final File dir;
   Map<String, Integer> pictures = new HashMap<>();

   /**
    * Only this constructor creates the head object of the hierarchy. The
    * hierarchy files are saved in its {@link #dir directory}.
    *
    * @param bd should contain {@link #name} and {@link #sf}
    */
   public MainChapter(IOSystem.Formater.BasicData bd) {
      super(bd, getPath() + bd.name + '\\' + bd.name + ".json");
      ELEMENTS.add(this);
      dir = createDir(getPath() + name);
      createDir(dir.getPath() + "\\Pictures");
      createDir(dir.getPath() + "\\Chapters");
      File pics = new File(dir + "\\pictures.json");
      if (pics.exists()) {
         pictures = (Map<String, Integer>) IOSystem.Formater.serialize(pics.getPath());
      }
   }

   @Override
   public void destroy(Chapter parent) {
      if (parent != null) {
         throw new IllegalArgumentException("This chapter can't have a parent, but got: " + parent);
      }
      ELEMENTS.remove(this);
      dir.delete();
   }

   @Override
   public SaveChapter[] getChildren() {
      return children.toArray(new SaveChapter[children.size()]);
   }

   /**
    * This methos saves this MainChapter and then removes itself from the list.
    */
   public void close() {
      save(this);
      ELEMENTS.remove(this);
   }

   @Override
   public StringBuilder writeElement(StringBuilder sb, int tabs, Chapter cp) {
      tabs(sb, tabs++, "{ ").add(sb, this, true, true, true, true, true);
      IOSystem.Formater.deserializeTo(dir + "\\pictures.json", pictures);
      boolean first = true;
      for (SaveChapter sch : children) {
         if (sch.children.isEmpty()) {
            continue;
         }
         if (first) {
            first = false;
         } else {
            sb.append(',');
         }
         tabs(sb, tabs, "{ ").add(sb, sch, false, true, true, false, false).append(" }");
      }
      return sb.append(" ] }");
   }

   public static void readElement(IOSystem.ReadElement.Source src, Chapter parent) {
      IOSystem.ReadElement.readChildren(src, true, new MainChapter(IOSystem.ReadElement.get(
              src, true, null, true, true, true)), true, false).forEach((i) -> SaveChapter.mkElement(i));
   }
}
