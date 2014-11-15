package com.yogpc.fb.lc;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.Attributes;
import java.util.jar.Attributes.Name;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

public class LaunchClassLoader extends URLClassLoader {
  private static final int BUFFER_SIZE = 1 << 12;
  private final ClassLoader parent = getClass().getClassLoader();

  private final List<IClassTransformer> transformers = new ArrayList<IClassTransformer>(2);
  private final Map<String, Class<?>> cachedClasses = new ConcurrentHashMap<String, Class<?>>();
  private final Set<String> invalidClasses = new HashSet<String>(1000);

  private final Set<String> classLoaderExceptions = new HashSet<String>();
  private final Set<String> transformerExceptions = new HashSet<String>();

  private LaunchClassLoader(final URL[] sources) {
    super(sources, null);
    this.classLoaderExceptions.add("java.");
    this.classLoaderExceptions.add("javax.");
    this.classLoaderExceptions.add("sun.");
    this.classLoaderExceptions.add("com.yogpc.fb.");
    this.transformerExceptions.add("org.objectweb.asm.");
  }

  private void registerTransformer(final String name) {
    try {
      this.classLoaderExceptions.add(name.substring(0, name.lastIndexOf('.') + 1));
      final IClassTransformer trans = (IClassTransformer) loadClass(name).newInstance();
      this.transformers.add(trans);
    } catch (final Exception e) {
      e.printStackTrace();
    }
  }

  private static boolean isSealed(final String name, final Manifest man) {
    final String path = name.replace('.', '/').concat("/");
    Attributes attr = man.getAttributes(path);
    String sealed = null;
    if (attr != null)
      sealed = attr.getValue(Name.SEALED);
    if (sealed == null)
      if ((attr = man.getMainAttributes()) != null)
        sealed = attr.getValue(Name.SEALED);
    return "true".equalsIgnoreCase(sealed);
  }

  private Package getAndVerifyPackage(final String pkgname, final Manifest man, final URL url) {
    final Package pkg = getPackage(pkgname);
    if (pkg != null)
      if (pkg.isSealed()) {
        if (!pkg.isSealed(url))
          throw new SecurityException("sealing violation: package " + pkgname + " is sealed");
      } else if (man != null && isSealed(pkgname, man))
        throw new SecurityException("sealing violation: can't seal package " + pkgname
            + ": already loaded");
    return pkg;
  }

  private Class<?> defineClass(final String name, final URL url, final Manifest man,
      final byte[] b, final CodeSigner[] signers) {
    final int i = name.lastIndexOf('.');
    if (i != -1) {
      final String pkgname = name.substring(0, i);
      if (getAndVerifyPackage(pkgname, man, url) == null)
        try {
          if (man != null)
            definePackage(pkgname, man, url);
          else
            definePackage(pkgname, null, null, null, null, null, null, null);
        } catch (final IllegalArgumentException iae) {
          if (getAndVerifyPackage(pkgname, man, url) == null)
            throw new AssertionError("Cannot find package " + pkgname);
        }
    }
    final CodeSource cs = new CodeSource(url, signers);
    return defineClass(name, b, 0, b.length, cs);
  }

  @Override
  protected Class<?> findClass(final String name) throws ClassNotFoundException {
    if (this.invalidClasses.contains(name))
      throw new ClassNotFoundException(name);
    for (final String exception : this.classLoaderExceptions)
      if (name.startsWith(exception))
        return this.parent.loadClass(name);
    if (this.cachedClasses.containsKey(name))
      return this.cachedClasses.get(name);

    for (final String exception : this.transformerExceptions)
      if (name.startsWith(exception))
        try {
          final Class<?> clazz = super.findClass(name);
          this.cachedClasses.put(name, clazz);
          return clazz;
        } catch (final ClassNotFoundException e) {
          this.invalidClasses.add(name);
          throw e;
        }

    try {
      final String fileName = name.replace('.', '/').concat(".class");
      final URL url = findResource(fileName);
      final URLConnection urlConnection = url.openConnection();
      CodeSigner[] signers = null;
      Manifest manifest = null;
      if (urlConnection instanceof JarURLConnection) {
        final JarURLConnection jarURLConnection = (JarURLConnection) urlConnection;
        final JarFile jarFile = jarURLConnection.getJarFile();
        if (jarFile != null) {
          manifest = jarFile.getManifest();
          signers = jarFile.getJarEntry(fileName).getCodeSigners();
        }
        if (jarFile != null)
          jarFile.close();
      }
      byte[] data = readFully(url);
      for (final IClassTransformer transformer : this.transformers)
        data = transformer.transform(name, data);
      final Class<?> clazz = defineClass(name, url, manifest, data, signers);
      this.cachedClasses.put(name, clazz);
      return clazz;
    } catch (final Throwable e) {
      this.invalidClasses.add(name);
      throw new ClassNotFoundException(name, e);
    }
  }

  private static byte[] readFully(final URL url) {
    try {
      final InputStream stream = url.openStream();
      byte[] buffer = new byte[BUFFER_SIZE];
      int read;
      int totalLength = 0;
      while ((read = stream.read(buffer, totalLength, buffer.length - totalLength)) != -1) {
        totalLength += read;
        if (totalLength >= buffer.length - 1) {
          final byte[] newBuffer = new byte[buffer.length + BUFFER_SIZE];
          System.arraycopy(buffer, 0, newBuffer, 0, buffer.length);
          buffer = newBuffer;
        }
      }
      stream.close();
      final byte[] result = new byte[totalLength];
      System.arraycopy(buffer, 0, result, 0, totalLength);
      return result;
    } catch (final Throwable t) {
      return new byte[0];
    }
  }

  public static File minecraftHome;
  private static final LaunchClassLoader classLoader = new LaunchClassLoader(
      ((URLClassLoader) LaunchClassLoader.class.getClassLoader()).getURLs());

  public static void main(final String[] args) {// TODO parse args
    minecraftHome = null;
    final String launchTarget = null;
    final List<String> tweakClassNames = new ArrayList<String>(null);
    final String[] argument = null;

    try {
      for (final String tweakName : tweakClassNames)
        classLoader.registerTransformer(tweakName);
      final Class<?> clazz = Class.forName(launchTarget, false, classLoader);
      final Method mainMethod = clazz.getMethod("main", new Class[] {String[].class});
      mainMethod.invoke(null, (Object) argument);
    } catch (final Exception e) {
      e.printStackTrace();
    }
  }
}
