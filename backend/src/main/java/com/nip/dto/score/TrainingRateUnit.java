package com.nip.dto.score;

import com.nip.common.utils.ScoreMath;

import java.math.BigDecimal;

public enum TrainingRateUnit {
  CHARACTERS_PER_MINUTE(1),
  FOUR_CHARACTER_GROUPS_PER_MINUTE(4);

  private final int charactersPerUnit;

  TrainingRateUnit(int charactersPerUnit) {
    this.charactersPerUnit = charactersPerUnit;
  }

  public BigDecimal rate(long characters, long activeMillis) {
    return ScoreMath.rate(characters, Math.multiplyExact(activeMillis, charactersPerUnit));
  }
}
