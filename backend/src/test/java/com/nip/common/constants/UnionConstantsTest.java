package com.nip.common.constants;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * 联合训练 WS 指令码表的唯一性契约。
 *
 * <p>{@link UnionConstants#getByCode(int)} 是 {@code WebSocketUnionService.onMessage}
 * 唯一的入站指令解析入口（WebSocketUnionService.java:142），它按声明顺序线性查找首个命中。
 * 一旦两个常量共用同一 code，后声明的那个就永远解析不出来，且出站帧会被前端订阅者错位分发
 * —— 历史上 {@code ADD_ROOM_FAIL} 与 {@code UPDATE_ROOM_INFO} 同为 121，导致「添加房间失败」
 * 帧被前端当成「更新房间信息」处理（见 docs/reviews/2026-09-18-frontend-consistency-redundancy-review.md C1）。
 *
 * <p>本测试守的是该错位的可观察形态：每个常量都必须能用自己的 code 原样取回。
 */
class UnionConstantsTest {

  @Test
  void everyCodeResolvesBackToItsOwnConstant() {
    for (UnionConstants constant : EnumSet.allOf(UnionConstants.class)) {
      assertSame(constant, UnionConstants.getByCode(constant.getCode()),
        () -> "指令码 " + constant.getCode() + " 被多个常量共用，" + constant.name() + " 永远解析不出来");
    }
  }
}
