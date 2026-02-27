---
title: "SpotBugs Static Analysis"
layout: docs
---

LuCLI uses [SpotBugs](https://spotbugs.github.io/) for static analysis to detect potential bugs, dead code, thread safety issues, and other defects in the Java codebase.

## Prerequisites

- Java 21+ (use `sdk env` if you have SDKMAN configured)
- Project must be compiled first (`mvn compile` or `mvn package`)

## Running SpotBugs

### Generate a Report

```bash
mvn spotbugs:spotbugs
```

Runs the analysis and writes an XML report to `target/spotbugsXml.xml`. The build will **not** fail even if bugs are found — use this for informational analysis.

### Check (Fail on Bugs)

```bash
mvn spotbugs:check
```

Runs the analysis and **fails the build** if any bugs are found. Useful for CI pipelines or pre-merge gates.

### Browse Results in the GUI

```bash
mvn spotbugs:gui
```

Opens an interactive Swing GUI to browse findings by category, file, and severity. This is the easiest way to triage results.

## Configuration

SpotBugs is configured in `pom.xml`:

```xml
<plugin>
    <groupId>com.github.spotbugs</groupId>
    <artifactId>spotbugs-maven-plugin</artifactId>
    <version>4.8.6.6</version>
    <configuration>
        <effort>Max</effort>
        <threshold>Low</threshold>
        <xmlOutput>true</xmlOutput>
    </configuration>
</plugin>
```

- **`effort`** — analysis depth: `Min`, `Default`, or `Max`. Higher effort finds more bugs but takes longer.
- **`threshold`** — minimum confidence to report: `High`, `Medium`, `Low`, or `Exp` (experimental). Lower thresholds produce more findings.
- **`xmlOutput`** — write results to `target/spotbugsXml.xml`.

### Adjusting Sensitivity

For a quicker check focusing on high-confidence bugs only:

```bash
mvn spotbugs:check -Dspotbugs.threshold=High
```

Or for a focused analysis with less depth:

```bash
mvn spotbugs:check -Dspotbugs.effort=Default -Dspotbugs.threshold=Medium
```

## Common Bug Categories

SpotBugs detects over 400 bug patterns. The most common ones in LuCLI:

- **DM_DEFAULT_ENCODING** — Reliance on the platform's default character encoding instead of specifying one explicitly (e.g. `UTF-8`).
- **MS_EXPOSE_REP** — A public method returns a reference to a mutable internal field (e.g. returning an array or collection directly).
- **REC_CATCH_EXCEPTION** — Catching `Exception` instead of a more specific exception type.
- **LI_LAZY_INIT_STATIC** — Lazy initialization of a static field without synchronization.
- **NP_NULL_ON_SOME_PATH** — Possible null pointer dereference on some code path.
- **DM_CONVERT_CASE** — Using `String.toUpperCase()` or `toLowerCase()` without specifying a `Locale`.

For the full list, see the [SpotBugs Bug Descriptions](https://spotbugs.readthedocs.io/en/stable/bugDescriptions.html).

## Filtering False Positives

If SpotBugs reports false positives, you can suppress them:

### Per-method / per-class suppression

Add the `@SuppressFBWarnings` annotation (requires `spotbugs-annotations` dependency):

```java
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@SuppressFBWarnings(value = "DM_DEFAULT_ENCODING", justification = "Output is for local terminal display only")
public void myMethod() { ... }
```

### Project-wide exclusion filter

Create a `spotbugs-exclude.xml` file and reference it in `pom.xml`:

```xml
<configuration>
    <excludeFilterFile>spotbugs-exclude.xml</excludeFilterFile>
</configuration>
```

Example filter file:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<FindBugsFilter>
    <!-- Exclude all test classes -->
    <Match>
        <Class name="~.*Test" />
    </Match>
    <!-- Exclude specific bug pattern in a package -->
    <Match>
        <Package name="org.lucee.lucli.server" />
        <Bug pattern="REC_CATCH_EXCEPTION" />
    </Match>
</FindBugsFilter>
```

## Combining with Other Tools

SpotBugs is one part of the LuCLI quality toolchain:

- **JaCoCo** — code coverage (`mvn test` generates reports to `target/site/jacoco/`)
- **SpotBugs** — static bug detection (this page)
- **JUnit 5** — unit tests (`mvn test`)
- **`tests/test.sh`** — integration / shell-based test suite

## Troubleshooting

### "Unsupported class file major version 69"

You're running SpotBugs with the wrong Java version. LuCLI targets Java 21 — make sure your shell is using the correct JDK:

```bash
sdk env          # If using SDKMAN (reads .sdkmanrc)
java -version    # Should show 21.x
```

Then re-run SpotBugs.
