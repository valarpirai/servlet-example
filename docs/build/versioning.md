# Versioning

Every module builds as `major.minor.<commit>`. The commit part is the first 6
characters of the git commit id. Example: `1.0.d7cf60`.

## What it does

All five modules share one version. The root `pom.xml` holds it as `${revision}`.
Each module takes the root as its parent, so the version flows down.

`./build.sh` reads `app.version.base` (the major and minor) from the root
`pom.xml`, reads the commit from git and passes `-Drevision=1.0.d7cf60` to Maven.

```bash
./build.sh                  # clean verify
./build.sh install          # publish the jars to ~/.m2
./build.sh test -pl core -am
```

Any Maven arguments work. With none, it runs `clean verify`.

## Where the jars land

| Module | Jar |
|---|---|
| common | `common/target/servlet-example-common-1.0.d7cf60.jar` |
| core | `core/target/servlet-example-core-1.0.d7cf60.jar` and `-tests.jar` |
| scheduler | `scheduler/target/servlet-example-scheduler-1.0.d7cf60.jar` |
| web | `web/target/servlet-example-1.0.d7cf60.jar`, the runnable shaded jar |

`install` and `deploy` publish the same version. The `flatten-maven-plugin` writes the
resolved version into the published POM, so consumers see `1.0.d7cf60`, never
`${revision}`.

## A plain mvn build

`mvn` without `build.sh` has no commit to use. It builds `<major.minor>.dev`, for
example `1.0.dev`. Use that for local work. Use `./build.sh` for any jar you keep.

`build.sh` warns when the tree has uncommitted changes. The version then names the
commit, not the exact source.

## Changing major or minor

Edit `app.version.base` in the root `pom.xml`. Nothing else changes.

## Why a script

Maven reads the version before any plugin runs, so a plugin cannot supply the
commit. A build extension can, but the one tried gives 7 characters. The script gives
exactly 6.

## Related

- [Build system](./build-system.md) — the POM chain
- [Modules](../architecture/modules.md) — what the modules hold
- [Workflows](../development/workflows.md) — build and run
