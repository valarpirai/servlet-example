# Command Mapping Reference

**Purpose**: Quick lookup table for equivalent commands across different tech stacks.

**Use this when**: Adapting documentation templates from one language to another.

---

## Core Development Commands

| Concept | Java/Maven | Rust/Cargo | JavaScript/npm | Go | Python |
|---------|------------|------------|----------------|----|----|
| **Dev server** | `mvn -PappRun` | `cargo run` | `npm run dev` | `go run .` | `python -m uvicorn main:app --reload` |
| **Build** | `mvn clean package` | `cargo build --release` | `npm run build` | `go build` | `python -m build` |
| **Build (dev)** | `mvn compile` | `cargo build` | `npm run build:dev` | `go build` | `pip install -e .` |
| **Clean** | `mvn clean` | `cargo clean` | `rm -rf dist node_modules` | `go clean` | `rm -rf dist __pycache__` |

---

## Testing Commands

| Concept | Java/Maven | Rust/Cargo | JavaScript/npm | Go | Python |
|---------|------------|------------|----------------|----|----|
| **Run all tests** | `mvn test` | `cargo test` | `npm test` | `go test ./...` | `pytest` |
| **Run one test** | `mvn test -Dtest=TestName` | `cargo test test_name` | `npm test -- TestName` | `go test -run TestName` | `pytest tests/test_name.py` |
| **Test with coverage** | `mvn test jacoco:report` | `cargo tarpaulin` | `npm run test:coverage` | `go test -cover ./...` | `pytest --cov` |
| **Watch mode** | N/A | `cargo watch -x test` | `npm test -- --watch` | `gotestsum --watch` | `pytest-watch` |
| **Verbose output** | `mvn test -X` | `cargo test -- --nocapture` | `npm test -- --verbose` | `go test -v ./...` | `pytest -v` |

---

## Code Quality Commands

| Concept | Java/Maven | Rust/Cargo | JavaScript/npm | Go | Python |
|---------|------------|------------|----------------|----|----|
| **Format check** | `mvn spotless:check` | `cargo fmt --check` | `npm run format:check` | `gofmt -l .` | `black --check .` |
| **Format apply** | `mvn spotless:apply` | `cargo fmt` | `npm run format` | `gofmt -w .` | `black .` |
| **Lint** | `mvn checkstyle:check` | `cargo clippy` | `npm run lint` | `golangci-lint run` | `flake8` |
| **Lint fix** | N/A | `cargo clippy --fix` | `npm run lint:fix` | `golangci-lint run --fix` | `autopep8 -i -r .` |
| **Type check** | `mvn compile` | `cargo check` | `npm run typecheck` | N/A (compile-time) | `mypy .` |

---

## Dependency Management

| Concept | Java/Maven | Rust/Cargo | JavaScript/npm | Go | Python |
|---------|------------|------------|----------------|----|----|
| **Install deps** | `mvn install` | `cargo build` | `npm install` | `go mod download` | `pip install -r requirements.txt` |
| **Add dependency** | Edit `pom.xml` | `cargo add package` | `npm install package` | `go get package` | `pip install package` |
| **Update deps** | `mvn versions:use-latest` | `cargo update` | `npm update` | `go get -u all` | `pip install --upgrade -r requirements.txt` |
| **List deps** | `mvn dependency:tree` | `cargo tree` | `npm list` | `go list -m all` | `pip list` |
| **Audit security** | `mvn dependency-check:check` | `cargo audit` | `npm audit` | `go list -json -m all \| nancy` | `safety check` |

---

## Development Workflow

| Concept | Java/Maven | Rust/Cargo | JavaScript/npm | Go | Python |
|---------|------------|------------|----------------|----|----|
| **Watch & reload** | `mvn compile -Dwatch` | `cargo watch -x run` | `npm run dev` | `air` or `realize` | `uvicorn main:app --reload` |
| **Debug mode** | `mvn -PappRun -Ddebug` | `cargo run --features debug` | `npm run dev:debug` | `go run -race .` | `python -m pdb app.py` |
| **Profiling** | `mvn -Dprofile` | `cargo flamegraph` | `npm run profile` | `go test -cpuprofile` | `python -m cProfile` |

---

## Environment & Configuration

| Concept | Java/Maven | Rust/Cargo | JavaScript/npm | Go | Python |
|---------|------------|------------|----------------|----|----|
| **Custom port** | `SERVER_PORT=9090 mvn -PappRun` | `PORT=3000 cargo run` | `PORT=3000 npm run dev` | `PORT=8080 go run .` | `PORT=8000 python app.py` |
| **Environment file** | `application.yml` or `.env` | `config/*.toml` or `.env` | `.env` | `.env` or config files | `.env` |
| **Load env vars** | `mvn -Denv.file=.env` | `dotenv` crate | `dotenv` package | `godotenv` package | `python-dotenv` |

---

## Health Checks & Monitoring

| Concept | Java/Maven | Rust/Cargo | JavaScript/npm | Go | Python |
|---------|------------|------------|----------------|----|----|
| **Health check** | `curl localhost:8080/health` | `curl localhost:3000/health` | `curl localhost:3000/health` | `curl localhost:8080/health` | `curl localhost:8000/health` |
| **Metrics** | `curl localhost:8080/metrics` | `curl localhost:3000/metrics` | `curl localhost:3000/metrics` | `curl localhost:8080/metrics` | `curl localhost:8000/metrics` |
| **View logs** | `tail -f logs/app.log` | `tail -f logs/app.log` | `npm run logs` | `tail -f logs/app.log` | `tail -f logs/app.log` |

---

## Build Artifacts

| Concept | Java/Maven | Rust/Cargo | JavaScript/npm | Go | Python |
|---------|------------|------------|----------------|----|----|
| **Output location** | `target/*.jar` | `target/release/binary` | `dist/` or `build/` | Binary in project root | `dist/*.whl` |
| **Run artifact** | `java -jar target/app.jar` | `./target/release/app` | `node dist/index.js` | `./app` | `python -m app` |
| **Check artifact size** | `du -h target/*.jar` | `du -h target/release/*` | `du -h dist/` | `du -h binary` | `du -h dist/*.whl` |

---

## Additional Stacks

### Java/Gradle
```bash
./gradlew run                    # Dev server
./gradlew build                  # Build
./gradlew test                   # Run tests
./gradlew spotlessApply          # Format
```

### TypeScript (specific)
```bash
npm run dev                      # Dev server (ts-node or tsx)
npm run build                    # Compile TypeScript
tsc --noEmit                     # Type check only
```

### C++/CMake
```bash
cmake -B build                   # Configure
cmake --build build              # Build
ctest --test-dir build           # Run tests
```

### Ruby/Rails
```bash
rails server                     # Dev server
bundle exec rake                 # Build/setup
bundle exec rspec                # Run tests
rubocop -a                       # Format
```

---

## Special Cases

### Java with Spring Boot
```bash
mvn spring-boot:run              # Dev server
mvn clean package               # Build JAR
mvn test                        # Tests
```

### Rust with specific features
```bash
cargo run --features full        # With features
cargo test --all-features        # Test all features
cargo build --release --target x86_64-unknown-linux-musl  # Cross-compile
```

### Node.js with different package managers

**npm:**
```bash
npm install
npm test
npm run build
```

**yarn:**
```bash
yarn install
yarn test
yarn build
```

**pnpm:**
```bash
pnpm install
pnpm test
pnpm build
```

---

## Quick Substitution Guide

When adapting docs from Java to Rust:
- `mvn test` → `cargo test`
- `mvn -PappRun` → `cargo run`
- `pom.xml` → `Cargo.toml`
- `src/main/java` → `src`
- `*Test.java` → `*_test.rs` or `#[cfg(test)]`

When adapting docs from Rust to JavaScript:
- `cargo run` → `npm run dev`
- `cargo test` → `npm test`
- `Cargo.toml` → `package.json`
- `src/main.rs` → `src/index.ts`

When adapting docs from JavaScript to Go:
- `npm run dev` → `go run .`
- `npm test` → `go test ./...`
- `package.json` → `go.mod`
- `src/` → Package directories

---

## Usage Example

**Original (Java):**
```markdown
## Commands
```bash
mvn -PappRun                    # Dev server
mvn test                        # Run tests
```
```

**Adapted (Rust):**
```markdown
## Commands
```bash
cargo run                       # Dev server
cargo test                      # Run tests
```
```

**Adapted (JavaScript):**
```markdown
## Commands
```bash
npm run dev                     # Dev server
npm test                        # Run tests
```
```

---

## Tech Stack Detection

Use these patterns to auto-detect tech stack in documentation:

| File Present | Tech Stack |
|--------------|------------|
| `pom.xml` | Java/Maven |
| `build.gradle` or `build.gradle.kts` | Java/Gradle |
| `Cargo.toml` | Rust |
| `package.json` | JavaScript/TypeScript |
| `go.mod` | Go |
| `pyproject.toml` or `requirements.txt` | Python |
| `Gemfile` | Ruby |
| `CMakeLists.txt` | C/C++ |

---

## Contributing

If you have commands for other tech stacks, please add them to this reference!
