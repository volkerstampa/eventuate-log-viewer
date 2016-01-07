package com.github.volkerstampa.eventuate

import java.time.Instant

import akka.actor.ActorPath
import akka.actor.ActorSelection
import akka.actor.ActorSystem
import akka.actor.ExtendedActorSystem
import akka.util.Timeout
import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.beust.jcommander.ParameterException
import com.rbmhtechnology.eventuate.DurableEvent
import com.rbmhtechnology.eventuate.ReplicationConnection
import com.rbmhtechnology.eventuate.ReplicationConnection.DefaultRemoteSystemName
import com.rbmhtechnology.eventuate.ReplicationEndpoint.DefaultLogName
import com.rbmhtechnology.eventuate.ReplicationFilter.NoFilter
import com.rbmhtechnology.eventuate.ReplicationProtocol.ReplicationReadEnvelope
import com.rbmhtechnology.eventuate.ReplicationProtocol.ReplicationRead
import com.rbmhtechnology.eventuate.ReplicationProtocol.ReplicationReadSuccess
import com.rbmhtechnology.eventuate.VectorTime
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

import scala.util.Failure
import scala.util.Success
import scala.concurrent.duration.DurationInt
import scala.collection.JavaConverters.mapAsJavaMapConverter
import akka.pattern.ask

import scala.util.Try

object SimpleLogViewer extends App {

  val params: LogViewerParameters = parseCommandLineArgs(args)
  import params._

  implicit val system: ActorSystem = ActorSystem(
    ReplicationConnection.DefaultRemoteSystemName,
    ConfigFactory.parseMap(
      Map[String, Any](
        "akka.remote.netty.tcp.hostname" -> localAddress,
        "akka.remote.netty.tcp.port" -> localPort,
        "akka.actor.provider" -> "akka.remote.RemoteActorRefProvider",
        "akka.loglevel" -> "WARNING"
      ).asJava)
      .withFallback(ConfigFactory.load)
  )

  val acceptor: ActorSelection = system.actorSelection(remoteActorPath(
    akkaProtocol(system),
    ReplicationConnection(remoteHost, remotePort, remoteSystemName),
    "acceptor"))

  replicateEventsAndDo(acceptor, logName, fromSequenceNo, maxEvents, batchSize)
    { event =>
      println(s"${event.localSequenceNr} ${Instant.ofEpochMilli(event.systemTimestamp)}: $event")
    }
    { _.printStackTrace() }
    { system.terminate() }

  def replicateEventsAndDo(acceptor: ActorSelection, logName: String, fromSequenceNo: Long, maxEvents: Long, batchSize: Int)
      (handleEvent: DurableEvent => Unit)(handleFailure: Throwable => Unit)(terminate: => Unit)
      (implicit actorSystem: ActorSystem): Unit = {

    import system.dispatcher
    implicit val timeout = Timeout(15.seconds)

    def replicate(fromSeqNo: Long, replicationCnt: Long): Unit = {
      val replicationCommand = new ReplicationReadEnvelope(
        new ReplicationRead(
          fromSeqNo,
          batchSize.toLong.min(maxEvents - replicationCnt).toInt,
          NoFilter, "", system.deadLetters, new VectorTime()
        ),
        logName
      )
      (acceptor ? replicationCommand).onComplete(handleResponse(replicationCnt))
    }

    def handleResponse(replicationCnt: Long): PartialFunction[Try[Any], Unit] = {
      case Success(ReplicationReadSuccess(events, progresss, _, _)) =>
        events.foreach(handleEvent)
        val newReplicationCnt = replicationCnt + events.size
        if (newReplicationCnt < maxEvents && events.nonEmpty)
          replicate(progresss + 1, newReplicationCnt)
        else
          terminate
      case Failure(ex) =>
        handleFailure(ex)
        terminate
      case response =>
        handleFailure(new IllegalStateException(s"Unhandled response: $response"))
        terminate
    }

    replicate(fromSequenceNo, 0)
  }

  def remoteActorPath(protocol: String, connectionInfo: ReplicationConnection, actorName: String): ActorPath =
    ActorPath.fromString(s"$protocol://${connectionInfo.name}@${connectionInfo.host}:${connectionInfo.port}/user/$actorName")

  /**
    * Return the protocol used by the given ActorSystem,
    * if the ActorSystem is an ExtendedActorSystem and "akka.tcp" as default otherwise.
    */
  def akkaProtocol(system: ActorSystem): String = system match {
    case sys: ExtendedActorSystem => sys.provider.getDefaultAddress.protocol
    case _ => "akka.tcp"
  }

  class LogViewerParameters {

    private val config: Config = ConfigFactory.load()

    @Parameter(
      names = Array("--help", "-h"),
      description = "Display this help-message and exit",
      help = true)
    var help: Boolean = false

    @Parameter(
      names = Array("--systemName", "-n"),
      description = "Name of the akka-system of the eventuate application"
    )
    var remoteSystemName: String = DefaultRemoteSystemName

    @Parameter(
      names = Array("--remoteHost", "-rh"),
      description = "Remote host running the eventuate application"
    )
    var remoteHost: String = "localhost"

    @Parameter(
      names = Array("--logName", "-log"),
      description = "Name of the log to be viewed"
    )
    var logName: String = DefaultLogName

    @Parameter(
      names = Array("--remotePort", "-r"),
      description = "akka-port of the remote host"
    )
    var remotePort: Int = config.getInt("akka.remote.netty.tcp.port")

    @Parameter(
      names = Array("--localPort", "-l"),
      description = "akka-port of the log-viewer"
    )
    var localPort: Int = 12552

    @Parameter(
      names = Array("--localBindAddress", "-lh"),
      description = "akka-bind-address of the log-viewer"
    )
    var localAddress: String = ""

    @Parameter(
      names = Array("--fromSeqNo", "-f"),
      description = "from sequence number"
    )
    var fromSequenceNo: Long = 0

    @Parameter(
      names = Array("--maxEvents", "-m"),
      description = "maximal number of events to view (default: all)"
    )
    var maxEvents: Long = Long.MaxValue

    @Parameter(
      names = Array("--batchSize", "-b"),
      description = "maximal number of events to replicate at once"
    )
    var batchSize: Int = config.getInt("eventuate.log.write-batch-size")
  }

  private def parseCommandLineArgs(args: Array[String]): LogViewerParameters = {
    val jCommander = new JCommander()
    val params = new LogViewerParameters()
    jCommander.addObject(params)
    try {
      jCommander.parse(args: _*)
      if(params.help) {
        jCommander.usage()
        sys.exit()
      }
      params
    } catch {
      case ex: ParameterException =>
        println(ex.getMessage)
        jCommander.usage()
        sys.exit(1)
    }
  }
}
