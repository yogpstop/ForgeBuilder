package com.yogpc.fb;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.Gson;
import com.yogpc.fb.sa.Constants;
import com.yogpc.fb.sa.Eclipse;
import com.yogpc.fb.sa.MavenWrapper;
import com.yogpc.fb.sa.Patcher;
import com.yogpc.fb.sa.ProjectConfig;
import com.yogpc.fb.sa.Utils;

public final class CompilerCaller {
  private static final Pattern env = Pattern.compile("\\{ENV:(.+)\\}");

  private static String replace_vnum(final String from, final ProjectConfig c,
      final ProjectConfig.ForgeVersion v, final String mcv) {
    String to = from.replace("{version}", c.version);
    to = to.replace("{mcversion}", mcv);
    to = to.replace("{forgev}", v.forgev);
    Matcher m = env.matcher(to);
    StringBuffer sb = new StringBuffer();
    while (m.find()) {
      m.appendReplacement(sb, "");
      sb.append(System.getenv(m.group(1)));
    }
    m.appendTail(sb);
    return sb.toString();
  }

  private static void addFileName(final StringBuilder sb, final ProjectConfig c,
      final ProjectConfig.ForgeVersion v, final boolean omit, final String cv) {
    if (!omit) {
      sb.append(c.artifactId);
      sb.append('-');
    }
    if (omit)
      if (cv != null)
        sb.append(cv);
      else
        sb.append(c.version);
    if (v.name != null) {
      if (omit)
        sb.append('-');
      sb.append(v.name);
      if (!omit)
        sb.append('-');
    }
    if (!omit)
      if (cv != null)
        sb.append(cv);
      else
        sb.append(c.version);
  }

  private static String genOutPath(final File base, final ProjectConfig c,
      final ProjectConfig.ForgeVersion v, final String mcv, final boolean maven,
      final boolean omit, final String cv) {
    File ret;
    if (maven) {
      final StringBuilder sb = new StringBuilder();
      sb.append(c.groupId.replace(".", File.separator));
      sb.append(File.separator);
      sb.append(c.artifactId);
      sb.append(File.separator);
      if (v.name != null) {
        sb.append(v.name);
        sb.append('-');
      }
      if (cv != null)
        sb.append(cv);
      else
        sb.append(c.version);
      sb.append(File.separator);
      addFileName(sb, c, v, omit, cv);
      ret = new File(Constants.MINECRAFT_LIBRARIES, sb.toString());
    } else if (v.output != null)
      ret = new File(base, replace_vnum(v.output, c, v, mcv));
    else if (c.output != null)
      ret = new File(base, replace_vnum(c.output, c, v, mcv));
    else {
      final StringBuilder sb = new StringBuilder();
      sb.append("target");
      sb.append(File.separator);
      addFileName(sb, c, v, omit, cv);
      ret = new File(base, sb.toString());
    }
    return ret.getPath();
  }

  private static LinkedHashMap<Pattern, String> processReplaces(final ProjectConfig c,
      final ProjectConfig.ForgeVersion v, final String mcv) {
    final LinkedHashMap<Pattern, String> ret = new LinkedHashMap<Pattern, String>();
    if (c.replace != null)
      for (final Map.Entry<String, String> entry : c.replace.entrySet()) {
        if (entry.getValue().equals("{version}"))
          entry.setValue(c.version);
        if (entry.getValue().equals("{mcversion}"))
          entry.setValue(mcv);
        if (entry.getValue().equals("{forgev}"))
          entry.setValue(v.forgev);
        ret.put(Pattern.compile(Utils.reencode(entry.getKey())), Utils.reencode(entry.getValue()));
      }
    ret.put(Pattern.compile(Utils.reencode("\\{version\\}")), Utils.reencode(c.version));
    ret.put(Pattern.compile(Utils.reencode("\\{mcversion\\}")), Utils.reencode(mcv));
    ret.put(Pattern.compile(Utils.reencode("\\{forgev\\}")), Utils.reencode(v.forgev));
    return ret;
  }

  private static void loadDir(final File b, final File f, final Map<String, String> srces)
      throws IOException {
    if (!f.exists())
      return;
    if (f.isDirectory()) {
      for (final File e : f.listFiles())
        loadDir(b, e, srces);
      return;
    }
    String n = f.getPath().replace(b.getPath(), "").replace(File.separatorChar, '/');
    if (n.charAt(0) == '/')
      n = n.substring(1);
    srces.put(n, Utils.fileToString(f, Utils.ISO_8859_1));
  }

  private static boolean loadAll(final File base, final ProjectConfig pc,
      final ProjectConfig.ForgeVersion fv, final Map<String, String> patches, final boolean debug)
      throws IOException {
    if (fv.parent != null)
      for (final ProjectConfig.ForgeVersion p : pc.forge)
        if (p.name.equals(fv.parent)) {
          fv.srces.putAll(p.srces);
          return Patcher.applyPatch(patches, fv, debug, new File(base, "src"));
        }
    final List<String> from = new LinkedList<String>();
    if (pc.java == null)
      from.add("java");
    else
      from.addAll(pc.java);
    if (pc.resources == null)
      from.add("resources");
    else
      from.addAll(pc.resources);
    if (fv.src_base == null)
      fv.src_base = "src/" + (fv.parent == null ? "main" : fv.name);
    final File sbase = new File(base, fv.src_base.replace('/', File.separatorChar));
    for (final String s : from)
      loadDir(new File(sbase, s.replace('/', File.separatorChar)),
          new File(sbase, s.replace('/', File.separatorChar)), fv.srces);
    return true;
  }

  private static final boolean has(final List<String> l, final ProjectConfig.ForgeVersion v) {
    return l != null && (l.size() == 0 || l.contains(v.name == null ? v.forgev : v.name));
  }

  private static boolean build(final String _base, final List<String> debugs, final String eclipse,
      final List<String> skips, final boolean maven, final boolean omit, final String version)
      throws Exception {
    System.out.print("<<< Start project ");
    System.out.println(_base);
    final LinkedList<String> compiled = new LinkedList<String>();
    final File base = new File(_base).getCanonicalFile();
    ProjectConfig pc;
    {
      final Gson gson = new Gson();
      final InputStream is = new FileInputStream(new File(base, "build.cfg"));
      final Reader r = new InputStreamReader(is, "UTF-8");
      pc = gson.fromJson(r, ProjectConfig.class);
      r.close();
      is.close();
    }
    final File patch = new File(base, "src" + File.separatorChar + "patch");
    final Map<String, String> patches = new HashMap<String, String>();
    if (patch.exists())
      loadDir(patch, patch, patches);
    final Queue<ProjectConfig.ForgeVersion> q =
        new LinkedList<ProjectConfig.ForgeVersion>(pc.forge);
    ProjectConfig.ForgeVersion fv;
    ForgeData fd = null;
    while ((fv = q.poll()) != null) {
      if (fv.parent != null && !compiled.contains(fv.parent)) {
        q.add(fv);
        continue;
      }
      System.out.print("<< Start compile of ");
      System.out.println(fv.name == null ? fv.forgev : fv.name);
      final boolean ecl = (fv.name == null ? fv.forgev : fv.name).equals(eclipse);
      final boolean skip = has(skips, fv);
      final MavenWrapper w1 = new MavenWrapper(), w2 = new MavenWrapper();
      if (ecl || !skip) {
        fd = ForgeData.get(fv.forgev);
        w1.addDownload(fv.depends, ecl, false, fv.forgev);
        w2.addDownload(fd.config.depends, ecl, false, fv.forgev);
        System.out.println("> Downloading dependencies");
        MavenWrapper.getJar(w1, w2);// Wait for download
        MavenWrapper.getSources(w1, w2);
        if (ecl)
          Eclipse.createEclipse(base, fd, pc, fv);
      }
      System.out.println("> Load sources and resources");
      if (!loadAll(base, pc, fv, patches, has(debugs, fv) || ecl))
        return false;
      int ret = 0;
      if (fd != null && !skip) {
        final String out = genOutPath(base, pc, fv, fd.config.mcv, maven, omit, version);
        final LinkedHashMap<Pattern, String> rep = processReplaces(pc, fv, fd.config.mcv);
        ret = Compiler.compile(fv, out, rep, fd, w1, w2);
      }
      compiled.add(fv.name == null ? fv.forgev : fv.name);
      if (ret != 0)
        return false;
    }
    System.out.println("<<< Compile is done");
    return true;
  }

  public static void main(final String[] args) throws Exception {
    final List<String> skips = new ArrayList<String>();
    final List<String> debugs = new ArrayList<String>();
    boolean skip = false, debug = false, maven = false, omit = false;
    String ecl = null, version = null;
    for (final String arg : args)
      if (arg.startsWith("-s")) {
        if (arg.length() > 2)
          skips.add(arg.substring(2));
        skip = true;
      } else if (arg.startsWith("-d")) {
        if (arg.length() > 2)
          debugs.add(arg.substring(2));
        debug = true;
      } else if (arg.startsWith("-e"))
        ecl = arg.substring(2);
      else if (arg.startsWith("-v"))
        version = arg.substring(2);
      else if (arg.equals("-m"))
        maven = true;
      else if (arg.equals("-o"))
        omit = true;
      else if (!build(arg, debug ? debugs : null, ecl, skip ? skips : null, maven, omit, version)) {
        System.err.println("<<< Compile is failed!");
        System.exit(-1);
      }
  }
}
