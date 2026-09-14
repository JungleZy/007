package com.nip.entity;

import jakarta.persistence.Cacheable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Historical aggregate and persisted server-side clock for pre-job radio study. */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity(name = "t_radiotelephone_train")
@Table(name = "t_radiotelephone_train", uniqueConstraints =
    @UniqueConstraint(name = "uk_radiotelephone_train_user_type", columnNames = {"user_id", "type"}))
@Cacheable(false)
public class RadiotelephoneEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;

  /** 用户 id. */
  private String userId;

  /** 0 通报用语，1 军语密语. */
  private Integer type;

  /** Historical cumulative active seconds, retained as the existing string column. */
  private String totalTime;

  /** Historical number of completed sessions. */
  private Integer totalCount;

  /** Active session id; null when there is no session in progress. */
  @Column(name = "active_session_id", length = 64)
  private String activeSessionId;

  /** Start of the currently running active interval; null while paused. */
  @Column(name = "session_started_at", columnDefinition = "datetime(6)")
  private Instant sessionStartedAt;

  /** Milliseconds accumulated before the current active interval. */
  @Column(name = "active_millis")
  private Long activeMillis;

  /** Last finalized session id, used as a replay marker. */
  @Column(name = "finalized_session_id", length = 64)
  private String finalizedSessionId;
}
