package objects;

import IOSystem.Formater.BasicData;
import static IOSystem.ReadElement.get;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author Josef Litoš
 */
public class Word extends TwoSided<Word> {

   /**
    * @see Element#ELEMENTS
    */
   public static final Map<MainChapter, List<Word>> TRANSLATES = new HashMap<>();
   /**
    * @see Element#ELEMENTS
    */
   public static final Map<MainChapter, List<Word>> ELEMENTS = new HashMap<>();

   /**
    * The only allowed way to create Word objects. Automaticaly controls its
    * existence and returns the proper Word.
    *
    * @param translates translates for this object from one badge
    * @param parent the Chapter which this Word belongs to
    * @return nes
    * {@linkplain #Word(java.lang.String, objects.Chapter, objects.MainChapter, int, int) Word object}
    * if the word doesn't exist yet, otherwise returns the word object with the
    * same name and adds the new translations.
    */
   public static final Word mkElement(BasicData bd, List<BasicData> translates,
           Chapter parent) {
      if (ELEMENTS.get(bd.identifier) == null) {
         ELEMENTS.put(bd.identifier, new ArrayList<>());
         TRANSLATES.put(bd.identifier, new ArrayList<>());
      }
      for (Word w : ELEMENTS.get(bd.identifier)) {
         if (bd.name.equals(w.toString())) {
            w.parentCount++;
            w.addTranslates(translates, parent);
            parent.children.add(w);
            return w;
         }
      }
      return new Word(bd, translates, parent);
   }

   /**
    * This constructor is used only to create translates.
    */
   private Word(BasicData bd, Word word, Chapter parent) {
      super(bd, false, TRANSLATES);
      children.put(parent, new Word[]{word});
   }

   /**
    * This constructor is used only to create Words.
    */
   private Word(BasicData bd, List<BasicData> translates, Chapter parent) {
      super(bd, true, ELEMENTS);
      addTranslates(translates, parent);
      parent.children.add(this);
   }

   /**
    * Checks for potencial doubling of translates.
    *
    * @param translates names of translates for this word in the specified
    * Chapter
    * @param parent Chapter containing this word
    * @param sfs number of successes and fails for each of the translates
    */
   private void addTranslates(List<BasicData> translates, Chapter parent) {
      Set<Integer> found = new HashSet<>();
      if (children.get(parent) == null) {
         children.put(parent, new Word[0]);
      }
      Word[] trls = Arrays.copyOf(children.get(parent), translates.size() + children.get(parent).length);
      TRANSLATES.get(identifier).forEach((t) -> {
         for (int j = translates.size() - 1; j >= 0; j--) {
            condition:
            if (translates.get(j).equals(t.toString())) {
               for (Integer i : found) {
                  if (i == j) {
                     break condition;
                  }
               }
               t.parentCount++;
               Word[] chldrn = Arrays.copyOf(t.children.get(parent), 1 + t.children.get(parent).length);
               chldrn[chldrn.length - 1] = this;
               t.children.put(parent, chldrn);
               trls[j] = t;
               found.add(j);
               if (translates.size() == found.size()) {
                  return;
               }
            }
         }
      });
      for (int j = translates.size() - 1; j >= 0; j--) {
         control:
         {
            for (Integer i : found) {
               if (i <= j) {
                  found.remove(i);
                  if (i == j) {
                     break control;
                  }
               }
            }
            trls[j] = new Word(translates.get(j), this, parent);
         }
      }
      children.put(parent, trls);
   }

   @Override
   public void destroy(Chapter parent) {
      if (isMain) {
         for (Word t : children.get(parent)) {
            t.remove(parent, this);
            t.destroy(parent);
         }
         children.remove(parent);
         parent.children.remove(this);
         if (--parentCount == 0) {
            ELEMENTS.get(identifier).remove(this);
         }
      } else {
         if (children.get(parent).length == 0 && --parentCount == 0) {
            TRANSLATES.get(identifier).remove(this);
         }
      }
   }

   @Override
   void remove(Chapter parent, Word toRem) {
      Word[] prev = children.get(parent);
      Word[] chdrn = new Word[prev.length - 1];
      for (int i = 0; i < chdrn.length; i++) {
         if (prev[i] == toRem) {
            chdrn[i] = prev[chdrn.length];
         } else {
            chdrn[i] = prev[i];
         }
      }
      children.put(parent, chdrn);
   }

   @Override
   public Word[] mkArray(int size) {
      return new Word[size];
   }

   public static void readElement(IOSystem.ReadElement.Source src, Chapter parent) {
      BasicData data = get(src, true, parent.identifier, true, true, true);
      List<BasicData> children = IOSystem.ReadElement.readChildren(src, true, data.identifier, true, true);
      mkElement(data, children, parent);
   }
}
