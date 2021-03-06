package com.yogpc.fb;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Queue;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.github.abrarsyed.jastyle.ASFormatter;
import com.github.abrarsyed.jastyle.OptParser;
import com.github.abrarsyed.jastyle.exceptions.MalformedOptionException;
import com.google.gson.Gson;
import com.yogpc.fb.asm.MainTransformer;
import com.yogpc.fb.fg.FFPatcher;
import com.yogpc.fb.fg.FmlCleanup;
import com.yogpc.fb.fg.GLConstantFixer;
import com.yogpc.fb.fg.McpCleanup;
import com.yogpc.fb.map.MappingBuilder;
import com.yogpc.fb.sa.Constants;
import com.yogpc.fb.sa.Downloader;
import com.yogpc.fb.sa.MavenWrapper;
import com.yogpc.fb.sa.UnifiedDiff;
import com.yogpc.fb.sa.Utils;

public final class Decompiler {
  private static Map<String, Decompiler> list;

  private static final String PAT_BASE =
      "/maven/net/minecraftforge/forge/";
  private static final Pattern FORGE_PATTERN = Pattern.compile(PAT_BASE
      + "[^/]+/forge-([0-9\\._]+)-[0-9\\._]+\\.([0-9]+)(?:-[^\\-]+)?-(?:mdk|src)\\.zip");
  private static final Pattern PAT_IDX = Pattern.compile(PAT_BASE + "index_([^\"]+)\\.html");

  static final boolean exec(final String version) throws Exception {
    if (list == null) {
      System.out.println("> Updating forge indexes");
      final Queue<Downloader> l = new LinkedList<Downloader>();
      final Set<String> done = new HashSet<String>();
      l.add(new Downloader("forge_main", Constants.FORGE_MAVEN + "net/minecraftforge/forge/",
          "html"));
      list = new HashMap<String, Decompiler>();
      Downloader d;
      while ((d = l.poll()) != null) {
        final File f = d.process(null);
        if (f == null)
          continue;
        final String data = Utils.fileToString(f, Utils.UTF_8);
        final Matcher y = PAT_IDX.matcher(data);
        while (y.find())
          if (done.add(y.group()))
            l.add(new Downloader("forge_" + y.group(1), Constants.FORGE_FILES + y.group(), "html"));
        final Matcher z = FORGE_PATTERN.matcher(data);
        while (z.find())
          list.put(z.group(2), new Decompiler(z.group(2), Constants.FORGE_FILES + z.group(), z.group(1)));
      }
      final File cc = new File(Constants.DATA_DIR, "custom.cfg");
      if (cc.isFile()) {
        final InputStream is = new FileInputStream(cc);
        final Properties p = new Properties();
        p.load(is);
        for (final String s : p.stringPropertyNames()) {
          final String v = p.getProperty(s);
          final int i = v.indexOf(':');
          final Decompiler t = list.get(v.substring(i + 1));
          list.put(s, new Decompiler(t.forgev, t.url, v.substring(0, i), new File(
              Constants.DATA_DIR, s), t.mmcv));
        }
        is.close();
      }
    }
    return list.get(version).decompile();
  }

  private final String forgev, url, mcv, mmcv;
  private final int forgevi;
  private final Mapping m;
  private final File cff;// TODO my tweaks

  private Decompiler(final String _forgev, final String _url, final String _mcv) {
    this(_forgev, _url, _mcv, null, _mcv);
  }

  private Decompiler(final String _forgev, final String _url, final String _mcv, final File _cff,
      final String _mmcv) {
    this.cff = _cff;
    this.m = new Mapping();
    this.forgev = _forgev;
    this.forgevi = Utils.atoi(_forgev, 9999);
    this.url = _url;
    if (this.forgevi < 188)
      this.mcv = this.mmcv = "1.3.1";
    else {
      this.mcv = _mcv;
      this.mmcv = _mmcv;
    }
  }

  private final int fernflower(final File in) throws IOException, InterruptedException {
    final File tmp = File.createTempFile("ForgeBuilder", "dir");
    tmp.delete();
    tmp.mkdir();
    final File ffjar = File.createTempFile("ForgeBuilder", ".jar");
    Utils.resourceToFile(this.forgevi < 1048 ? "/f4" : "/f3", ffjar);
    final List<String> l = new LinkedList<String>();
    l.add("java");
    l.add("-Xms2G");
    l.add("-Xmx2G");
    l.add("-jar");
    l.add(ffjar.getPath());
    if (this.m.gradle)
      l.add("-din=1");
    else
      l.add("-din=0");
    l.add("-rbr=0");
    l.add("-dgs=1");
    l.add("-asc=1");
    l.add("-log=ERROR");
    l.add(in.getPath());
    l.add(tmp.getPath());
    // TODO less process
    final Process p = new ProcessBuilder(l).redirectErrorStream(true).start();
    final InputStream is = p.getInputStream();
    while (is.read() > -1)
      continue;// prevent stop
    is.close();
    final int ret = p.waitFor();
    in.delete();
    new File(tmp, in.getName()).renameTo(in);
    Utils.rm(tmp);
    ffjar.delete();
    return ret;
  }

  private final Map<String, String> ffpatcher(final File j) throws IOException {
    final Map<String, String> r = new HashMap<String, String>();
    final InputStream s = new FileInputStream(j);
    final ZipInputStream z = new ZipInputStream(s);
    ZipEntry e;
    while ((e = z.getNextEntry()) != null) {
      if (!e.isDirectory()) {
        String data = new String(Utils.jar_entry(z, e.getSize()), Utils.ISO_8859_1);
        if (e.getName().toLowerCase().endsWith(".java"))
          data = FFPatcher.processFile(data, this.forgevi, this.mcv);
        r.put(e.getName(), data);
      }
      z.closeEntry();
    }
    z.close();
    s.close();
    return r;
  }

  private static final Pattern BEFORE = Pattern
      .compile("(?m)((case|default).+(?:\\r\\n|\\r|\\n))(?:\\r\\n|\\r|\\n)");
  private static final Pattern AFTER = Pattern
      .compile("(?m)(?:\\r\\n|\\r|\\n)((?:\\r\\n|\\r|\\n)[ \\t]+(case|default))");

  private final void astyle(final Map<String, String> target) throws IOException {
    ASFormatter fmtr = new ASFormatter();
    OptParser opt = new OptParser(fmtr);
    BufferedReader br = new BufferedReader(new StringReader(this.m.astyle_conf));
    String line;
    while ((line = br.readLine()) != null) {
      if (line.length() == 0 || line.charAt(0) == '#')
        continue;
      try {
        opt.parseOption(line.trim());
      } catch (MalformedOptionException ex) {
        System.out.println("ignore unknown astyle config \"" + line + "\"");
      }
    }
    br.close();
    final List<String> names = new ArrayList<String>(target.keySet());
    for (final String name : names) {
      if (!name.toLowerCase().endsWith(".java"))
        continue;
      Reader reader;
      Writer writer;
      String text = target.get(name);
      text = McpCleanup.stripComments(text);
      text = McpCleanup.fixImports(text);
      text = McpCleanup.cleanup(text);
      text = GLConstantFixer.fixOGL(text);
      reader = new StringReader(text);
      writer = new StringWriter();
      fmtr.format(reader, writer);
      reader.close();
      writer.flush();
      writer.close();
      text = writer.toString();
      text = BEFORE.matcher(text).replaceAll("$1");
      text = AFTER.matcher(text).replaceAll("$1");
      if (this.forgevi > 534)
        text = FmlCleanup.renameClass(text, this.forgevi);
      target.put(name, text);
    }
  }

  private final boolean ffpatch(final Map<String, String> t, final File d1, final File d2,
      final File d3) throws IOException {
    for (final List<String> l : this.m.ff_patch.values())
      if (!UnifiedDiff.patch(t, d1, d2, d3, false, l))
        return false;
    return true;
  }

  private final boolean decompile() throws Exception {
    File dbg1 = new File(Constants.DATA_DIR, "debug");
    final File dbg2 = new File(dbg1, "to");
    final File dbg3 = new File(dbg1, "rej");
    dbg1 = new File(dbg1, "from");
    System.out.println("<<< Start decompile of " + this.forgev);
    System.out.println("> Start forge zip download");
    final File fzip =
        this.cff != null ? this.cff : new Downloader(this.forgev, this.url, "jar").process(null);
    this.m.checkAndLoad(this.url, this.forgev, this.forgevi, this.mmcv, fzip);
    this.m.finalyze();
    System.out.println("> Start MCPMerge AccessTransformer MCInjector and SpecialSource");
    final MainTransformer trans = new MainTransformer(this.forgevi, this.m);
    final File tmp = File.createTempFile("ForgeBuilder", ".jar");
    final Map<String, byte[]> res = new HashMap<String, byte[]>();
    final OutputStream sout = new FileOutputStream(tmp);
    MainTransformer.write_jar(sout, trans.process_jar(this.mcv, res), res, null);
    sout.close();
    System.out.println("> Start fernflower");
    fernflower(tmp);
    System.out.println("> Start ffpatcher");
    final Map<String, String> sources = ffpatcher(tmp);
    tmp.delete();
    System.out.println("> Apply ffpatch");
    if (!ffpatch(sources, dbg1, dbg2, dbg3))
      return false;
    System.out.println("> Apply astyle");
    astyle(sources);
    System.out.println("> Apply fmlpatch");
    if (!this.m.fml_patches.patch(sources, false, dbg1, dbg2, dbg3, false))
      return false;
    if (this.forgevi >= 1048) {
      System.out.println("> Apply forgepatch");
      if (!this.m.forge_patches.patch(sources, false, dbg1, dbg2, dbg3, false))
        return false;
    }
    System.out.println("> Rename source (Phase 1)");
    this.m.rename(sources, false, this.forgevi);
    if (this.forgevi < 1048) {
      System.out.println("> Apply forgepatch");
      if (!this.m.forge_patches.patch(sources, false, dbg1, dbg2, dbg3, false))
        return false;
    }
    System.out.println("> Add source");
    for (final String k : this.m.sources.keySet())
      sources.put(k, new String(this.m.sources.get(k), Utils.ISO_8859_1));
    System.out.println("> Rename source (Phase 2)");
    this.m.rename(sources, true, this.forgevi);
    System.out.println("> Process my stuff");
    boolean paulscode = false;
    final List<String> names = new ArrayList<String>(sources.keySet());
    for (final String name : names) {
      if (name.startsWith("argo") && this.m.json.contains("net.sourceforge.argo:argo"))
        sources.remove(name);
      if (name.startsWith("paulscode") || name.startsWith("com/jcraft") || name.startsWith("ibxm")) {
        paulscode = true;
        sources.remove(name);
      }
    }
    if (paulscode) {
      final ZipInputStream in =
          new ZipInputStream(getClass().getResourceAsStream("/paulscode.zip"));
      ZipEntry entry;
      while ((entry = in.getNextEntry()) != null) {
        if (!entry.isDirectory())
          sources.put(entry.getName(), new String(Utils.jar_entry(in, entry.getSize()),
              Utils.ISO_8859_1));
        in.closeEntry();
      }
      in.close();
    }
    System.out.println("> Compile minecraft");
    final int ret =
        Compiler.exec_javac(sources, null, MavenWrapper.getLegacy(this.m.json.getNames(),
            this.forgev), null, new File(Constants.DATA_DIR, this.forgev + "-sources.jar"),
            new File(Constants.DATA_DIR, this.forgev + "-dev.jar"), null);
    if (ret != 0)
      throw new IllegalStateException(Integer.toString(ret));
    System.out.println("> Save config");
    final ForgeData.ForgeConfig fdfc = new ForgeData.ForgeConfig();
    if (this.forgevi > 534)
      fdfc.identifier = "RGMCPSRG";
    fdfc.mcv = this.mcv;
    fdfc.depends = new ArrayList<String>();
    fdfc.depends.addAll(this.m.json.getNames());
    final Gson gson = new Gson();
    OutputStream os = new FileOutputStream(new File(Constants.DATA_DIR, this.forgev + ".cfg"));
    Writer w = new OutputStreamWriter(os, "UTF-8");
    w.write(gson.toJson(fdfc));
    w.close();
    os.close();
    System.out.println("> Save srg");
    os = new FileOutputStream(new File(Constants.DATA_DIR, this.forgev + ".srg"));
    w = new OutputStreamWriter(os, Utils.UTF_8);
    MappingBuilder.writeSrg(w, this.m.ss);
    w.close();
    os.close();
    System.out.println("<<< Decompile is done");
    return true;
  }
}
