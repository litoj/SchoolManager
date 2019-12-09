/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package objects;

import java.util.Arrays;
import IOSystem.Formatter;
import IOSystem.Formatter.Data;
import java.util.LinkedList;
import objects.templates.BasicData;
import objects.templates.Container;

/**
 * Basic element of the school object project element hierarchy. Defines the
 * basic data that every of its instances must have.
 *
 * @see MainChapter
 * @author Josef Litoš
 */
public class TestingClass {

   public static void main(String[] args) {
      char D = new java.io.File("").getAbsolutePath().charAt(0);
      Formatter.loadSettings();
      String imgs = "děložné rostliny\\";
      MainChapter mch = new MainChapter(new Data("try", null, 2, 3));
      SaveChapter sch1 = (SaveChapter) mkE(mch, null, SaveChapter.mkElement(new Data("8.5.", mch, "test", mch)));
      Chapter ch1 = (Chapter) mkE(sch1, mch, new Chapter(new Data("Society", mch, 6, 7, "", sch1)));
      mkE(sch1, mch, Word.mkElement(new Data("hard", mch, sch1), list(new Data("těžk/ý/á/é", mch, 1, 5, sch1), new Data("těžce", mch, sch1))));
      mkE(sch1, mch, Word.mkElement(new Data("hardly", mch, sch1), list(new Data("sotva", mch, sch1), new Data("stěží", mch, sch1))));
      mkE(ch1, sch1, Word.mkElement(new Data("hard", mch, ch1), list(new Data("sotva", mch, ch1), new Data("náročn\"/ý/á/é/ě", mch, ch1))));
      mkE(sch1, mch, Word.mkElement(new Data("hardly", mch, sch1), list(new Data("test1", mch, sch1), new Data("test2", mch, sch1))));
      Chapter ch2 = (Chapter) mkE(sch1, mch, new Chapter(new Data("noShow", mch, sch1)));
      Picture pic = (Picture) mkE(ch2, sch1, Picture.mkElement(new Data("bříza", mch, "biologie", ch2), list(new Data(imgs + "bříza bělokorá 5.jpg", mch, ch2),
              new Data(imgs + "bříza bělokorá 4.jpg", mch, ch2))));
      SaveChapter sch2 = (SaveChapter) mkE(mch, null, SaveChapter.mkElement(new Data("6.8.", mch, 4, 5, mch)));
      Picture pic2 = (Picture) mkE(sch1, mch, Picture.mkElement(new Data("bříza bělokorá", mch, sch1), list(new Data(imgs + "bříza bělokorá 2.jpg", mch, sch1),
              new Data(imgs + "bříza bělokorá 3.jpg", mch, sch1)), true));
      SaveChapter sch3 = (SaveChapter) mkE(mch, null, SaveChapter.mkElement(new Data("reftest", mch)));
      mkE(sch3, mch, Reference.mkElement(pic, list(mch, sch3), new Container[]{mch, sch1, ch2})).isEmpty(pic);
      mkE(sch1, mch, Picture.mkElement(new Data("bříza", mch, sch1), list(new Data(imgs + "bříza bělokorá 1.jpg", mch, sch1))));
      pic2.setName("bříza");
      pic.removeChild(ch2, pic.children.get(ch2).get(1));
      pic2.setName("nothing");
      pic.removeChild(ch2, pic.children.get(ch2).get(0));
      Picture.clean(mch);
      if (!mch.setName("finalTouches")) {
         mch.save(null);
      }
   }

   static <T> LinkedList<T> list(T... d) {
      LinkedList<T> l = new LinkedList<>();
      l.addAll(Arrays.asList(d));
      return l;
   }

   public static BasicData mkE(Container par, Container parPar, BasicData src) {
      if (!par.hasChild(parPar, src)) {
         par.putChild(parPar, src);
      }
      return src;
   }
}
