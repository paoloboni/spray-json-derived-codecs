import Dependencies._

name := "spray-json-derived-codecs"

lazy val scala211               = "2.11.12"
lazy val scala212               = "2.12.12"
lazy val scala213               = "2.13.4"
lazy val supportedScalaVersions = List(scala211, scala212, scala213)

ThisBuild / scalafmtOnCompile := false
ThisBuild / organization := "io.github.paoloboni"
ThisBuild / startYear := Some(2020)

lazy val root = (project in file("."))
  .settings(
    scalaVersion := scala213,
    releaseCrossBuild := true,
    releasePublishArtifactsAction := PgpKeys.publishSigned.value,
    crossScalaVersions := supportedScalaVersions,
    libraryDependencies ++= Seq(
      sprayJson,
      shapeless,
      scalacheckShapeless % Test,
      scalaTest           % Test,
      scalacheck          % Test
    )
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
