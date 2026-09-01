# Lombok

Lombok removes getter, setter and builder boilerplate from model classes.

It is a `provided` dependency, so it participates in compilation and stays out of
the JAR.

## Annotations in use

| Annotation | Generates |
|---|---|
| `@Getter` | Getters for every field |
| `@Setter` | Setters for every field |
| `@Builder` | A builder |
| `@NoArgsConstructor` | A no-arg constructor |

```java
@Getter
@Setter
public class MyModel {
  private String name;
  private int value;
}
```

## Where it is used

Model classes in `com.example.model`:

- `Attachment`, file metadata
- `Module`, module metadata
- `Route`, route configuration
- `ProcessorResponse`, HTTP response builder

`AppProperty` in `config` is a Java record instead. Records suit fixed value
types; Lombok suits classes that need setters or a builder.

## IDE setup

Lombok generates code during annotation processing, so an IDE that does not know
about it will report errors on valid code.

**IntelliJ IDEA.** Install the Lombok plugin, then enable annotation processing
under Build, Compiler, Annotation Processors.

**VS Code.** Install Language Support for Java. Lombok works without extra
setup.

**Eclipse.** Download `lombok.jar` from projectlombok.org, run
`java -jar lombok.jar`, and point it at the Eclipse install.

The Maven build never needs this. It is an IDE concern only.

## Keeping it out of the JAR

The `provided` scope is set in the catalog, so `pom.xml` does not repeat it.
Verify with:

```bash
mvn dependency:tree | grep lombok
```

## Related

- [Package map](../architecture/package-map.md) — where models live
- [Build conventions](../build/build-conventions.md) — scopes and JAR contents
- [Adding a dependency](../build/adding-a-dependency.md) — how scope is decided
