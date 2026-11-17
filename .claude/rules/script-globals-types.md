# Script Global Types

Rules for any change that adds, renames or removes something scripts can use.

## The rule

- MUST update `types/servlet-example.d.ts` in the same change as any global script
  API: a new global, a changed method or field, a new bean a script can read.
- MUST update `types/business-rule.d.ts` when `BusinessRuleRunner` adds or removes a
  name that rule scripts see, such as `current` or `previous`.
- MUST NOT merge a script API change without its type change.

A global script API is any name a script can reach without importing it:
`TableRecord`, `BusinessRules`, `Jobs`, `console`, `require`, `module`, and anything
registered with `ScriptGlobals.register`. Public methods of an exposed class count too.
Every public method of an exposed class is visible to scripts.

## What to write

- Declare the global with `declare const` or `declare class`.
- Declare each bean a script can read as an `interface` with `readonly` fields.
- Name the Java types scripts receive: `JavaList<T>`, `JavaInstant`, `ScriptText`.
- Add a short comment per member. Say what it returns and when it returns null.
- Use `string` unions for fixed values, such as `"before" | "after"`.

## How to check

Compile a small TypeScript file that calls the new API, with the settings in
[TypeScript for scripts](../../docs/scripting/typescript.md). It must compile. A
call with a wrong argument must fail to compile.

Update [TypeScript for scripts](../../docs/scripting/typescript.md) when a new global
changes how a script is written.

## Related

- [Script globals](../../docs/scripting/script-globals.md) — registering a global
- [TypeScript for scripts](../../docs/scripting/typescript.md) — using the types
- [Project rules](../../CLAUDE.md) — repository-wide guidance
