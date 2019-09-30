/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package testing;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import objects.Element;
import objects.TwoSided;

/**
 *
 * @author Josef Litoš
 * @param <T> type of {@link Element} to be tested
 */
public class Test<T extends TwoSided> {

   /**
    * Default duration of any test (in seconds).
    */
   public static int DEFAULT_TIME = 120;

   List<T> source;
   int time;
   Timer doOnSec;

   /**
    * Prepares everything for the next test.
    *
    * @param amount the amount of object to be randomly picked for the test
    * @param doOnSec action to be done every second of the test, last call on
    * time out
    * @param timeSec duration of a test
    * @param source the array of objects for the test
    */
   public void setTested(int amount, Timer doOnSec, int timeSec, List<T> source) {
      if (amount < 1 || amount > source.size()) {
         throw new IllegalArgumentException("Amount of tested elements can't be less than 1\nnor above the size of the source!");
      } else if (timeSec < 1) {
         throw new IllegalArgumentException("Duration of the test can't be less than 1 second!");
      } else if (source.size() < 2) {
         throw new IllegalArgumentException("The source must contain more than one element");
      }
      Random rd = new Random(System.nanoTime());
      for (int i = 0; i < source.size() * 2; i++) {
         int temp1 = rd.nextInt(source.size());
         int temp2 = rd.nextInt(source.size());
         T word = source.get(temp1);
         source.set(temp1, source.get(temp2));
         source.set(temp2, word);
      }
      for (int i = source.size(); i > amount; i--) {
         source.remove(i);
      }
      this.source = source;
      time = timeSec;
      this.doOnSec = doOnSec;
   }

   /**
    * Starts the test.
    */
   public void startTest() {
      new Thread(() -> {
         try {
            for (; time >= 0; time--) {
               doOnSec.doOnSec(time);
               Thread.sleep(1000);
            }
         } catch (InterruptedException ex) {
         }
      }).start();
   }

   /**
    * Tests if user answered correctly.
    *
    * @param source {@link Element} that is the main answer, but doesn't have to
    * @param answer the answer of the user
    * @return {@code true} if the user matched the translation for all of the
    * source Element's children (in most cases if matches its name), otherwise
    * {@code false}
    *
    */
   public boolean isAnswer(T source, String answer) {
      for (TwoSided t : source.getChildren()) {
         boolean isAnswer = false;
         for (TwoSided ch : t.getChildren()) {
            for (String s : NameReader.readName(ch)) {
               if (s.equals(answer)) {
                  isAnswer = true;
               }
            }
         }
         if (!isAnswer) {
            return false;
         }
      }
      return true;
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

   public TwoSided[][] getTested() {
      ArrayList<TwoSided[]> list = new ArrayList<>();
      source.forEach((t) -> list.add(t.getChildren()));
      return list.toArray(new TwoSided[list.size()][]);
   }
}
