package build

import mill.*
import mill.scalalib.*
import mill.scalajslib.*
import mill.scalanativelib.*
import coursier.maven.MavenRepository

object KruegerVersions:
    /** Minimum required Kyo version. Later snapshot or stable releases are acceptable.
      * Lower bound exists because this build introduced kyo-schema on the Kyo mainline.
      */
    val Kyo: String = "1.0.0-RC5"

    /** Pinned scribe version for cross-platform logging (JVM + JS + Native). */
    val Scribe: String = "3.16.1"

trait CommonScalaModule extends ScalaModule with scalafmt.ScalafmtModule {
  override def scalaVersion = Task {
    "3.8.3"
  }

  override def scalacOptions = Task {
    Seq(
      "-Wvalue-discard",
      "-Wnonunit-statement",
      "-Wconf:msg=(unused.*value|discarded.*value|pure.*statement):error",
      "-language:strictEquality",
      "-deprecation",
      "-feature",
      "-Werror"
    )
  }

  override def repositoriesTask: Task[Seq[coursier.Repository]] = Task.Anon {
    super.repositoriesTask() ++ Seq(
      MavenRepository("https://central.sonatype.com/repository/maven-snapshots/"),
      MavenRepository("https://oss.sonatype.org/content/repositories/snapshots/")
    )
  }
}

trait CommonScalaTestModule extends ScalaModule with scalafmt.ScalafmtModule

/** Mill-native kyo-test wiring.
  *
  * Kyo ships no built-in Mill test module, so we wire the framework class and
  * mandatory dependencies here for each platform.
  */
trait KyoTestModule extends TestModule {
  def kyoVersion: T[String] = Task { KruegerVersions.Kyo }

  override def testFramework: T[String] = "kyo.test.runner.SbtFramework"

  override def forkArgs: T[Seq[String]] = Task {
    super.forkArgs() ++ Seq("--add-opens", "java.base/java.lang=ALL-UNNAMED")
  }

  override def mandatoryMvnDeps: T[Seq[Dep]] = Task {
    super.mandatoryMvnDeps() ++ Seq(
      mvn"io.getkyo::kyo-core::${kyoVersion()}",
      mvn"io.getkyo::kyo-test-api::${kyoVersion()}",
      mvn"io.getkyo::kyo-test-runner::${kyoVersion()}"
    )
  }
}

trait KyoTestJSModule extends KyoTestModule {
  override def testFramework: T[String] = "kyo.test.runner.JsFramework"
}

trait KyoTestNativeModule extends KyoTestModule {
  override def testFramework: T[String] = "kyo.test.runner.NativeFramework"
}

trait KyoTestWasmModule extends KyoTestJSModule

trait CommonScalaJSModule extends ScalaJSModule with scalafmt.ScalafmtModule {
  def scalaJSVersion = "1.22.0"
}

/** Scala.js module variant that emits a Wasm GC module instead of plain JS.
  *
  * The experimental WebAssembly backend requires `ModuleKind.ESModule` and the default `ModuleSplitStyle.FewestModules`;
  * both are set here so callers only need to mix this trait in. Output is loadable in Chrome 119+, Firefox 120+, and
  * Safari 18.2+ (browsers with Wasm GC).
  *
  * @note
  *   The WebAssembly backend treats `@JSExport*` annotations differently from the JS linker:
  *   - `@JSExport` on object/class members is silently dropped — those members are not callable from JS.
  *   - `@JSExportTopLevel` on a top-level **`val`** IS honored: the linker generates an import-callback that
  *     populates the named ES-module export with the val's value. So FFI surfaces that need a populated namespace
  *     should expose it as `@JSExportTopLevel("Name") val foo: js.Object = js.Dynamic.literal(...)` rather than as an
  *     `@JSExportTopLevel object` (the latter produces an empty namespace under the Wasm linker).
  *   - `@JSExportTopLevel` on an `object` produces the ES-module export but with no members, since the per-member
  *     `@JSExport` annotations are dropped.
  *
  *   The `webapp-wasm.wasm` submodule's `WasmFacade` uses the val pattern; the JS-linked `webapp-wasm` module reuses
  *   the conventional `@JSExportTopLevel object` pattern under the JS linker, where it works as expected.
  */
trait CommonScalaJSWasmModule extends CommonScalaJSModule {
  override def scalaJSExperimentalUseWebAssembly: T[Boolean] = Task { true }
  override def moduleKind: T[mill.scalajslib.api.ModuleKind] =
    Task { mill.scalajslib.api.ModuleKind.ESModule }
}

trait CommonScalaNativeModule extends ScalaNativeModule with scalafmt.ScalafmtModule {
  def scalaNativeVersion = "0.5.11"
}
