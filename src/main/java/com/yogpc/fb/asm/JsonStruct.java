package com.yogpc.fb.asm;

import java.util.ArrayList;

public class JsonStruct {
  public EnclosingMethod enclosingMethod = null;
  public ArrayList<InnerClass> innerClasses = null;

  public static class EnclosingMethod {
    public final String owner;
    public final String name;
    public final String desc;

    EnclosingMethod(final String owner, final String name, final String desc) {
      this.owner = owner;
      this.name = name;
      this.desc = desc;
    }
  }

  public static class InnerClass {
    public final String inner_class;
    public final String outer_class;
    public final String inner_name;
    public final String access;

    InnerClass(final String inner_class, final String outer_class, final String inner_name,
        final String access) {
      this.inner_class = inner_class;
      this.outer_class = outer_class;
      this.inner_name = inner_name;
      this.access = access;
    }

    public int getAccess() {
      return this.access == null ? 0 : Integer.parseInt(this.access, 16);
    }
  }

  public void setEnclosing(final String owner, final String name, final String desc) {
    this.enclosingMethod = new EnclosingMethod(owner, name, desc);
  }

  public void addInner(final String cls, final String outer, final String name, final int access) {
    if (this.innerClasses == null)
      this.innerClasses = new ArrayList<InnerClass>();
    for (final InnerClass i : this.innerClasses)
      if (i.inner_class.equals(cls))
        return;
    this.innerClasses.add(new InnerClass(cls, outer, name, access == 0 ? null : Integer
        .toHexString(access)));
  }
}
