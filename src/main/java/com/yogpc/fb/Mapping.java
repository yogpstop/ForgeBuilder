package com.yogpc.fb;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.objectweb.asm.commons.Remapper;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.yogpc.fb.fg.FmlCleanup;
import com.yogpc.fb.map.JarMapping;
import com.yogpc.fb.map.MappingBuilder;
import com.yogpc.fb.ml.CompleteMinecraftVersion;
import com.yogpc.fb.sa.Constants;
import com.yogpc.fb.sa.Downloader;
import com.yogpc.fb.sa.MavenWrapper;
import com.yogpc.fb.sa.UnifiedDiff;
import com.yogpc.fb.sa.Utils;

public final class Mapping {
  private static final Pattern libs = Pattern.compile("for +lib +in +(\\[[^\\]]+\\])");
  private static final Pattern argo = Pattern.compile("argo-([23]\\.[0-9]{1,2}).*");
  private static final Pattern guava = Pattern
      .compile("guava-(1[0-8]\\.0(?:\\.1|-rc[1-9]|-final)?)\\.jar");
  private static final Pattern asm = Pattern.compile("asm-(?:debug-)?all-(4\\.[0-2])\\.jar");
  private static final Pattern bcprov = Pattern
      .compile("bcprov-(?:debug-)?jdk15on-(1\\.?4[6-8])\\.jar");
  private static final Pattern SRG_PAT =
      Pattern
          .compile("(?<!// JAVADOC (?:METHOD|FIELD) \\$\\$ )(func_[0-9]+_[a-zA-Z]+_?|field_[0-9]+_[a-zA-Z]+|p_[a-zA-Z0-9]+_[0-9]+_)");
  private static final Pattern JD_PAT = Pattern
      .compile("^([ \t]+)// JAVADOC (METHOD|FIELD) \\$\\$ (.+)$");
  private static final Pattern FIELD_REG = Pattern
      .compile("^([\\t ]+)([^\\s=;]+ +)+(field_\\w+) *(=|;)");
  private static final Pattern PKG_PAT_BS = Pattern
      .compile("net\\\\minecraft\\\\src\\\\([^\\\\\\.]+)");
  private static final Pattern PKG_PAT_FS = Pattern.compile("net/minecraft/src/[A-Za-z0-9$_]+");

  public final JarMapping ss = new JarMapping();
  final UnifiedDiff fml_patches = new UnifiedDiff();
  final UnifiedDiff forge_patches = new UnifiedDiff();
  public final Map<String, byte[]> sources = new HashMap<String, byte[]>();
  final Map<String, List<String>> ff_patch = new HashMap<String, List<String>>();
  CompleteMinecraftVersion json;
  public String[] merge_buf;
  String astyle_conf;
  public final Properties mci_cfg = new Properties();
  public JsonObject json_buf;
  private byte[] local_mci_buf, local_fmlpy_buf;
  public boolean gradle;
  public String urlSide;
  public String urlSideOnly;

  private final void buildJavadoc(final String indent, final String name, final boolean isMethod,
      final List<String> dest, final int forgevi, final boolean force) {
    int back = 0;
    if (967 <= forgevi)
      while (dest.get(dest.size() - 1 - back).trim().startsWith("@"))
        back++;
    final String javadoc = this.ss.getJavadoc(name);
    if (javadoc == null || javadoc.length() <= 0)
      return;
    if (967 <= forgevi && forgevi < 1048 && !force)
      dest.add(dest.size() - back, indent + "// JAVADOC " + (isMethod ? "METHOD" : "FIELD")
          + " $$ " + name);
    else {
      final String prev = dest.get(dest.size() - 1 - back).trim();
      if (forgevi < 967 && prev != null && !prev.equals("") && !prev.equals("{"))
        dest.add(dest.size() - back, "");
      if (isMethod || javadoc.length() >= 70) {
        dest.add(dest.size() - back, indent + "/**");
        for (final String line : wrapText(javadoc, 120 - (indent.length() + 3)))
          dest.add(dest.size() - back, indent + " * " + line);
        dest.add(dest.size() - back, indent + " */");
      } else
        dest.add(dest.size() - back, indent + "/** " + javadoc + " */");
    }
  }

  private static final List<String> wrapText(final String text, final int len) {
    final List<String> lines = new ArrayList<String>();
    final StringBuilder line = new StringBuilder();
    final StringBuilder word = new StringBuilder();
    int tempNum;
    for (final char c : text.toCharArray())
      if (c == ' ' || c == ',' || c == '-') {
        word.append(c);
        tempNum = Character.isWhitespace(c) ? 1 : 0;
        if (line.length() + word.length() - tempNum > len) {
          lines.add(line.toString().trim());
          line.delete(0, line.length());
        }
        line.append(word);
        word.delete(0, word.length());
      } else
        word.append(c);
    if (word.length() > 0) {
      if (line.length() + word.length() > len) {
        lines.add(line.toString().trim());
        line.delete(0, line.length());
      }
      line.append(word);
    }
    if (line.length() > 0)
      lines.add(line.toString().trim());
    return lines;
  }

  private static final boolean matchMap(final String name, final String base) {
    return name.equals("conf/" + base + ".csv") || name.equals("forge/fml/conf/" + base + ".csv")
        || name.equals("mappings/" + base + ".csv") || name.equals(base + ".csv");
  }

  private static final boolean matchSrgExc(final String name, final String ext) {
    return name.endsWith(ext)
        && (name.startsWith("conf/") || name.startsWith("forge/fml/conf/") || name.indexOf('/') == -1);
  }

  private static final void load_patch(final byte[] file, final UnifiedDiff diff)
      throws IOException {
    final InputStream fis = new ByteArrayInputStream(file);
    final InputStreamReader isr = new InputStreamReader(fis, Utils.ISO_8859_1);
    final BufferedReader br = new BufferedReader(isr);
    diff.add(br, -1);
    br.close();
    isr.close();
    fis.close();
  }

  private static final void load_zip_patch(final byte[] jar, final UnifiedDiff diff)
      throws IOException {
    final InputStream is = new ByteArrayInputStream(jar);
    final ZipInputStream in = new ZipInputStream(is);
    ZipEntry entry;
    while ((entry = in.getNextEntry()) != null) {
      if (!entry.isDirectory())
        load_patch(Utils.jar_entry(in, entry.getSize()), diff);
      in.closeEntry();
    }
    in.close();
    is.close();
  }

  private final void load_zip_source(final byte[] jar) throws IOException {
    final InputStream is = new ByteArrayInputStream(jar);
    final ZipInputStream in = new ZipInputStream(is);
    ZipEntry entry;
    while ((entry = in.getNextEntry()) != null) {
      if (!entry.isDirectory())
        load_file(entry.getName(), Utils.jar_entry(in, entry.getSize()));
      in.closeEntry();
    }
    in.close();
    is.close();
  }

  final void rename(final Map<String, String> target, final boolean skipJD, final int forgevi) {
    final Remapper r = this.ss.getRemapper(JarMapping.SRG_RAW, null);
    for (final String name : new ArrayList<String>(target.keySet())) {
      if (!name.toLowerCase().endsWith(".java"))
        continue;
      final List<String> dest = new ArrayList<String>();
      for (final String line : target.get(name).split("(\r\n|\r|\n)")) {
        Matcher m;
        if (!skipJD) {
          // Method javadoc
          m = FmlCleanup.METHOD_REG.matcher(line);
          if (m.find())
            buildJavadoc(m.group(1), m.group(4), true, dest, forgevi, false);
          // Field javadoc
          m = FIELD_REG.matcher(line);
          if (m.find())
            buildJavadoc(m.group(1), m.group(3), false, dest, forgevi, false);
        } else {
          m = JD_PAT.matcher(line);
          if (m.matches()) {
            buildJavadoc(m.group(1), m.group(3), m.group(2).equals("METHOD"), dest, forgevi, true);
            continue;
          }
        }
        m = SRG_PAT.matcher(line);
        final StringBuffer sb = new StringBuffer();
        while (m.find()) {
          m.appendReplacement(sb, "");
          sb.append(r.mapMethodName(null, m.group(0), null));
        }
        m.appendTail(sb);
        dest.add(sb.toString());
      }
      final StringBuilder sb = new StringBuilder();
      for (final String line : dest)
        sb.append(line).append('\n');
      target.put(name, sb.toString());
    }
  }

  private static final void check_mcpbot(final String mcv, final String ch, final String ver,
      final List<String> out) throws Exception {
    final JsonPrimitive pver = new JsonPrimitive(Long.valueOf(ver));
    final int idx = ch.indexOf('_');
    final String chb = idx >= 0 ? ch.substring(0, idx) : ch;
    final JsonObject root =
        new JsonParser().parse(
            Utils.fileToString(new Downloader("mcpbot_versions",
                "http://export.mcpbot.bspk.rs/versions.json", "json").process(null),
                Utils.ISO_8859_1)).getAsJsonObject();
    for (final Map.Entry<String, JsonElement> e : root.entrySet())
      if (e.getValue().getAsJsonObject().getAsJsonArray(chb).contains(pver)) {
        out.add("de.oceanlabs.mcp:mcp_" + ch + ":" + ver + "-" + e.getKey() + ":zip");
        break;
      }
  }

  private static final Pattern BGM = Pattern.compile("mappings\\s*=\\s*\"([^\"]+)_(\\d+)\"");

  final void checkAndLoad(final String url, final String fv, final int fvi, final String mcv, final File fl)
      throws Exception {
    final InputStream is = new FileInputStream(fl);
    final ZipInputStream in = new ZipInputStream(is);
    ZipEntry entry;
    final List<String> s = new ArrayList<String>(2);
    boolean found = false;
    while ((entry = in.getNextEntry()) != null) {
      if (entry.getName().equals("build.gradle")) {
        found = true;
        final byte[] d = Utils.jar_entry(in, entry.getSize());
        final Matcher m = BGM.matcher(new String(d, Utils.ISO_8859_1));
        if (m.find())
          check_mcpbot(mcv, m.group(1), m.group(2), s);
        break;
      }
      in.closeEntry();
    }
    in.close();
    is.close();
    if (!found) {
      load_forge_zip(fl, fvi);
      return;
    }
    final File f =
        new Downloader(fv + "ud", url.substring(0, url.length() - 7) + "userdev.jar", "jar")
            .process(null);
    load_forge_zip(f, fvi);
    if (s.size() < 1)
      return;
    if (fvi >= 1503)
      s.add("de.oceanlabs.mcp:mcp:" + mcv + ":srg:zip");
    for (final File lf : MavenWrapper.getLegacy(s, fv))
      load_forge_zip(lf, fvi);
  }

  private final void load_forge_zip(final File f, final int fvi) throws IOException {
    final InputStream is = new FileInputStream(f);
    final ZipInputStream in = new ZipInputStream(is);
    ZipEntry entry;
    while ((entry = in.getNextEntry()) != null) {
      if (!entry.isDirectory()) {
        final String n = entry.getName();
        final byte[] d = Utils.jar_entry(in, entry.getSize());
        if (n.endsWith("~"))
          continue;
        else if (matchMap(n, "packages"))
          MappingBuilder.loadCsv(new String(d, Utils.ISO_8859_1), this.ss, false, true);
        else if (matchMap(n, "params"))
          MappingBuilder.loadCsv(new String(d, Utils.ISO_8859_1), this.ss, false, false);
        else if (matchMap(n, "methods"))
          MappingBuilder.loadCsv(new String(d, Utils.ISO_8859_1), this.ss, true, false);
        else if (matchMap(n, "fields"))
          MappingBuilder.loadCsv(new String(d, Utils.ISO_8859_1), this.ss, true, false);
        else if (matchSrgExc(n, ".srg"))
          MappingBuilder.loadSrg(new String(d, Utils.ISO_8859_1), this.ss);
        else if (n.equals("yogpstop/srg"))// TODO my tweaks
          MappingBuilder.loadNew(new String(d, Utils.ISO_8859_1), this.ss, true);
        else if ((n.startsWith("conf/") || n.startsWith("forge/fml/conf/")) && n.contains(".patch")
            && !n.contains("minecraft_server_ff"))
          loadFFPatch(n, d, fvi);
        else if (n.startsWith("patches/minecraft_merged_ff/") && n.contains(".patch"))
          loadFFPatch(n, d, fvi);
        else if (n.startsWith("forge/fml/patches/minecraft/")
            || n.startsWith("forge/fml/patches/common/"))
          load_patch(d, this.fml_patches);
        else if (n.startsWith("forge/patches/minecraft/") || n.startsWith("forge/patches/common/"))
          load_patch(d, this.forge_patches);
        else if (n.startsWith("forge/fml/common/") || n.startsWith("forge/fml/client/"))
          load_file(n.substring(17), d);
        else if (n.startsWith("forge/common/") || n.startsWith("forge/client/"))
          load_file(n.substring(13), d);
        else if (n.startsWith("src/main/java/"))
          load_file(n.substring(14), d);
        else if (n.startsWith("src/main/resources/"))
          load_file(n.substring(19), d);
        else if (n.equals("sources.zip") || n.equals("resources.zip"))
          load_zip_source(d);
        else if (n.equals("fmlpatches.zip"))
          load_zip_patch(d, this.fml_patches);
        else if (n.equals("forgepatches.zip") || n.equals("patches.zip"))
          load_zip_patch(d, this.forge_patches);
        else if (n.equals("forge/fml/fml.json"))
          loadJson(d);
        else if (n.equals("dev.json"))
          loadJson(d);
        else if (matchSrgExc(n, ".exc"))
          this.local_mci_buf = d;
        else if (n.endsWith("/mcp_merge.cfg"))
          this.merge_buf = new String(d, Utils.UTF_8).split("(\r\n|\r|\n)");
        else if (n.equals("forge/fml/fml.py"))
          this.local_fmlpy_buf = d;
        else if (n.equals("conf/exceptor.json") || n.equals("exceptor.json"))
          this.json_buf = (JsonObject) new JsonParser().parse(new String(d));
        else if (n.equals("forge/fml/conf/astyle.cfg") || n.equals("conf/astyle.cfg") || n.equals("astyle.cfg"))
          this.astyle_conf = new String(d, Utils.ISO_8859_1);
      }
      in.closeEntry();
    }
    in.close();
    is.close();
  }

  final void finalyze() throws IOException {
    if (urlSide == null)
      urlSide = Constants.DEFAULT_SIDE_PATH + "/Side";
    if (!this.sources.containsKey(urlSide + ".java"))
      this.sources.put(urlSide + ".java", Utils.jar_entry(Mapping.class.getResourceAsStream("/Side.java"), -1));
    if (urlSideOnly == null)
      urlSideOnly = Constants.DEFAULT_SIDE_PATH + "/SideOnly";
    if (!this.sources.containsKey(urlSideOnly + ".java"))
      this.sources.put(urlSideOnly + ".java", Utils.jar_entry(Mapping.class.getResourceAsStream("/SideOnly.java"), -1));
    // //////////////////////////////////////////////////////////////
    this.gradle = this.local_fmlpy_buf == null;
    if (this.json == null && this.local_fmlpy_buf != null)
      generateJson();
    final List<String> ks = new ArrayList<String>(this.ff_patch.keySet());
    Matcher m;
    StringBuffer sb;
    for (final String k : ks) {
      final List<String> ls = this.ff_patch.get(k);
      final List<String> vs = new ArrayList<String>(ls);
      ls.clear();
      for (final String v : vs) {
        m = PKG_PAT_BS.matcher(v);
        sb = new StringBuffer();
        while (m.find()) {
          final String s = this.ss.toPkd("net/minecraft/src/" + m.group(1));
          if (s != null) {
            m.appendReplacement(sb, "");
            sb.append(s);
          }
        }
        m.appendTail(sb);
        ls.add(sb.toString());
      }
    }
    // //////////////////////////////////////////////////////////////
    m = PKG_PAT_FS.matcher(new String(this.local_mci_buf));
    sb = new StringBuffer();
    while (m.find()) {
      final String s = this.ss.toPkd(m.group());
      if (s != null) {
        m.appendReplacement(sb, "");
        sb.append(s);
      }
    }
    m.appendTail(sb);
    final Reader r = new StringReader(sb.toString());
    this.mci_cfg.load(r);
    r.close();
    MappingBuilder.fix(this.ss);
  }

  private final void generateJson() {
    this.json = new CompleteMinecraftVersion();
    this.json.add("org.lwjgl.lwjgl:lwjgl_util:2.9.1");
    this.json.add("org.lwjgl.lwjgl:lwjgl:2.9.1");
    this.json.add("org.lwjgl.lwjgl:lwjgl-platform:2.9.1");
    this.json.add("net.java.jinput:jinput:2.0.5");
    this.json.add("net.java.jinput:jinput-platform:2.0.5");
    Matcher m = libs.matcher(new String(this.local_fmlpy_buf, Utils.ISO_8859_1));
    if (m.find()) {
      String s = m.group(1).replaceAll("[\\[\\]]", ",").replaceAll("[\\s'\"]*,[\\s'\"]*", ",");
      s = s.substring(1, s.length() - 1);
      for (final String lib : s.split(",")) {
        if (lib.startsWith("scala")) {
          this.json.add("org.scala-lang:scala-library:2.10.0");// on 1.5.2
          continue;
        }
        m = argo.matcher(lib);
        if (m.matches()) {
          this.json.add("net.sourceforge.argo:argo:" + m.group(1));
          continue;
        }
        m = guava.matcher(lib);
        if (m.matches()) {
          this.json.add("com.google.guava:guava:" + m.group(1));
          continue;
        }
        m = asm.matcher(lib);
        if (m.matches()) {
          this.json.add("org.ow2.asm:asm-debug-all:" + m.group(1));
          continue;
        }
        m = bcprov.matcher(lib);
        if (m.matches()) {
          String v = m.group(1);
          if (v.length() == 3)
            v = new String(new char[] {v.charAt(0), '.', v.charAt(1), v.charAt(2)});
          this.json.add("org.bouncycastle:bcprov-jdk15on:" + v);
          continue;
        }
      }
    }
  }

  private final void loadJson(final byte[] data) throws IOException {
    final Gson gson = new Gson();
    final InputStream is = new ByteArrayInputStream(data);
    final Reader r = new InputStreamReader(is, "UTF-8");
    this.json = gson.fromJson(r, CompleteMinecraftVersion.class);
    r.close();
  }

  private final void loadFFPatch(final String name, final byte[] patch, final int fvi) {
    // FIXME may duplicate class name?
    final String s = name.substring(name.lastIndexOf('/') + 1, name.lastIndexOf(".patch"));
    if (fvi >= 1503 && s.contains("Enum")) // acb3b85d25d-adebc409202 FIXME finish at?
      return;
    List<String> l = this.ff_patch.get(s);
    if (l == null)
      this.ff_patch.put(s, l = new ArrayList<String>());
    l.add(new String(patch, Utils.ISO_8859_1));
  }

  private final void load_file(final String name, final byte[] data) {
    if (name.endsWith("/Side.java"))
      this.urlSide = name.substring(0, name.length() - 5);
    if (name.endsWith("/SideOnly.java"))
      this.urlSideOnly = name.substring(0, name.length() - 5);
    this.sources.put(name, data);
  }
}
