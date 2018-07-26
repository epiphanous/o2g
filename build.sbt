name := "o2g"
organization := "io.epiphanous"
version := "1.0.2"
scalaVersion := "2.12.6"

val v = {
  object versions {
    val rdf4j = "2.3.2"
    val scopt = "3.7.0"
    val logback = "1.2.3"
    val logging = "3.9.0"
    val jsoup = "1.11.3"
    val remark = "1.1.0"
    val xml = "1.1.0"
    val config = "1.3.3"
  }
  versions
}

libraryDependencies += "org.eclipse.rdf4j" % "rdf4j-runtime" % v.rdf4j

libraryDependencies += "com.github.scopt" %% "scopt" % v.scopt

libraryDependencies += "org.jsoup" % "jsoup" % v.jsoup

libraryDependencies += "com.overzealous" % "remark" % v.remark

libraryDependencies += "org.scala-lang.modules" %% "scala-xml" % v.xml

libraryDependencies += "com.typesafe" % "config" % v.config

libraryDependencies ++= Seq(
  "com.typesafe.scala-logging" %% "scala-logging"                 % v.logging,
  "ch.qos.logback"             %  "logback-classic"               % v.logback
)

lazy val root = (project in file(".")).
  enablePlugins(BuildInfoPlugin).
  settings(
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
    buildInfoPackage := "io.epiphanous.semantic.o2g"
  )


assembly / test := {}

assembly / assemblyMergeStrategy := {
  case PathList("org", "joda", "time", "base", "BaseDateTime.class") => MergeStrategy.first
  case x =>
    val oldStrategy = (assembly / assemblyMergeStrategy).value
    oldStrategy(x)
}

