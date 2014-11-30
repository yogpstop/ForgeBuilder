package com.yogpc.fb;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.HashMap;
import java.util.List;

import com.google.gson.Gson;
import com.yogpc.fb.map.JarMapping;
import com.yogpc.fb.map.MappingBuilder;
import com.yogpc.fb.sa.Constants;

public class ForgeData {
  private static final HashMap<String, ForgeData> versions = new HashMap<String, ForgeData>();

  public static class ForgeConfig {
    String identifier;
    String mcv;
    public List<String> depends;
  }

  public final ForgeConfig config;
  final JarMapping srg = new JarMapping();
  final File jar;


  private ForgeData(final File c, final File s, final File j) throws IOException {
    final InputStream is = new FileInputStream(c);
    final Reader r = new InputStreamReader(is, "UTF-8");
    this.config = new Gson().fromJson(r, ForgeConfig.class);
    r.close();
    this.jar = j;
    MappingBuilder.loadNew(s, this.srg);
  }

  static ForgeData get(final String version) throws Exception {
    if (versions.containsKey(version))
      return versions.get(version);
    final File jar = new File(Constants.DATA_DIR, version + "-dev.jar");
    final File config = new File(Constants.DATA_DIR, version + ".cfg");
    final File srg = new File(Constants.DATA_DIR, version + ".srg");
    if (jar.isFile() && config.isFile() && srg.isFile())
      versions.put(version, new ForgeData(config, srg, jar));
    else if (!Decompiler.exec(version))
      return null;
    return get(version);
  }

  // For debug
  public static final void main(final String[] arg) throws Exception {
    // ForgeGradle 1.2
    get("1245");// 1.8
    get("1237");// 1.8
    get("1240");// 1.7.10
    get("1236");// 1.7.10
    get("1208");// 1.7.10
    get("1150");// 1.7.10
    get("1161");// 1.7.2
    get("1147");// 1.7.2
    get("1048");// 1.7.2
    // ForgeGradle 1.1
    get("1047");// 1.7.2
    get("967");// 1.7.2
    // ForgeGradle 1.0
    get("960");// 1.6.4
    // Mod Coder Pack
    get("965");// 1.6.4
    get("738");// 1.5.2
    get("534");// 1.4.7
    get("443");// 1.4.5
    get("355");// 1.4.2
    get("318");// 1.3.2
    get("188");// 1.3.2
    get("183");// 1.3.1
  }
}
