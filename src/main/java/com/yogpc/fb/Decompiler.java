package com.yogpc.fb;

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
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.github.abrarsyed.jastyle.ASFormatter;
import com.github.abrarsyed.jastyle.constants.EnumFormatStyle;
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
  private static final ASFormatter fmtr = new ASFormatter();
  static {
    fmtr.setFormattingStyle(EnumFormatStyle.ALLMAN);// style=allman
    // TODO add-brackets
    fmtr.setBreakClosingHeaderBracketsMode(true);// break-closing-brackets
    fmtr.setSwitchIndent(true);// indent-switches
    fmtr.setMaxInStatementIndentLength(40);// max-instatement-indent=40
    fmtr.setOperatorPaddingMode(true);// pad-oper
    // TODO pad-header
    fmtr.setParensUnPaddingMode(true);// unpad-paren
    fmtr.setBreakBlocksMode(true);// break-blocks
    fmtr.setDeleteEmptyLinesMode(true);// delete-empty-lines
  }

  private static final String PAT_BASE = "http://files\\.minecraftforge\\.net/";
  private static final String PAT_MAV =
      PAT_BASE
          + "maven/net/minecraftforge/forge/([0-9\\._]+)-([0-9\\._]+)\\.([0-9]+)(?:-[^\\-]+)?/forge-([0-9\\._]+)-([0-9\\._]+)\\.([0-9]+)(?:-[^\\-]+)?-";
  private static final Pattern[] FORGE_PATTERN = {Pattern.compile(PAT_MAV + "src\\.zip"),
      Pattern.compile(PAT_MAV + "userdev\\.jar")};
  private static final Pattern PAT_IDX = Pattern.compile(PAT_BASE + "minecraftforge//?([^\"]+)");

  static final boolean exec(final String version) throws Exception {
    if (list == null) {
      final Queue<Downloader> l = new LinkedList<Downloader>();
      l.add(new Downloader("forge_main", new URL(Constants.FORGE_BASE + "minecraftforge/"), "html"));
      list = new HashMap<String, Decompiler>();
      Downloader d;
      while ((d = l.poll()) != null) {
        final File f = d.process(null);
        if (f == null)
          continue;
        final String data = Utils.fileToString(f, Utils.UTF_8);
        final Matcher y = PAT_IDX.matcher(data);
        while (y.find()) {
          if (y.group(1).endsWith(".css") || y.group(1).endsWith(".html"))
            continue;
          l.add(new Downloader("forge_" + y.group(1), new URL(y.group()), "html"));
        }
        for (final Pattern p : FORGE_PATTERN) {
          final Matcher z = p.matcher(data);
          while (z.find())
            list.put(z.group(3), new Decompiler(z.group(3), z.group(), z.group(1)));
        }
      }
    }
    return list.get(version).decompile();
  }

  private final String forgev, url, mcv;
  private final int forgevi;
  private final Mapping m;

  private Decompiler(final String _forgev, final String _url, final String _mcv) {
    this.m = new Mapping();
    this.forgev = _forgev;
    this.forgevi = Integer.parseInt(_forgev);
    this.url = _url;
    if (this.forgevi < 188)
      this.mcv = "1.3.1";
    else
      this.mcv = _mcv;
  }

  private final int fernflower(final File in) throws IOException, InterruptedException {
    final File tmp = File.createTempFile("ForgeBuilder", "dir");
    tmp.delete();
    tmp.mkdir();
    final File ffjar = File.createTempFile("ForgeBuilder", ".jar");
    Utils.byteArrayToFile(
        Utils.urlToByteArray(Decompiler.class.getResource(this.forgevi < 1048 ? "/f4" : "/f3")),
        ffjar);
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

  private static final String MC_BASE = "https://s3.amazonaws.com/Minecraft.Download/versions/";
  private static final File MINECRAFT_VERSIONS = new File(Constants.MINECRAFT_DIR, "versions");

  private final File[] download() throws Exception {
    final Downloader[] a =
        new Downloader[] {
            new Downloader(this.forgev, new URL(this.url), "jar"),
            new Downloader(this.mcv, new URL(MC_BASE + this.mcv + "/" + this.mcv + ".jar"),
                new File(MINECRAFT_VERSIONS, this.mcv + File.separatorChar + this.mcv + ".jar")),
            new Downloader(this.mcv + "server", new URL(MC_BASE + this.mcv + "/minecraft_server."
                + this.mcv + ".jar"), "jar")};
    return new File[] {a[0].process(null), a[1].process(null), a[2].process(null)};
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
    dbg1.delete();
    dbg2.delete();
    dbg3.delete();
    System.out.println("<<< Start decompile");
    System.out.println("> Start downloads");
    final MCPData md = new MCPData(this.url, this.forgev, this.mcv);
    final File[] files = download();
    this.m.load_forge_zip(files[0]);
    md.patch(this.m.ss);
    this.m.finalyze();
    System.out.println("> Start MCPMerge AccessTransformer MCInjector and SpecialSource");
    final MainTransformer trans = new MainTransformer(this.forgevi, null, this.m);
    final File tmp = File.createTempFile("ForgeBuilder", ".jar");
    trans.process_jar(files[2], files[1], tmp);
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
