/**
 * Extra globals for business rule scripts only. Include this file in the folder that holds
 * rule scripts, and leave it out of every other script folder.
 *
 * Keep this file in step with BusinessRuleRunner. See .claude/rules/script-globals-types.md.
 */

/** The record being written. Change fields, or call setAbortAction(true) in a before rule. */
declare const current: TableRecord;

/** The row as it was read. Null on insert. */
declare const previous: TableRecord | null;
