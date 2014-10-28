package com.yogpc.fb;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.yogpc.fb.asm.MainTransformer;
import com.yogpc.fb.map.JarMapping;
import com.yogpc.fb.sa.Constants;

public class Deobfuscator {
  public static void main(final String[] args) throws Exception {
    String fv = "", in = "", out = "";
    Boolean b = null;
    final List<String> cp = new ArrayList<String>();
    for (final String arg : args)
      if (arg.startsWith("-f"))
        fv = arg.substring(2);
      else if (arg.startsWith("-i"))
        in = arg.substring(2);
      else if (arg.startsWith("-o"))
        out = arg.substring(2);
      else if (arg.startsWith("-c"))
        cp.add(arg.substring(2));
      else if (arg.startsWith("-s"))
        b = new Boolean(true);
      else if (arg.startsWith("-b"))
        b = new Boolean(false);
    final int fvi = Integer.parseInt(fv);
    if (b == null)
      b = new Boolean(fvi > 534);
    final ForgeData fd = ForgeData.get(fv);
    final MainTransformer mt = new MainTransformer(fvi, null, fd.srg);
    for (final String s : cp)
      mt.addCP(new File(s));
    final String[] s = out.split(":");
    final StringBuilder sb = new StringBuilder();
    sb.append(s[0].replace(".", File.separator));
    sb.append(File.separator);
    sb.append(s[1]);
    sb.append(File.separator);
    sb.append(s[2]);
    sb.append(File.separator);
    sb.append(s[1]);
    sb.append('-');
    sb.append(s[2]);
    sb.append("-dev.jar");
    final File fo = new File(Constants.MINECRAFT_LIBRARIES, sb.toString());
    fo.getParentFile().mkdirs();
    mt.process_jar(new File(in), fo, null, null, b.booleanValue() ? JarMapping.SRG_RAW
        : JarMapping.OBF_RAW);
  }
}
