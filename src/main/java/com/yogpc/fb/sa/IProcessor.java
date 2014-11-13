package com.yogpc.fb.sa;

import java.io.File;

public interface IProcessor {
  public File process(File in) throws Exception;

  public void setChild(IProcessor ip);
}
