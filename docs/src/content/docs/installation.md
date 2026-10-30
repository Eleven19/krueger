---
title: Installation
description: Add Krueger to your build.
---

## Requirements

- JDK 25+ (Temurin recommended)
- sbt 2.0.11, bootstrapped by `./sbt` when building from source
- For Scala Native: `clang`, `libunwind`, and a boehm-gc library (`libgc` on Linux)

## Add the dependency

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
