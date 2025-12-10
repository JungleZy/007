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
        // todo... 组间隔过小 pakKey.length()/2+1

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
          break;
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
        // todo... 改错+1
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
