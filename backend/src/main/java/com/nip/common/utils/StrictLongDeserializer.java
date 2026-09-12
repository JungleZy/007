package com.nip.common.utils;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;

import java.io.IOException;

public final class StrictLongDeserializer extends JsonDeserializer<Long> {
  @Override
  public Long deserialize(JsonParser parser, DeserializationContext context) throws IOException {
    if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT) {
      return context.reportInputMismatch(Long.class, "采集时刻必须为JSON整数毫秒");
    }
    return parser.getLongValue();
  }

  @Override
  public Long getNullValue(DeserializationContext context) throws JsonMappingException {
    return context.reportInputMismatch(Long.class, "采集时刻不能为空");
  }
}
