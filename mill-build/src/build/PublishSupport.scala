package build

import mill.*
import mill.scalalib.JavaModule
import mill.scalalib.PublishModule
import mill.scalalib.SonatypeCentralPublishModule
import mill.scalalib.publish.{Developer, License, PomSettings, VersionControl}

trait PublishSupport extends PublishModule with SonatypeCentralPublishModule {
  this: JavaModule =>
  private val defaultPublishVersion = "0.1.0-SNAPSHOT"

  def publishVersion = Task.Input {
    sys.env.getOrElse("PUBLISH_VERSION", defaultPublishVersion)
  }

  def pomSettings = Task {
    PomSettings(
      description = "An Elm dialect parser and compiler toolchain for Scala.",
      organization = "io.eleven19.krueger",
      url = "https://github.com/Eleven19/krueger",
      licenses = Seq(License.`Apache-2.0`),
      versionControl = VersionControl.github("Eleven19", "krueger"),
      developers = Seq(
        Developer(
          id = "DamianReeves",
          name = "Damian Reeves",
          url = "https://github.com/DamianReeves"
        )
      )
    )
  }
}
