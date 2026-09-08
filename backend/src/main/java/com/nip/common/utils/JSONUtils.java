package com.nip.common.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.ToNumberPolicy;
import com.google.gson.reflect.TypeToken;
import io.quarkus.runtime.util.StringUtil;

import java.lang.reflect.Type;
import java.time.LocalDateTime;

public class JSONUtils {
  public static final Gson gson = new GsonBuilder()
      .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
      .setObjectToNumberStrategy(ToNumberPolicy.LAZILY_PARSED_NUMBER)
      .create();

  public static String toJson(Object object) {
    if (object == null) {
      return "";
    }
    return gson.toJson(object);
  }

  public static <T> T fromJson(String jsonStr, Class<T> tClass) {
    if (StringUtil.isNullOrEmpty(jsonStr)) {
      return null;
    }
    return gson.fromJson(jsonStr, tClass);
  }

  public static <T> T fromJson(String jsonStr, TypeToken<T> typeOfT) {
    if (StringUtil.isNullOrEmpty(jsonStr)) {
      return null;
    }
    return gson.fromJson(jsonStr, typeOfT);
  }

  public <T> T fromJson(String jsonStr, Type typeOfT) {
    if (StringUtil.isNullOrEmpty(jsonStr)) {
      return null;
    }
    return gson.fromJson(jsonStr, typeOfT);
  }
}
