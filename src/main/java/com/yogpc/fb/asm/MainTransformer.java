package com.yogpc.fb.asm;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;
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
  private final String idt;

  public MainTransformer(final int fvi, final String ident, final Object o) {
    this.idt = ident;
    this.ops = new TransformRule(fvi);
    this.at = new AccessTransformer(this.ops);
    this.et = new ExceptionTransformer(this.ops);
    this.it = new InnerTransformer(this.ops);
    this.mt = new MarkerTransformer(this.ops);
    if (o instanceof Mapping) {
      final Mapping m = (Mapping) o;
      this.ss = m.ss;
      this.lt = new LVTTransformer(this.ops, m.gradle);
      this.st = new SideTransformer(m.side_path, m.sideonly_path, this.ops);
      this.ops.loadAT(m.sources.get("fml_at.cfg"));
      this.ops.loadAT(m.sources.get("forge_at.cfg"));
      this.ops.loadMerge(m.merge_buf);
      this.ops.loadMap(m.mci_cfg);
      if (m.json_buf != null)
        this.ops.loadJson(m.json_buf);
    } else if (o instanceof JarMapping) {
      this.ss = (JarMapping) o;
      this.lt = new LVTTransformer(this.ops, false);
      this.st = new SideTransformer("", "", this.ops);
    } else
      throw new UnsupportedOperationException(o == null ? "null" : o.toString());
  }

  static byte[] writeClassToBA(final ClassNode cn, final String idt) {
    final ClassWriter cw = new ClassWriter(0);
    cn.accept(cw);
    if (idt != null)
      cw.newUTF8(idt);
    return cw.toByteArray();
  }

  static ClassNode readClassFromBA(final byte[] ba) {
    final ClassReader cr = new ClassReader(ba);
    final ClassNode cn = new ClassNode();
    cr.accept(cn, 0);
    return cn;
  }

  private static void write_jar(final OutputStream os, final Map<String, ClassNode> ret,
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

  public static void read_jar(final InputStream is, final Collection<String> all,
      final Map<String, ClassNode> ret, final Map<String, byte[]> res,
      final Map<String, Object> dontProcess) throws IOException {
    ZipEntry ze;
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
  }

  private void process_jar(final InputStream sis, final InputStream cis, final OutputStream out)
      throws Exception {
    final List<String> all = new ArrayList<String>();
    final Map<String, ClassNode> cli = new HashMap<String, ClassNode>();
    final Map<String, ClassNode> srv = new HashMap<String, ClassNode>();
    final Map<String, byte[]> res = new HashMap<String, byte[]>();
    final boolean man = this.ops.dontProcess.put(JarFile.MANIFEST_NAME, Utils.DUMMY_OBJECT) == null;
    read_jar(cis, all, cli, res, this.ops.dontProcess);
    read_jar(sis, all, srv, res, this.ops.dontProcess);
    if (man)
      this.ops.dontProcess.remove(JarFile.MANIFEST_NAME);
    Map<String, ClassNode> ret = this.st.process(all, srv, cli);
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
    write_jar(out, ret, res, this.idt);
  }

  public void process_jar(final File srv, final File cli, final File out) throws Exception {
    final InputStream ssrv = new FileInputStream(srv);
    final InputStream scli = new FileInputStream(cli);
    final OutputStream sout = new FileOutputStream(out);
    process_jar(ssrv, scli, sout);
    sout.close();
    scli.close();
    ssrv.close();
  }

  private void process_jar(final InputStream in, final OutputStream out,
      final Collection<String> depCls, final Collection<String> mask, final int mode)
      throws Exception {
    Map<String, ClassNode> cls = new HashMap<String, ClassNode>();
    final Map<String, byte[]> res = new HashMap<String, byte[]>();
    read_jar(in, null, cls, res, this.ops.dontProcess);
    cls = this.jr.process(cls, mode, this.ss, depCls, mask);
    write_jar(out, cls, res, this.idt);
  }

  public void process_jar(final File in, final File out, final Collection<String> depCls,
      final Collection<String> mask, final int mode) throws Exception {
    final InputStream is = new FileInputStream(in);
    final OutputStream os = new FileOutputStream(out);
    process_jar(is, os, depCls, mask, mode);
    os.close();
    is.close();
  }

  public void addCP(final File cp) throws IOException {
    this.jr.addCP(cp);
  }
}
