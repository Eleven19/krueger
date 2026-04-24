---
title: Installation
description: Add Krueger to your build.
---

## Requirements

- JDK 25+ (Temurin recommended)
- [Mill](https://mill-build.org/) 1.1.5+ (only if you build Krueger from source)
- For Scala Native: `clang`, `libunwind`, and a boehm-gc library (`libgc` on Linux)

## Add the dependency

### Mill

In Mill 1.1.5+, add the dependency inside a `package.mill` module's
`mvnDeps`:

```scala
def mvnDeps = Seq(
  mvn"io.eleven19.krueger::krueger-core:VERSION"
)
```

> Mill 1.x uses `mvn"…"` (the former `ivy"…"` macro was renamed in the
> 1.0 release) and loads build definitions from `package.mill` files —
> `build.sc` is no longer supported.

### sbt

```scala
"io.eleven19.krueger" %% "krueger-core" % "VERSION"
```

### Maven

```xml
<dependency>
  <groupId>io.eleven19.krueger</groupId>
  <artifactId>krueger-core_3</artifactId>
  <version>VERSION</version>
</dependency>
```

Replace `VERSION` with the latest release from
[Maven Central](https://central.sonatype.com/artifact/io.eleven19.krueger/krueger-core_3).
