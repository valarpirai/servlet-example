# TypeScript for Scripts

Write server scripts in TypeScript. Compile them to JavaScript. Store the output.

## What it does

`types/servlet-example.d.ts` declares every global a script can use: `TableRecord`,
`BusinessRules`, `Jobs`, `console`, `require` and `module`. It also declares the
shapes handlers receive, `ApiRequest` and `ApiResponse`. The editor then checks
calls and completes names.

`types/business-rule.d.ts` adds `current` and `previous`. Use it only for rule scripts.

## Setup

```json
{
  "compilerOptions": {
    "target": "ES2015",
    "module": "preserve",
    "moduleResolution": "bundler",
    "lib": ["ES2015"],
    "types": [],
    "strict": true,
    "outDir": "out"
  },
  "files": ["types/servlet-example.d.ts", "src/handler.ts"]
}
```

- `lib` has no `dom`. The `dom` library declares its own `console` and clashes.
- `target` stays at ES2015 or lower. Rhino 1.7.15 does not run newer syntax.
- `module: preserve` keeps `import x from 'name'` as written. The server rewrites
  that line to `require` before the script runs.

## An endpoint

```typescript
function httpHandler(request: ApiRequest, response: ApiResponse): void {
  const jobs = Jobs.list();
  response.setStatus(200);
  response.setBody({ count: jobs.length });
}
```

Do not add `export`. The server calls `httpHandler` by name. A job script exports
`run(request, response)` the same way.

## A stored module

```typescript
import calculator from "utils/calculator";
module.exports = { total: calculator.add(1, 2) };
```

Declare each stored module in a `.d.ts` file with no top-level import or export:

```typescript
declare module "utils/calculator" {
  const calculator: { add(a: number, b: number): number };
  export default calculator;
}
```

Assign to `module.exports`. Do not use `export` in a module body.

## Business rules

Include `types/business-rule.d.ts` in the project that holds rule scripts only.

```typescript
if (current.changes("content") && String(current.getValue("content")) === "") {
  current.setAbortAction(true);
}
```

## Things the types cannot hide

- **Text is a Java string.** `TableRecord.getValue` and the bean fields return Java
  strings. `typeof` says `object`, and `===` with a JavaScript string is false. Wrap
  with `String(value)` first. The types call these `ScriptText`.
- **Lists are Java lists.** Read `length`, index them, or call `size()` and `get(i)`.
  They are not arrays, so `map` and `forEach` are missing.
- **`request` in `/api/script` is a Java map.** Read fields with
  `request.get('method')`. `request.method` is undefined there. API and job scripts
  get a plain object, so `request.method` works.
- **Script numbers are doubles.** `{ seconds: 60 }` is stored as `60`. A fraction is
  kept.

## Keeping the types current

A change that adds or alters a script API updates the types in the same commit. See
[Script global types](../../.claude/rules/script-globals-types.md).

## Related

- [Script globals](./script-globals.md) — how a global reaches scripts
- [Table records and business rules](./table-records-and-rules.md) — `TableRecord`
- [Managing business rules](./managing-business-rules.md) — `BusinessRules`
- [Managing jobs](../scheduler/managing-jobs.md) — `Jobs`
- [API scripting](./api-scripting.md) — `httpHandler`
