import Dependencies._

name := "spray-json-derived-codecs"

inThisBuild(
  List(
    organization := "io.github.paoloboni",
    homepage     := Some(url("https://github.com/paoloboni/spray-json-derived-codecs")),
    licenses     := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
    developers := List(
      Developer(
        "paoloboni",
        "Paolo Boni",
        "boni.paolo@gmail.com",
        url("https://github.com/paoloboni")
      )
    ),
    startYear := Some(2020)
  )
)

lazy val scala212               = "2.12.15"
lazy val scala213               = "2.13.8"
lazy val scala3                 = "3.1.1"
lazy val supportedScalaVersions = List(scala212, scala213, scala3)

ThisBuild / scalafmtOnCompile := false

lazy val root = (project in file("."))
  .settings(
    scalaVersion       := scala213,
    crossScalaVersions := supportedScalaVersions,
    libraryDependencies ++= Seq(
      scalaTest % Test
    ) ++ (CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, _)) =>
        Seq(
          shapeless,
          sprayJson,
          scalacheckShapeless % Test,
          scalacheck_2        % Test
        )
      case _ =>
        Seq(
          sprayJson.cross(CrossVersion.for3Use2_13),
          scalacheck_3 % Test
        )
    })
  )
  .settings(scalacOptions -= "-Vimplicits")
  .enablePlugins(AutomateHeaderPlugin)
