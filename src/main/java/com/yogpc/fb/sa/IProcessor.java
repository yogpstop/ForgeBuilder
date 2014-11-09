package com.yogpc.fb.sa;

import java.io.File;

public interface IProcessor {
  public File process(File in) throws InterruptedException;

  public void setChild(IProcessor ip);
}
