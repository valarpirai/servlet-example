package com.example.scheduler;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Names a {@link JobHandler} class. At startup {@link JobHandlerScanner} finds every class with
 * this annotation and registers it under the name. A job row uses the name as its {@code handler}
 * with {@code handler_type = 'java'}.
 *
 * <p>The class needs a public no-argument constructor. Names must be unique.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface JobName {

  /** The handler key, for example {@code hello-world}. */
  String value();
}
