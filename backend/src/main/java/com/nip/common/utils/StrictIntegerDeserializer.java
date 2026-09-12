package com.nip.common.utils;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;

import java.io.IOException;

public final class StrictIntegerDeserializer extends JsonDeserializer<Integer> {
  @Override
  public Integer deserialize(JsonParser parser, DeserializationContext context) throws IOException {
    if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT) {
      return context.reportInputMismatch(Integer.class, "必须使用JSON整数，不接受小数或字符串");
    }
    return parser.getIntValue();
  }

  @Override
  public Integer getNullValue(DeserializationContext context) throws JsonMappingException {
    return context.reportInputMismatch(Integer.class, "整数不能为空");
  }
}
