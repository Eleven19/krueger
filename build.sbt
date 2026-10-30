import org.scalajs.linker.interface.{ESVersion, ModuleKind}

scalaVersion := "3.9.0"
organization := "io.eleven19.krueger"

resolvers ++= Seq(
    "Central snapshots" at "https://central.sonatype.com/repository/maven-snapshots/",
    "Legacy snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/"
)
outputPath := s"${platform.value}/scala-${scalaVersion.value}/${thisProject.value.id}"
version    := sys.env.getOrElse("PUBLISH_VERSION", IO.read(file("VERSION")).trim + "-SNAPSHOT")

lazy val verifyTests = taskKey[Unit]("Fail when required tests are undiscovered")

verifyTests := Def.uncached {
    val suites = (Test / definedTests).value
    val id     = thisProject.value.id
    if (suites.isEmpty) sys.error(s"$id: no tests discovered")
    IO.delete(target.value / "test-reports")
    streams.value.log.info(s"$id: ${suites.size} test suites discovered")
}

lazy val verifyTestReports = taskKey[Unit]("Require fresh test events for every discovered suite")

verifyTestReports := Def.uncached {
    import scala.sys.process.*
    val suites = (Test / definedTests).value.map(_.name).sorted
    val root   = (LocalRootProject / baseDirectory).value
    val status = Process(
        Seq("node", "scripts/ci/verify-test-reports.mjs", (target.value / "test-reports").toString) ++ suites,
        root
    ).!
    if (status != 0) sys.error(s"${thisProject.value.id}: test report verification failed")
}

val kyoVersion = "1.0.0-RC7"

val sharedOptions = Seq(
    "-Wvalue-discard",
    "-Wnonunit-statement",
    "-Wconf:msg=(unused.*value|discarded.*value|pure.*statement):error",
    "-language:strictEquality",
    "-deprecation",
    "-feature",
    "-Werror"
)

def librarySettings(artifact: String, sourceRoot: String) = Seq(
    moduleName                             := artifact,
    Compile / unmanagedSourceDirectories   := Seq((LocalRootProject / baseDirectory).value / sourceRoot / "src"),
    Compile / unmanagedResourceDirectories := Seq((LocalRootProject / baseDirectory).value / sourceRoot / "resources"),
    Test / unmanagedSourceDirectories := Seq((LocalRootProject / baseDirectory).value / sourceRoot / "test" / "src"),
    Test / unmanagedResourceDirectories := Seq(
        (LocalRootProject / baseDirectory).value / sourceRoot / "test" / "resources"
    ),
    scalacOptions ++= sharedOptions,
    libraryDependencies ++= Seq(
        "io.getkyo" %% "kyo-core"        % kyoVersion,
        "io.getkyo" %% "kyo-prelude"     % kyoVersion,
        "io.getkyo" %% "kyo-test-api"    % kyoVersion % Test,
        "io.getkyo" %% "kyo-test-runner" % kyoVersion % Test
    ),
    testFrameworks := Seq(new TestFramework("kyo.test.runner.SbtFramework")),
    Test / fork    := true,
    Test / javaOptions ++= Seq("--add-opens", "java.base/java.lang=ALL-UNNAMED")
)

val jsSettings = Seq(
    testFrameworks := Seq(new TestFramework("kyo.test.runner.JsFramework")),
    Test / fork    := false
)

val nativeSettings = Seq(
    testFrameworks := Seq(new TestFramework("kyo.test.runner.NativeFramework")),
    Test / fork    := false
)

val wasmSettings = jsSettings ++ Seq(
    jsEnv := Def.uncached {
        new org.scalajs.jsenv.nodejs.NodeJSEnv(
            org.scalajs.jsenv.nodejs.NodeJSEnv.Config().withArgs(List("--experimental-wasm-exnref"))
        )
    },
    scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.ESModule)
        .withESFeatures(_.withESVersion(ESVersion.ES2022).withUseWebAssembly(true)))
)

val treesSettings = librarySettings("krueger-trees", "krueger/trees") ++ Seq(
    libraryDependencies ++= Seq(
        "com.github.j-mie6"    %% "parsley"                    % "4.6.2",
        "io.github.kitlangton" %% "neotype"                    % "0.4.10",
        "com.kubuszok"         %% "kindlings-fast-show-pretty" % "0.1.0"
    )
)

val coreSettings = librarySettings("krueger-core", "krueger/core") ++ Seq(
    libraryDependencies ++= Seq(
        "com.github.j-mie6"    %% "parsley" % "4.6.2",
        "io.github.kitlangton" %% "neotype" % "0.4.10",
        "com.outr"             %% "scribe"  % "3.16.1"
    )
)

val compilerSettings = librarySettings("krueger-compiler-api", "krueger/compiler-api") ++ Seq(
    libraryDependencies ++= Seq(
        "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-core"   % "2.38.9",
        "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-macros" % "2.38.9"
    )
)

val webappSettings = librarySettings("krueger-webapp", "krueger/webapp-wasm") ++ Seq(
    publish / skip := true,
    scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.ESModule))
)

lazy val treesJvm = project.in(file("krueger/trees/jvm")).settings(treesSettings)
lazy val treesJs = project.in(file("krueger/trees/js")).enablePlugins(ScalaJSPlugin).settings(treesSettings, jsSettings)

lazy val treesNative =
    project.in(file("krueger/trees/native")).enablePlugins(ScalaNativePlugin).settings(treesSettings, nativeSettings)
lazy val coreJvm = project.in(file("krueger/core/jvm")).dependsOn(treesJvm).settings(coreSettings)

lazy val coreJs = project
    .in(file("krueger/core/js"))
    .enablePlugins(ScalaJSPlugin)
    .dependsOn(treesJs)
    .settings(coreSettings, jsSettings)

lazy val coreNative = project
    .in(file("krueger/core/native"))
    .enablePlugins(ScalaNativePlugin)
    .dependsOn(treesNative)
    .settings(coreSettings, nativeSettings)

lazy val compilerApiJvm =
    project.in(file("krueger/compiler-api/jvm")).dependsOn(coreJvm, treesJvm).settings(compilerSettings)

lazy val compilerApiJs = project
    .in(file("krueger/compiler-api/js"))
    .enablePlugins(ScalaJSPlugin)
    .dependsOn(coreJs, treesJs)
    .settings(compilerSettings, jsSettings)

lazy val compilerApiNative = project
    .in(file("krueger/compiler-api/native"))
    .enablePlugins(ScalaNativePlugin)
    .dependsOn(coreNative, treesNative)
    .settings(compilerSettings, nativeSettings)

// WASM and JS library IR have the same Maven coordinate; publish that IR once via compilerApiJs.
lazy val compilerApiWasm = project
    .in(file("krueger/compiler-api/wasm"))
    .enablePlugins(ScalaJSPlugin)
    .dependsOn(coreJs, treesJs)
    .settings(compilerSettings, wasmSettings)
    .settings(
        publish / skip                            := true,
        Compile / scalaJSUseMainModuleInitializer := true,
        Compile / mainClass                       := Some("io.eleven19.krueger.compiler.wasm.Main"),
        Compile / fullLinkJS / scalaJSLinkerOutputDirectory := rootOutputDirectory.value
            .resolve("linked/compiler-api-wasm")
            .toFile()
    )

lazy val webappJs = project
    .in(file("krueger/webapp-wasm/js"))
    .enablePlugins(ScalaJSPlugin)
    .dependsOn(compilerApiJs)
    .settings(webappSettings, jsSettings)
    .settings(
        Compile / fullLinkJS / scalaJSLinkerOutputDirectory := rootOutputDirectory.value
            .resolve("linked/webapp-js")
            .toFile()
    )

lazy val webappWasm = project
    .in(file("krueger/webapp-wasm/wasm"))
    .enablePlugins(ScalaJSPlugin)
    .dependsOn(compilerApiJs)
    .settings(webappSettings, wasmSettings)
    .settings(
        Compile / unmanagedSourceDirectories += (LocalRootProject / baseDirectory).value / "krueger/webapp-wasm/wasm/src",
        Compile / fullLinkJS / scalaJSLinkerOutputDirectory := rootOutputDirectory.value
            .resolve("linked/webapp-wasm")
            .toFile()
    )

lazy val umbrella = project
    .in(file("krueger"))
    .dependsOn(coreJvm)
    .settings(
        moduleName                           := "krueger",
        Compile / unmanagedSourceDirectories := Seq.empty,
        Test / unmanagedSourceDirectories    := Seq.empty
    )

lazy val integration = project
    .in(file("krueger/itest"))
    .dependsOn(coreJvm, compilerApiJvm)
    .settings(
        publish / skip                       := true,
        Compile / unmanagedSourceDirectories := Seq.empty,
        Test / unmanagedSourceDirectories    := Seq(baseDirectory.value / "src"),
        Test / unmanagedResourceDirectories  := Seq(baseDirectory.value / "resources"),
        Test / fork                          := true,
        Test / javaOptions ++= {
            val wasm   = (compilerApiWasm / Compile / fullLinkJSOutput).value
            val facade = (webappJs / Compile / fullLinkJSOutput).value
            Seq(
                "--add-opens",
                "java.base/java.lang=ALL-UNNAMED",
                s"-Dkrueger.compiler-api.wasm.dir=$wasm",
                s"-Dkrueger.webapp-wasm.facade.dir=$facade"
            )
        },
        scalacOptions ++= sharedOptions,
        libraryDependencies ++= Seq(
            "org.scalameta"       %% "munit"                          % "1.0.4"  % Test,
            "com.dylibso.chicory"  % "runtime"                        % "1.7.5"  % Test,
            "com.dylibso.chicory"  % "wabt"                           % "1.7.5"  % Test,
            "io.cucumber"         %% "cucumber-scala"                 % "8.39.1" % Test,
            "io.cucumber"          % "cucumber-picocontainer"         % "7.20.1" % Test,
            "io.cucumber"          % "cucumber-junit-platform-engine" % "7.20.1" % Test,
            "org.junit.platform"   % "junit-platform-suite"           % "1.14.0" % Test,
            "org.junit.platform"   % "junit-platform-suite-engine"    % "1.14.0" % Test,
            "com.github.sbt.junit" % "jupiter-interface"              % "0.15.2" % Test
        )
    )

lazy val root = project
    .in(file("."))
    .aggregate(
        treesJvm,
        treesJs,
        treesNative,
        coreJvm,
        coreJs,
        coreNative,
        compilerApiJvm,
        compilerApiJs,
        compilerApiNative,
        compilerApiWasm,
        webappJs,
        webappWasm,
        umbrella,
        integration
    )
    .settings(publish / skip := true, Compile / unmanagedSourceDirectories := Seq.empty)

lazy val stageWasm           = taskKey[Unit]("Link and stage both playground backends")
lazy val apiDocs             = taskKey[Unit]("Build and stage combined API documentation")
lazy val site                = taskKey[Unit]("Build the complete Pages site")
lazy val prepareLocalDevSite = taskKey[Unit]("Prepare the playground for Astro dev")

def nodeSite(root: File, args: String*): Unit = {
    import scala.sys.process.*
    val status = Process(Seq("node", "scripts/build/site-cli.mjs") ++ args, root).!
    if (status != 0) sys.error(s"Site operation ${args.head} failed ($status)")
}

stageWasm := Def.uncached {
    val facade = (webappJs / Compile / fullLinkJSOutput).value
    val wasm   = (webappWasm / Compile / fullLinkJSOutput).value
    nodeSite(baseDirectory.value, "stage-wasm", facade.toString, wasm.toString)
}

def apiSettings(sourceProjects: Seq[ProjectReference]) = Seq(
    publish / skip                       := true,
    Compile / unmanagedSourceDirectories := Seq.empty,
    Test / unmanagedSourceDirectories    := Seq.empty,
    Compile / sources                    := sourceProjects.map(p => p / Compile / sources).join.value.flatten,
    Compile / doc / scalacOptions := sharedOptions.filterNot(_ == "-Werror") ++ Seq(
        "-project",
        "Krueger",
        "-project-version",
        IO.read((LocalRootProject / baseDirectory).value / "VERSION").trim,
        "-source-links:github://Eleven19/krueger",
        "-revision",
        "main",
        "-social-links:github::https://github.com/Eleven19/krueger",
        "-snippet-compiler:nocompile",
        "-external-mappings:.*parsley.*::scaladoc3::https://javadoc.io/doc/com.github.j-mie6/parsley_3/latest/,.*neotype.*::scaladoc3::https://javadoc.io/doc/io.github.kitlangton/neotype_3/latest/"
    )
)

lazy val apiJvm = project
    .in(file("target/projects/apiJvm"))
    .dependsOn(coreJvm, treesJvm)
    .settings(apiSettings(Seq(coreJvm, treesJvm)))

lazy val apiJs = project
    .in(file("target/projects/apiJs"))
    .enablePlugins(ScalaJSPlugin)
    .dependsOn(coreJs, treesJs)
    .settings(apiSettings(Seq(coreJs, treesJs)))

lazy val apiNative = project
    .in(file("target/projects/apiNative"))
    .enablePlugins(ScalaNativePlugin)
    .dependsOn(coreNative, treesNative)
    .settings(apiSettings(Seq(coreNative, treesNative)))

apiDocs := Def.uncached {
    val jvm    = (apiJvm / Compile / doc).value.toString
    val js     = (apiJs / Compile / doc).value.toString
    val native = (apiNative / Compile / doc).value.toString
    nodeSite(baseDirectory.value, "stage-api", jvm, js, native)
}

site := Def.uncached {
    apiDocs.value
    stageWasm.value
    nodeSite(baseDirectory.value, "site")
}

prepareLocalDevSite := Def.uncached {
    apiDocs.value
    stageWasm.value
    nodeSite(baseDirectory.value, "prepare")
}

// Preserve the published Mill coordinates and POM metadata.
description := "An Elm dialect parser and compiler toolchain for Scala."
homepage    := Some(uri("https://github.com/Eleven19/krueger"))
licenses    := List(License.Apache2)

scmInfo := Some(
    ScmInfo(
        uri("https://github.com/Eleven19/krueger"),
        "scm:git:https://github.com/Eleven19/krueger.git",
        Some("scm:git:git@github.com:Eleven19/krueger.git")
    )
)
developers           := List(Developer("DamianReeves", "Damian Reeves", "", uri("https://github.com/DamianReeves")))
publishMavenStyle    := true
pomIncludeRepository := (_ => false)

publishTo := {
    if (version.value.endsWith("-SNAPSHOT"))
        Some("central-snapshots" at "https://central.sonatype.com/repository/maven-snapshots/")
    else localStaging.value
}
pgpPassphrase := Def.uncached(sys.env.get("PGP_PASSPHRASE").map(_.toCharArray))

stageWasm / aggregate           := false
apiDocs / aggregate             := false
site / aggregate                := false
prepareLocalDevSite / aggregate := false

addCommandAlias(
    "testJvmFull",
    ";treesJvm/verifyTests;coreJvm/verifyTests;compilerApiJvm/verifyTests;treesJvm/Test/testFull;coreJvm/Test/testFull;compilerApiJvm/Test/testFull;treesJvm/verifyTestReports;coreJvm/verifyTestReports;compilerApiJvm/verifyTestReports"
)

addCommandAlias(
    "testJsFull",
    ";treesJs/verifyTests;coreJs/verifyTests;compilerApiJs/verifyTests;webappJs/verifyTests;treesJs/Test/testFull;coreJs/Test/testFull;compilerApiJs/Test/testFull;webappJs/Test/testFull;treesJs/verifyTestReports;coreJs/verifyTestReports;compilerApiJs/verifyTestReports;webappJs/verifyTestReports"
)

addCommandAlias(
    "testNativeFull",
    ";treesNative/verifyTests;coreNative/verifyTests;compilerApiNative/verifyTests;treesNative/Test/testFull;coreNative/Test/testFull;compilerApiNative/Test/testFull;treesNative/verifyTestReports;coreNative/verifyTestReports;compilerApiNative/verifyTestReports"
)

addCommandAlias(
    "testWasmFull",
    ";compilerApiWasm/verifyTests;webappWasm/verifyTests;compilerApiWasm/Test/testFull;webappWasm/Test/testFull;compilerApiWasm/verifyTestReports;webappWasm/verifyTestReports"
)

addCommandAlias(
    "testIntegrationFull",
    ";stageWasm;integration/verifyTests;integration/Test/testFull;integration/verifyTestReports"
)
addCommandAlias("qualifyAll", ";testJvmFull;testJsFull;testNativeFull;testWasmFull;testIntegrationFull")

addCommandAlias("compileAll", ";compile;Test/compile;apiJvm/compile;apiJs/compile;apiNative/compile")
