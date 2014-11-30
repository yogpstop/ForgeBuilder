package com.yogpc.fb;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.yogpc.fb.map.JarMapping;
import com.yogpc.fb.map.MappingBuilder;
import com.yogpc.fb.sa.Downloader;
import com.yogpc.fb.sa.MavenWrapper;
import com.yogpc.fb.sa.Utils;

public class MCPData implements Runnable {
  private static final Pattern BGM = Pattern.compile("mappings\\s*=\\s*\"([^\"]+)\"");
  private String packages, params, methods, fields;
  private final String url, fv, mcv;
  private final Thread t;

  MCPData(final String u, final String f, final String m) {
    this.url = u;
    this.fv = f;
    this.mcv = m;
    this.t = new Thread(this);
    this.t.start();
  }

  void patch(final JarMapping jm) throws InterruptedException {
    this.t.join();
    if (this.packages != null)
      MappingBuilder.loadCsv(this.packages, jm, false, true);
    if (this.params != null)
      MappingBuilder.loadCsv(this.params, jm, false, false);
    if (this.methods != null)
      MappingBuilder.loadCsv(this.methods, jm, true, false);
    if (this.fields != null)
      MappingBuilder.loadCsv(this.fields, jm, true, false);
  }

  @Override
  public void run() {
    if (!this.url.endsWith("userdev.jar"))
      return;
    try {
      File f =
          new Downloader(this.fv + "src", new URL(this.url.substring(0, this.url.length() - 11)
              + "src.zip"), "zip").process(null);
      InputStream is = new FileInputStream(f);
      ZipInputStream in = new ZipInputStream(is);
      ZipEntry entry;
      String s = null;
      while ((entry = in.getNextEntry()) != null) {
        if (entry.getName().equals("build.gradle")) {
          final byte[] d = Utils.jar_entry(in, entry.getSize());
          final Matcher m = BGM.matcher(new String(d, Utils.ISO_8859_1));
          if (!m.find())
            break;
          final int i = m.group(1).lastIndexOf('_');
          if (i < 0)
            break;
          s =
              "de.oceanlabs.mcp:mcp_" + m.group(1).substring(0, i) + ":"
                  + m.group(1).substring(i + 1) + "-" + this.mcv + ":zip";
        }
        in.closeEntry();
      }
      in.close();
      is.close();
      if (s == null)
        return;
      f = MavenWrapper.getLegacy(Arrays.asList(s), this.fv).get(0);
      is = new FileInputStream(f);
      in = new ZipInputStream(is);
      while ((entry = in.getNextEntry()) != null) {
        if (!entry.isDirectory()) {
          final String n = entry.getName();
          final byte[] d = Utils.jar_entry(in, entry.getSize());
          if (n.endsWith("~"))
            continue;
          else if (n.contains("packages"))
            this.packages = new String(d, Utils.ISO_8859_1);
          else if (n.contains("params"))
            this.params = new String(d, Utils.ISO_8859_1);
          else if (n.contains("methods"))
            this.methods = new String(d, Utils.ISO_8859_1);
          else if (n.contains("fields"))
            this.fields = new String(d, Utils.ISO_8859_1);
        }
        in.closeEntry();
      }
      in.close();
      is.close();
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
  }
}
