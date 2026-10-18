# ManaDesk - instructions for Claude

ManaDesk is a Magic: The Gathering collection and deck manager: an Eclipse RCP
application (Eclipse 4.26, SWT/JFace, Tycho 3 Maven build) forked from Magic
Assistant. It ships for **Windows, Linux and macOS**.

## Git - the user commits, never Claude

- Never run `git add`, `git commit`, `git mv`, `git rm`, `git stash`, `git reset`,
  `git push` or anything else that changes the index or history. Use plain `mv` /
  `rm` for files; leave every change unstaged.
- One task = one branch. The user creates the branch, reviews, stages and commits.
- When a task is done, give a commit message (title line + short sections).

## Before changing behavior

- Ask first - describing how it works today - before removing or relaxing an
  existing rule, protection or confirmation, or changing a behavior the user did
  not ask to change.
- When a request reverses an earlier decision, follow the latest one.

## Java and code style

- Plug-ins build at **Java 17** (parent `pom.xml`; the manifests require
  `JavaSE-17`), so Java 9-17 **APIs** (`List.of`, `String.isBlank`, ...) are fine.
  Careful with newer **syntax** (`var`, records, text blocks, switch expressions):
  several Eclipse projects (`core`, `ui`, `magiccards_rcp`...) still have
  `compiler.compliance=1.8` in `.settings/org.eclipse.jdt.core.prefs`, and Eclipse
  then rejects it. Use it only in a project whose setting was raised to 17.
- Match the surrounding code: naming, comment density, tabs.
- **New files** start with the full header (current year), then `package`:

```
/*******************************************************************************
 * Copyright (c) 2026 Rémi Dutil.
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v2.0 which accompanies
 * this distribution, and is available at:
 *   https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 *
 * Contributors:
 *     Rémi Dutil - created for ManaDesk
 *******************************************************************************/
```

- **Existing files** keep their header; add one contributor line describing the
  change: ` *     Rémi Dutil (2026) - <what changed>`.
- Help pages (`com.reflexit.magiccards.help/html/**`) carry the copyright + EPL
  text as an HTML comment right after `<!DOCTYPE html>`; `help.css` as a `/* */`
  comment. Not `toc.xml`, `contexts.xml` or the generated `toc.html`.
- Debug traces stay in the code behind a `static boolean` flag (e.g.
  `TRACE_VIEW_RESTORE`, `DeckTextExtractor.TRACE_WEB_IMPORT`), off when committed.
  Never delete them - flip the flag to diagnose.

## Windows, Linux and macOS

Every change must work on all three. In particular:

- File names are case-sensitive on Linux and macOS: a resource path in code must
  match the file name exactly.
- Build paths with `File` / `Path`, never hard-coded `C:/...` or `\\`; use the
  workspace or user home, not fixed folders.
- Open links, mail and files with SWT `Program.launch`, and use the SWT
  `Clipboard` - not AWT (AWT is kept off GTK: `java.awt.headless=true`).
- The embedded `Browser` differs per platform (Edge or IE on Windows, WebKit on
  Linux and macOS): plain HTML/CSS that works in all of them; HTML meant for the
  Windows preview uses tables, not CSS grid.
- GTK lays out widgets differently: no fixed pixel sizes that assume Windows
  fonts; let layouts compute sizes.
- Line endings: Git uses `core.autocrlf=true` on the Windows machine, so working
  files there are **CRLF** - write CRLF when editing them. `*.sh` files are
  **always LF** (`.gitattributes`); a CRLF launcher script does not run on Linux.
- Linux and macOS launchers in the release archives must stay executable
  (`com.reflexit.magiccards.product/FixTarModes.java`, run by `releases.xml`).

## Unit tests

- Every fix or feature comes with unit tests where the logic can be tested.
  Extract the logic into a small (package-private, static if possible) method
  when that makes it testable.
- Core tests: `com.reflexit.magiccards.core.test` (suite `AllLocalTests`).
- UI tests: `com.reflexit.magiccards.ui.tests`, a **fragment** of the UI plug-in -
  a test in the same package can call package-private code. Add each new test
  class to `com.reflexit.magiccards.ui.tests/.../tests/AllTests.java`.
- Tests that need the Eclipse runtime (preferences, workbench) only run as a
  JUnit Plug-in Test or under Maven/Tycho (`eclipse-test-plugin`); pure-logic
  tests also run as plain JUnit. Say which kind each new test is.
- Tests use JUnit (`junit.framework.TestCase` style, as the existing ones). Never
  touch real user settings or files: unique preference ids / temp folders,
  cleaned up in `tearDown`.
- Report results faithfully, including failures and tests that could not be run.

## What's New and the help - part of every user-visible change

- **What's New**: `com.reflexit.magiccards_rcp/src/com/reflexit/magiccards_rcp/WhatsNew.java`
  (`FEATURES` entries: topic, title, one or two plain sentences). Add or update
  the entry for every change a user can see - new features and visible fixes.
- **Help**: `com.reflexit.magiccards.help/html/**`. Update the pages a change
  touches; list the screenshots that must be recaptured
  (`com.reflexit.magiccards.help/SCREENSHOTS.md`).
- Document only features that are enabled in the app today.
- Do not promote other tools or apps: for an import/export format of another
  app, say "already using it? import easily", one help page per supported app.

## Product facts

- Proxies and sideboard lists print smaller than real cards: at most 60 mm wide,
  83.8 mm high, printed at 100%, 9 per Letter sheet.
- Bug / feature reports by email go to BKurler-Manadesk@outlook.com. The user's
  own email address is only for identification - never put it in the app or in
  requests to other services.

## Repository map

- `com.reflexit.magiccards.core` - model, storage, import/export, Scryfall sync.
- `com.reflexit.magiccards.ui` - views, dialogs, wizards, preferences.
- `com.reflexit.magiccards_rcp` - application, workbench, What's New, splash.
- `com.reflexit.magiccards.help` - online help (Eclipse help system).
- `com.reflexit.magiccards.product` - product, release archives.
- `com.manadesk.monetization` - support dialog and Buyer view.
