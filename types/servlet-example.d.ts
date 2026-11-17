/**
 * Types for the global APIs that server-side scripts can use.
 *
 * Scripts run in Rhino 1.7.15, inside the sandbox. This file describes what the server
 * adds: TableRecord, BusinessRules, Jobs, console, require and module.
 *
 * Keep this file in step with the server. Adding or changing a global script API means
 * updating this file in the same change. See .claude/rules/script-globals-types.md.
 */

// ---- Java values that scripts receive ----------------------------------------------------

/**
 * A java.util.List. Index it, read length, or call the Java methods.
 * Convert to a real array with Array.prototype.slice.call(list) when you need one.
 */
interface JavaList<T> {
  readonly length: number;
  readonly [index: number]: T;
  size(): number;
  get(index: number): T;
  isEmpty(): boolean;
}

/** A java.time.Instant. */
interface JavaInstant {
  toString(): string;
  toEpochMilli(): number;
}

/**
 * Text read from the server arrives as a Java string object, not a JavaScript string.
 * `typeof` says "object" and `===` against a JavaScript string is false. Wrap it with
 * `String(value)` before you compare. The types below say `string` for ease of use.
 */
type ScriptText = string;

// ---- console, require, module ---------------------------------------------------------------

/** Collects output. A script endpoint returns it in the `console` field of the reply. */
declare const console: {
  log(...args: unknown[]): void;
};

/** Pulls in a stored module by its name. Modules live in the js_modules table. */
declare function require(name: string): any;

/** Assign to `module.exports` to export from a stored module. */
declare const module: { exports: any };

// ---- TableRecord ----------------------------------------------------------------------------

type QueryOperator = "=" | "!=" | ">" | "<" | ">=" | "<=" | "LIKE";

/** A value that setValue and addQuery accept. */
type RecordValue = string | number | boolean | null;

/**
 * Query and write rows of one table. Writes run the business rules for the table.
 * Table and column names must match [A-Za-z_][A-Za-z0-9_]*.
 */
declare class TableRecord {
  constructor(table: string);

  /** Adds an equality condition. */
  addQuery(column: string, value: RecordValue): void;
  /** Adds a condition with an operator. */
  addQuery(column: string, operator: QueryOperator, value: RecordValue): void;
  /** Sorts ascending. Call again for a lower-priority sort column. */
  orderBy(column: string): void;
  /** Sorts descending. */
  orderByDesc(column: string): void;
  setLimit(limit: number): void;

  /** Runs the query. Then call next() to walk the rows. */
  query(): void;
  /** Moves to the next row. False at the end. */
  next(): boolean;

  /** The key column used for writes. Throws on a table with no single-column key. */
  getKeyColumn(): string;
  /** Columns of the current row. */
  getColumnNames(): JavaList<ScriptText>;

  /** Reads a field of the current row as text. Null when the field is null. */
  getValue(column: string): ScriptText | null;
  /** Sets a field. Nothing is written until insert() or update(). */
  setValue(column: string, value: RecordValue): void;
  /** True when a field differs from the row as it was read. */
  changes(column: string): boolean;

  /** In a before rule, stops the write. */
  setAbortAction(abort: boolean): void;
  isAborted(): boolean;

  /** Inserts the current fields. Returns the new key, or null when a rule aborted. */
  insert(): ScriptText | null;
  /** Writes the changed fields. False when a rule aborted. */
  update(): boolean;
  /** Deletes the current row. False when a rule aborted. */
  deleteRecord(): boolean;

  /** Counts the rows that match. */
  count(): number;
  /** Writes the fields you set to every matching row. Needs at least one addQuery. */
  updateMultiple(): number;
  /** Deletes every matching row. Needs at least one addQuery. */
  deleteMultiple(): number;
}

// ---- BusinessRules --------------------------------------------------------------------------

/** One row of business_rules, as BusinessRules.get and list return it. */
interface BusinessRule {
  readonly id: ScriptText;
  readonly name: ScriptText;
  readonly collection: ScriptText;
  readonly runWhen: "before" | "after";
  readonly onInsert: boolean;
  readonly onUpdate: boolean;
  readonly onDelete: boolean;
  /** JavaScript expression. Empty or null means the rule always runs. */
  readonly condition: ScriptText | null;
  readonly script: ScriptText;
  /** Lower runs first. */
  readonly orderNo: number;
  readonly active: boolean;
}

/** Fields you pass to BusinessRules.create. */
interface BusinessRuleFields {
  name: string;
  collection: string;
  runWhen: "before" | "after";
  onInsert?: boolean;
  onUpdate?: boolean;
  onDelete?: boolean;
  condition?: string | null;
  script: string;
  /** Default 100. */
  orderNo?: number;
  /** Default true. */
  active?: boolean;
}

/** Create, read, change and delete business rules. */
declare const BusinessRules: {
  /** Checks a rule without saving it. Throws when it is invalid or the script does not compile. */
  validate(fields: BusinessRuleFields): void;
  /** Saves a new rule. Returns its id, or null when a rule aborted the insert. */
  create(fields: BusinessRuleFields): ScriptText | null;
  /** Reads one rule. Null when no rule has the id. */
  get(id: string): BusinessRule | null;
  /** Lists rules in run order. Pass null for every table. */
  list(collection: string | null): JavaList<BusinessRule>;
  /** Changes fields. The result must still be valid. */
  update(id: string, changes: Partial<BusinessRuleFields>): boolean;
  setActive(id: string, active: boolean): boolean;
  /** False when no rule has the id. */
  delete(id: string): boolean;
};

// ---- Jobs -----------------------------------------------------------------------------------

type JobHandlerType = "java" | "script";
type JobTriggerType = "once" | "interval" | "daily" | "on_demand";

/** Fields you pass to Jobs.create. */
interface JobFields {
  name: string;
  handlerType: JobHandlerType;
  /** A registered Java key, or the name of an api_scripts row. */
  handler: string;
  triggerType: JobTriggerType;
  /** interval: { seconds: 60 }. daily: { time: "02:30" }, in UTC. An object or JSON text. */
  triggerSpec?: Record<string, unknown> | string;
  /** First run, as ISO text in UTC. Default now. */
  nextRun?: string | null;
  /** Lower runs first. Default 100. */
  priority?: number;
  /** State handed to the handler. An object or JSON text. */
  context?: Record<string, unknown> | string;
  /** Stop repeating after this time, as ISO text in UTC. */
  runEnd?: string | null;
  maxRetries?: number;
  retryDelaySeconds?: number;
  retryExponential?: boolean;
  retryMaxDelaySeconds?: number;
  /** 0 to 1. */
  retryJitter?: number;
}

/** A job as scripts read it. JSON fields are JSON text. Times are ISO text. */
interface JobInfo {
  readonly id: ScriptText;
  readonly name: ScriptText;
  readonly handlerType: JobHandlerType;
  readonly handler: ScriptText;
  readonly triggerType: JobTriggerType;
  readonly triggerSpec: ScriptText;
  readonly nextRun: ScriptText | null;
  /** 0 ready, 1 queued, 2 running, 3 error. */
  readonly state: 0 | 1 | 2 | 3;
  readonly stateName: "ready" | "queued" | "running" | "error";
  readonly priority: number;
  readonly owner: ScriptText | null;
  readonly context: ScriptText;
  readonly lastError: ScriptText | null;
  readonly runEnd: ScriptText | null;
  readonly maxRetries: number;
  readonly retryDelaySeconds: number;
  readonly retryExponential: boolean;
  readonly retryMaxDelaySeconds: number;
  readonly retryJitter: number;
  readonly retryCount: number;
}

/** One run of a job, from the history table. */
interface JobRun {
  readonly jobId: ScriptText;
  readonly jobName: ScriptText;
  readonly handlerType: JobHandlerType;
  readonly handler: ScriptText;
  readonly node: ScriptText;
  readonly runId: ScriptText;
  readonly status: "success" | "failed" | "no_handler" | "parked";
  readonly error: ScriptText | null;
  readonly startedAt: JavaInstant | null;
  readonly endedAt: JavaInstant | null;
  readonly durationMillis: number;
}

/**
 * Create, run, change and delete background jobs. A queued or running job belongs to a
 * node, so update and delete leave it alone and return false.
 */
declare const Jobs: {
  /** Checks a job without saving it, including that its handler exists. */
  validate(fields: JobFields): void;
  /** Saves a job. Returns its id. */
  create(fields: JobFields): ScriptText;
  /** Queues a one-time job to run now. */
  enqueue(
    name: string,
    handlerType: JobHandlerType,
    handler: string,
    context: Record<string, unknown> | null,
  ): ScriptText;
  /** Queues a one-time copy of a job. The job keeps its schedule. Returns the copy's id. */
  runNow(id: string): ScriptText;
  /** Null when no job has the id. */
  get(id: string): JobInfo | null;
  /** Every job, soonest due first. */
  list(): JavaList<JobInfo>;
  /** Jobs in one state: 0 ready, 1 queued, 2 running, 3 error. */
  listByState(state: 0 | 1 | 2 | 3): JavaList<JobInfo>;
  /** Changes fields and puts the job back to ready. False when a node holds the job. */
  update(id: string, changes: Partial<JobFields>): boolean;
  /** False when the job is missing or a node holds it. */
  delete(id: string): boolean;
  /** Run history, newest first. */
  history(id: string, limit: number): JavaList<JobRun>;
};

// ---- Shapes handed to handler functions -------------------------------------------------------

/** The request an API script (httpHandler) or job script (run) receives. */
interface ApiRequest {
  method: string;
  path: string;
  queryParams: Record<string, string>;
  headers: Record<string, string>;
  /** Parsed JSON on POST, PUT and PATCH. A job script gets the job context here. */
  body: any;
}

/** The response an API script (httpHandler) or job script (run) fills in. */
interface ApiResponse {
  status: number;
  headers: Record<string, string>;
  body: string;
  setStatus(status: number): void;
  setHeader(name: string, value: string): void;
  /** Text is kept as is. Anything else is turned into JSON. */
  setBody(body: unknown): void;
}

/**
 * The `request` global of a script run through /api/script. It is a Java map, so the
 * fields are read with get(). `request.method` is undefined there.
 */
interface ScriptRequest {
  get(key: "method" | "path" | "remoteAddr"): ScriptText | null;
  get(key: "queryParams"): { get(name: string): ScriptText | null };
}
