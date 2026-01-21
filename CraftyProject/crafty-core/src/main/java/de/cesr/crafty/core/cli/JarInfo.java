package de.cesr.crafty.core.cli;

import java.net.URI;
import java.nio.file.Paths;
import java.security.CodeSource;

public final class JarInfo {
  private JarInfo() {}

  /** Returns the JAR file name that contains the given anchor class, or "unknown". */
  public static String jarFileName(Class<?> anchorClass) {
    try {
      // 1) If launched via `java -jar foo.jar`, this usually contains the jar name.
      String cmd = System.getProperty("sun.java.command", "");
      if (cmd.endsWith(".jar")) {
        return Paths.get(cmd).getFileName().toString();
      }

      // 2) CodeSource location of the anchor class -> .../foo.jar or .../classes/
      CodeSource cs = anchorClass.getProtectionDomain().getCodeSource();
      if (cs != null) {
        URI uri = cs.getLocation().toURI();
        String name = Paths.get(uri).getFileName().toString();
        if (name.endsWith(".jar")) {
          return name;
        }
      }

      // 3) If classpath has a single entry that is a jar
      String cp = System.getProperty("java.class.path", "");
      String sep = System.getProperty("path.separator");
      if (!cp.isEmpty() && sep != null) {
        String[] entries = cp.split(java.util.regex.Pattern.quote(sep));
        if (entries.length == 1 && entries[0].endsWith(".jar")) {
          return Paths.get(entries[0]).getFileName().toString();
        }
      }
    } catch (Exception ignore) {
      // fall through
    }
    return "";
  }
  
}
