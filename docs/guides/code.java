public class code {
  /**
   * 处理消息体，将拍发电码字符串进行整理
   *
   * @param messageBody 包含拍发电码字符串的消息体列表
   * @return 处理后的消息体列表，每个元素包含转换后的拍发电码字符串
   */
  public static List<PostTelegramTrainContentAddParam> handleMessageBody(
      List<PostTelegramTrainContentAddParam> messageBody) {
    if (messageBody == null || messageBody.isEmpty()) {
      return new ArrayList<>();
    }
    boolean splitMode = false;
    for (PostTelegramTrainContentAddParam item : messageBody) {
      String patKeysJson = item.getPatKeys();
      List<String> list = null;
      try {
        list = JSONUtils.fromJson(patKeysJson, new TypeToken<List<String>>() {
        });
      } catch (Exception ignore) {
      }
      if (list == null && patKeysJson != null && !patKeysJson.contains("?") &&
          patKeysJson.length() > 4 && patKeysJson.length() % 4 == 0) {
        splitMode = true;
        break;
      }
    }
    if (splitMode) {
      List<PostTelegramTrainContentAddParam> ret = new ArrayList<>();
      for (PostTelegramTrainContentAddParam item : messageBody) {
        String patKeysJson = item.getPatKeys();
        List<String> patKeyList = null;
        try {
          patKeyList = JSONUtils.fromJson(patKeysJson, new TypeToken<List<String>>() {
          });
        } catch (Exception ignore) {
        }
        String patKeyStr = patKeyList != null ? String.join("", patKeyList) : (patKeysJson == null ? "" : patKeysJson);
        List<List<Map<String, Object>>> patLogs = null;
        List<List<Integer>> moresTime = null;
        List<List<Integer>> moresValue = null;
        try {
          patLogs = JSONUtils.fromJson(item.getPatLogs(), new TypeToken<>() {
          });
        } catch (Exception ignore) {
        }
        try {
          moresTime = JSONUtils.fromJson(item.getMoresTime(), new TypeToken<>() {
          });
        } catch (Exception ignore) {
        }
        try {
          moresValue = JSONUtils.fromJson(item.getMoresValue(), new TypeToken<>() {
          });
        } catch (Exception ignore) {
        }
        if (patKeyStr.length() > 4 && patKeyStr.length() % 4 == 0 && !patKeyStr.contains("?")) {
          int logsIdx = 0;
          int timesIdx = 0;
          int valuesIdx = 0;
          int groups = patKeyStr.length() / 4;
          for (int j = 0; j < groups; j++) {
            String sub = patKeyStr.substring(j * 4, j * 4 + 4);
            List<List<Map<String, Object>>> subLogs = new ArrayList<>(4);
            List<List<Integer>> subTimes = new ArrayList<>(4);
            List<List<Integer>> subValues = new ArrayList<>(4);
            for (int z = 0; z < 4; z++) {
              List<Map<String, Object>> l = (patLogs != null && logsIdx < patLogs.size()) ? patLogs.get(logsIdx++)
                  : new ArrayList<>();
              List<Integer> t = (moresTime != null && timesIdx < moresTime.size()) ? moresTime.get(timesIdx++)
                  : new ArrayList<>();
              List<Integer> v = (moresValue != null && valuesIdx < moresValue.size()) ? moresValue.get(valuesIdx++)
                  : new ArrayList<>();
              subLogs.add(l);
              subTimes.add(t);
              subValues.add(v);
            }
            List<String> chars = new ArrayList<>(sub.length());
            for (int k = 0; k < sub.length(); k++) {
              chars.add(String.valueOf(sub.charAt(k)));
            }
            PostTelegramTrainContentAddParam newItem = new PostTelegramTrainContentAddParam();
            newItem.setId(item.getId());
            newItem.setPatKeys(JSONUtils.toJson(chars));
            newItem.setMoresKey(item.getMoresKey());
            newItem.setPatLogs(JSONUtils.toJson(subLogs));
            newItem.setMoresTime(JSONUtils.toJson(subTimes));
            newItem.setMoresValue(JSONUtils.toJson(subValues));
            ret.add(newItem);
          }
        } else {
          List<String> chars = new ArrayList<>(patKeyStr.length());
          for (int k = 0; k < patKeyStr.length(); k++) {
            chars.add(String.valueOf(patKeyStr.charAt(k)));
          }
          PostTelegramTrainContentAddParam newItem = new PostTelegramTrainContentAddParam();
          newItem.setId(item.getId());
          newItem.setPatKeys(JSONUtils.toJson(chars));
          newItem.setMoresKey(item.getMoresKey());
          newItem.setPatLogs(item.getPatLogs() == null ? "[]" : item.getPatLogs());
          newItem.setMoresTime(item.getMoresTime() == null ? "[]" : item.getMoresTime());
          newItem.setMoresValue(item.getMoresValue() == null ? "[]" : item.getMoresValue());
          ret.add(newItem);
        }
      }
      return ret;
    } else {
      int n = messageBody.size();
      List<List<String>> pkLists = new ArrayList<>(n);
      List<List<List<Map<String, Object>>>> logsLists = new ArrayList<>(n);
      List<List<List<Integer>>> timesLists = new ArrayList<>(n);
      List<List<List<Integer>>> valuesLists = new ArrayList<>(n);
      for (int i = 0; i < n; i++) {
        PostTelegramTrainContentAddParam item = messageBody.get(i);
        List<String> pk = null;
        try {
          pk = JSONUtils.fromJson(item.getPatKeys(), new TypeToken<List<String>>() {
          });
        } catch (Exception ignore) {
        }
        if (pk == null) {
          pk = new ArrayList<>();
          String raw = item.getPatKeys();
          if (raw != null) {
            for (int c = 0; c < raw.length(); c++) {
              pk.add(String.valueOf(raw.charAt(c)));
            }
          }
        }
        List<List<Map<String, Object>>> logs = null;
        List<List<Integer>> times = null;
        List<List<Integer>> values = null;
        try {
          logs = JSONUtils.fromJson(item.getPatLogs(), new TypeToken<>() {
          });
        } catch (Exception ignore) {
        }
        try {
          times = JSONUtils.fromJson(item.getMoresTime(), new TypeToken<>() {
          });
        } catch (Exception ignore) {
        }
        try {
          values = JSONUtils.fromJson(item.getMoresValue(), new TypeToken<>() {
          });
        } catch (Exception ignore) {
        }
        pkLists.add(pk != null ? pk : new ArrayList<>());
        logsLists.add(logs != null ? logs : new ArrayList<>());
        timesLists.add(times != null ? times : new ArrayList<>());
        valuesLists.add(values != null ? values : new ArrayList<>());
      }
      for (int i = 0; i < n; i++) {
        List<String> curPk = pkLists.get(i);
        List<List<Map<String, Object>>> curLogs = logsLists.get(i);
        List<List<Integer>> curTimes = timesLists.get(i);
        List<List<Integer>> curValues = valuesLists.get(i);
        int curLen = curPk.size();
        if (curLen < 4) {
          int needed = 4 - curLen;
          int j = i + 1;
          while (needed > 0 && j < n) {
            List<String> nextPk = pkLists.get(j);
            if (nextPk.isEmpty()) {
              j++;
              continue;
            }
            int consumable = Math.min(needed, nextPk.size());
            List<List<Map<String, Object>>> nextLogs = logsLists.get(j);
            List<List<Integer>> nextTimes = timesLists.get(j);
            List<List<Integer>> nextValues = valuesLists.get(j);
            for (int k = 0; k < consumable; k++) {
              curPk.add(nextPk.get(k));
              curLogs.add(nextLogs != null && nextLogs.size() > k ? nextLogs.get(k) : new ArrayList<>());
              curTimes.add(nextTimes != null && nextTimes.size() > k ? nextTimes.get(k) : new ArrayList<>());
              curValues.add(nextValues != null && nextValues.size() > k ? nextValues.get(k) : new ArrayList<>());
            }
            pkLists.set(j, new ArrayList<>());
            logsLists.set(j, new ArrayList<>());
            timesLists.set(j, new ArrayList<>());
            valuesLists.set(j, new ArrayList<>());
            needed = 4 - curPk.size();
            j++;
          }
        }
      }
      List<PostTelegramTrainContentAddParam> ret = new ArrayList<>(n);
      for (int i = 0; i < n; i++) {
        PostTelegramTrainContentAddParam src = messageBody.get(i);
        PostTelegramTrainContentAddParam dst = new PostTelegramTrainContentAddParam();
        dst.setId(src.getId());
        dst.setMoresKey(src.getMoresKey());
        dst.setPatKeys(JSONUtils.toJson(pkLists.get(i)));
        dst.setPatLogs(JSONUtils.toJson(logsLists.get(i)));
        dst.setMoresTime(JSONUtils.toJson(timesLists.get(i)));
        dst.setMoresValue(JSONUtils.toJson(valuesLists.get(i)));
        ret.add(dst);
      }
      return ret;
    }
  }
}
