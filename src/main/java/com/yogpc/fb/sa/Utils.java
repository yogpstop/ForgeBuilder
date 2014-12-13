package com.yogpc.fb.sa;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class Utils {
  public static final Charset UTF_8 = Charset.forName("UTF-8");
  public static final Charset ISO_8859_1 = Charset.forName("ISO-8859-1");
  public static final Object DUMMY_OBJECT = new Object();

  public static final void jar_dir(final ZipOutputStream out, final List<String> dir,
      final String entry) throws IOException {
    final int i = entry.lastIndexOf('/');
    if (i >= 0) {
      final String s = entry.substring(0, i + 1);
      if (dir.contains(s))
        return;
      dir.add(s);
      out.putNextEntry(new ZipEntry(s));
      out.closeEntry();
      jar_dir(out, dir, entry.substring(0, i));
    }
  }

  public static String reencode(final String from) {
    return new String(from.getBytes(UTF_8), ISO_8859_1);
  }

  public static void rm(final File i) {
    if (i.isDirectory())
      for (final File j : i.listFiles())
        rm(j);
    i.delete();
  }

  public static byte[] urlToByteArray(final URL l) throws IOException {
    final InputStream is = l.openStream();
    final byte[] ret = new byte[is.available()];
    int p = 0;
    while (p < ret.length)
      p += is.read(ret, p, ret.length - p);
    is.close();
    return ret;
  }

  public static byte[] fileToByteArray(final File f) throws IOException {
    final FileInputStream is = new FileInputStream(f);
    final byte[] ret = new byte[(int) f.length()];
    int p = 0;
    while (p < ret.length)
      p += is.read(ret, p, ret.length - p);
    is.close();
    return ret;
  }

  public static String fileToString(final File f, final Charset c) throws IOException {
    return new String(fileToByteArray(f), c);
  }

  public static void byteArrayToFile(final byte[] b, final File f) throws IOException {
    final FileOutputStream fos = new FileOutputStream(f);
    fos.write(b);
    fos.close();
  }

  public static void stringToFile(final String s, final File f, final Charset c) throws IOException {
    byteArrayToFile(s.getBytes(c), f);
  }

  private static void jar_add(final File b, final File f, final List<String> l,
      final ZipOutputStream j) throws IOException {
    String n = f.getPath().replace(b.getPath(), "").replace(File.separator, "/");
    if (n.length() > 0 && n.charAt(0) == '/')
      n = n.substring(1);
    if (!n.equals("") && !l.contains(n)) {
      if (n.equals(JarFile.MANIFEST_NAME))
        return;
      if (f.isDirectory() && n.charAt(n.length() - 1) != '/')
        n += "/";
      l.add(n);
      j.putNextEntry(new ZipEntry(n));
      if (!f.isDirectory())
        j.write(fileToByteArray(f));
      j.closeEntry();
    }
    if (f.isDirectory())
      for (final File e : f.listFiles())
        jar_add(b, e, l, j);
  }

  public static void jar_c(final File jar, final Map<String, String> _man, final File... dirs)
      throws IOException {
    final Manifest man = new Manifest();
    man.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    if (_man != null)
      for (final Map.Entry<String, String> m : _man.entrySet())
        man.getMainAttributes().putValue(m.getKey(), m.getValue());
    final OutputStream o = new FileOutputStream(jar);
    final ZipOutputStream z = new ZipOutputStream(o);
    z.setLevel(Deflater.BEST_COMPRESSION);
    z.putNextEntry(new ZipEntry(JarFile.MANIFEST_NAME));
    man.write(z);
    z.closeEntry();
    final List<String> l = new ArrayList<String>();
    for (final File f : dirs)
      jar_add(f, f, l, z);
    z.close();
    o.close();
  }

  public static byte[] jar_entry(final InputStream in, final long size) throws IOException {
    byte[] data;
    if (size > 0) {
      data = new byte[(int) size];
      int offset = 0;
      do
        offset += in.read(data, offset, data.length - offset);
      while (offset < data.length);
    } else {
      final ByteArrayOutputStream dataout = new ByteArrayOutputStream();
      data = new byte[4096];
      int len;
      while ((len = in.read(data)) != -1)
        dataout.write(data, 0, len);
      data = dataout.toByteArray();
    }
    return data;
  }

  public static final String[] split(final String s, final char de) {
    int a = 1, i = -1;
    while (++i < s.length())
      if (s.charAt(i) == de)
        a++;
    final String[] ret = new String[a];
    StringBuilder sb = new StringBuilder();
    char c;
    i = a = -1;
    while (++i < s.length()) {
      c = s.charAt(i);
      if (c == de) {
        ret[++a] = sb.toString();
        sb = new StringBuilder();
      } else
        sb.append(c);
    }
    ret[++a] = sb.toString();
    return ret;
  }

  public static final String join(final String[] a, final char de) {
    final StringBuilder sb = new StringBuilder();
    for (final String s : a)
      sb.append(s).append(de);
    return sb.substring(0, sb.length() - 1);
  }

  private static final char[] base64 = {'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L',
      'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z', 'a', 'b', 'c', 'd',
      'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v',
      'w', 'x', 'y', 'z', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '-', '_'};

  public static String b64e(final byte[] src) {
    final int rest = src.length % 3;
    final int pack = src.length - rest;
    final char[] dst = new char[4 * (src.length / 3) + (rest == 0 ? 0 : rest + 1)];
    int sp = 0, dp = 0;
    while (sp < pack) {
      final int bits = (src[sp++] & 0xff) << 16 | (src[sp++] & 0xff) << 8 | src[sp++] & 0xff;
      dst[dp++] = base64[bits >>> 18];
      dst[dp++] = base64[bits >>> 12 & 0x3f];
      dst[dp++] = base64[bits >>> 6 & 0x3f];
      dst[dp++] = base64[bits & 0x3f];
    }
    if (sp < src.length) {
      final int b0 = src[sp++] & 0xff;
      dst[dp++] = base64[b0 >> 2];
      if (sp < src.length) {
        final int b1 = src[sp++] & 0xff;
        dst[dp++] = base64[b0 << 4 & 0x3f | b1 >> 4];
        dst[dp++] = base64[b1 << 2 & 0x3f];
      } else
        dst[dp++] = base64[b0 << 4 & 0x3f];
    }
    return new String(dst);
  }
}
