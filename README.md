Simple event log viewer for eventuate
=====================================

SimpleLogViewer is a minimalistic scala-application for the command-line that can be used to view the
content of an [eventuate](https://github.com/RBMHTechnology/eventuate) event log. It connects remotely to a running
eventuate-based application to retrieve 
[`DurableEvent`s](http://rbmhtechnology.github.io/eventuate/latest/api/index.html#com.rbmhtechnology.eventuate.DurableEvent)
of a given span of sequence numbers and simply prints their `toString` representation to stdout.

Usage
-----
SimpleLogViewer uses
the [`ReplicationProtocol`](http://rbmhtechnology.github.io/eventuate/latest/api/index.html#com.rbmhtechnology.eventuate.ReplicationProtocol$)
to communicate with the running eventuate-based application, so it basically looks like just another replication-client.
To enable SimpleLogViewer to deserialize the 
[payload](http://rbmhtechnology.github.io/eventuate/latest/api/index.html#com.rbmhtechnology.eventuate.DurableEvent@payload:Any)
of a replicated event it needs to have access to the class-definitions of corresponding application specific classes
and their [custom serializers](http://rbmhtechnology.github.io/eventuate/reference/event-sourcing.html#custom-event-serialization) 
(including the corresponding akka-configuration for custom serializers). There are several options on 
how to customize the classpath depending on how SimpleLogViewer is started.

### Start through sbt

When you start SimpleLogViewer through sbt (i.e. from the source-tree with `sbt run`), you have two options to customize the classpath:

0. Modify `build.sbt` and include your dependencies.
0. Drop the jar-files containing your classes in the `lib` folder. As the `lib` folder is explicitly excluded from
   git-management, this will keep your working directory clean.
   
If the customized classpath does not contain a `reference.conf` or `application.conf` file containing the
configuration for the custom serializers, you can provide a corresponding file through the system property 
[`config.file`](https://github.com/typesafehub/config#standard-behavior). You have once again two options for this:

0. Add `javaOptions += "-Dconfig.file=..."` to `build.sbt`.
0. Call sbt with an additional first argument: `sbt 'set javaOptions += "-Dconfig.file=..."' "run <log-viewer-options>"`.

### Start the packaged java-application SimpleLogViewer

SimpleLogViewer uses [sbt-native-packager](https://github.com/sbt/sbt-native-packager) for packaging
the application into a distributable artifact. You can for example use `sbt universal:packageBin` to 
create a zip-file containing the application. This *universal artifact* contains a `bin` folder with
scripts for starting the application (`SimpleLogViewer`) and a `lib` folder with all required jars and an empty `ext` folder.
To _install_ the application, you can unzip the archive anywhere.
There are again two options to customize the classpath:

0. Modify `build.sbt` by including your dependencies **before** you build your application package.
0. Drop the required *custom* jar-files in the `ext` folder.
   
To ensure that the configuration for the custom serializers is used a corresponding configuration file can
be specified on command-line: `SimpleLogViewer -Dconfig.file=... <log-viewer-options>`

### Command line arguments

SimpleLogViewer comes with a usage page when called with command line option `-h` or `--help`. When
called through the script generated by the sbt-native-packager, you have to use `--help` or *escape* `-h` 
to avoid that it is interpreted by the logic provided by the sbt-native-packager: `SimpleLogViewer -- -h`:

0. sbt: `sbt "run <options>"`
0. Packaged: `SimpleLogViewer <options>`

In both cases the options are:

```
  --batchSize, -b
     maximal number of events to replicate at once
     Default: 512
  --eventFormat, -e
     format string for the event
     Default: %(localSequenceNr)s %(systemTimestamp)tFT%(systemTimestamp)tT.%(systemTimestamp)tL %(this)s
  --fromSeqNo, -f
     from sequence number
     Default: 0
  --help, -h
     Display this help-message and exit
  --localBindAddress, -lh
     akka-bind-address of the log-viewer
     Default: <empty string>
  --localPort, -l
     akka-port of the log-viewer
     Default: 12552
  --logName, -log
     Name of the log to be viewed
     Default: default
  --maxEvents, -m
     maximal number of events to view (default: all)
     Default: 9223372036854775807
  --remoteHost, -rh
     Remote host running the eventuate application
     Default: localhost
  --remotePort, -r
     akka-port of the remote host
     Default: 2552
  --systemName, -n
     Name of the akka-system of the eventuate application
     Default: location
```
