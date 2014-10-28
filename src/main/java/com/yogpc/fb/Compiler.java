package com.yogpc.fb;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.yogpc.fb.ProjectConfig.ForgeVersion;
import com.yogpc.fb.asm.MainTransformer;
import com.yogpc.fb.dep.MainAnalyzer;
import com.yogpc.fb.map.JarMapping;
import com.yogpc.fb.sa.MavenWrapper;
import com.yogpc.fb.sa.Utils;

public final class Compiler {
  private static List<File> copySource(final Map<String, String> in, final File res,
      final File src, final Map<Pattern, String> map, final File src_jar) throws IOException {
    final List<File> sb = new ArrayList<File>();
    File out;
    final List<String> dirs = new ArrayList<String>();
    final OutputStream os = new FileOutputStream(src_jar);
    final ZipOutputStream zos = new ZipOutputStream(os);
    zos.setLevel(Deflater.BEST_COMPRESSION);
    for (final Map.Entry<String, String> e : in.entrySet()) {
      final String name = e.getKey().replace('/', File.separatorChar);
      if (name.toLowerCase().endsWith(".java"))
        sb.add(out = new File(src, name));
      else
        out = new File(res, name);
      out.getParentFile().mkdirs();
      String s = e.getValue();
      if (map != null)
        for (final Map.Entry<Pattern, String> r : map.entrySet())
          s = r.getKey().matcher(s).replaceAll(r.getValue());
      Utils.stringToFile(s, out, Utils.ISO_8859_1);
      Utils.jar_dir(zos, dirs, e.getKey());
      zos.putNextEntry(new ZipEntry(e.getKey()));
      zos.write(s.getBytes(Utils.ISO_8859_1));
      zos.closeEntry();
    }
    zos.close();
    os.close();
    return sb;
  }

  static int exec_javac(final Map<String, String> src, final Map<Pattern, String> map,
      final List<File> cp, final ForgeData c, final File src_jar, final File bin_jar,
      final Map<String, String> man) throws IOException, InterruptedException {
    final File src_dir = File.createTempFile("ForgeBuilder", ".dir");
    final File bin_dir = File.createTempFile("ForgeBuilder", ".dir");
    src_dir.delete();
    bin_dir.delete();
    bin_dir.mkdir();
    final List<File> sources = copySource(src, bin_dir, src_dir, map, src_jar);
    final File cfg = File.createTempFile("ForgeBuilder", ".cfg");
    final FileOutputStream fos = new FileOutputStream(cfg);
    final OutputStreamWriter osw = new OutputStreamWriter(fos, Utils.UTF_8);
    osw.write("-sourcepath ");
    osw.write(src_dir.getPath());
    osw.write("\n-d ");
    osw.write(bin_dir.getPath());
    osw.write("\n-classpath ");
    for (final File f : cp) {
      osw.write(f.getPath());
      osw.write(File.pathSeparator);
    }
    if (c != null)
      osw.write(c.jar.getPath());
    osw.write('\n');
    for (final File f : sources) {
      osw.write(f.getPath());
      osw.write('\n');
    }
    osw.close();
    fos.close();
    final LinkedList<String> l = new LinkedList<String>();
    l.add("javac");
    // l.add("-Xlint:all");
    // l.add("-Xlint:none");
    l.add("-encoding");
    l.add("UTF-8");
    l.add("-J-Dfile.encoding=" + System.getProperty("file.encoding"));
    l.add("-g");
    l.add("-source");
    l.add("1.6");
    l.add("-target");
    l.add("1.6");
    l.add("@" + cfg.getPath());
    final Process p = new ProcessBuilder(l).redirectErrorStream(true).start();
    final InputStream is = p.getInputStream();
    int i;
    while ((i = is.read()) > -1)
      System.out.write(i);
    is.close();
    final int ret = p.waitFor();
    cfg.delete();
    Utils.jar_c(bin_jar, man, bin_dir);
    Utils.rm(bin_dir);
    Utils.rm(src_dir);
    return ret;
  }

  static int compile(final ForgeVersion fv, final String out,
      final LinkedHashMap<Pattern, String> map, final ForgeData fd, final MavenWrapper w1,
      final MavenWrapper w2) throws Exception {
    final File src = new File(out + "-sources.jar");
    src.getParentFile().mkdirs();
    System.out.println("> Compile mod");
    final int i =
        exec_javac(fv.srces, map, MavenWrapper.getJar(w1, w2), fd, src, new File(out + "-dev.jar"),
            fv.manifest);
    if (i != 0)
      return i;
    List<String> depCls = null;
    if (fv.contains != null) {
      System.out.println("> Resolve Class Dependencies");
      final MainAnalyzer ma = new MainAnalyzer();
      for (final File f : MavenWrapper.getJar(w1, w2))
        ma.addCP(f);
      ma.addCP(fd.jar);
      depCls = ma.process(new File(out + "-dev.jar"));
    }
    System.out.println("> Obfuscating");
    final int forgevi = Integer.parseInt(fv.forgev);
    final MainTransformer trans = new MainTransformer(forgevi, fd.config.identifier, fd.srg);
    trans.addCP(fd.jar);
    for (final File f : MavenWrapper.getJar(w1))
      trans.addCP(f);
    trans.process_jar(new File(out + "-dev.jar"), new File(out + ".jar"), depCls, fv.contains,
        forgevi <= 534 ? JarMapping.RAW_OBF : JarMapping.RAW_SRG);
    return 0;
  }
}
