package com.yogpc.fb.sa;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;

public class Downloader implements Runnable, IProcessor {
  private static File[] genPath(final String base, final File tout, final String ext) {
    final File out = new File(Constants.DATA_DIR, "cache" + File.separator + base);
    return new File[] {new File(out, "etag"), new File(out, "lm"), new File(out, "sum"),
        tout != null ? tout : new File(out, "file." + ext)};
  }

  private static Object[] genMPath(final String group, final String artifact, final String version,
      final String sub) {
    final StringBuilder sb = new StringBuilder();
    sb.append(group.replace(".", "/")).append("/");
    sb.append(artifact).append("/").append(version);
    String cp = sb.toString();
    sb.append("/").append(artifact).append('-').append(version);
    if (sub == null || sub.equals("jar"))
      sb.append(".jar");
    else if (sub.equals("zip"))
      sb.append(".zip");
    else {
      sb.append('-').append(sub);
      cp = cp + "-" + sub;
      sb.append(".jar");
    }
    final File lp =
        new File(Constants.MINECRAFT_LIBRARIES, sb.toString().replace("/", File.separator));
    return new Object[] {sb.toString(), genPath(cp, lp, "jar")};
  }

  private static boolean tryDownload(final URL url, final File[] fa)
      throws NoSuchAlgorithmException, IOException {
    final byte[] buf = new byte[8192];
    int nread;
    if (!fa[3].exists()) {
      fa[0].delete();
      fa[1].delete();
      fa[2].delete();
      fa[3].delete();
    } else if (fa[2].exists()) {
      final MessageDigest md = MessageDigest.getInstance("SHA-512");
      final InputStream is = new FileInputStream(fa[3]);
      while ((nread = is.read(buf)) > -1)
        md.update(buf, 0, nread);
      is.close();
      if (!Arrays.equals(md.digest(), Utils.fileToByteArray(fa[2]))) {
        fa[0].delete();
        fa[1].delete();
        fa[2].delete();
        fa[3].delete();
      }
    }
    final HttpURLConnection uc = (HttpURLConnection) url.openConnection();
    uc.setInstanceFollowRedirects(true);
    if (fa[0].exists())
      uc.setRequestProperty("If-None-Match", Utils.fileToString(fa[0], Utils.UTF_8));
    if (fa[1].exists())
      uc.setRequestProperty("If-Modified-Since", Utils.fileToString(fa[1], Utils.UTF_8));
    uc.connect();
    final int res = uc.getResponseCode();
    if (res == HttpURLConnection.HTTP_NOT_MODIFIED)
      return true;
    if (res == HttpURLConnection.HTTP_NOT_FOUND || res == HttpURLConnection.HTTP_FORBIDDEN)
      return false;
    if (res != HttpURLConnection.HTTP_OK)
      throw new FileNotFoundException(String.format("%d %s", Integer.valueOf(res),
          uc.getResponseMessage()));
    fa[3].getParentFile().mkdirs();
    fa[0].getParentFile().mkdirs();
    if (uc.getHeaderField("ETag") != null)
      Utils.stringToFile(uc.getHeaderField("ETag"), fa[0], Utils.UTF_8);
    if (uc.getHeaderField("Last-Modified") != null)
      Utils.stringToFile(uc.getHeaderField("Last-Modified"), fa[1], Utils.UTF_8);
    final MessageDigest md = MessageDigest.getInstance("SHA-512");
    final InputStream is = uc.getInputStream();
    final OutputStream os = new FileOutputStream(fa[3]);
    while ((nread = is.read(buf)) > -1) {
      os.write(buf, 0, nread);
      md.update(buf, 0, nread);
    }
    os.close();
    is.close();
    uc.disconnect();
    Utils.byteArrayToFile(md.digest(), fa[2]);
    return true;
  }

  private static final String[] REPOS = {"http://repo.maven.apache.org/maven2/",
      Constants.FORGE_MAVEN, "https://libraries.minecraft.net/"};

  private static void downloadMaven(final Object[] oa) throws Exception {
    final ArrayList<Exception> al = new ArrayList<Exception>();
    for (final String base : REPOS) {
      try {
        if (!tryDownload(new URL(base + (String) oa[0]), (File[]) oa[1]))
          continue;
      } catch (final Exception e) {
        al.add(e);
        continue;
      }
      return;
    }
    if (((File[]) oa[1])[3].exists()) {
      System.out.println(">> Use local library cache " + (String) oa[0]);
      return;
    }
    if (al.isEmpty())
      return;
    final StringBuilder sb = new StringBuilder();
    for (final Exception e : al)
      sb.append(e.toString()).append(", ");
    throw new Exception(sb.toString());
  }

  private final String name;
  private final String[] maven;
  private final URL url;
  private final String ext;
  private final Thread t;
  private File ret;

  Downloader(final String g, final String a, final String v, final String s) {
    this.name = null;
    this.maven = new String[] {g, a, v, s};
    this.url = null;
    this.ret = null;
    this.ext = null;
    this.t = new Thread(this);
    this.t.start();
  }

  public Downloader(final String n, final String u, final String e) throws MalformedURLException {
    this.name = n;
    this.maven = null;
    this.url = new URL(u);
    this.ret = null;
    this.ext = e;
    this.t = new Thread(this);
    this.t.start();
  }

  public Downloader(final String n, final String u, final File f) throws MalformedURLException {
    this.name = n;
    this.maven = null;
    this.url = new URL(u);
    this.ret = f;
    this.ext = null;
    this.t = new Thread(this);
    this.t.start();
  }

  @Override
  public void run() {
    int remain = 3;
    Exception le = null;
    Object[] oa = null;
    if (this.maven != null) {
      oa = genMPath(this.maven[0], this.maven[1], this.maven[2], this.maven[3]);
      this.ret = ((File[]) oa[1])[3];
    } else if (this.url != null) {
      oa = genPath(this.name, this.ret, this.ext);
      this.ret = (File) oa[3];
    }
    while (remain-- > 0)
      try {
        if (this.maven != null)
          downloadMaven(oa);
        else if (this.url != null)
          tryDownload(this.url, (File[]) oa);
        le = null;
        break;
      } catch (final Exception e) {
        le = e;
        try {
          Thread.sleep(1000);
        } catch (final InterruptedException e1) {
        }
      }
    if (le != null)
      System.err.println(toString() + " is failed with " + le.toString());
    if (!this.ret.exists())
      this.ret = null;
  }

  @Override
  public String toString() {
    return "Downloader{"
        + (this.maven != null ? this.maven[0] + ":" + this.maven[1] + ":" + this.maven[2] + ":"
            + this.maven[3] : this.url.toString()) + "}";
  }

  private IProcessor child;

  @Override
  public File process(final File in) throws Exception {
    this.t.join();
    return this.child != null ? this.child.process(this.ret) : this.ret;
  }

  @Override
  public void setChild(final IProcessor ip) {
    this.child = ip;
  }
}
