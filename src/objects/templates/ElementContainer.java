/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package objects.templates;

import IOSystem.Formatter;
import java.util.LinkedList;
import java.util.List;

/**
 *
 * @author Josef Litoš
 */
public abstract class ElementContainer extends Element implements Container {

   public ElementContainer(Formatter.Data d) {
      super(d);
   }

   /**
    * Contains all objects, which belong to this object.
    */
   public final java.util.Map<Container, java.util.List<BasicData>> children = new java.util.HashMap<>();

   @Override
   public BasicData[] getChildren() {
      List<BasicData> l = new LinkedList<>();
      children.keySet().stream().forEach((c) -> l.addAll(children.get(c)));
      return l.toArray(new BasicData[0]);
   }

   @Override
   public BasicData[] getChildren(Container c) {
      return children.get(c)==null?null:children.get(c).toArray(new BasicData[0]);
   }

   @Override
   public void putChild(Container c, BasicData e) {
      if (children.get(c) == null) {
         children.put(c, new LinkedList<>());
      }
      children.get(c).add(e);
   }

   @Override
   public boolean removeChild(Container c, BasicData e) {
      return children.get(c).remove(e);
   }

   @Override
   public Container removeChild(BasicData e) {
      for (Container c : children.keySet()) {
         if (children.get(c).remove(e)) {
            return c;
         }
      }
      return null;
   }
}
