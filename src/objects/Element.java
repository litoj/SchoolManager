/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package objects;

import IOSystem.Formater;
import IOSystem.Formater.BasicData;
import java.util.List;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Basic element of the school object project element hierarchy. Defines the
 * basic data for every of its instances.
 *
 * @see MainChapter
 * @author Josef Litoš
 */
public abstract class Element extends IOSystem.WriteElement {

   /**
    * This map contains every instance of this class divided under MainChapters
    * as their identifiers.
    */
   public static final Map<MainChapter, List<Element>> ELEMENTS = new HashMap<>();

   /**
    * Name of this object.
    */
   String name;

   public void setName(String name) {
      this.name = name;
   }

   /**
    * The head hierarchy object which this object belongs to.
    */
   public final MainChapter identifier;
   /**
    * Success and Fail for this object
    */
   protected int[] sf;
   /**
    * The description for this object.
    */
   public String description;

   public int getSuccess() {
      return sf[0];
   }

   public int getFail() {
      return sf[1];
   }

   protected Element(IOSystem.Formater.BasicData bd) {
      name = bd.name;
      identifier = bd.identifier;
      if (bd.sf == null) {
         sf = new int[]{0, 0};
      } else if (bd.sf[0] >= 0 || bd.sf[1] >= 0) {
         sf = bd.sf;
      } else {
         throw new IllegalArgumentException("Values of variable sf can't be less than 0!\nGot " + sf[0] + ", " + sf[1]);
      }
      description = (bd.description == null ? "" : bd.description);
   }

   /**
    *
    * @param path the path to the Word with translate or Picture
    * @param did <code>true</code> if the user matched correctly
    */
   public static final void success(List<Element> path, boolean did) {
      for (Element e : path) {
         e.sf[did ? 0 : 1]++;
      }
   }

   /**
    *
    * @return % value of success in matching the {@link #children children}
    */
   public float success() {
      return sf[0] / (sf[0] + sf[1]) * 100;
   }

   /**
    * Removes itself from the specified Chapter and from the net.
    *
    * @param parent the Chapter which this Element is being removed from
    */
   abstract public void destroy(Chapter parent);

   /**
    *
    * @return Array of children in the specified Element
    */
   abstract public Element[] getChildren();

   @Override
   public String toString() {
      return name;
   }

   public static void main(String[] args) {
      char D = 'D';
      Formater.loadSettings();
      //Formater.changeDir(new File(""));
      String imgs = D + ":\\skola\\poznavacka\\k poznání\\";
      MainChapter mch = new MainChapter(new BasicData("newSetts", null, 2, 3, "new picture parenCount use"));
      SaveChapter sch1 = SaveChapter.mkElement(new BasicData("8.5.", mch));
      Chapter ch1 = new Chapter(new BasicData("Society", mch, 6, 7), sch1);
      Word.mkElement(new BasicData("hard", mch), Arrays.asList(new BasicData("těžk/ý/á/é", mch, 1, 5), new BasicData("těžce", mch, "Description test")), sch1);
      Word.mkElement(new BasicData("hardly", mch), Arrays.asList(new BasicData("sotva", mch), new BasicData("stěží", mch)), sch1);
      Word.mkElement(new BasicData("hard", mch), Arrays.asList(new BasicData("tvrd/ý/á/é/ě", mch), new BasicData("náročn\"/ý/á/é/ě", mch)), ch1);
      Picture pic = Picture.mkElement(new BasicData("broskvoň", mch, "biologie"), Arrays.asList(new BasicData(D + ":\\skola\\poznavacka\\asdf.jpg", mch),
              new BasicData(D + ":\\skola\\poznavacka\\_vyr_1013boskvon-Catherina.jpg", mch)), ch1, true);
      //SaveChapter sch2 = SaveChapter.mkElement(new BasicData("6.8.", mch, 4, 5));
      Reference.mkElement(sch1, new Chapter(new BasicData("refSChTest", mch), ch1), null);
      Picture pic2 = Picture.mkElement(new BasicData("broskve", mch), Arrays.asList(new BasicData(imgs + "broskvoň 2.jpg", mch),
              new BasicData(imgs + "broskvoň 3.jpg", mch)), sch1, true);
      Reference.mkElement(pic, SaveChapter.mkElement(new BasicData("reftest", mch, 20, 3)), "8.5.");
      Picture.mkElement(new BasicData("broskvoň", mch), Arrays.asList(new BasicData(imgs + "broskvoň 1.jpg", mch)), sch1, true);
      pic.removeChild(pic.children.get(ch1)[1], ch1);
      pic2.setName("broskvoň");
      //((SaveChapter)e.get(1)).destroy(null);
      IOSystem.WriteElement.saveAll(mch);
   }

   public static void readElement(IOSystem.ReadElement.Source src, Chapter parent) {
      throw new UnsupportedOperationException("Can be called only in non-abstract objects");
   }
}
