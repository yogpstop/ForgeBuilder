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
import java.util.regex.Pattern;

import com.google.gson.Gson;
import com.yogpc.fb.sa.Constants;
import com.yogpc.fb.sa.MavenWrapper;
import com.yogpc.fb.sa.Utils;

public final class CompilerCaller {
  private static String replace_vnum(final String from, final ProjectConfig c,
      final ProjectConfig.ForgeVersion v, final String mcv) {
    String to = from.replace("{version}", c.version);
    to = to.replace("{mcversion}", mcv);
    to = to.replace("{forgev}", v.forgev);
    return to;
  }

  private static String genOutPath(final File base, final ProjectConfig c,
      final ProjectConfig.ForgeVersion v, final String mcv) {
    File ret;
    if (v.output != null)
      ret = new File(base, replace_vnum(v.output, c, v, mcv));
    else if (c.output != null)
      ret = new File(base, replace_vnum(c.output, c, v, mcv));
    else {
      final StringBuilder sb = new StringBuilder();
      sb.append(c.groupId.replace(".", File.separator));
      sb.append(File.separator);
      sb.append(c.artifactId);
      sb.append(File.separator);
      sb.append(mcv);
      sb.append('-');
      sb.append(c.version);
      sb.append(File.separator);
      sb.append(c.artifactId);
      sb.append('-');
      sb.append(mcv);
      sb.append('-');
      sb.append(c.version);
      ret = new File(Constants.MINECRAFT_LIBRARIES, sb.toString());
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

  private static void loadAll(final File base, final ProjectConfig pc,
      final ProjectConfig.ForgeVersion fv, final Map<String, String> patches, final boolean debug)
      throws IOException {
    if (fv.parent != null)
      for (final ProjectConfig.ForgeVersion p : pc.forge)
        if (p.forgev.equals(fv.parent)) {
          fv.srces.putAll(p.srces);
          Patcher.applyPatch(patches, fv, debug, new File(base, "src"));
          return;
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
      fv.src_base = "src/" + (fv.parent == null ? "main" : fv.forgev);
    final File sbase = new File(base, fv.src_base.replace('/', File.separatorChar));
    for (final String s : from)
      loadDir(new File(sbase, s.replace('/', File.separatorChar)),
          new File(sbase, s.replace('/', File.separatorChar)), fv.srces);
  }

  private static int build(final String _base, final List<String> debugs, final String eclipse,
      final List<String> skips) throws Exception {
    System.out.print("<<< Start compile ");
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
      System.out.print("<< Start forge ");
      System.out.println(fv.forgev);
      final boolean ecl = fv.forgev.equals(eclipse);
      final boolean skip = skips != null && (skips.size() == 0 || skips.contains(fv.forgev));
      final MavenWrapper w1 = new MavenWrapper(), w2 = new MavenWrapper();
      if (ecl || !skip) {
        fd = ForgeData.get(fv.forgev);
        w1.addDownload(fv.depends);
        w2.addDownload(fd.config.depends);
        System.out.println("> Downloading dependencies");
        MavenWrapper.getJar(w1, w2);// Wait for download
        MavenWrapper.getSources(w1, w2);
        if (ecl)
          Eclipse.createEclipse(base, fd, pc, fv);
      }
      loadAll(base, pc, fv, patches,
          debugs != null && (debugs.size() == 0 || debugs.contains(fv.forgev)) || ecl);
      int ret = 0;
      if (fd != null && !skip) {
        final String out = genOutPath(base, pc, fv, fd.config.mcv);
        final LinkedHashMap<Pattern, String> rep = processReplaces(pc, fv, fd.config.mcv);
        ret = Compiler.compile(fv, out, rep, fd, w1, w2);
      }
      compiled.add(fv.forgev);
      if (ret != 0)
        return ret;
    }
    System.out.println("<<< Compile is done");
    return 0;
  }

  public static void main(final String[] args) {
    final List<String> skips = new ArrayList<String>();
    final List<String> debugs = new ArrayList<String>();
    boolean skip = false, debug = false;
    String ecl = null;
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
      else
        try {
          build(arg, debug ? debugs : null, ecl, skip ? skips : null);
        } catch (final Exception e) {
          e.printStackTrace();
        }
  }
}
