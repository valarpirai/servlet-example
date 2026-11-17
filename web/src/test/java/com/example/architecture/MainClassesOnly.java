package com.example.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.importer.Location;

/**
 * Imports production classes only. ArchUnit's own {@code DoNotIncludeTests} skips test class
 * directories. It misses a packaged test jar such as {@code core-1.0-tests.jar}, which {@code web}
 * puts on its test classpath. This option skips both.
 */
public final class MainClassesOnly implements ImportOption {

  private final ImportOption doNotIncludeTests = new ImportOption.DoNotIncludeTests();

  /**
   * Decides whether a class file belongs to the analysis.
   *
   * @param location the class file location
   * @return false for test class directories and test jars
   */
  @Override
  public boolean includes(Location location) {
    return doNotIncludeTests.includes(location) && !location.contains("-tests.jar");
  }
}
