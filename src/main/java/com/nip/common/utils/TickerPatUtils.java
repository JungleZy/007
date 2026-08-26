package com.nip.common.utils;

import com.google.gson.reflect.TypeToken;
import com.nip.dto.PostTelegramTrainFinishInfoDto;
import com.nip.dto.score.MessageDeduct;
import com.nip.dto.score.PostTelegramTrainRule;
import com.nip.dto.score.SpeedDeduct;
import com.nip.dto.vo.PostTelegramTrainResolverVO;
import com.nip.dto.vo.PostTelegramTrainScoreVO;
import com.nip.dto.vo.PostTelegramTrainStatisticsVO;
import com.nip.dto.vo.param.PostTelegramTrainContentAddParam;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class TickerPatUtils {
  /**
   * 解析用户的原始报文
   *
   * @return
   */
  public static PostTelegramTrainResolverVO resolverMessage(List<String> patKeys,
      PostTelegramTrainScoreVO scoreVO,
      PostTelegramTrainRule rule,
      List<PostTelegramTrainContentAddParam> userContents) {
    PostTelegramTrainResolverVO resolverVO = new PostTelegramTrainResolverVO();
    List<String> ret = new ArrayList<>();
    // 拍发日志
    List<String> resolverPatLogs = new ArrayList<>();
    // 拍发表示 0点 1划
    List<String> resolverMoresValue = new ArrayList<>();
    // 拍发电划耗时
    List<String> resolverMoresTime = new ArrayList<>();

    // 边界检查和空值处理
    if (patKeys == null || patKeys.isEmpty()) {
      resolverVO.setResolverMessage(ret);
      resolverVO.setResolverPatLogs(resolverPatLogs);
      resolverVO.setResolverMoresTime(resolverMoresTime);
      resolverVO.setResolverMoresValue(resolverMoresValue);
      return resolverVO;
    }

    if (userContents == null) {
      userContents = new ArrayList<>();
    }

    patKeys = patKeys.stream().filter(StringUtils::isNotBlank).toList();

    // 确保userContents大小足够
    while (userContents.size() < patKeys.size()) {
      userContents.add(new PostTelegramTrainContentAddParam());
    }

    for (int i = 0; i < patKeys.size(); i++) {
      PostTelegramTrainContentAddParam contentAddParam = userContents.get(i);

      // 安全解析JSON，添加空值检查
      List<List<Map<String, Object>>> patLogs = null;
      List<List<Integer>> moresTime = null;
      List<List<Integer>> moresValue = null;

      try {
        if (contentAddParam.getPatLogs() != null) {
          patLogs = JSONUtils.fromJson(contentAddParam.getPatLogs(), new TypeToken<>() {
          });
        }
        if (contentAddParam.getMoresTime() != null) {
          moresTime = JSONUtils.fromJson(contentAddParam.getMoresTime(), new TypeToken<>() {
          });
        }
        if (contentAddParam.getMoresValue() != null) {
          moresValue = JSONUtils.fromJson(contentAddParam.getMoresValue(), new TypeToken<>() {
          });
        }
      } catch (Exception e) {
        // JSON解析失败时使用空列表
        patLogs = new ArrayList<>();
        moresTime = new ArrayList<>();
        moresValue = new ArrayList<>();
      }
      String patKey = patKeys.get(i);
      if (patKey.length() > 4 && patKey.length() % 4 == 0 && !patKey.contains("?")) {
        // 将粘起来的字符串按4位进行拆分12345678 => 1234 5678
        for (int j = 0; j < patKey.length() / 4; j++) {
          String substring = patKey.substring(j * 4, j * 4 + 4);
          List<List<Map<String, Object>>> logs = new ArrayList<>();
          List<String> times = new ArrayList<>();
          List<String> values = new ArrayList<>();
          for (int z = 0; z < 4; z++) {
            if (patLogs != null && !patLogs.isEmpty()) {
              logs.add(patLogs.removeFirst());
            } else {
              logs.add(new ArrayList<>());
            }
            if (moresTime != null && !moresTime.isEmpty()) {
              times.add(JSONUtils.toJson(moresTime.removeFirst()));
            } else {
              times.add("[]");
            }
            if (moresValue != null && !moresValue.isEmpty()) {
              values.add(JSONUtils.toJson(moresValue.removeFirst()));
            } else {
              values.add("[]");
            }
          }
          ret.add(substring);
          resolverPatLogs.add(JSONUtils.toJson(logs));
          resolverMoresTime.add(JSONUtils.toJson(times));
          resolverMoresValue.add(JSONUtils.toJson(values));
        }

        scoreVO.setGroupScore(patKey.length() / (2 + 1) * rule.getLarge().getL());

      } else if (Objects.equals(patKey, "?")) {
        // 拿到上一组和下一组
        if (i - 1 > 0 && i + 1 < patKeys.size()) {
          String nextPatKey = patKeys.get(i + 1);
          PostTelegramTrainContentAddParam nextAddParam = userContents.get(i + 1);
          String nextPatLogs = nextAddParam.getPatLogs();
          String nextMoresTime = nextAddParam.getMoresTime();
          String nextMoresValue = nextAddParam.getMoresValue();

          ret.set(ret.size() - 1, nextPatKey);
          // 交换patLogs
          resolverPatLogs.set(resolverPatLogs.size() - 1, nextPatLogs);
          // 交换morestime
          resolverMoresTime.set(resolverMoresTime.size() - 1, nextMoresTime);
          // 交换moresValue
          resolverMoresValue.set(resolverMoresValue.size() - 1, nextMoresValue);
          i = i + 1;
        } else {
          ret.add(patKey);
          resolverPatLogs.add(contentAddParam.getPatLogs());
          resolverMoresValue.add(contentAddParam.getMoresTime());
          resolverMoresTime.add(contentAddParam.getMoresValue());
        }
      } else if (patKey.contains("?")) {
        int index = patKey.lastIndexOf("?") + 1;
        // 判断 ？在前还是在后
        if (patKey.startsWith("?") && i - 1 >= 0) {
          // 获取前一组
          String substring = patKey.substring(index);
          List<List<Map<String, Object>>> newPatLogs = new ArrayList<>();
          List<String> newTimes = new ArrayList<>();
          List<String> newValues = new ArrayList<>();
          for (int z = 0; z < substring.length(); z++) {
            List<Map<String, Object>> patLog = null;
            if (patLogs != null && index + z < patLogs.size()) {
              patLog = patLogs.get(index + z);
            }
            newPatLogs.add(patLog != null ? patLog : new ArrayList<>());
            List<Integer> time = null;
            if (moresTime != null && index + z < moresTime.size()) {
              time = moresTime.get(index + z);
            }
            newTimes.add(JSONUtils.toJson(time));
            List<Integer> value = null;
            if (moresValue != null && index + z < moresValue.size()) {
              value = moresValue.get(index + z);
            }
            newValues.add(JSONUtils.toJson(value));
          }
          ret.set(ret.size() - 1, substring);
          resolverPatLogs.set(resolverPatLogs.size() - 1, JSONUtils.toJson(newPatLogs));
          resolverMoresTime.add(resolverMoresTime.size() - 1, JSONUtils.toJson(newTimes));
          resolverMoresValue.add(resolverMoresValue.size() - 1, JSONUtils.toJson(newValues));

        } else if (patKey.endsWith("?") && patKeys.size() > i + 1) {
          scoreVO.setAlterErrorScore(scoreVO.getAlterErrorScore() + rule.getAlterError().getL());
          int first = patKey.indexOf("?");
          int last = patKey.lastIndexOf("?");
          String between = (first >= 0 && last > first + 1) ? patKey.substring(first + 1, last) : "";
          if (!between.isEmpty()) {
            List<List<Map<String, Object>>> newPatLogs = new ArrayList<>();
            List<String> newTimes = new ArrayList<>();
            List<String> newValues = new ArrayList<>();
            for (int z = 0; z < between.length(); z++) {
              List<Map<String, Object>> patLog = null;
              if (patLogs != null && first + 1 + z < patLogs.size()) {
                patLog = patLogs.get(first + 1 + z);
              }
              newPatLogs.add(patLog != null ? patLog : new ArrayList<>());
              List<Integer> time = null;
              if (moresTime != null && first + 1 + z < moresTime.size()) {
                time = moresTime.get(first + 1 + z);
              }
              newTimes.add(JSONUtils.toJson(time));
              List<Integer> value = null;
              if (moresValue != null && first + 1 + z < moresValue.size()) {
                value = moresValue.get(first + 1 + z);
              }
              newValues.add(JSONUtils.toJson(value));
            }
            ret.add(between);
            resolverPatLogs.add(JSONUtils.toJson(newPatLogs));
            resolverMoresTime.add(JSONUtils.toJson(newTimes));
            resolverMoresValue.add(JSONUtils.toJson(newValues));
          } else {
            ret.add(patKey);
            resolverPatLogs.add(contentAddParam.getPatLogs());
            resolverMoresValue.add(contentAddParam.getMoresTime());
            resolverMoresTime.add(contentAddParam.getMoresValue());
          }
        } else {
          String substring = patKey.substring(index);
          List<List<Map<String, Object>>> newPatLogs = new ArrayList<>();
          List<String> newTimes = new ArrayList<>();
          List<String> newValues = new ArrayList<>();
          if (!substring.isEmpty()) {
            ret.add(substring);
            for (int z = 0; z < substring.length(); z++) {
              List<Map<String, Object>> patLog = null;
              if (patLogs != null && index + z < patLogs.size()) {
                patLog = patLogs.get(index + z);
              }
              newPatLogs.add(patLog != null ? patLog : new ArrayList<>());
              List<Integer> time = null;
              if (moresTime != null && index + z < moresTime.size()) {
                time = moresTime.get(index + z);
              }
              newTimes.add(JSONUtils.toJson(time));
              List<Integer> value = null;
              if (moresValue != null && index + z < moresValue.size()) {
                value = moresValue.get(index + z);
              }
              newValues.add(JSONUtils.toJson(value));
            }
            resolverPatLogs.add(JSONUtils.toJson(newPatLogs));
            resolverMoresTime.add(JSONUtils.toJson(newTimes));
            resolverMoresValue.add(JSONUtils.toJson(newValues));

          } else {
            ret.add(patKey);
            resolverPatLogs.add(contentAddParam.getPatLogs());
            resolverMoresValue.add(contentAddParam.getMoresTime());
            resolverMoresTime.add(contentAddParam.getMoresValue());
          }
        }
        scoreVO.setAlterErrorScore(scoreVO.getAlterErrorScore() + rule.getAlterError().getL());
      } else if (!patKey.isEmpty()) {
        ret.add(patKey);
        resolverPatLogs.add(contentAddParam.getPatLogs() != null ? contentAddParam.getPatLogs() : "[]");
        resolverMoresValue.add(contentAddParam.getMoresTime() != null ? contentAddParam.getMoresTime() : "[]");
        resolverMoresTime.add(contentAddParam.getMoresValue() != null ? contentAddParam.getMoresValue() : "[]");
      } else {
        // 处理空字符串情况
        ret.add("");
        resolverPatLogs.add("[]");
        resolverMoresValue.add("[]");
        resolverMoresTime.add("[]");
      }
    }
    resolverVO.setResolverMessage(ret);
    resolverVO.setResolverPatLogs(resolverPatLogs);
    resolverVO.setResolverMoresTime(resolverMoresTime);
    resolverVO.setResolverMoresValue(resolverMoresValue);
    return resolverVO;
  }

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
      // 协议容忍：纯文本 patKeys 逐字符拆分；副作用：损坏的 JSON 数组文本也会被拆成含 [ " , 的垃圾按键——无协议标记无法区分，接受此残留
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
      } catch (Exception e) {
        throw new IllegalStateException("patLogs JSON 损坏，拒绝写入（index=" + i + ")", e);
      }
      try {
        times = JSONUtils.fromJson(item.getMoresTime(), new TypeToken<>() {
        });
      } catch (Exception e) {
        throw new IllegalStateException("moresTime JSON 损坏，拒绝写入（index=" + i + ")", e);
      }
      try {
        values = JSONUtils.fromJson(item.getMoresValue(), new TypeToken<>() {
        });
      } catch (Exception e) {
        throw new IllegalStateException("moresValue JSON 损坏，拒绝写入（index=" + i + ")", e);
      }
      pkLists.add(pk != null ? pk : new ArrayList<>());
      logsLists.add(logs != null ? logs : new ArrayList<>());
      timesLists.add(times != null ? times : new ArrayList<>());
      valuesLists.add(values != null ? values : new ArrayList<>());
    }
    for (int i = 0; i < n; i++) {
      List<String> curPk = pkLists.get(i);
      int curLen = curPk != null ? curPk.size() : 0;
      if (curLen >= 4) {
        continue;
      }
      int curNumCount = 0;
      boolean curHasQuestion = false;
      boolean curAllSharp = true;
      if (curPk != null) {
        for (String s : curPk) {
          if (s != null && !s.isEmpty()) {
            char ch = s.charAt(0);
            if ((ch >= '0' && ch <= '9') || (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z')) {
              curNumCount++;
              curAllSharp = false;
            }
            if ("?".equals(s)) {
              curHasQuestion = true;
            }
            if (!"#".equals(s)) {
              curAllSharp = false;
            }
          }
        }
      }
      int j = i + 1;
      while (j < n) {
        curLen = curPk != null ? curPk.size() : 0;
        List<String> nextPk = pkLists.get(j);
        List<List<Map<String, Object>>> nextLogs = logsLists.get(j);
        List<List<Integer>> nextTimes = timesLists.get(j);
        List<List<Integer>> nextValues = valuesLists.get(j);
        boolean nextEmpty = nextLogs == null || nextLogs.isEmpty();
        int nextLen = nextPk != null ? nextPk.size() : 0;
        boolean nextHasQuestion = false;
        if (nextPk != null) {
          for (String s : nextPk) {
            if ("?".equals(s)) {
              nextHasQuestion = true;
              break;
            }
          }
        }
        if (nextEmpty || nextLen == 0 || nextHasQuestion) {
          break;
        }
        int nextNumCount = 0;
        if (nextPk != null) {
          for (String s : nextPk) {
            if (s != null && !s.isEmpty()) {
              char ch = s.charAt(0);
              if ((ch >= '0' && ch <= '9') || (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z')) {
                nextNumCount++;
              }
            }
          }
        }
        if (curLen >= 4) {
          break;
        }
        if (curHasQuestion && nextNumCount > 0) {
          break;
        }
        if (curAllSharp && curLen >= 2 && nextNumCount > 0) {
          break;
        }
        if (curNumCount + nextNumCount <= 4) {
          List<List<Map<String, Object>>> curLogs = logsLists.get(i);
          List<List<Integer>> curTimes = timesLists.get(i);
          List<List<Integer>> curValues = valuesLists.get(i);
          if (curLogs == null)
            curLogs = new ArrayList<>();
          if (curTimes == null)
            curTimes = new ArrayList<>();
          if (curValues == null)
            curValues = new ArrayList<>();
          if (nextPk != null) {
            curPk.addAll(nextPk);
          }
          if (nextLogs != null) {
            curLogs.addAll(nextLogs);
          }
          if (nextTimes != null) {
            curTimes.addAll(nextTimes);
          }
          if (nextValues != null) {
            curValues.addAll(nextValues);
          }
          pkLists.set(i, curPk);
          logsLists.set(i, curLogs);
          timesLists.set(i, curTimes);
          valuesLists.set(i, curValues);
          for (int s = j; s < n - 1; s++) {
            pkLists.set(s, pkLists.get(s + 1));
            logsLists.set(s, logsLists.get(s + 1));
            timesLists.set(s, timesLists.get(s + 1));
            valuesLists.set(s, valuesLists.get(s + 1));
          }
          pkLists.set(n - 1, new ArrayList<>());
          logsLists.set(n - 1, new ArrayList<>());
          timesLists.set(n - 1, new ArrayList<>());
          valuesLists.set(n - 1, new ArrayList<>());
          curLen = curPk.size();
          curNumCount += nextNumCount;
          curHasQuestion = false;
          curAllSharp = true;
          for (String s2 : curPk) {
            if ("?".equals(s2)) {
              curHasQuestion = true;
            }
            if (s2 != null && !s2.isEmpty()) {
              char ch2 = s2.charAt(0);
              if ((ch2 >= '0' && ch2 <= '9') || (ch2 >= 'a' && ch2 <= 'z') || (ch2 >= 'A' && ch2 <= 'Z')) {
                curAllSharp = false;
              }
            }
            if (!"#".equals(s2)) {
              curAllSharp = false;
            }
          }
        } else {
          break;
        }
      }
    }
    for (int i = 0; i < n; i++) {
      int size = pkLists.get(i) != null ? pkLists.get(i).size() : 0;
      List<List<Map<String, Object>>> ls = logsLists.get(i) != null ? logsLists.get(i) : new ArrayList<>();
      List<List<Integer>> ts = timesLists.get(i) != null ? timesLists.get(i) : new ArrayList<>();
      List<List<Integer>> vs = valuesLists.get(i) != null ? valuesLists.get(i) : new ArrayList<>();
      while (ls.size() < size) {
        ls.add(new ArrayList<>());
      }
      while (ts.size() < size) {
        ts.add(new ArrayList<>());
      }
      while (vs.size() < size) {
        vs.add(new ArrayList<>());
      }
      while (ls.size() > size) {
        ls.remove(ls.size() - 1);
      }
      while (ts.size() > size) {
        ts.remove(ts.size() - 1);
      }
      while (vs.size() > size) {
        vs.remove(vs.size() - 1);
      }
      logsLists.set(i, ls);
      timesLists.set(i, ts);
      valuesLists.set(i, vs);
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

  /**
   * 检查点划线及间隔的合规性并进行评分统计
   *
   * @param patKey       拍发电码字符串，用于遍历每个字符对应的拍发数据
   * @param i            当前处理的组索引，用于定位标准值位置
   * @param patLogs      JSON格式的拍发日志数据，记录点划间隔原始数据
   * @param standards    标准参数列表，包含各组标准点划间隔值
   * @param rule         扣分规则对象，包含不同项目的扣分阈值和基准值
   * @param isDuct       是否为导通状态标志，影响是否实际扣分
   * @param statisticsVO 统计结果容器，记录各类情况的数量统计
   * @param scoreVO      评分结果容器，记录各项目的扣分和时间总和
   */
  public static void checkDotLineGap(String patKey,
      int i,
      String patLogs,
      List<PostTelegramTrainFinishInfoDto> standards,
      PostTelegramTrainRule rule,
      boolean isDuct,
      PostTelegramTrainStatisticsVO statisticsVO,
      PostTelegramTrainScoreVO scoreVO) {// 比对每组的每一个字

    // 边界检查和空值处理
    if (patKey == null || patKey.isEmpty() || standards == null || standards.isEmpty()) {
      return;
    }

    List<List<PostTelegramTrainFinishInfoDto.PatLogs>> p = null;
    try {
      if (patLogs != null && !patLogs.trim().isEmpty()) {
        p = JSONUtils.fromJson(patLogs, new TypeToken<>() {
        });
      }
    } catch (Exception e) {
      // JSON解析失败时使用空列表
      p = new ArrayList<>();
    }

    for (int z = 0; z < patKey.length(); z++) {
      // 拍发值
      List<PostTelegramTrainFinishInfoDto.PatLogs> logs = new ArrayList<>();
      if (p != null && z < p.size() && p.get(z) != null) {
        logs = p.get(z);
      }
      // 每页第一个间隔移除掉
      if (i == 0 && z == 0) {
        if (logs != null && !logs.isEmpty()) {
          logs.removeFirst();
        }
      }

      // 相等的情况下，拿到本行的标准值，与偏移量 进行 判断点虚 点粗 划虚 划粗
      int standarIndex = i / 10;
      // 标准值 - 添加边界检查
      if (standarIndex >= standards.size()) {
        standarIndex = standards.size() - 1;
      }
      PostTelegramTrainFinishInfoDto standerd = standards.get(standarIndex);

      // 拿到点、划、码间隔 、字间隔、 组间隔 本行的标准值，与偏移量 进行 判断点虚 点粗 划虚 划粗 是否扣分
      // 计算不扣分区间 粗：标准值+偏移量 细：标准值-偏移量
      Integer dot = standerd.getDot();
      Integer line = standerd.getLine();
      Integer codeGap = standerd.getCodeGap();
      Integer wordGap = standerd.getWordGap();
      Integer groupGap = standerd.getGroupGap();

      int dotMin = dot - rule.getDot().getBase();
      int dotMax = dot + rule.getDot().getBase();

      int linMin = line - rule.getDash().getBase();
      int linMax = line + rule.getDash().getBase();

      int codeGapMin = codeGap - rule.getLittle().getBase();
      int codeGapMax = codeGap + rule.getLittle().getBase();

      int wordGapMin = wordGap - rule.getMiddle().getBase();
      int wordGapMax = wordGap + rule.getMiddle().getBase();

      int groupGapMin = groupGap - rule.getLarge().getBase();
      int groupGapMax = groupGap + rule.getLarge().getBase();
      // 循环拍发内容，判断是否有点粗，点虚。。。
      if (logs == null) {
        continue;
      }

      for (int k = 0; k < logs.size(); k++) {
        PostTelegramTrainFinishInfoDto.PatLogs log = logs.get(k);
        if (log == null) {
          continue;
        }
        int pkey = log.getKey();
        int value = log.getValue();
        // 点
        if (pkey == 0) {
          if (value < dotMin) {
            // 是否扣分
            scoreVO.setDotScore(scoreVO.getDotScore() + (isDuct ? 0 : rule.getDot().getL()));
            // 点虚数量++
            statisticsVO.setDotMinNumber(statisticsVO.getDotMinNumber() + 1);
          } else if (value > dotMax) {
            // 是否扣分
            scoreVO.setDotScore(scoreVO.getDotScore() + (isDuct ? 0 : rule.getDot().getR()));
            // 点粗数量++
            statisticsVO.setDotMaxNumber(statisticsVO.getDotMaxNumber() + 1);
          } else {
            // 完美
            statisticsVO.setDotPerfectNumber(statisticsVO.getDotPerfectNumber() + 1);
          }
          scoreVO.setDotTotalTime(scoreVO.getDotTotalTime() + value);
        }
        // 划
        else if (pkey == 1) {
          if (value < linMin) {
            scoreVO.setLineScore(scoreVO.getLineScore() + (isDuct ? 0 : rule.getDash().getL()));
            statisticsVO.setLineMinNumber(statisticsVO.getLineMinNumber() + 1);
          } else if (value > linMax) {
            scoreVO.setLineScore(scoreVO.getLineScore() + (isDuct ? 0 : rule.getDash().getR()));
            statisticsVO.setLineMaxNumber(statisticsVO.getLineMaxNumber() + 1);
          } else {
            // 完美
            statisticsVO.setLinePerfectNumber(statisticsVO.getLinePerfectNumber() + 1);
          }
          scoreVO.setLineTotalTime(scoreVO.getLineTotalTime() + value);
        }
        // 间隔
        else if (pkey == 2) {
          // 组间隔 z==0,k==0
          if (z == 0 && k == 0) {
            if (value < groupGapMin) {
              // 细
              scoreVO.setGroupScore(scoreVO.getGroupScore() + (isDuct ? 0 : rule.getLarge().getL()));
              statisticsVO.setGroupMinNumber(statisticsVO.getGroupMinNumber() + 1);
            } else if (value > groupGapMax) {
              // 粗
              scoreVO.setGroupScore(scoreVO.getGroupScore() + (isDuct ? 0 : rule.getLarge().getR()));
              statisticsVO.setGroupMaxNumber(statisticsVO.getGroupMaxNumber() + 1);
            } else {
              statisticsVO.setGroupPerfectNumber(statisticsVO.getGroupPerfectNumber() + 1);
            }
            scoreVO.setGroupTotalTime(scoreVO.getGroupTotalTime() + value);
          }
          // 词间隔
          else if (z != 0 && k == 0) {
            if (value < wordGapMin) {
              // 细
              scoreVO.setWordScore(scoreVO.getWordScore() + (isDuct ? 0 : rule.getMiddle().getL()));
              statisticsVO.setWordMinNumber(statisticsVO.getWordMinNumber() + 1);
            } else if (value > wordGapMax) {
              // 粗
              scoreVO.setWordScore(scoreVO.getWordScore() + (isDuct ? 0 : rule.getMiddle().getR()));
              statisticsVO.setWordMaxNumber(statisticsVO.getWordMaxNumber() + 1);
            } else {
              statisticsVO.setWordPerfectNumber(statisticsVO.getCodePerfectNumber() + 1);
            }
            scoreVO.setWordTotalTime(scoreVO.getWordTotalTime() + value);
          }
          // 码间隔
          else {
            if (value < codeGapMin) {
              // 细
              scoreVO.setCodeScore(scoreVO.getCodeScore() + (isDuct ? 0 : rule.getLittle().getL()));
              statisticsVO.setCodeMinNumber(statisticsVO.getCodeMinNumber() + 1);
            } else if (value > codeGapMax) {
              // 粗
              scoreVO.setCodeScore(scoreVO.getCodeScore() + (isDuct ? 0 : rule.getLittle().getR()));
              statisticsVO.setCodeMaxNumber(statisticsVO.getCodeMaxNumber() + 1);
            } else {
              statisticsVO.setCodePerfectNumber(statisticsVO.getCodePerfectNumber() + 1);
            }
            scoreVO.setCodeTotalTime(scoreVO.getCodeTotalTime() + value);
          }
        }
      }
    }
  }

  /**
   * 解析content
   *
   * @param content
   * @return
   */
  public static PostTelegramTrainRule parseContent(String content) {
    PostTelegramTrainRule ret = new PostTelegramTrainRule();
    Map<String, Object> contentMap = JSONUtils.fromJson(content, new TypeToken<>() {
    });
    SpeedDeduct wpm = JSONUtils.fromJson(contentMap.get("wpm").toString(), SpeedDeduct.class);
    // 得到偏移量
    Integer skew = Integer.valueOf(contentMap.get("skew").toString());
    // 得到code
    Map<String, Object> code = JSONUtils.fromJson(contentMap.get("code").toString(), new TypeToken<>() {
    });
    // 得到点 和划
    SpeedDeduct dot = JSONUtils.fromJson(code.get("dot").toString(), SpeedDeduct.class);
    SpeedDeduct dash = JSONUtils.fromJson(code.get("dash").toString(), SpeedDeduct.class);
    // 从 code中得到间隔gap
    Map<String, Object> gap = JSONUtils.fromJson(contentMap.get("gap").toString(), new TypeToken<>() {
    });
    // 从gap中得到little
    SpeedDeduct little = JSONUtils.fromJson(gap.get("little").toString(), SpeedDeduct.class);
    SpeedDeduct middle = JSONUtils.fromJson(gap.get("middle").toString(), SpeedDeduct.class);
    SpeedDeduct large = JSONUtils.fromJson(gap.get("large").toString(), SpeedDeduct.class);

    // 得到报文错误扣分
    Map<String, Object> otherMap = JSONUtils.fromJson(contentMap.get("other").toString(), new TypeToken<>() {
    });

    return ret.setSkew(skew)
        .setWpm(wpm)
        .setDot(dot)
        .setDash(dash)
        .setLittle(little)
        .setMiddle(middle)
        .setLarge(large)
        .setErrorCode(handleMessageDeduct(otherMap, "errorCode"))// 错字
        .setQuantoCode(handleMessageDeduct(otherMap, "quantoCode"))// 多少字
        .setQuantoGroup(handleMessageDeduct(otherMap, "quantoGroup"))// 多少组
        .setAlterError(handleMessageDeduct(otherMap, "alterError"))// 改错
        .setQuantoRow(handleMessageDeduct(otherMap, "quantoRow"))
        .setBunchGroup(handleMessageDeduct(otherMap, "bunchGroup"));
  }

  private static MessageDeduct handleMessageDeduct(Map<String, Object> otherMap, String deduct) {
    return JSONUtils.fromJson(otherMap.get(deduct).toString(), MessageDeduct.class);
  }
}
