/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package testing;

import IOSystem.ReadElement;
import java.util.Arrays;
import java.util.LinkedList;
import objects.Element;

/**
 *
 * @author Josef Litoš
 */
public class NameReader {

   /**
    * Reads the {@link Element#name} and gives back all name posibilities. Keep
    * in mind, names with more variants will in most cases make a space after
    * ',' or '.' or '!' or '?'. To eliminate this, write '\\' before theese
    * chars in these cases. Doesn't work for cases like {@code "\\.)more"}
    * <p>
    * Examples, for names:
    * <blockquote><pre>
    * "They/(He and she) moved." returns [They moved., He and she moved.]
    * "/I/You smile." returns [smile., I smile., You smile.]
    * "(I/You smile.)/(Smile!)" returns [I smile., You smile., Smile!]
    * "I 'm/am here/there." returns [I'm here., I am here., I'm there., I am there.]
    * "(We/(May you) 're)/He's /out." returns [We're., May you're., He's., We're out., May you're out., He's out.]
    * </pre></blockquote>
    *
    * @param e the {@link Element} its name will be proccessed
    * @return all variants of the {@link Element#name}
    */
   public static String[] readName(Element e) {
      return getParts(new ReadElement.Source(e.toString(), 0));
   }

   private static String[] getParts(ReadElement.Source src) {
      boolean slash = false, bracket = false, itpcn = false;
      int start = src.index, index = start;
      String[] ret = {""};
      LinkedList<String> slashStrs = new LinkedList<>();
      cycle:
      for (; index < src.str.length(); index++) {
         switch (src.str.charAt(index)) {
            case '\\':
               index++;
               break;
            case '.':
            case '!':
            case '?':
            case ',':
               itpcn = true;
            case ' ':
               if (itpcn && !slash) {
                  itpcn = false;
                  break;
               }
               if (slash) {
                  if (!bracket) {
                     slashStrs.add(substr(src.str, start, index));
                  }
                  if (itpcn) {
                     for (int i = slashStrs.size() - 1; i >= 0; i--) {
                        slashStrs.set(i, slashStrs.get(i) + src.str.charAt(index));
                     }
                  }
                  ret = compile(ret, slashStrs.toArray(new String[slashStrs.size()]));
                  slash = false;
                  slashStrs.clear();
               } else {
                  ret = compile(ret, new String[]{substr(src.str, start, index + (itpcn ? 1 : 0))});
               }
               if (itpcn) {
                  itpcn = false;
                  if (++index + 1 >= src.str.length()) {
                     src.index = src.str.length() - 1;
                     return ret;
                  }
               }
               start = index + 1;
               break;
            case '(':
               src.index = index + 1;
               slashStrs.addAll(Arrays.asList(getParts(src)));
               start = 1 + (index = src.index);
               bracket = true;
               break;
            case ')':
               bracket = false;
               break cycle;
            case '/':
               if (!bracket) {
                  slashStrs.add(substr(src.str, start, index));
               } else {
                  bracket = false;
               }
               start = index + 1;
               slash = true;
         }
      }
      if (slash) {
         if (start < (src.index = index)) {
            slashStrs.add(substr(src.str, start, src.index = index));
         }
         return compile(ret, slashStrs.toArray(new String[slashStrs.size()]));
      } else if (bracket) {
         ret = compile(ret, slashStrs.toArray(new String[slashStrs.size()]));
      }
      return compile(ret, new String[]{substr(src.str, start, src.index = index)});
   }

   private static String[] compile(String[] src1, String[] src2) {
      String[] ret = new String[src1.length * src2.length];
      for (int i = src2.length - 1; i != -1; i--) {
         int ch = src2[i].isEmpty() ? -1 : src2[i].charAt(0);
         for (int j = src1.length - 1; j != -1; j--) {
            ret[j + i * src1.length] = src1[j].isEmpty() ? src2[i] : (src1[j]
                    + (ch != ',' && ch != '.' && ch != '?' && ch != '!' && ch != '\'' && ch != -1 ? ' ' + src2[i] : src2[i]));
         }
      }
      return ret;
   }

   private static String substr(String str, int begin, int end) {
      return str.substring(begin, end).replace("\\", "");
   }
}
