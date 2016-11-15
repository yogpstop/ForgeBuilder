package com.yogpc.fb.asm;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import com.yogpc.fb.Mapping;
import com.yogpc.fb.map.JarMapping;
import com.yogpc.fb.map.JarRemapper;
import com.yogpc.fb.sa.Constants;
import com.yogpc.fb.sa.Downloader;
import com.yogpc.fb.sa.Utils;

public class MainTransformer {
  private final TransformRule ops;
  private final AccessTransformer at;
  private final ExceptionTransformer et;
  private final InnerTransformer it;
  private final LVTTransformer lt;
  private final MarkerTransformer mt;
  private final SideTransformer st;
  private final JarMapping ss;
  private final JarRemapper jr = new JarRemapper();

  public MainTransformer(final int fvi, final Object o) {
    this.ops = new TransformRule(fvi);
    this.at = new AccessTransformer(this.ops);
    this.et = new ExceptionTransformer(this.ops);
    this.it = new InnerTransformer(this.ops);
    this.mt = new MarkerTransformer(this.ops);
    if (o instanceof Mapping) {
      final Mapping m = (Mapping) o;
      this.ss = m.ss;
      this.lt = new LVTTransformer(this.ops, m.gradle);
      this.st = new SideTransformer(m.urlSide, m.urlSideOnly, this.ops);
      final byte[] fmlat = m.sources.get("fml_at.cfg");
      if (fmlat != null)
        this.ops.loadAT(fmlat);
      this.ops.loadAT(m.sources.get("forge_at.cfg"));
      if (m.merge_buf == null) { // fvi >= 1503
        this.ops.dontProcess.put("org/bouncycastle", Utils.DUMMY_OBJECT);
        this.ops.dontProcess.put("org/apache", Utils.DUMMY_OBJECT);
        this.ops.dontProcess.put("com/google", Utils.DUMMY_OBJECT);
        this.ops.dontProcess.put("com/mojang/authlib", Utils.DUMMY_OBJECT);
        this.ops.dontProcess.put("com/mojang/util", Utils.DUMMY_OBJECT);
        this.ops.dontProcess.put("gnu/trove", Utils.DUMMY_OBJECT);
        this.ops.dontProcess.put("io/netty", Utils.DUMMY_OBJECT);
        this.ops.dontProcess.put("javax/annotation", Utils.DUMMY_OBJECT);
        this.ops.dontProcess.put("argo", Utils.DUMMY_OBJECT);
      } else
        this.ops.loadMerge(m.merge_buf);
      this.ops.loadMap(m.mci_cfg);
      if (m.json_buf != null)
        this.ops.loadJson(m.json_buf);
    } else if (o instanceof JarMapping) {
      this.ss = (JarMapping) o;
      this.lt = null;
      this.st = null;
    } else {
      this.ss = null;
      this.lt = null;
      this.st = new SideTransformer(null, null, this.ops);
    }
  }

  private static byte[] writeClassToBA(final ClassNode cn, final String idt) {
    final ClassWriter cw = new ClassWriter(0);
    cn.accept(cw);
    if (idt != null)
      cw.newUTF8(idt);
    return cw.toByteArray();
  }

  private static ClassNode readClassFromBA(final byte[] ba) {
    final ClassReader cr = new ClassReader(ba);
    final ClassNode cn = new ClassNode();
    cr.accept(cn, 0);
    return cn;
  }

  public static void write_jar(final OutputStream os, final Map<String, ClassNode> ret,
      final Map<String, byte[]> res, final String ident) throws IOException {
    final List<String> dirs = new ArrayList<String>();
    final ZipOutputStream zos = new ZipOutputStream(os);
    zos.setLevel(Deflater.BEST_COMPRESSION);
    for (final Map.Entry<String, ClassNode> e : ret.entrySet()) {
      Utils.jar_dir(zos, dirs, e.getKey());
      zos.putNextEntry(new ZipEntry(e.getKey() + ".class"));
      zos.write(writeClassToBA(e.getValue(), ident));
      zos.closeEntry();
    }
    for (final Map.Entry<String, byte[]> e : res.entrySet()) {
      Utils.jar_dir(zos, dirs, e.getKey());
      zos.putNextEntry(new ZipEntry(e.getKey()));
      zos.write(e.getValue());
      zos.closeEntry();
    }
    zos.close();
  }

  public static void read_jar(final File inf, final Collection<String> all,
      final Map<String, ClassNode> ret, final Map<String, byte[]> res,
      final Map<String, Object> dontProcess) throws IOException {
    ZipEntry ze;
    final InputStream is = new FileInputStream(inf);
    final ZipInputStream in = new ZipInputStream(is);
    while ((ze = in.getNextEntry()) != null) {
      skip: if (!ze.isDirectory()) {
        if (dontProcess != null)
          for (final String s : dontProcess.keySet())
            if (ze.getName().startsWith(s))
              break skip;
        final byte[] data = Utils.jar_entry(in, ze.getSize());
        if (!ze.getName().toLowerCase().endsWith(".class")) {
          if (res != null) {
            final byte[] prev = res.put(ze.getName(), data);
            if (prev != null && !Arrays.equals(prev, data))
              System.out.println(">> WARN: " + ze.getName());
          }
        } else {
          String cn = ze.getName();
          cn = cn.substring(0, cn.length() - 6);
          if (all != null && !all.contains(cn))
            all.add(cn);
          ret.put(cn, readClassFromBA(data));
        }
      }
      in.closeEntry();
    }
    in.close();
    is.close();
  }

  private static final String MC_BASE = "https://s3.amazonaws.com/Minecraft.Download/versions/";
  private static final File MINECRAFT_VERSIONS = new File(Constants.MINECRAFT_DIR, "versions");

  private static final File[] mc_get(final String mcv) throws Exception {
    final Downloader[] a =
        new Downloader[] {
            new Downloader(mcv, MC_BASE + mcv + "/" + mcv + ".jar", new File(MINECRAFT_VERSIONS,
                mcv + File.separatorChar + mcv + ".jar")),
            new Downloader(mcv + "server", MC_BASE + mcv + "/minecraft_server." + mcv + ".jar",
                "jar")};
    return new File[] {a[0].process(null), a[1].process(null)};
  }

  public Map<String, ClassNode> process_jar(final String mcv, final Map<String, byte[]> res)
      throws Exception {
    System.out.println("> Start minecraft jar download");
    final File[] mcz = mc_get(mcv);
    final List<String> all = new ArrayList<String>();
    final Map<String, ClassNode> cli = new HashMap<String, ClassNode>();
    final Map<String, ClassNode> srv = new HashMap<String, ClassNode>();
    final boolean man = this.ops.dontProcess.put("META-INF/", Utils.DUMMY_OBJECT) == null;
    read_jar(mcz[0], all, cli, res, this.ops.dontProcess);
    read_jar(mcz[1], all, srv, res, this.ops.dontProcess);
    if (man)
      this.ops.dontProcess.remove("META-INF/");
    Map<String, ClassNode> ret = this.st.process(all, srv, cli);
    if (this.lt != null) {
      for (final ClassNode cn : ret.values())
        this.at.process(cn);
      ret = this.jr.process(ret, JarMapping.OBF_SRG, this.ss, null, null);
      for (final ClassNode cn : ret.values()) {
        this.at.process(cn);
        this.et.process(cn);
        this.it.process(cn);
        this.lt.process(cn);
        this.mt.process(cn);
      }
    }
    return ret;
  }

  public Map<String, ClassNode> process_jar(final File in, final Map<String, byte[]> res,
      final Collection<String> depCls, final Collection<String> mask, final int mode)
      throws Exception {
    final Map<String, ClassNode> cls = new HashMap<String, ClassNode>();
    read_jar(in, null, cls, res, this.ops.dontProcess);
    return this.jr.process(cls, mode, this.ss, depCls, mask);
  }

  public void addCP(final File cp) throws IOException {
    read_jar(cp, null, this.jr.cp, null, null);
  }
}
