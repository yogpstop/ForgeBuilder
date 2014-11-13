package com.yogpc.fb.sa;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;

public class Bootstrap {
  private static final String DEFAULT_CLASS = "com.yogpc.fb.CompilerCaller";

  @SuppressWarnings("resource")
  public static void main(final String[] args) throws Exception {
    System.out.println("> Downloading ForgeBuilder dependencies.");
    final URLClassLoader cl = (URLClassLoader) Bootstrap.class.getClassLoader();
    final Method addURL = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
    addURL.setAccessible(true);
    for (final File f : MavenWrapper.getLegacy(
        Arrays.asList(new String[] {"org.ow2.asm:asm-all:4.2",
            "com.github.abrarsyed.jastyle:jAstyle:1.2", "com.google.code.gson:gson:2.3"}), null))
      addURL.invoke(cl, f.toURI().toURL());
    System.out.println("> Downloading is done.");
    String main;
    String[] par;
    if (args.length < 1 || !args[0].startsWith("com.yogpc.fb.")) {
      main = DEFAULT_CLASS;
      par = new String[args.length];
      System.arraycopy(args, 0, par, 0, par.length);
    } else {
      main = args[0];
      par = new String[args.length - 1];
      System.arraycopy(args, 1, par, 0, par.length);
    }
    final Class<?> mc = Class.forName(main);
    if (mc == null)
      return;
    final Method mm = mc.getDeclaredMethod("main", String[].class);
    if (mm == null)
      return;
    mm.setAccessible(true);
    mm.invoke(null, new Object[] {par});
  }
}
