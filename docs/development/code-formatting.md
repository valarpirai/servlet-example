# Code Formatting

Spotless with google-java-format, GOOGLE style.

```bash
mvn spotless:check      # report violations
mvn spotless:apply      # fix them
```

`spotless:check` runs at `verify`, so `mvn clean verify` fails on unformatted
code. Configuration lives in `shared-build/pom.xml`, applied to every project.

## The pre-commit hook

`hooks/pre-commit` runs `spotless:check` before every commit. On a violation it
runs `spotless:apply`, re-stages the files with `git add -u`, and exits non-zero.

So the first commit attempt fails and formats your code. Review the changes and
commit again. This is expected, not an error.

## Installing the hook

The hook lives in `hooks/`, which git does not use directly. Point git at it or
copy it in:

```bash
cp hooks/pre-commit .git/hooks/pre-commit
chmod +x .git/hooks/pre-commit
```

## Formatting is not negotiable

google-java-format has no options. Do not argue with its line breaks and do not
hand-format around it. Run `spotless:apply` and move on.

Keep a formatting-only change in its own commit. Mixing it with logic changes
makes the diff unreadable.

## Version pinning

The formatter version is pinned in `shared-build/pom.xml` as
`google.java.format.version`. Everyone formats identically because the version
is fixed, not because everyone configured their IDE the same way.

Bumping it reformats the codebase. Do it in a dedicated commit.

## Related

- [Definition of done](./definition-of-done.md) — the gate before committing
- [Build conventions](../build/build-conventions.md) — how spotless is wired
- [Build issues](../troubleshooting/build-issues.md) — format check failures
