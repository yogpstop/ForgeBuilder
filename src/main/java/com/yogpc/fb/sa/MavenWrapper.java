package com.yogpc.fb.sa;

import java.io.File;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;

import com.yogpc.fb.Deobfuscator;

public class MavenWrapper {
  private final List<IProcessor> jr = new ArrayList<IProcessor>();
  private final List<IProcessor> sc = new ArrayList<IProcessor>();
  private final List<IProcessor> nt = new ArrayList<IProcessor>();

  public void addDownload(final List<String> l, final boolean src, final boolean nat,
      final String fv) throws MalformedURLException {
    if (l == null)
      return;
    for (final String s : l) {
      IProcessor par = null, n = null, root = null;
      final String[] pl = Utils.split(s, '|');
      for (final String p : pl) {
        final int i = p.indexOf('`');
        if (i < 0) {
          final String[] m = Utils.split(p, ':');
          n =
              new Downloader(m[0], m[1], m[2], m.length > 3 ? m[3] : null, m.length > 4 ? m[4]
                  : null);
          if (src)
            this.sc.add(new Downloader(m[0], m[1], m[2], "sources", null));
          if (nat)
            this.nt.add(new Downloader(m[0], m[1], m[2], "natives-" + Constants.OS, null));
        } else {
          final String pr = p.substring(0, i);
          final String u = p.substring(i + 1);
          switch (pr.charAt(0)) {
            case 'U':
              n =
                  new Downloader(u.replace(":", "%3A").replace("//", "/")
                      .replace('/', File.separatorChar), u, "jar");
              break;
            case 'D':
              boolean srg = Utils.atoi(fv, 9999) > 534;
              if (pr.length() > 1)
                srg = pr.charAt(1) == 'S';
              n = new Deobfuscator(srg, fv, null);
              break;
          }
        }
        if (n == null)
          continue;
        if (par != null)
          par.setChild(n);
        if (root == null)
          root = n;
        par = n;
      }
      if (root != null)
        this.jr.add(root);
    }
  }

  private static void getResult(final List<IProcessor> l, final List<File> r) throws Exception {
    for (final IProcessor d : l) {
      final File f = d.process(null);
      if (f != null)
        r.add(f);
    }
  }

  public static List<File> getJar(final MavenWrapper... l) throws Exception {
    final List<File> r = new ArrayList<File>();
    for (final MavenWrapper w : l)
      getResult(w.jr, r);
    return r;
  }

  public static List<File> getSources(final MavenWrapper... l) throws Exception {
    final List<File> r = new ArrayList<File>();
    for (final MavenWrapper w : l)
      getResult(w.sc, r);
    return r;
  }

  public static List<File> getNatives(final MavenWrapper... l) throws Exception {
    final List<File> r = new ArrayList<File>();
    for (final MavenWrapper w : l)
      getResult(w.nt, r);
    return r;
  }

  public static List<File> getLegacy(final List<String> l, final String fv) throws Exception {
    final MavenWrapper w = new MavenWrapper();
    w.addDownload(l, false, false, fv);
    final List<File> r = new ArrayList<File>();
    getResult(w.jr, r);
    return r;
  }
}
