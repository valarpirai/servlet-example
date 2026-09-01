# Documentation Rules

Rules for every file in `docs/`. Read these before writing or editing documentation.

## Overview

This project uses a contextual documentation system. Rules load based on file
paths. All documentation keeps this structure and keeps cross-references
synchronized.

## Writing Requirements

### Style

- MUST follow Hemingway style: short sentences (under 25 words), active voice,
  simple words, no hedging, concrete details, no fluff
- MUST NOT duplicate content across files — use references instead
- MUST use kebab-case filenames
- MUST keep files to 20-250 lines, one concept per file
- SHOULD split when a file exceeds 250 lines, unless it is a cohesive narrative

### Structure

- MUST use relative paths for cross-references
- MUST include a "Related" section at the bottom with links to adjacent docs
- MUST NOT reference line numbers in other files — they shift
- MUST reference by file path plus function or class name — these are stable

### Document Current State

- MUST NOT include "this used to be X" narratives
- MUST NOT include version history tables — git tracks this
- MUST NOT include completed-migration paths or "next steps" for done work
- MUST NOT add plan or implementation docs that describe a specific changeset
  alongside the change itself

Documentation describes the world as it is. Past and future tense belong in
commit messages, PR descriptions, and tickets. After a change lands, only the
durable knowledge it produced belongs in `docs/`, written as if things had
always been that way.

### Placement

- Docs live in `docs/` under one of the subdirectories below. Do not add docs at
  the repository root, and do not add loose files directly in `docs/`. The only
  exception is `docs/readme.md`, the index.

| Directory | Holds |
|---|---|
| `architecture/` | How the app fits together, flows, packages, configuration |
| `routing/` | Routes, matching, dispatch |
| `storage/` | Attachments, chunking, backends |
| `scripting/` | Rhino sandbox, execution, modules |
| `data-browser/` | Database browsing and connection strategies |
| `observability/` | Logging, correlation IDs, log format |
| `build/` | shared-build, catalogs, conventions |
| `development/` | Day-to-day workflow, testing, formatting, debugging |
| `troubleshooting/` | Symptom-first fixes, one area per file |

- Adding a new subdirectory means adding a row here and a section in
  `docs/readme.md`
- Every new file gets a link in `docs/readme.md`

## Micro-Modular Approach

This project uses a micro-modular documentation strategy. 20-250 lines per file.
One concept per file.

### When to Split

Split when:

- A pattern collection grows — give each pattern its own file
- A comprehensive guide covers several concepts — break it by concept
- A file exceeds 250 lines
- Different audiences need different parts

Keep together when:

- An architecture narrative tells one story
- An API specification forms a complete contract
- Design rationale couples decisions tightly

Never duplicate content. Reference instead.

## Checklist

Run through this before finishing any documentation change:

- [ ] Filename is kebab-case
- [ ] File is 20-250 lines
- [ ] File covers one concept
- [ ] Sentences run under 25 words, active voice
- [ ] No content duplicated from another doc
- [ ] Cross-references use relative paths
- [ ] Cross-references name files and symbols, never line numbers
- [ ] A "Related" section closes the file
- [ ] Every statement describes the current state
- [ ] Claims were checked against the code, not copied from another doc
- [ ] The file is linked from `docs/readme.md`
- [ ] Docs referencing this change are updated in the same commit

## Related

- [Documentation index](../../docs/readme.md) — every doc, by area
- [Package map](../../docs/architecture/package-map.md) — where code lives
- [Definition of done](../../docs/development/definition-of-done.md) — the commit gate
- [Project rules](../../CLAUDE.md) — repository-wide guidance
