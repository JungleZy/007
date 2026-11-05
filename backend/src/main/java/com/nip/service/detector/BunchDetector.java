package com.nip.service.detector;

import com.nip.service.context.ComparisonContext;
import com.nip.service.enums.DetectionResult;
import java.util.List;
import java.util.Objects;

/** 成对串组：两组互换只扣一次，配对状态归当前页所有，不跨请求共享。 */
public class BunchDetector {
  public DetectionResult detectPair(ComparisonContext context, int capturedIndex, String patKey) {
    int sourceIndex = context.getSourceIndex();
    if (!context.isValidSourceIndex(sourceIndex)) return DetectionResult.FAILED;
    List<String> sources = context.getSources();
    List<String> captured = context.getResolverVO().getResolverMessage();
    Integer partner = context.getBunchPartners().get(capturedIndex);
    if (partner != null && Objects.equals(patKey, sources.get(partner))) return DetectionResult.SUCCESS;
    String expected = sources.get(sourceIndex);
    for (int offset = 1; sourceIndex + offset < sources.size() && capturedIndex + offset < captured.size(); offset++) {
      int next = capturedIndex + offset;
      if (context.getBunchPartners().containsKey(next)) continue;
      if (!Objects.equals(expected, patKey) && Objects.equals(sources.get(sourceIndex + offset), patKey)
          && Objects.equals(captured.get(next), expected)) {
        context.getBunchPartners().put(capturedIndex, sourceIndex + offset);
        context.getBunchPartners().put(next, sourceIndex);
        context.getScoreVO().setBunchGroup(context.getScoreVO().getBunchGroup() + 1);
        return DetectionResult.SUCCESS;
      }
    }
    return DetectionResult.FAILED;
  }
}
