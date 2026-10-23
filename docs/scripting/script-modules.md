# Script Modules

Reusable JavaScript stored on the server and pulled into scripts by name.

Modules live in the `js_modules` table. `ModuleManager` handles storage. `ModuleDependencyResolver` walks the dependency graph.
`ScriptModuleLoader` turns the result into one source string for Rhino.

## Importing

Use `import`.

```javascript
import calculator from 'utils/calculator';
```

Before the script runs, each `import x from 'path'` becomes
`var x = require('path')`. This is a textual rewrite, so it accepts the common
shapes and not the full ES6 grammar. Keep the imported names on one line unless
they sit inside braces.

Modules can use `import` too. A module body gets the same rewrite as the script
that loads it.

## Exporting

Assign to `module.exports`.

```javascript
// module name: utils/calculator
function add(a, b) {
  return a + b;
}

module.exports = { add: add };
```

## How resolution works

`prepareWithModules` runs before any user code:

1. `ModuleDependencyResolver.resolveImports` scans the source for imports
2. Each dependency is loaded, then scanned itself, transitively
3. The graph is checked for cycles
4. Each module is wrapped CommonJS style
5. The wrapped modules are prepended to the script as a preamble

Rhino then evaluates one combined source. There is no runtime module lookup.

## Cycles fail loudly

A circular dependency throws before execution, naming the full path:

```
Circular dependency detected: a.js -> b.js -> c.js -> a.js
```

Because resolution happens up front, a broken graph never runs half a script.

## Managing modules

`ModuleManager` is a singleton exposing `listModules`, `getModule`,
`createModule`, `updateModule` and `deleteModule`. The `ModuleProcessor` route at
`/api/modules/**` puts these on HTTP, and the module manager UI uses that.

Create, update and delete go through `TableRecord`, so business rules on `js_modules`
run. A `before` rule that stops a write returns 409 and leaves the row alone. Reads
use the database directly.

`modules.maxFileSize` caps a single module at 1 MB by default.

## Storage

Each module is one row. `name` is the path without `.js`, such as `utils/math`.
`getModule` accepts either form and strips leading slashes.

Modules need the database. Without it, module calls fail with "Modules are stored
in the database, which is not ready". Scripts that import nothing still run.

## Related

- [Script execution](./script-execution.md) — where resolution fits
- [API scripting](./api-scripting.md) — import in scripted endpoints
- [Script sandbox](./script-sandbox.md) — limits apply to module code too
- [Configuration](../architecture/configuration.md) — the modules properties
