package build

import mill.*
import mill.scalalib.*
import mill.scalajslib.*
import mill.scalanativelib.*

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
}

trait CommonScalaTestModule extends ScalaModule with scalafmt.ScalafmtModule

trait CommonScalaJSModule extends ScalaJSModule with scalafmt.ScalafmtModule {
  def scalaJSVersion = "1.20.1"
}

/** Scala.js module variant that emits a Wasm GC module instead of plain JS.
  *
  * The experimental WebAssembly backend requires `ModuleKind.ESModule` and the default `ModuleSplitStyle.FewestModules`;
  * both are set here so callers only need to mix this trait in. Output is loadable in Chrome 119+, Firefox 120+, and
  * Safari 18.2+ (browsers with Wasm GC).
  *
  * @note
  *   The WebAssembly backend silently ignores `@JSExport` and `@JSExportAll`. FFI surfaces that need JS exports should
  *   use a conventional JS variant; WASM variants expose behavior through explicit ES-module entry points instead.
  */
trait CommonScalaJSWasmModule extends CommonScalaJSModule {
  override def scalaJSExperimentalUseWebAssembly: T[Boolean] = Task { true }
  override def moduleKind: T[mill.scalajslib.api.ModuleKind] =
    Task { mill.scalajslib.api.ModuleKind.ESModule }
}

trait CommonScalaNativeModule extends ScalaNativeModule with scalafmt.ScalafmtModule {
  def scalaNativeVersion = "0.5.11"
}
