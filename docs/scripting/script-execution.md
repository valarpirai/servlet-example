# Script Execution

Three classes split the work of running a script.

`ScriptExecutor` owns the Rhino context. `ScriptScopeBuilder` decides what the
script can see. `ScriptModuleLoader` resolves imports before anything runs.

```
script source
    ↓
ScriptModuleLoader.prepareWithModules   resolve imports, produce combined source
    ↓
ScriptExecutor.execute(source, scopeSetup)
    ├─ SecureContextFactory from ScriptSecurityManager
    ├─ optimization level and instruction threshold applied
    ├─ ScopeSetup callback populates the scope
    └─ evaluate
    ↓
ScriptExecutionResult
```

## ScriptExecutor

`execute(String script, ScopeSetup scopeSetup)` is the single entry point. It
builds a secure context, applies the limits, lets the caller populate the scope,
then evaluates.

`ScopeSetup` is a functional interface. That is the seam: the executor never
knows what a caller exposes to the script, so the same executor serves the
script editor and the API scripting path with different scopes.

`convertToJavaObject` converts a Rhino value back to a Java value on the way out.

## ScriptScopeBuilder

`populate` adds the server-side objects and helper functions a script expects,
including `console`. Anything a script can reach that is not a whitelisted Java
class arrives through here.

Adding a capability to scripts means adding it to the scope, not widening the
[sandbox policy](./sandbox-policy.md).

## ScriptModuleLoader

`prepareWithModules` takes the raw source, resolves every import through
`ModuleManager`, and returns one combined source string. Rhino sees a single
script.

Resolution runs before execution, so a missing or circular module fails before
any user code runs. See [script modules](./script-modules.md).

## Why the split matters

Module loading, scope construction and context management change for different
reasons. Keeping them apart means the security policy has one owner
and the module system has another.

Both `ScriptProcessor` and `ApiScriptProcessor` drive this same path, so a
change to limits or scope reaches both.

## Related

- [Script sandbox](./script-sandbox.md) — the limits applied here
- [Script modules](./script-modules.md) — how imports resolve
- [API scripting](./api-scripting.md) — script-defined HTTP endpoints
