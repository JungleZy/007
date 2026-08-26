package com.nip.common.utils;

import java.util.ArrayList;
import java.util.List;

public final class ListUtils {
  private ListUtils() {}

  public static <T> List<T> nullToEmpty(List<T> list) {
    return list == null ? new ArrayList<>() : list;
  }
}
