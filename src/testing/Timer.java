/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package testing;

/**
 * Used for management of a running {@link Test}.
 * @author Josef Litoš
 */
public interface Timer {

   /**
    * This method is called every second of the {@link Test}, until the time
    * runs out.
    *
    * @param secsLeft seconds left to the end of the test
    */
   void doOnSec(int secsLeft);
}
