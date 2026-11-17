package com.example.record;

import java.util.Map;

/**
 * One async rule run, saved in a job. It holds the rule, the write and the fields as they were when
 * the write finished. Fields are text, so the job context stays plain JSON.
 */
public class AsyncRuleCall {

  /** Id of the {@code business_rules} row. */
  public String ruleId;

  public String ruleName;
  public String table;

  /** {@code insert}, {@code update} or {@code delete}. */
  public String operation;

  /** The row after the write. For a delete, the row that was deleted. */
  public Map<String, String> current;

  /** The row before the write. Null on insert. */
  public Map<String, String> previous;

  /** How many async rules led here. A rule run from the job starts at its call's depth. */
  public int depth;
}
