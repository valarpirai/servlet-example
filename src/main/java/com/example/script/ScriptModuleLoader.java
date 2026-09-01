package com.example.script;

import com.example.exception.StorageException;
import com.example.model.Module;
import com.example.module.ModuleDependencyResolver;
import com.example.module.ModuleManager;
import java.io.IOException;
import java.util.List;

/** Resolves module imports and prepares the combined JS source to be executed by Rhino. */
public class ScriptModuleLoader {

  private final ModuleManager moduleManager;

  public ScriptModuleLoader(ModuleManager moduleManager) {
    this.moduleManager = moduleManager;
  }

  /**
   * Resolves all import dependencies, wraps each module in CommonJS style, and prepends the
   * resulting loader preamble to the user script. ES6 import statements are rewritten to require()
   * calls.
   */
  public String prepareWithModules(String script) throws IOException {
    ModuleDependencyResolver resolver = new ModuleDependencyResolver(moduleManager);
    List<String> moduleOrder = resolver.resolveImports(script);

    StringBuilder preamble = new StringBuilder();
    preamble.append("var __moduleCache = {};\n");

    for (String modulePath : moduleOrder) {
      Module module = moduleManager.getModule(modulePath);
      if (module == null) {
        throw new StorageException("Module not found: " + modulePath);
      }
      preamble.append("(function() {\n");
      preamble.append("  var module = { exports: {} };\n");
      preamble.append("  var exports = module.exports;\n");
      preamble.append("  var require = function(path) {\n");
      preamble.append("    if (__moduleCache[path]) return __moduleCache[path];\n");
      preamble.append("    throw new Error('Module not found: ' + path);\n");
      preamble.append("  };\n");
      preamble.append(module.getContent()).append("\n");
      preamble.append("  __moduleCache['").append(modulePath).append("'] = module.exports;\n");
      preamble.append("})();\n\n");
    }

    preamble.append("function require(modulePath) {\n");
    preamble.append("  if (__moduleCache[modulePath]) return __moduleCache[modulePath];\n");
    preamble.append("  throw new Error('Module not found: ' + modulePath);\n");
    preamble.append("}\n\n");

    String transformed =
        script.replaceAll(
            "import\\s+([\\w{},\\s*]+)\\s+from\\s+['\"]([^'\"]+)['\"]", "var $1 = require('$2')");

    return preamble + transformed;
  }
}
