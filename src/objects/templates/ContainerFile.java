/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package objects.templates;

import IOSystem.Formatter;
import java.io.File;

/**
 *
 * @author Joset Litoš
 */
public interface ContainerFile extends Container {

   File getSaveFile(Container parent);

   default void save(Container parent) {
      Formatter.saveFile(writeData(new StringBuilder(), 0, parent).toString(), getSaveFile(parent));
   }

   boolean isLoaded(Container parent);

   default void load(Container parent) {
      if (!isLoaded(parent)) {
         IOSystem.ReadElement.loadSch(getSaveFile(parent), getIdentifier(), parent);
      }
   }
}
