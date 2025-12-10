package com.nip.service.builder;

import com.google.gson.reflect.TypeToken;
import com.nip.common.utils.JSONUtils;
import com.nip.dto.vo.PostTelegramTrainResolverDetailVO;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

import static com.nip.service.constants.MessageComparisonConstants.*;

/**
 * 报文结果构建器
 * 统一处理报文对比结果的构建逻辑，消除重复代码
 * 
 * @author system
 * @date 2024-12-19
 */
@Data
public class MessageResultBuilder {
  
  // ==================== JSON缓存 ====================
  
  /** JSON序列化缓存，提升性能 */
  private static final Map<String, String> jsonCache = new ConcurrentHashMap<>();
  
  // ==================== 结果列表 ====================

  /** 解析后报文格式 */
  private final List<String> resolverMessage = new ArrayList<>();

  /** 拍发日志 */
  private final List<String> resolverPatLogs = new ArrayList<>();

  /** 拍发表示 0点 1划 */
  private final List<String> resolverMoresValue = new ArrayList<>();

  /** 拍发电划耗时 */
  private final List<String> resolverMoresTime = new ArrayList<>();

  /** 多组详情列表 */
  private final List<PostTelegramTrainResolverDetailVO> moreGroups = new ArrayList<>();

  // ==================== 添加方法 ====================

  /**
   * 添加正确的报文结果
   * 
   * @param patKey     拍发报文
   * @param moresValue 拍发表示值
   * @param moresTime  拍发耗时
   * @param patLog     拍发日志
   */
  public void addCorrectMessage(String patKey, String moresValue, String moresTime, String patLog) {
    resolverMessage.add(patKey);
    resolverMoresValue.add(moresValue);
    resolverMoresTime.add(moresTime);
    resolverPatLogs.add(patLog);
  }

  /**
   * 添加空的报文结果（用于少组、少行等情况）
   */
  public void addEmptyMessage() {
    resolverMessage.add("");
    resolverMoresValue.add(EMPTY_JSON_ARRAY);
    resolverMoresTime.add(EMPTY_JSON_ARRAY);
    resolverPatLogs.add(EMPTY_JSON_ARRAY);
  }

  /**
   * 批量添加空的报文结果
   * 
   * @param count 添加的数量
   */
  public void addEmptyMessages(int count) {
    for (int i = 0; i < count; i++) {
      addEmptyMessage();
    }
  }

  /**
   * 添加合并的报文结果（用于字间隔处理）
   * 
   * @param patKey1     第一个报文
   * @param patLog1     第一个日志
   * @param moresValue1 第一个表示值
   * @param moresTime1  第一个耗时
   * @param patKey2     第二个报文
   * @param patLog2     第二个日志
   * @param moresValue2 第二个表示值
   * @param moresTime2  第二个耗时
   */
  public void addMergedMessage(String patKey1, String patLog1, String moresValue1, String moresTime1,
      String patKey2, String patLog2, String moresValue2, String moresTime2) {
    // 合并报文
    resolverMessage.add(patKey1 + patKey2);

    // 合并日志
    List<Object> mergedPatLogs = mergeJsonArrays(patLog1, patLog2);
    resolverPatLogs.add(JSONUtils.toJson(mergedPatLogs));

    // 合并表示值
    List<Object> mergedMoresValue = mergeJsonArrays(moresValue1, moresValue2);
    resolverMoresValue.add(JSONUtils.toJson(mergedMoresValue));

    // 合并耗时
    List<Object> mergedMoresTime = mergeJsonArrays(moresTime1, moresTime2);
    resolverMoresTime.add(JSONUtils.toJson(mergedMoresTime));
  }

  /**
   * 添加多组详情
   * 
   * @param point    位置
   * @param messages 报文列表
   */
  public void addMoreGroupDetail(int point, List<String> messages) {
    PostTelegramTrainResolverDetailVO detailVO = new PostTelegramTrainResolverDetailVO();
    detailVO.setPoint(point);
    detailVO.setMessage(new ArrayList<>(messages));
    moreGroups.add(detailVO);
  }

  // ==================== 辅助方法 ====================

  /**
   * 合并两个JSON数组（优化版本，支持缓存）
   * 
   * @param json1 第一个JSON数组字符串
   * @param json2 第二个JSON数组字符串
   * @return 合并后的列表
   */
  private List<Object> mergeJsonArrays(String json1, String json2) {
    // 生成缓存键
    String cacheKey = json1 + "|" + json2;
    
    // 尝试从缓存获取
    if (jsonCache.containsKey(cacheKey)) {
      String cachedResult = jsonCache.get(cacheKey);
      return JSONUtils.fromJson(cachedResult, new TypeToken<List<Object>>() {});
    }
    
    // 执行合并逻辑
    List<Object> array1 = parseJsonArray(json1);
    List<Object> array2 = parseJsonArray(json2);
    
    if (array1 == null) {
      array1 = new ArrayList<>();
    }
    if (array2 != null) {
      array1.addAll(array2);
    }
    
    // 缓存结果（检查缓存大小）
    if (jsonCache.size() < JSON_CACHE_MAX_SIZE) {
      jsonCache.put(cacheKey, JSONUtils.toJson(array1));
    }
    
    return array1;
  }
  
  /**
   * 解析JSON数组（支持缓存）
   */
  private List<Object> parseJsonArray(String json) {
    if (json == null || json.trim().isEmpty()) {
      return new ArrayList<>();
    }
    
    // 尝试从缓存获取
    if (jsonCache.containsKey(json)) {
      String cachedResult = jsonCache.get(json);
      return JSONUtils.fromJson(cachedResult, new TypeToken<List<Object>>() {});
    }
    
    // 解析JSON
    List<Object> result = JSONUtils.fromJson(json, new TypeToken<List<Object>>() {});
    
    // 缓存结果
    if (result != null && jsonCache.size() < JSON_CACHE_MAX_SIZE) {
      jsonCache.put(json, JSONUtils.toJson(result));
    }
    
    return result;
  }

  /**
   * 获取当前结果的大小
   * 
   * @return 结果列表的大小
   */
  public int size() {
    return resolverMessage.size();
  }

  /**
   * 检查结果是否为空
   * 
   * @return 如果结果为空返回true
   */
  public boolean isEmpty() {
    return resolverMessage.isEmpty();
  }

  /**
   * 清空所有结果
   */
  public void clear() {
    resolverMessage.clear();
    resolverPatLogs.clear();
    resolverMoresValue.clear();
    resolverMoresTime.clear();
    moreGroups.clear();
  }

  /**
   * 验证所有列表的大小是否一致
   * 
   * @return 如果所有列表大小一致返回true
   */
  public boolean isConsistent() {
    int size = resolverMessage.size();
    return resolverPatLogs.size() == size &&
        resolverMoresValue.size() == size &&
        resolverMoresTime.size() == size;
  }
  
  /**
   * 清理JSON缓存（用于内存管理）
   */
  public static void clearJsonCache() {
    jsonCache.clear();
  }
  
  /**
   * 获取JSON缓存大小
   */
  public static int getJsonCacheSize() {
    return jsonCache.size();
  }
}