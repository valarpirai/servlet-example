package com.example.util;

import java.util.Optional;

/** Where {@link PropertiesUtil} looks a property up before it falls back to the YAML file. */
public interface PropertySource {

  /**
   * Finds one property value.
   *
   * @param name the property name
   * @return the value, or empty when no active row has that name
   * @throws Exception when the lookup fails
   */
  Optional<String> findValueByName(String name) throws Exception;
}
