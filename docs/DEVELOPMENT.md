# Development Guide

## Code Formatting

This project uses **Spotless** with **Google Java Format** to maintain consistent code style.

### Automatic Formatting

A pre-commit hook automatically runs Spotless before each commit:

```bash
# Pre-commit hook runs automatically
git commit -m "your message"

# If formatting issues found:
# 1. Spotless automatically formats code
# 2. Re-stages formatted files
# 3. Asks you to review and commit again
```

### Manual Formatting

```bash
# Check if code is formatted
mvn spotless:check

# Apply formatting to all files
mvn spotless:apply
```

### Code Style

- **Format**: Google Java Format
- **Indentation**: 2 spaces
- **Line length**: 100 characters (Google default)
- **Import ordering**: Automatic

### Pre-commit Hook Location

`.git/hooks/pre-commit` (not committed to repo)

To install the hook on a new clone:
```bash
chmod +x .git/hooks/pre-commit
```

Or copy from project if provided:
```bash
cp hooks/pre-commit .git/hooks/pre-commit
chmod +x .git/hooks/pre-commit
```

## Lombok

This project uses **Lombok** to reduce boilerplate code.

### Enabled Annotations

- `@Getter` / `@Setter` - Generate getters/setters
- `@Builder` - Generate builder pattern
- `@NoArgsConstructor` - Generate no-args constructor

### Model Classes

All model classes are in `com.example.servlet.model`:

- `Attachment` - File attachment metadata
- `Module` - JavaScript module metadata
- `Route` - Routing configuration
- `ProcessorResponse` - HTTP response builder

Example:
```java
@Getter
@Setter
public class MyModel {
    private String name;
    private int value;

    // No need to write getters/setters!
}
```

### IDE Setup

**IntelliJ IDEA**:
1. Install Lombok plugin: `Preferences → Plugins → Lombok`
2. Enable annotation processing: `Preferences → Build → Compiler → Annotation Processors`

**VS Code**:
1. Install "Language Support for Java" extension
2. Lombok is automatically supported

**Eclipse**:
1. Download lombok.jar from https://projectlombok.org/download
2. Run `java -jar lombok.jar`
3. Select Eclipse installation directory

## Testing

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=RouteRegistryTest

# Run with coverage
mvn clean test jacoco:report
```

## Building

```bash
# Development mode (quick run)
mvn -PappRun

# Production build
mvn clean package

# Run JAR
java -jar target/servlet-example.jar
```

## Code Quality

### Spotless (enforced via pre-commit hook)
- Automatic formatting
- Google Java Format style
- Runs before every commit

### Best Practices
- Use Lombok for models
- Keep methods small and focused
- Write tests for new features
- Follow Google Java Style Guide

## Git Workflow

```bash
# Make changes
git add .

# Commit (Spotless runs automatically)
git commit -m "your message"

# If formatting needed:
# - Spotless auto-formats
# - Files re-staged
# - Review changes
# - Commit again

# Push
git push
```

## Troubleshooting

### "Spotless check failed"
Run `mvn spotless:apply` to fix formatting issues.

### "Lombok not working in IDE"
Ensure Lombok plugin is installed and annotation processing is enabled.

### "Pre-commit hook not running"
Ensure `.git/hooks/pre-commit` is executable:
```bash
chmod +x .git/hooks/pre-commit
```
