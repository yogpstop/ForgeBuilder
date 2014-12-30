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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.Gson;
import com.yogpc.fb.sa.Constants;
import com.yogpc.fb.sa.Eclipse;
import com.yogpc.fb.sa.MavenWrapper;
import com.yogpc.fb.sa.Patcher;
import com.yogpc.fb.sa.ProjectConfig;
import com.yogpc.fb.sa.ProjectConfig.ForgeVersion;
import com.yogpc.fb.sa.Utils;

public final class CompilerCaller {
  private static final Pattern env = Pattern.compile("\\{ENV:([^\\{\\}]+)\\}");

  private static String replace_vnum(final String from, final String cv, final ForgeVersion v,
      final String mcv) {
    String to = from.replace("{version}", cv);
    to = to.replace("{mcversion}", mcv);
    to = to.replace("{forgev}", v.forgev);
    if (v.name != null)
      to = to.replace("{vname}", v.name);
    if (v.rname != null || v.name != null)
      to = to.replace("{vnamer}", v.rname != null ? v.rname : v.name);
    final Matcher m = env.matcher(to);
    final StringBuffer sb = new StringBuffer();
    while (m.find()) {
      m.appendReplacement(sb, "");
      sb.append(System.getenv(m.group(1)));
    }
    m.appendTail(sb);
    return sb.toString();
  }

  private static void addFileName(final StringBuilder sb, final ProjectConfig c,
      final ForgeVersion v, final String cv) {
    sb.append(File.separator);
    sb.append(c.artifactId).append('-');
    if (v.name != null)
      sb.append(v.name).append('-');
    else if (v.parent != null)
      sb.append(v.forgev).append('-');
    sb.append(cv);
  }

  private static String genOutPath(final File base, final ProjectConfig c, final ForgeVersion v,
      final String mcv, final boolean maven, final String cv) {
    File ret;
    if (maven) {
      final StringBuilder sb = new StringBuilder();
      sb.append(c.groupId.replace(".", File.separator)).append(File.separator);
      sb.append(c.artifactId).append(File.separator);
      if (v.name != null)
        sb.append(v.name).append('-');
      else if (v.parent != null)
        sb.append(v.forgev).append('-');
      sb.append(cv);
      addFileName(sb, c, v, cv);
      ret = new File(Constants.MINECRAFT_LIBRARIES, sb.toString());
    } else if (v.output != null)
      ret = new File(base, replace_vnum(v.output, cv, v, mcv));
    else if (c.output != null)
      ret = new File(base, replace_vnum(c.output, cv, v, mcv));
    else {
      final StringBuilder sb = new StringBuilder();
      sb.append("target");
      addFileName(sb, c, v, cv);
      ret = new File(base, sb.toString());
    }
    return ret.getPath();
  }

  private static LinkedHashMap<Pattern, String> processReplaces(final ProjectConfig c,
      final ForgeVersion v, final String mcv) {
    final LinkedHashMap<Pattern, String> ret = new LinkedHashMap<Pattern, String>();
    if (c.replace != null)
      for (final Map.Entry<String, String> e : c.replace.entrySet())
        ret.put(Pattern.compile(Utils.reencode(e.getKey())), Utils.reencode(e.getValue()));
    ret.put(Pattern.compile(Utils.reencode("\\{version\\}")), Utils.reencode(c.version));
    ret.put(Pattern.compile(Utils.reencode("\\{mcversion\\}")), Utils.reencode(mcv));
    ret.put(Pattern.compile(Utils.reencode("\\{forgev\\}")), Utils.reencode(v.forgev));
    if (v.name != null)
      ret.put(Pattern.compile(Utils.reencode("\\{vname\\}")), Utils.reencode(v.name));
    if (v.rname != null || v.name != null)
      ret.put(Pattern.compile(Utils.reencode("\\{vnamer\\}")),
          Utils.reencode(v.rname != null ? v.rname : v.name));
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

  private static boolean loadAll(final File base, final ProjectConfig pc, final ForgeVersion fv,
      final Map<String, String> patches, final boolean debug) throws IOException {
    if (fv.parent != null)
      for (final ForgeVersion p : pc.forge)
        if (fv.parent.equals(p.name == null ? p.forgev : p.name)) {
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
      fv.src_base = "src/" + (fv.parent == null ? "main" : fv.name == null ? fv.forgev : fv.name);
    final File sbase = new File(base, fv.src_base.replace('/', File.separatorChar));
    for (final String s : from)
      loadDir(new File(sbase, s.replace('/', File.separatorChar)),
          new File(sbase, s.replace('/', File.separatorChar)), fv.srces);
    return true;
  }

  private static boolean build(final String _base, final String eclipse, final boolean mvn,
      final String cv, final VersionSorter vs) throws Exception {
    System.out.print("<<< Start project ");
    System.out.println(_base);
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
    final File pdir = new File(base, "src" + File.separatorChar + "patch");
    final Map<String, String> patches = new HashMap<String, String>();
    if (pdir.exists())
      loadDir(pdir, pdir, patches);
    final List<ForgeVersion> patch = new ArrayList<ForgeVersion>();
    final List<ForgeVersion> debug = new ArrayList<ForgeVersion>();
    final List<ForgeVersion> q = vs.sort(pc.forge, patch, debug);
    ForgeData fd = null;
    for (final ForgeVersion fv : q) {
      System.out.print("<< Start compile of ");
      System.out.println(fv.name == null ? fv.forgev : fv.name);
      final boolean ecl = (fv.name == null ? fv.forgev : fv.name).equals(eclipse);
      final MavenWrapper w1 = new MavenWrapper(), w2 = new MavenWrapper();
      if (ecl || !patch.contains(fv)) {
        fd = ForgeData.get(fv.forgev);
        if (fd == null)
          return false;
        System.out.println("> Downloading dependencies");
        w1.addDownload(fv.depends, ecl, false, fv.forgev);
        w2.addDownload(fd.config.depends, ecl, false, fv.forgev);
        MavenWrapper.getJar(w1, w2);
        if (ecl) {
          MavenWrapper.getSources(w1, w2);
          Eclipse.createEclipse(base, fd, pc, fv);
        }
      }
      System.out.println("> Load sources and resources");
      if (!loadAll(base, pc, fv, patches, debug.contains(fv) || ecl))
        return false;
      if (fd != null && !patch.contains(fv))
        if (0 != Compiler.compile(fv,
            genOutPath(base, pc, fv, fd.config.mcv, mvn, cv != null ? cv : pc.version),
            processReplaces(pc, fv, fd.config.mcv), fd, w1, w2)) {
          if ("failed".equals(eclipse)) {
            System.out.println("> Generating debug workspace");
            w1.addDownload(fv.depends, true, false, fv.forgev);
            w2.addDownload(fd.config.depends, true, false, fv.forgev);
            MavenWrapper.getJar(w1, w2);
            MavenWrapper.getSources(w1, w2);
            Eclipse.createEclipse(base, fd, pc, fv);
          }
          return false;
        }
    }
    System.out.println("<<< Compile is done");
    return true;
  }

  private static final ArrayList<String> add(final String arg, final String cmp,
      final ArrayList<String> p) {
    ArrayList<String> list = p;
    if (arg.startsWith(cmp)) {
      if (list == null)
        list = new ArrayList<String>();
      if (arg.length() > 2)
        list.add(arg.substring(2));
    }
    return list;
  }

  public static void main(final String[] args) throws Exception {
    ArrayList<String> skip = null, debug = null, only = null, patch = null;
    boolean maven = false;
    String ecl = null, cv = null;
    for (final String arg : args) {
      skip = add(arg, "-s", skip);
      debug = add(arg, "-d", debug);
      only = add(arg, "-o", only);
      patch = add(arg, "-p", patch);
      if (arg.charAt(0) == '-' && "sdop".contains(arg.substring(1, 2)))
        continue;
      if (arg.startsWith("-e"))
        ecl = arg.substring(2);
      else if (arg.startsWith("-v"))
        cv = arg.substring(2);
      else if (arg.equals("-m"))
        maven = true;
      else if (!build(arg, ecl, maven, cv, new VersionSorter(skip, debug, only, patch))) {
        System.err.println("<<< Compile is failed!");
        System.exit(-1);
      }
    }
  }
}
