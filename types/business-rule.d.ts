/**
 * Extra globals for business rule scripts only. Include this file in the folder that holds
 * rule scripts, and leave it out of every other script folder.
 *
 * Keep this file in step with BusinessRuleRunner. See .claude/rules/script-globals-types.md.
 */

/**
 * The record being written. Change fields, or call setAbortAction(true) in a before rule.
 * In an async rule it is a read-only snapshot of the row after the write. Writes throw.
 */
declare const current: TableRecord;

/** The row as it was read. Null on insert. In an async rule it is a read-only snapshot. */
declare const previous: TableRecord | null;
