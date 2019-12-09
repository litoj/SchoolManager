package objects;

import IOSystem.Formatter.Data;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import objects.templates.BasicData;
import objects.templates.Container;

/**
 * Contains name and translates for its name. The translates are sorted under
 * the given {@link Chapter}, which they belong to.
 *
 * @author Josef Litoš
 */
public class Word extends TwoSided<Word> {

   /**
    * read-only data
    */
   public static final Map<MainChapter, List<Word>> TRANSLATES = new HashMap<>();
   public static final Map<MainChapter, List<Word>> ELEMENTS = new HashMap<>();

   /**
    * The only allowed way to create Word objects. Automaticaly controls its
    * existence and returns the proper Word.
    *
    * @param bd all the necessary data to create new {@link Word} object
    * @param translates translates for this object under one {@link Chapter}
    * must contain their {@link Data#name name} each
    * @return new
    * {@linkplain #Word(java.lang.String, objects.Chapter, objects.MainChapter, int, int) Word object}
    * if the word doesn't exist yet, otherwise returns the word object with the
    * same name and adds the new translations.
    */
   public static final Word mkElement(Data bd, List<Data> translates) {
      if (ELEMENTS.get(bd.identifier) == null) {
         ELEMENTS.put(bd.identifier, new LinkedList<>());
         TRANSLATES.put(bd.identifier, new LinkedList<>());
      }
      for (Word w : ELEMENTS.get(bd.identifier)) {
         if (bd.name.equals(w.name)) {
            if (w.children.get(bd.par) == null) {
               w.children.put(bd.par, new LinkedList<>());
               w.parentCount++;
            }
            w.addTranslates(translates, bd.par);
            return w;
         }
      }
      return new Word(bd, translates);
   }

   /**
    * Creates all children of this object. Checks for potencial doubling of
    * translates.
    *
    * @param translates all the necessary data for every new translate created
    * @param parent Chapter containing this word
    * @param sfs number of successes and fails for each of the translates
    */
   private void addTranslates(List<Data> translates, Container parent) {
      TRANSLATES.get(identifier).forEach((t) -> {
         for (int i = translates.size()-1;i>=0;i--) {
            condition:
            if (translates.get(i).name.equals(t.name)) {
               if (t.children.get(parent) != null) {
                  for (BasicData w : t.children.get(parent)) {
                     if (w == this) {
                        break condition;
                     }
                  }
               } else {
                  t.parentCount++;
                  t.children.put(parent, new LinkedList<>());
               }
               t.children.get(parent).add(this);
               children.get(parent).add(t);
               translates.remove(translates.get(i));
            }
         }
      });
      translates.forEach((d) -> children.get(parent).add(new Word(d, this)));
   }

   /**
    * This constructor is used only to create translates.
    */
   private Word(Data bd, Word word) {
      super(bd, false, TRANSLATES);
      children.put(bd.par, new LinkedList<>(Arrays.asList(new Word[]{word})));
   }

   /**
    * This constructor is used only to create main instance of this class.
    */
   private Word(Data bd, List<Data> translates) {
      super(bd, true, ELEMENTS);
      children.put(bd.par, new LinkedList<>());
      addTranslates(translates, bd.par);
   }

   @Override
   public boolean destroy(Container parent) {
      if (isMain) {
         for (BasicData t : children.get(parent)) {
            ((Word) t).remove1(parent, this);
            t.destroy(parent);
         }
         children.remove(parent);
         parent.removeChild(this);
         if (--parentCount == 0) {
            ELEMENTS.get(identifier).remove(this);
         }
      } else {
         if (children.get(parent).isEmpty() && --parentCount == 0) {
            TRANSLATES.get(identifier).remove(this);
         }
      }
      return true;
   }

   @Override
   public boolean setName(String name) {
      if (this.name.equals(name) || children.isEmpty() || !isMain) {
         return false;
      }
      for (Word w : ELEMENTS.get(identifier)) {
         if (w.name.equals(name)) {
            for (Container ch : children.keySet()) {
               Container parpar = ch.removeChild(this);
               if (!ch.hasChild(w)) {
                  ch.putChild(parpar, w);
                  w.children.put(ch, children.get(ch));
                  w.parentCount++;
               } else {
                  w.children.get(ch).addAll(Arrays.asList(getChildren(ch)));
               }
            }
            ELEMENTS.get(identifier).remove(this);
            return true;
         }
      }
      this.name = name;
      return true;
   }

   public static void readElement(IOSystem.ReadElement.Source src, Container parent) {
      Data data = IOSystem.ReadElement
              .get(src, true, true, true, true, parent);
      List<Data> children = IOSystem.ReadElement
              .readChildren(src, true, true, true, parent);
      mkElement(data, children);
   }
}
