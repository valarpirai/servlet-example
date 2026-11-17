package com.example.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One row of {@code business_rules}: JavaScript that runs before or after a table write. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BusinessRule {

  private String id;
  private String name;
  private String collection;

  /** Either {@code before} or {@code after}. */
  private String runWhen;

  private boolean onInsert;
  private boolean onUpdate;
  private boolean onDelete;

  /** JavaScript expression. Empty means the rule always runs. */
  private String condition;

  private String script;

  /** Lower runs first. */
  @Builder.Default private int orderNo = 100;

  @Builder.Default private boolean active = true;
}
