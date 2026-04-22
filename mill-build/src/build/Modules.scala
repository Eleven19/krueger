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

trait CommonScalaNativeModule extends ScalaNativeModule with scalafmt.ScalafmtModule {
  def scalaNativeVersion = "0.5.11"
}
