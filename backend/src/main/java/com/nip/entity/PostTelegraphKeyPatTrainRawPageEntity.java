package com.nip.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity(name = "t_post_telegraph_key_pat_train_raw_page")
@Table(name = "t_post_telegraph_key_pat_train_raw_page", uniqueConstraints =
    @UniqueConstraint(name = "uk_personal_key_raw_page", columnNames = {"train_id", "page_number"}))
@Cacheable(false)
public class PostTelegraphKeyPatTrainRawPageEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;
  @Column(nullable = false)
  private String trainId;
  @Column(nullable = false)
  private Integer pageNumber;
  @Column(nullable = false)
  private Integer attempt;
  @Column(nullable = false, columnDefinition = "LONGTEXT")
  private String value;
  @Column(nullable = false, columnDefinition = "LONGTEXT")
  private String captureIntervals;
  @Column(nullable = false)
  private LocalDateTime receivedAt;
}
