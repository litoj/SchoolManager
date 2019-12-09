/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package testing;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import objects.TwoSided;

/**
 * Creates and manages a test for any {@link TwoSided} object. Resets all values
 * using {@link #setTested(int, testing.Timer, int, java.util.List) }.
 *
 * @author Josef Litoš
 * @param <T> type of {@link objects.Element} to be tested
 */
public class Test<T extends TwoSided> {

   /**
    * Default duration of a test in seconds.
    */
   private static int DEFAULT_TIME;

   public static int getDefaultTime() {
      if (DEFAULT_TIME <= 0) {
         try {
            return Integer.parseInt(IOSystem.Formatter.getSetting("defaultTestTime"));
         } catch (NumberFormatException | NullPointerException e) {
            setDefaultTime(180);
            return 180;
         }
      }
      return DEFAULT_TIME;
   }

   public static void setDefaultTime(int newTime) {
      if (newTime < 1) {
         throw new IllegalArgumentException("Duration of any test can't be less than 1 second!");
      }
      IOSystem.Formatter.putSetting("defaultTestTime", "" + (DEFAULT_TIME = newTime));
   }
   /**
    * {@code true} - clever choosing when creating a test
    */
   private static boolean CLEVER_RND = true;

   public static boolean isClever() {
      return CLEVER_RND;
   }

   public static void setClever(boolean isClever) {
      IOSystem.Formatter.putSetting("randomType", "" + (CLEVER_RND = isClever));
   }

   private List<T> source;
   private boolean[] answered;
   private int time;
   private Timer doOnSec;

   /**
    * Prepares everything for the next test.
    *
    * @param amount the amount of object to be randomly picked for the test or
    * -1 if all of the words are supposed to be used
    * @param doOnSec action to be done every second of the test, last call on
    * time out
    * @param timeSec duration of a test
    * @param source the array of objects for the test
    */
   public void setTested(int amount, Timer doOnSec, int timeSec, List<T> source) {
      if (amount == -1) {
         amount = source.size();
      } else if (amount < 1 || amount > source.size()) {
         throw new IllegalArgumentException("Amount of tested elements can't be less than 1\nnor above the size of the source!");
      } else if (timeSec < 1) {
         throw new IllegalArgumentException("Duration of the test can't be less than 1 second!");
      } else if (source.size() < 2) {
         throw new IllegalArgumentException("The source must contain more than one element");
      }
      this.source = CLEVER_RND ? cleverTest(source, amount) : rndTest(source, amount);
      time = timeSec;
      this.doOnSec = doOnSec;
      answered = new boolean[source.size()];
   }

   private List<T> rndTest(List<T> source, int amount) {
      Random rd = new Random(System.nanoTime());
      int pos = rd.nextInt(source.size());
      T current = source.get(pos);
      for (int i = 0; i < source.size(); i++) {
         current = source.set(pos, current);
      }
      for (int i = source.size(); i > amount; i--) {
         source.remove(i);
      }
      return source;
   }

   private List<T> cleverTest(List<T> source, int amount) {
      ToSort<T> num = (t) -> t.getSF()[0] + t.getSF()[1];
      float i = -1;
      LinkedList<LinkedList<T>> tested = new LinkedList<>();
      LinkedList<T> part = null;
      for (T t : MergeSort.sort(true, source, num)) {
         if (num.getNum(t) == i) {
            part.add(t);
         } else {
            tested.add(part);
            i = num.getNum(t);
            part = new LinkedList<>();
            part.add(t);
         }
      }
      source.clear();
      for (LinkedList<T> x : tested) {
         if (source.size() + x.size() < amount) {
            source.addAll(x);
         } else {
            for (T t : MergeSort.sort(true, source, (t) -> t.getRatio())) {
               if (source.size() + 1 < amount) {
                  source.add(t);
               } else {
                  break;
               }
            }
            break;
         }
      }
      return rndTest(source, amount);
   }

   /**
    * Starts the test.
    */
   public void startTest() {
      new Thread(() -> {
         try {
            for (; time >= 0; time--) {
               if (doOnSec != null) {
                  doOnSec.doOnSec(time);
               }
               Thread.sleep(1000);
            }
         } catch (InterruptedException ex) {
         }
      }).start();
   }

   /**
    * Tests if user answered correctly.
    *
    * @param source {@link objects.Element} that is the main answer, but doesn't
    * have to
    * @param answer the answer of the user
    * @return {@code true} if the user matched the translation for all of the
    * source Element's children (in most cases if matches its name), otherwise
    * {@code false}
    *
    */
   public boolean isAnswer(T source, String answer) {
      if (answered[this.source.indexOf(source)]) {
         throw new IllegalArgumentException("Can't answer more than once, only one try allowed!");
      } else {
         answered[this.source.indexOf(source)] = true;
      }
      if (answer == null || answer.isEmpty()) {
         return false;
      }
      return Arrays.stream(source.getChildren()).allMatch((t) -> Arrays.stream(
              ((TwoSided) t).getChildren()).anyMatch((ch) -> Arrays.stream(
              NameReader.readName(ch)).anyMatch((s) -> s.equals(answer))));
   }

   /**
    * Gives translates for the piced up testing objects.
    *
    * @param pos index of the tested object which translates you want to get
    * @return children of the asked object
    */
   public T[] getTested(int pos) {
      return (T[]) source.get(pos).getChildren();
   }
}
