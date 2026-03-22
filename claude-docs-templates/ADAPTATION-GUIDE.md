# Universal Claude Code Documentation - Adaptation Guide

**Version**: 1.0
**Last updated**: 2026-03-22

## Purpose

This guide shows you how to adapt the universal Claude Code documentation templates for your specific tech stack (Java, Rust, JavaScript, Go, Python, C++, etc.).

**Time estimate**: 30-60 minutes for initial setup

---

## Quick Start

### Step 1: Copy Templates (2 minutes)

```bash
# Copy all templates to your project root
cp claude-docs-templates/* your-project/

# Create docs directory structure
cd your-project
mkdir -p docs/features
mkdir -p docs/guides
mkdir -p docs/decisions
mkdir -p .claude
```

### Step 2: Choose Your Customization Approach (1 minute)

**Option A: Direct editing** - Replace `[CUSTOMIZE: ...]` markers in CLAUDE.md
- ✅ Simple, everything in one file
- ❌ Need to update if switching languages

**Option B: External mapping** - Use `.claude/tech-stack.yml` for commands
- ✅ Easy to switch tech stacks
- ✅ Share command mappings across projects
- ❌ Slightly more complex setup

**Recommendation**: Start with Option A, migrate to Option B if managing multiple projects.

---

## Step 3: Customize CLAUDE.md (15 minutes)

Open `CLAUDE.md.template` and work through each section:

### 3.1 Header (1 minute)
```markdown
**Last updated**: 2026-03-22  ← Update to today
**Tech stack**: Rust | **Framework**: Axum  ← Your stack
```

### 3.2 Where to Look Table (3 minutes)
Replace `[CUSTOMIZE: ...]` with your actual paths:

**Before:**
```markdown
Routing | docs/features/ROUTING.md | [CUSTOMIZE: e.g., routes/, main.rs]
```

**After (Rust example):**
```markdown
Routing | docs/features/ROUTING.md | src/routes/mod.rs
```

**After (JavaScript example):**
```markdown
Routing | docs/features/ROUTING.md | src/routes/index.ts
```

### 3.3 Decision Tree (2 minutes)
Update with your patterns:

**Before:**
```markdown
- New HTTP endpoint? → [CUSTOMIZE: Add to routes file] → See docs/features/ROUTING.md
```

**After (Rust):**
```markdown
- New HTTP endpoint? → Add handler to src/routes/ → See docs/features/ROUTING.md
```

**After (Go):**
```markdown
- New HTTP endpoint? → Add handler in handlers/ → See docs/features/ROUTING.md
```

### 3.4 Critical Section (5 minutes)
Add 3-5 constraints specific to your stack.

**Rust example:**
```markdown
## ⚠️ CRITICAL

**1. NEVER use `std::fs::read()`** → Always use `tokio::fs::File` with streaming
- Why: 500MB file = 1MB heap (not 500MB heap)
- Doc: docs/features/STORAGE.md

**2. NEVER `clone()` large structs** → Use references or `Arc<T>`
- Why: Prevents memory bloat
- File: `src/storage/chunked.rs:45-89`

**3. NEVER block in async context** → Use `spawn_blocking` for CPU work
- Why: Blocks entire runtime executor
- Doc: docs/ARCHITECTURE.md
```

**JavaScript example:**
```markdown
## ⚠️ CRITICAL

**1. NEVER use synchronous I/O** → Always use async/await with fs.promises
- Why: Blocks event loop
- Doc: docs/features/STORAGE.md

**2. NEVER store secrets in code** → Use environment variables
- Why: Security risk
- File: `.env.example`

**3. NEVER forget input validation** → Always validate with zod/joi
- Why: Prevents injection attacks
- Doc: docs/features/SECURITY.md
```

### 3.5 File Patterns (2 minutes)
Update glob patterns for your language:

**Java:**
```markdown
- All handlers: `src/main/java/**/handler/*.java`
- All tests: `src/test/java/**/*Test.java`
```

**Rust:**
```markdown
- All handlers: `src/routes/**/*.rs`
- All tests: `tests/**/*.rs` and `src/**/*.rs` with `#[cfg(test)]`
```

**TypeScript:**
```markdown
- All controllers: `src/controllers/**/*.ts`
- All tests: `__tests__/**/*.test.ts`
```

### 3.6 Commands Section (2 minutes)
Replace with your actual commands using the mapping reference:

**See `COMMAND-MAPPING-REFERENCE.md` for quick lookup**

**Rust example:**
```markdown
## Commands

```bash
cargo run                              # Dev server
cargo build --release                  # Build
cargo test                             # Run all tests
cargo test test_name                   # Run specific test
cargo fmt                              # Format code

RUST_LOG=debug cargo run               # Debug logging
curl localhost:3000/health             # Health check
```
```

---

## Step 4: Customize CODEBASE-MAP.md (10 minutes)

### 4.1 Update TL;DR (1 minute)
```markdown
**Total tests**: 145 tests (all passing)  ← Your actual count
**Main modules**: routes/, services/, models/  ← Your structure
**Entry point**: src/main.rs  ← Your entry point
```

### 4.2 Document Your Structure (5 minutes)
Replace the example structure with your actual project layout.

Use the appropriate template section (Java/Rust/JavaScript) as starting point, then customize.

### 4.3 Add "When Working On" Sections (4 minutes)
Create 3-5 sections for major areas:

```markdown
### Authentication Changes
**Files to check**:
- `src/middleware/auth.rs` - JWT validation
- `src/models/user.rs` - User model

**Tests to run**:
```bash
cargo test auth::
```

**Reference**: docs/features/SECURITY.md
```

---

## Step 5: Create Feature Docs (15 minutes total, 3 min each)

For each major feature, copy `FEATURE.md.template` to `docs/features/[NAME].md`:

```bash
cp FEATURE.md.template docs/features/ROUTING.md
cp FEATURE.md.template docs/features/STORAGE.md
cp FEATURE.md.template docs/features/SECURITY.md
cp FEATURE.md.template docs/features/DATABASE.md
cp FEATURE.md.template docs/features/LOGGING.md
```

For each file, customize:
1. Title and status
2. TL;DR section (3-4 sentences)
3. Key Files (3-5 files)
4. Pre-flight checklist (4-6 items before, 4-6 after)
5. How It Works section

**Pro tip**: Start with just TL;DR and Key Files, expand later.

---

## Step 6: Customize Remaining Core Docs (10 minutes)

### DEVELOPMENT.md (5 minutes)
- Update commands throughout
- Customize Definition of Done checklist
- Add IDE setup for your stack

### TROUBLESHOOTING.md (5 minutes)
- Add 5-10 common issues for your framework
- Include stack traces examples
- Add diagnostic commands

---

## Step 7: Optional Enhancements (Later)

### Create .claude/tech-stack.yml (10 minutes)
If you want external command mapping:

```yaml
stack:
  language: rust
  framework: axum

commands:
  dev_server: "cargo run"
  build: "cargo build --release"
  # ... etc
```

### Create Architecture Decision Records (Ongoing)
As you make decisions, document them:

```bash
echo "# Why We Chose Axum Over Actix" > docs/decisions/001-axum-choice.md
```

---

## Validation Checklist

After customization, verify:

- [ ] All `[CUSTOMIZE: ...]` markers replaced
- [ ] All commands work when copy-pasted
- [ ] File patterns match your actual files
- [ ] Cross-references between docs resolve
- [ ] Critical warnings are specific to your stack
- [ ] TL;DR sections are concise (< 5 sentences)
- [ ] At least 3 feature docs created

**Test with Claude Code:**
- Ask: "Add a new route" - does it find ROUTING.md?
- Ask: "How do I run tests?" - does it show correct command?
- Ask: "Where is the database code?" - does it find correct files?

---

## Common Patterns by Language

### Java/Spring Boot Customizations
- Commands: Use `mvn` or `gradle`
- Structure: `src/main/java/com/company/project/`
- Tests: `src/test/java/` with `*Test.java`
- Config: `application.yml` or `application.properties`

### Rust Customizations
- Commands: Use `cargo`
- Structure: `src/` with `mod.rs` files
- Tests: `tests/` and `#[cfg(test)]` modules
- Config: `Cargo.toml` and `config/*.toml`

### JavaScript/TypeScript Customizations
- Commands: Use `npm` or `yarn`
- Structure: `src/` with index files
- Tests: `__tests__/`, `*.test.ts`, or `*.spec.ts`
- Config: `package.json` and `.env`

### Go Customizations
- Commands: Use `go` commands
- Structure: Flat or by feature packages
- Tests: `*_test.go` alongside source
- Config: `go.mod` and env vars

### Python Customizations
- Commands: Use `pip`, `poetry`, or `uv`
- Structure: Package directories with `__init__.py`
- Tests: `tests/` with `test_*.py`
- Config: `pyproject.toml` or `requirements.txt`

---

## Need Help?

1. **Command not sure?** → Check `COMMAND-MAPPING-REFERENCE.md`
2. **Structure unclear?** → Look at the template examples for your language
3. **Missing section?** → It's probably optional, skip for now
4. **Stuck?** → Start with just CLAUDE.md and one feature doc, expand later

---

## Next Steps

After completing this guide:

1. **Commit your docs** to version control
2. **Test with Claude Code** - verify navigation works
3. **Iterate** - docs improve with use
4. **Share** - help your team understand the structure

Your documentation is now Claude Code optimized! 🎉
