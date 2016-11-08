package com.yogpc.fb.sa;

import java.io.File;

public class Constants {
  public static final String FORGE_FILES = "http://files.minecraftforge.net";
  public static final String FORGE_MAVEN = FORGE_FILES + "/maven/";

  private static final String osn = System.getProperty("os.name").toLowerCase();

  private static final String getOS() {
    if (osn.contains("windows") || osn.contains("win"))
      return "windows";
    else if (osn.contains("linux") || osn.contains("unix"))
      return "linux";
    else if (osn.contains("osx") || osn.contains("mac"))
      return "osx";
    else
      return "";
  }

  public static final String OS = getOS();

  private static final File getDirectory(final String name) {
    final String home = System.getProperty("user.home", ".");
    File wdir;
    if (osn.startsWith("win")) {
      final String appdata = System.getenv("APPDATA");
      wdir = new File(appdata != null ? appdata : home, "." + name);
    } else if (osn.startsWith("mac"))
      wdir = new File(home, "Library/Application Support/" + name);
    else if (osn.startsWith("linux") || osn.startsWith("unix") || osn.startsWith("sunos"))
      wdir = new File(home, "." + name);
    else
      wdir = new File(home, name);

    return wdir;
  }

  public static final File DATA_DIR = getDirectory("mcp");
  public static final File MINECRAFT_DIR = getDirectory("minecraft");
  public static final File MINECRAFT_LIBRARIES = new File(MINECRAFT_DIR, "libraries");
}
