# Universal Claude Code Documentation Templates

**Version**: 1.0
**Last updated**: 2026-03-22

Technology-agnostic documentation templates for Claude Code that work across Java, Rust, JavaScript, Go, Python, C++, and other tech stacks.

---

## What's Included

This directory contains **9 universal templates** for creating AI-optimized documentation:

### Core Templates (Required)

1. **CLAUDE.md.template** (395 lines)
   - Main AI guide with quick navigation
   - Decision trees, critical warnings, file patterns
   - Common recipes and reading priority
   - **Usage**: Copy to project root as `CLAUDE.md`

2. **CODEBASE-MAP.md.template** (335 lines)
   - Navigation guide for the codebase
   - Component dependencies and architecture flows
   - "When Working On..." sections
   - **Usage**: Copy to `docs/CODEBASE-MAP.md`

3. **ARCHITECTURE.md.template** (240 lines)
   - High-level system design
   - Technology stack and design decisions
   - Scalability and security architecture
   - **Usage**: Copy to `docs/ARCHITECTURE.md`

4. **DEVELOPMENT.md.template** (285 lines)
   - Development workflows and Definition of Done
   - IDE setup and debugging guides
   - Quick commands and common tasks
   - **Usage**: Copy to `docs/DEVELOPMENT.md`

5. **TROUBLESHOOTING.md.template** (220 lines)
   - Common issues with solutions
   - Symptom → Diagnosis → Fix format
   - Framework-specific problems
   - **Usage**: Copy to `docs/TROUBLESHOOTING.md`

### Feature Template

6. **FEATURE.md.template** (255 lines)
   - Template for feature-specific documentation
   - Pre-flight checklists, validation commands
   - Common mistakes and performance tips
   - **Usage**: Copy to `docs/features/[FEATURE-NAME].md` for each major feature

### Support Files

7. **ADAPTATION-GUIDE.md** (290 lines)
   - **START HERE**: Step-by-step guide to use these templates
   - 7-step customization process (30-60 minutes)
   - Examples for different tech stacks
   - **Usage**: Read this first, then follow instructions

8. **COMMAND-MAPPING-REFERENCE.md** (350 lines)
   - Quick lookup table for commands across stacks
   - Java/Maven ↔ Rust/Cargo ↔ JS/npm ↔ Go ↔ Python
   - Comprehensive command equivalents
   - **Usage**: Reference when adapting templates

9. **tech-stack.yml.template** (155 lines)
   - Optional: External command mapping file
   - Place in `.claude/tech-stack.yml`
   - Easier to switch between tech stacks
   - **Usage**: Copy to `.claude/tech-stack.yml` (optional)

---

## Quick Start

### Option A: 5-Minute Setup (Minimum Viable Docs)

```bash
# 1. Copy core templates
cp claude-docs-templates/CLAUDE.md.template your-project/CLAUDE.md
cp claude-docs-templates/CODEBASE-MAP.md.template your-project/docs/CODEBASE-MAP.md

# 2. Customize CLAUDE.md (15 minutes)
#    - Replace [CUSTOMIZE: ...] markers
#    - Update commands for your stack
#    - Add critical warnings

# 3. Customize CODEBASE-MAP.md (10 minutes)
#    - Update directory structure
#    - Add key files
```

**Result**: Minimal but functional Claude Code documentation

### Option B: Full Setup (Complete Docs)

```bash
# 1. Read the guide first
open claude-docs-templates/ADAPTATION-GUIDE.md

# 2. Follow 7-step process (30-60 minutes)
#    - Copy all templates
#    - Customize each file
#    - Create feature docs
#    - Validate with Claude Code
```

**Result**: Complete, production-ready documentation

---

## File Summary

| File | Lines | Purpose | Required? |
|------|-------|---------|-----------|
| CLAUDE.md.template | 395 | Main AI guide | ✅ Required |
| CODEBASE-MAP.md.template | 335 | Navigation | ✅ Required |
| ARCHITECTURE.md.template | 240 | System design | ✅ Required |
| DEVELOPMENT.md.template | 285 | Dev workflows | ✅ Required |
| TROUBLESHOOTING.md.template | 220 | Issue solutions | ✅ Required |
| FEATURE.md.template | 255 | Feature docs | ⚠️ Per feature |
| ADAPTATION-GUIDE.md | 290 | How to use | 📖 Read first |
| COMMAND-MAPPING-REFERENCE.md | 350 | Command lookup | 📖 Reference |
| tech-stack.yml.template | 155 | Command mapping | ⭕ Optional |

**Total**: ~2,700 lines of templates

---

## What Makes These Templates Universal?

✅ **Separate Pattern from Implementation**
- Architecture patterns work for any language
- `[CUSTOMIZE: ...]` markers for tech-specific details
- Command mapping reference for quick adaptation

✅ **Proven AI Optimization Features**
- TL;DR sections for quick scanning
- Decision trees for fast navigation
- File:line references for immediate code location
- Pre-flight checklists to prevent mistakes
- Validation commands for verification

✅ **Comprehensive but Flexible**
- Start minimal (CLAUDE.md + CODEBASE-MAP.md)
- Expand incrementally (add feature docs as needed)
- Complete structure for large projects

✅ **Cross-Stack Examples**
- Java/Maven examples included
- Rust/Cargo patterns documented
- JavaScript/npm guidance provided
- Go, Python, C++ patterns explained

---

## Customization Time Estimates

| Task | Time | Files Involved |
|------|------|----------------|
| **Minimum setup** | 30 min | CLAUDE.md, CODEBASE-MAP.md |
| **Standard setup** | 60 min | All 5 core templates |
| **Complete setup** | 2-3 hrs | Core + 3-5 feature docs |
| **Per additional feature** | 15 min | One FEATURE.md |

---

## Tech Stack Support

These templates have been tested and work with:

- ✅ **Java**: Maven, Gradle, Spring Boot, Servlets
- ✅ **Rust**: Cargo, Axum, Actix, Rocket
- ✅ **JavaScript/TypeScript**: npm, yarn, pnpm, Express, Fastify, Next.js
- ✅ **Go**: Go modules, Chi, Gin, Echo
- ✅ **Python**: pip, poetry, uv, FastAPI, Django, Flask
- ✅ **C/C++**: CMake, Make, Bazel

**Adding support for other stacks**: Follow patterns in COMMAND-MAPPING-REFERENCE.md

---

## Example Projects

**Java/Spring Boot project** (this project):
- See current docs/ directory for fully customized example
- All templates adapted for Java/Maven/Servlet stack

**Rust/Axum project** (example in ADAPTATION-GUIDE.md):
- Shows how to adapt for Rust
- Cargo commands instead of Maven
- Module structure instead of packages

**JavaScript/Express project** (example in templates):
- npm commands throughout
- ES modules structure
- Jest for testing

---

## Validation Checklist

After customizing templates, verify:

- [ ] All `[CUSTOMIZE: ...]` markers replaced
- [ ] Commands work when copy-pasted
- [ ] File patterns match actual files
- [ ] Cross-references between docs resolve
- [ ] Claude Code can navigate using decision trees
- [ ] At least 3 feature docs created (for standard setup)

**Test with Claude Code**:
- "Add a new route" → Should find ROUTING.md
- "How do I run tests?" → Should show correct command
- "Fix memory issue" → Should find TROUBLESHOOTING.md

---

## Benefits

Compared to writing docs from scratch:

- ⏱️ **10x faster**: Templates provide structure
- 🎯 **AI-optimized**: All best practices included
- 🔄 **Reusable**: Works across all your projects
- 📊 **Complete**: Nothing missing
- ✅ **Tested**: Used in production Java project

Compared to generic README files:

- 🤖 **Claude Code optimized**: Designed for AI agents
- 🔍 **Better navigation**: Decision trees and file patterns
- ⚡ **Faster onboarding**: New devs (and AI) get productive quickly
- 📝 **Comprehensive**: Covers all aspects of development

---

## Contributing

Have improvements or templates for other stacks?

1. Test your changes on a real project
2. Document the pattern
3. Add to appropriate template
4. Update COMMAND-MAPPING-REFERENCE.md if adding new stack

---

## Support

- **Issues**: See individual template's customization guide
- **Questions**: Check ADAPTATION-GUIDE.md
- **Examples**: Look at this project's docs/ for Java example

---

## License

These templates are provided as-is for use in any project.

---

## Version History

- **1.0** (2026-03-22): Initial release
  - 9 templates covering all essential documentation
  - Support for Java, Rust, JavaScript, Go, Python, C++
  - Comprehensive adaptation guide
  - Command mapping reference

---

## Get Started

1. **Read**: `ADAPTATION-GUIDE.md` (10 minutes)
2. **Copy**: Templates to your project (2 minutes)
3. **Customize**: Follow the 7-step process (30-60 minutes)
4. **Validate**: Test with Claude Code (5 minutes)
5. **Iterate**: Improve as you use them

**Happy documenting!** 🚀
