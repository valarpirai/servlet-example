package com.example.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.PackageMatcher;
import java.util.Optional;

/**
 * Maps packages to MVC layers. The ArchUnit rules and the classification test both read it.
 *
 * <p>Adding a package under {@code com.example} means adding it here, or the classification test
 * fails.
 */
final class Layers {

  static final String CONTROLLER = "Controller";
  static final String SERVICE = "Service";
  static final String MODEL = "Model";
  static final String VIEW = "View";

  static final String[] CONTROLLER_PACKAGES = {
    "com.example.handler..",
    "com.example.processor..",
    "com.example.route..",
    "com.example.servlet.."
  };

  static final String[] SERVICE_PACKAGES = {
    "com.example.tableapi..",
    "com.example.storage..",
    "com.example.scheduler..",
    "com.example.datasource..",
    "com.example.extlib..",
    "com.example.mongo..",
    "com.example.script..",
    "com.example.module..",
    "com.example.routing.."
  };

  static final String[] MODEL_PACKAGES = {
    "com.example.record..",
    "com.example.repository..",
    "com.example.config..",
    "com.example.model.."
  };

  /** Response shaping. The rules treat the layer as optional. */
  static final String[] VIEW_PACKAGES = {"com.example.view.."};

  /** Shared code any layer may use, and the root bootstrap class. */
  static final String[] OUTSIDE_PACKAGES = {
    "com.example", "com.example.util..", "com.example.exception..", "com.example.security.."
  };

  private Layers() {}

  /**
   * Finds the layer a class belongs to.
   *
   * @param c the class to classify
   * @return the layer name, or empty when no layer claims the class's package
   */
  static Optional<String> layerOf(JavaClass c) {
    String pkg = c.getPackageName();
    if (matchesAny(pkg, CONTROLLER_PACKAGES)) {
      return Optional.of(CONTROLLER);
    }
    if (matchesAny(pkg, SERVICE_PACKAGES)) {
      return Optional.of(SERVICE);
    }
    if (matchesAny(pkg, MODEL_PACKAGES)) {
      return Optional.of(MODEL);
    }
    if (matchesAny(pkg, VIEW_PACKAGES)) {
      return Optional.of(VIEW);
    }
    return Optional.empty();
  }

  /**
   * Tells whether a class is shared code or the root bootstrap class.
   *
   * @param c the class to check
   * @return true when the class belongs to no layer by design
   */
  static boolean isOutsideLayers(JavaClass c) {
    return matchesAny(c.getPackageName(), OUTSIDE_PACKAGES);
  }

  private static boolean matchesAny(String pkg, String[] globs) {
    for (String glob : globs) {
      if (PackageMatcher.of(glob).matches(pkg)) {
        return true;
      }
    }
    return false;
  }
}
