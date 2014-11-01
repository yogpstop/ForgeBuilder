package com.yogpc.fb.sa;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MavenWrapper {
  private final List<Downloader> jr = new ArrayList<Downloader>();
  private final List<Downloader> sc = new ArrayList<Downloader>();
  private final List<Downloader> jd = new ArrayList<Downloader>();
  private final List<Downloader> nt = new ArrayList<Downloader>();

  public static final Pattern lib_nam = Pattern.compile("([^:]+):([^:]+):([^:]+)(?::([^:]+))?");

  public void addDownload(final List<String> l) throws MalformedURLException {
    if (l == null)
      return;
    for (final String s : l) {
      final Matcher m = lib_nam.matcher(s);
      if (!s.startsWith("http://") && !s.startsWith("https://") && m.matches()) {
        final String g = m.group(1), a = m.group(2), v = m.group(3);
        this.jr.add(new Downloader(g, a, v, m.group(4)));
        this.sc.add(new Downloader(g, a, v, "sources"));
        this.jd.add(new Downloader(g, a, v, "javadoc"));
        this.nt.add(new Downloader(g, a, v, "natives-" + Constants.OS));
      } else {
        final String n = s.replace(":", "%3A").replace("//", "/").replace('/', File.separatorChar);
        this.jr.add(new Downloader(n, new URL(s), "jar"));
      }
    }
  }

  private static void getResult(final List<Downloader> l, final List<File> r)
      throws InterruptedException {
    for (final Downloader d : l) {
      d.join();
      if (d.getFile() != null)
        r.add(d.getFile());
    }
  }

  public static List<File> getJar(final MavenWrapper... l) throws InterruptedException {
    final List<File> r = new ArrayList<File>();
    for (final MavenWrapper w : l)
      getResult(w.jr, r);
    return r;
  }

  public static List<File> getSources(final MavenWrapper... l) throws InterruptedException {
    final List<File> r = new ArrayList<File>();
    for (final MavenWrapper w : l)
      getResult(w.sc, r);
    return r;
  }

  public static List<File> getJavadoc(final MavenWrapper... l) throws InterruptedException {
    final List<File> r = new ArrayList<File>();
    for (final MavenWrapper w : l)
      getResult(w.jd, r);
    return r;
  }

  public static List<File> getNatives(final MavenWrapper... l) throws InterruptedException {
    final List<File> r = new ArrayList<File>();
    for (final MavenWrapper w : l)
      getResult(w.nt, r);
    return r;
  }

  public static List<File> getLegacy(final List<String> l) throws MalformedURLException,
      InterruptedException {
    final MavenWrapper w = new MavenWrapper();
    w.addDownload(l);
    final List<File> r = new ArrayList<File>();
    getResult(w.jr, r);
    return r;
  }
}
