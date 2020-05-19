package objects.templates;

/**
 * Basic implementation of the simplest hierarchy object, where it is not necessary to
 * know the parent.
 *
 * @author Josef Litoš
 */
public abstract class BasicElement implements BasicData {

   protected BasicElement(IOSystem.Formatter.Data d) {
      BasicData.isValid(name = d.name);
      sf = d.sf == null ? new int[]{0, 0} : d.sf;
   }

   /**
    * Name of this object.
    */
   protected String name;

   @Override
   public BasicData setName(Container none, String name) {
      this.name = name;
      return this;
   }

   @Override
   public String getName() {
      return name;
   }

   /**
    * Success and Fail for this object.
    */
   protected int[] sf;

   @Override
   public int[] getSF() {
      return sf.clone();
   }

   @Override
   public void addSF(boolean scs) {
      sf[scs ? 0 : 1]++;
   }

   @Override
   public String toString() {
      return getName();
   }
}
