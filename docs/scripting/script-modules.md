# Script Modules

Reusable JavaScript stored on the server and pulled into scripts by name.

Modules live under `modules.directory`, `data/modules` by default. `ModuleManager`
handles storage. `ModuleDependencyResolver` walks the dependency graph.
`ScriptModuleLoader` turns the result into one source string for Rhino.

## Both import styles work

CommonJS is the native form.

```javascript
var calculator = require('utils/calculator.js');
```

ES6 `import` is rewritten to `require()` before execution. This is a textual
rewrite, so it accepts the common shapes and not the full ES6 grammar.

```javascript
import calculator from 'utils/calculator.js';
// becomes: var calculator = require('utils/calculator.js')
```

## Exporting

Assign to `module.exports`.

```javascript
// data/modules/utils/calculator.js
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

`modules.maxFileSize` caps a single module at 1 MB by default.

## The modules directory is live state

`data/modules/` is runtime storage, treated like `data/attachments/` and `logs/`. Sample
modules are committed; anything created through the UI or the API is not tracked.

## Related

- [Script execution](./script-execution.md) — where resolution fits
- [API scripting](./api-scripting.md) — require() in scripted endpoints
- [Script sandbox](./script-sandbox.md) — limits apply to module code too
- [Configuration](../architecture/configuration.md) — the modules properties
