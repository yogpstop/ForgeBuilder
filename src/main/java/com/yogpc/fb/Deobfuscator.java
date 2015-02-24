package com.yogpc.fb;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.xml.bind.DatatypeConverter;

import com.yogpc.fb.asm.MainTransformer;
import com.yogpc.fb.map.JarMapping;
import com.yogpc.fb.map.MappingBuilder;
import com.yogpc.fb.sa.Constants;
import com.yogpc.fb.sa.IProcessor;

public class Deobfuscator implements IProcessor {
  public static void main(final String[] args) throws Exception {
    String fv = "", in = "";
    Boolean b = null;
    final List<String> cp = new ArrayList<String>();
    for (final String arg : args)
      if (arg.startsWith("-f"))
        fv = arg.substring(2);
      else if (arg.startsWith("-i"))
        in = arg.substring(2);
      else if (arg.startsWith("-c"))
        cp.add(arg.substring(2));
      else if (arg.equals("-s"))
        b = new Boolean(true);
      else if (arg.equals("-b"))
        b = new Boolean(false);
    final int fvi = Integer.parseInt(fv);
    if (b == null)
      b = new Boolean(fvi > 534);
    new Deobfuscator(b.booleanValue(), fv, cp).process(new File(in));
  }

  private final boolean srg;
  private final String fv;
  private final List<String> cp;

  public Deobfuscator(final boolean srg, final String fv, final List<String> cp) {
    this.srg = srg;
    this.fv = fv;
    this.cp = cp == null ? new ArrayList<String>() : cp;
  }

  private static final boolean check(final File in, final File out, final File cfg,
      final Properties p) throws Exception {
    InputStream is = null;
    if (cfg.isFile()) {
      is = new FileInputStream(cfg);
      p.load(is);
      is.close();
    }
    final MessageDigest md = MessageDigest.getInstance("SHA-512");
    final byte[] buf = new byte[8192];
    int nread;
    is = new FileInputStream(in);
    while ((nread = is.read(buf)) > -1)
      md.update(buf, 0, nread);
    is.close();
    String hash = DatatypeConverter.printHexBinary(md.digest());
    if (!hash.equals(p.getProperty("IN_HASH"))) {
      p.setProperty("IN_HASH", hash);
      return false;
    }
    if (!out.isFile())
      return false;
    is = new FileInputStream(out);
    while ((nread = is.read(buf)) > -1)
      md.update(buf, 0, nread);
    is.close();
    hash = DatatypeConverter.printHexBinary(md.digest());
    if (!hash.equals(p.getProperty("OUT_HASH")))
      return false;
    return true;
  }

  private IProcessor child;

  @Override
  public File process(final File in) throws Exception {
    String op = in.getPath();
    final int i = op.lastIndexOf('.');
    final String ext = op.substring(i);
    op = op.substring(0, i) + "-dev";
    final File out = new File(op + ext);
    final File cfg = new File(op + ".cfg");
    final Properties p = new Properties();
    if (!check(in, out, cfg, p)) {
      final JarMapping tsrg = new JarMapping();
      MappingBuilder.loadNew(new File(Constants.DATA_DIR, this.fv + ".srg"), tsrg);
      final MainTransformer mt = new MainTransformer(Integer.parseInt(this.fv), tsrg);
      for (final String s : this.cp)
        mt.addCP(new File(s));
      out.getParentFile().mkdirs();
      final OutputStream os = new FileOutputStream(out);
      final MessageDigest md = MessageDigest.getInstance("SHA-512");
      final OutputStream wos = new DigestOutputStream(os, md);
      final Map<String, byte[]> res = new HashMap<String, byte[]>();
      MainTransformer.write_jar(wos,
          mt.process_jar(in, res, null, null, this.srg ? JarMapping.SRG_RAW : JarMapping.OBF_RAW),
          res, null);
      wos.close();
      os.close();
      p.setProperty("OUT_HASH", DatatypeConverter.printHexBinary(md.digest()));
    }
    final OutputStream os = new FileOutputStream(cfg);
    p.store(os, null);
    os.close();
    return this.child != null ? this.child.process(out) : out;
  }

  @Override
  public void setChild(final IProcessor ip) {
    this.child = ip;
  }
}
