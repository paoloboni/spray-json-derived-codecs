import sbt._

object Dependencies {
  lazy val shapeless           = "com.chuusai"                %% "shapeless"                 % "2.4.0-M1"
  lazy val sprayJson           = "io.spray"                   %% "spray-json"                % "1.3.6"
  lazy val scalacheckShapeless = "com.github.alexarchambault" %% "scalacheck-shapeless_1.14" % "1.2.5"
  lazy val scalaTest           = "org.scalatest"              %% "scalatest"                 % "3.2.9"
  lazy val scalacheck_2        = "org.scalatestplus"          %% "scalacheck-1-14"           % "3.2.2.0"
  lazy val scalacheck_3        = "org.scalatestplus"          %% "scalacheck-1-15"           % "3.2.10.0"
}
