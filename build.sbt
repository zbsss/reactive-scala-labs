enablePlugins(GatlingPlugin)

name := "EShop"

version := "0.3"

scalaVersion := "2.13.6"

val akkaVersion     = "2.6.16"
val akkaHttpVersion = "10.2.6"

libraryDependencies ++= Seq(
  "com.typesafe.akka"        %% "akka-actor"                % akkaVersion,
  "com.typesafe.akka"        %% "akka-testkit"              % akkaVersion % "test",
  "com.typesafe.akka"        %% "akka-actor-typed"          % akkaVersion,
  "com.typesafe.akka"        %% "akka-actor-testkit-typed"  % akkaVersion % "test",
  "com.typesafe.akka"        %% "akka-persistence"          % akkaVersion,
  "com.typesafe.akka"        %% "akka-persistence-query"    % akkaVersion,
  "com.typesafe.akka"        %% "akka-persistence-typed"    % akkaVersion,
  "com.typesafe.akka"        %% "akka-persistence-testkit"  % akkaVersion % "test",
  "com.typesafe.akka"        %% "akka-cluster-typed"        % akkaVersion,
  "com.typesafe.akka"        %% "akka-http"                 % akkaHttpVersion,
  "com.typesafe.akka"        %% "akka-http-spray-json"      % akkaHttpVersion,
  "org.iq80.leveldb"          % "leveldb"                   % "0.12",
  "org.fusesource.leveldbjni" % "leveldbjni-all"            % "1.8",
  "com.github.dnvriend"      %% "akka-persistence-inmemory" % "2.5.15.2",
  "org.scalatest"            %% "scalatest"                 % "3.2.9"     % "test",
  "ch.qos.logback"            % "logback-classic"           % "1.2.6",
  "io.gatling"                % "gatling-http"              % "3.5.1"
)

libraryDependencies += "io.gatling.highcharts" % "gatling-charts-highcharts" % "3.5.1" % "test,it"
libraryDependencies += "io.gatling"            % "gatling-test-framework"    % "3.5.1" % "test,it"

// scalaFmt
scalafmtOnCompile := true

fork := true
