package testing;

import java.util.LinkedList;
import java.util.List;

/**
 *
 * @author Josef Litoš
 */
public class MergeSort {

   public static <T> List<T> sort(boolean mV, List<T> right, ToSort<T> num) {
      if (right.size() <= 1) {
         return right;
      }
      LinkedList<T> left = new LinkedList<>();
      while (left.size() < right.size()) {
         left.add(right.get(0));
         right.remove(0);
      }
      return sort(mV, sort(mV, left, num), sort(mV, right, num), num);
   }

   private static <T> List<T> sort(boolean mV, List<T> left, List<T> right, ToSort<T> num) {
      LinkedList<T> ret = new LinkedList<>();
      do {
         T l = left.get(0);
         T r = right.get(0);
         if (mV ? num.getNum(l) < num.getNum(r)
                 : num.getNum(l) > num.getNum(r)) {
            ret.add(l);
            left.remove(0);
         } else {
            ret.add(r);
            right.remove(0);
         }
      } while (left.size() > 0 & right.size() > 0);
      while (left.size() > 0) {
         ret.add(left.get(0));
         left.remove(0);
      }
      while (right.size() > 0) {
         ret.add(right.get(0));
         right.remove(0);
      }
      return ret;
   }
}
