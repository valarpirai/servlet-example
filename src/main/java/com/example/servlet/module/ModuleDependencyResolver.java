package com.example.servlet.module;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ModuleDependencyResolver {

  private static final Logger logger = LoggerFactory.getLogger(ModuleDependencyResolver.class);

  // Pattern to match ES6 import statements
  private static final Pattern IMPORT_PATTERN =
      Pattern.compile("import\\s+.*?\\s+from\\s+['\"]([^'\"]+)['\"]");

  private final ModuleManager moduleManager;

  public ModuleDependencyResolver(ModuleManager moduleManager) {
    this.moduleManager = moduleManager;
  }

  public List<String> resolveImports(String script) throws IOException {
    Set<String> allImports = new HashSet<>();
    Map<String, Set<String>> dependencyGraph = new HashMap<>();

    // Parse imports from main script
    Set<String> scriptImports = extractImports(script);

    // Build dependency graph
    buildDependencyGraph(scriptImports, dependencyGraph, new HashSet<>());

    // Detect circular dependencies
    detectCircularDependencies(dependencyGraph);

    // Return topologically sorted modules
    return topologicalSort(dependencyGraph);
  }

  private Set<String> extractImports(String code) {
    Set<String> imports = new HashSet<>();
    Matcher matcher = IMPORT_PATTERN.matcher(code);

    while (matcher.find()) {
      String modulePath = matcher.group(1);
      imports.add(modulePath);
      logger.debug("Found import: {}", modulePath);
    }

    return imports;
  }

  private void buildDependencyGraph(
      Set<String> imports, Map<String, Set<String>> graph, Set<String> visited)
      throws IOException {

    for (String modulePath : imports) {
      if (visited.contains(modulePath)) {
        continue;
      }

      visited.add(modulePath);

      // Load module content
      Module module = moduleManager.getModule(modulePath);
      if (module == null) {
        throw new IOException("Module not found: " + modulePath);
      }

      // Extract imports from this module
      Set<String> moduleImports = extractImports(module.getContent());

      // Add to graph
      graph.put(modulePath, moduleImports);

      // Recursively build graph for module dependencies
      buildDependencyGraph(moduleImports, graph, visited);
    }
  }

  private void detectCircularDependencies(Map<String, Set<String>> graph) throws IOException {
    Set<String> visiting = new HashSet<>();
    Set<String> visited = new HashSet<>();

    for (String node : graph.keySet()) {
      if (!visited.contains(node)) {
        detectCircularDependenciesHelper(node, graph, visiting, visited, new ArrayList<>());
      }
    }
  }

  private void detectCircularDependenciesHelper(
      String node,
      Map<String, Set<String>> graph,
      Set<String> visiting,
      Set<String> visited,
      List<String> path)
      throws IOException {

    visiting.add(node);
    path.add(node);

    Set<String> dependencies = graph.get(node);
    if (dependencies != null) {
      for (String dep : dependencies) {
        if (visiting.contains(dep)) {
          // Circular dependency detected
          int cycleStart = path.indexOf(dep);
          List<String> cycle = path.subList(cycleStart, path.size());
          cycle.add(dep);
          throw new IOException("Circular dependency detected: " + String.join(" -> ", cycle));
        }

        if (!visited.contains(dep)) {
          detectCircularDependenciesHelper(dep, graph, visiting, visited, path);
        }
      }
    }

    visiting.remove(node);
    visited.add(node);
    path.remove(path.size() - 1);
  }

  private List<String> topologicalSort(Map<String, Set<String>> graph) {
    List<String> result = new ArrayList<>();
    Set<String> visited = new HashSet<>();

    for (String node : graph.keySet()) {
      if (!visited.contains(node)) {
        topologicalSortHelper(node, graph, visited, result);
      }
    }

    return result;
  }

  private void topologicalSortHelper(
      String node, Map<String, Set<String>> graph, Set<String> visited, List<String> result) {

    visited.add(node);

    Set<String> dependencies = graph.get(node);
    if (dependencies != null) {
      for (String dep : dependencies) {
        if (!visited.contains(dep)) {
          topologicalSortHelper(dep, graph, visited, result);
        }
      }
    }

    result.add(node);
  }
}
