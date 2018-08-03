import Dependencies._

enablePlugins(JavaServerAppPackaging)
enablePlugins(DockerPlugin)
enablePlugins(DockerSpotifyClientPlugin)


lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      organization := "trood",
      scalaVersion := "2.11.8",
      version      := "0.1.0-SNAPSHOT"
    )),
    name := "crossover",
    libraryDependencies += scalaTest % Test,
    libraryDependencies += "io.druid" % "tranquility-core_2.11" % "0.8.2",
    libraryDependencies += "com.metamx" % "java-util" % "0.27.9",
    libraryDependencies += "com.typesafe" % "config" % "1.3.1",
    libraryDependencies += "com.rabbitmq" % "amqp-client" % "4.2.0",
    libraryDependencies += "com.typesafe.akka" %% "akka-http" % "10.0.10",
    libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.2.3",
    libraryDependencies += "org.apache.hadoop" % "hadoop-hdfs" % "2.8.0",
    libraryDependencies += "org.apache.hadoop" % "hadoop-common" % "2.8.0",
    libraryDependencies += "com.github.scopt" %% "scopt" % "3.6.0"
  )
