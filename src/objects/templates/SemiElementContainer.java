/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package objects.templates;

import IOSystem.Formatter.Data;

/**
 *
 * @author Josef Litoš
 */
public abstract class SemiElementContainer extends BasicElement implements Container {

   /**
    * Contains its children.
    */
   protected final java.util.List<BasicData> children = new java.util.ArrayList<>();

   @Override
   public BasicData[] getChildren() {
      return children.toArray(new BasicData[children.size()]);
   }

   @Override
   public BasicData[] getChildren(Container c) {
      return getChildren();
   }

   @Override
   public void putChild(Container none, BasicData e) {
      children.add(e);
   }

   @Override
   public boolean removeChild(Container c, BasicData e) {
      removeChild(e);
      return true;
   }

   @Override
   public Container removeChild(BasicData e) {
      children.remove(e);
      return null;
   }

   /**
    * The description for this object.
    */
   protected String description;

   @Override
   public String getDesc(Container c) {
      return description;
   }

   @Override
   public String putDesc(Container c, String desc) {
      String old = description;
      description = desc;
      return old;
   }

   protected SemiElementContainer(Data d) {
      super(d);
      description = d.description;
   }
}
