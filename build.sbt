import Dependencies._

name := "spray-json-derived-codecs"

lazy val scala212               = "2.12.14"
lazy val scala213               = "2.13.6"
lazy val scala3                 = "3.0.2"
lazy val supportedScalaVersions = List(scala212, scala213, scala3)

ThisBuild / scalafmtOnCompile := false
ThisBuild / organization      := "io.github.paoloboni"
ThisBuild / startYear         := Some(2020)

lazy val root = (project in file("."))
  .settings(
    scalaVersion                  := scala213,
    releaseCrossBuild             := true,
    releasePublishArtifactsAction := PgpKeys.publishSigned.value,
    crossScalaVersions            := supportedScalaVersions,
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
  .enablePlugins(AutomateHeaderPlugin)

import ReleaseTransformations._

releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  runTest,
  setReleaseVersion,
  commitReleaseVersion,
  tagRelease,
  publishArtifacts,
  setNextVersion,
  commitNextVersion,
  releaseStepCommand("sonatypeReleaseAll"),
  pushChanges
)
