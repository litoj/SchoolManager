/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package objects.templates;

import java.util.HashMap;
import java.util.Map;
import objects.MainChapter;

/**
 *
 * @author Josef Litoš
 */
public abstract class Element extends BasicElement {

   public Element(IOSystem.Formatter.Data d) {
      super(d);
      identifier = d.identifier;
      description.put(d.par, d.description);
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
    * The description for this object.
    */
   protected final Map<Container, String> description = new HashMap<>();

   @Override
   public String getDesc(Container c) {
      return description.get(c);
   }

   @Override
   public String putDesc(Container c, String desc) {
      return description.put(c, name);
   }
}
