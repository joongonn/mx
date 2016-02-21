import sbt._
import Keys._
import com.typesafe.sbteclipse.plugin.EclipsePlugin._
import sbtassembly.Plugin._ 
import AssemblyKeys._ 

object Resolvers {
  val typesafeRepo      = "Typesafe Repository"    at "http://repo.typesafe.com/typesafe/releases/"
  val mavenRepo         = "Maven Repository"       at "http://repo1.maven.org/maven2/"
  val sonatypeRelease   = "Sonatype OSS Releases"  at "http://oss.sonatype.org/content/repositories/releases/"
  val sonatypeSnapshots = "Sonatype OSS Snapshots" at "http://oss.sonatype.org/content/repositories/snapshots/"

  val mxResolvers = Seq(typesafeRepo, mavenRepo, sonatypeRelease, sonatypeSnapshots)
}

object BuildSettings {
  val buildOrganization = "jng"
  val buildVersion      = "0.1"
  val buildScalaVersion = "2.10.4-RC2"

  val mxBuildSettings = Defaults.defaultSettings ++ Seq (
    organization := buildOrganization,
    version      := buildVersion,
    scalaVersion := buildScalaVersion,
    resolvers := Resolvers.mxResolvers, 
    unmanagedSourceDirectories in Compile := (scalaSource in Compile).value :: Nil,
    unmanagedSourceDirectories in Test    := Nil,
    unmanagedResourceDirectories in Compile := Nil,
    unmanagedResourceDirectories in Test    := Nil,
    EclipseKeys.createSrc  := EclipseCreateSrc.Default + EclipseCreateSrc.Resource,
    EclipseKeys.withSource := true
  )
}

object Dependencies {
  val slf4j          = "org.slf4j"                     % "slf4j-api"            % "1.6.6"
  val logback        = "ch.qos.logback"                % "logback-classic"      % "1.0.7"
  val netty          = "io.netty"                      % "netty-all"            % "4.0.14.Final"
  val typesafeConfig = "com.typesafe"                  % "config"               % "1.0.2"
  val c3p0           = "com.mchange"                   % "c3p0"                 % "0.9.5-pre6"
  val slick          = "com.typesafe.slick"           %% "slick"                % "1.0.1"
  val jacksonModule  = "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.3.1"
  val javamail       = "com.sun.mail"                  % "mailapi"              % "1.5.1"
  val jbcrypt        = "org.mindrot"                   % "jbcrypt"              % "0.3m"

  val h2SampleDB     = "com.h2database"                % "h2"                   % "1.3.172"
  val commonsDaemon  = "commons-daemon"                % "commons-daemon"       % "1.0.15"

  val specs2         = "org.specs2"                   %% "specs2"               % "2.3.6"        % "test"

}

object MxBuild extends Build {
  import Resolvers._
  import Dependencies._
  import BuildSettings._

  val mxCommonNetDeps   = Seq(slf4j, logback, netty)
  val mxDomainDeps      = Seq(slf4j, logback, slick, javamail, jbcrypt)
  val mxSmtpDeps        = Seq(slf4j, logback)
  val mxImapDeps        = Seq(slf4j, logback, specs2)
  val mxWebApiDeps      = Seq(slf4j, logback, jacksonModule)
  val mxRunnersDeps     = Seq(slf4j, logback, typesafeConfig, c3p0, commonsDaemon, h2SampleDB)

  lazy val mxDomain = Project(
    "mx-domain",
    file ("mx-domain"),
    settings = mxBuildSettings ++ Seq(libraryDependencies ++= mxDomainDeps)
  )

  lazy val mxCommonNet = Project(
    "mx-common-net",
    file ("mx-common/mx-common-net"),
    settings = mxBuildSettings ++ Seq(libraryDependencies ++= mxCommonNetDeps)
  )

  lazy val mxSmtp = Project(
    "mx-smtp",
    file ("mx-smtp"),
    settings = mxBuildSettings ++ Seq(libraryDependencies ++= mxSmtpDeps)
  ) dependsOn (mxCommonNet, mxDomain)

  lazy val mxImap = Project(
    "mx-imap",
    file ("mx-imap"),
    settings = mxBuildSettings
        ++ Seq(libraryDependencies ++= mxImapDeps)
        ++ Seq(unmanagedSourceDirectories in Test := (scalaSource in Test).value :: Nil)

  ) dependsOn (mxCommonNet, mxDomain)

  lazy val mxWebApi = Project(
    "mx-web-api",
    file ("mx-web-api"),
    settings = mxBuildSettings
        ++ Seq(unmanagedResourceDirectories in Compile := (resourceDirectory in Compile).value :: Nil)
        ++ Seq(libraryDependencies ++= mxWebApiDeps)
  ) dependsOn (mxCommonNet, mxDomain)

  val mxRunnersMergeStrategy = mergeStrategy in assembly <<= (mergeStrategy in assembly) { (old) => {
      case PathList("reference.conf") => MergeStrategy.discard
      case x => old(x) } }

  lazy val mxRunners = Project(
    "mx-runners",
    file ("mx-runners"),
    settings = mxBuildSettings 
        ++ Seq(unmanagedResourceDirectories in Compile := (resourceDirectory in Compile).value :: Nil)
        ++ Seq(libraryDependencies ++= mxRunnersDeps)
        ++ assemblySettings
        ++ Seq(mxRunnersMergeStrategy)
    ) dependsOn (mxSmtp, mxImap, mxWebApi)
}
