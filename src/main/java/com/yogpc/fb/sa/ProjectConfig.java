package com.yogpc.fb.sa;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProjectConfig {
  public static class ForgeVersion {
    public String name;
    public String rname;
    public String forgev;
    public String parent;
    public String src_base;
    public Map<String, String> manifest;
    public List<String> depends;
    public String output;
    public List<String> contains;
    public final Map<String, String> srces = new HashMap<String, String>();
  }

  public String groupId, artifactId, version;
  public List<ForgeVersion> forge;
  public List<String> java;
  public List<String> resources;
  public String output;
  public Map<String, String> replace;
}
