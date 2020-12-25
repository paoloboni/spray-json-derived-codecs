import java.time.Year

lazy val contributors = Seq(
  "paoloboni" -> "Paolo Boni"
)

pgpSecretRing := pgpPublicRing.value

publishTo := sonatypePublishTo.value

sonatypeProfileName := "io.github.paoloboni"
publishMavenStyle := true
pomExtra := {
  <developers>
    {
    for ((username, name) <- contributors)
      yield <developer>
    <id>{username}</id>
    <name>{name}</name>
    <url>http://github.com/{username}</url>
  </developer>
  }
  </developers>
}
scmInfo := Some(
  ScmInfo(
    url("https://github.com/paoloboni/spray-json-derived-codecs"),
    "scm:git@github.com:paoloboni/spray-json-derived-codecs.git"
  )
)
headerLicense := Some(HeaderLicense.ALv2(Year.now().getValue.toString, "Paolo Boni"))
licenses := Seq("Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0.txt"))
homepage := Some(url("https://github.com/paoloboni/spray-json-derived-codecs"))
