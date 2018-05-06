package trood.crossover.flow

import akka.actor.{ActorRef, FSM, Props}
import org.apache.hadoop.hdfs.protocol.RecoveryInProgressException
import trood.crossover.RawJsonOutputStream
import trood.crossover.flow.RabbitConsumer.TryToStop

object Archive {
  sealed trait State
  case object Uninitializated extends State
  case object Opening extends State
  case object Reading extends State

  sealed trait Data
  final case class Buffer(buf: Seq[Decoder.Message], os: Option[RawJsonOutputStream], pendingFlush: Boolean) extends Data

  //Messages
  final case class Ready(period: Long)
  final case class Failed(reason: Throwable)
  final case class Opened(os: RawJsonOutputStream)
  final case object Close
  final case class NeedFlush(msg: Decoder.Message, src: ActorRef)
  final case class ArchiveIt(msg: Decoder.Message, src: ActorRef)
  
  val flushThreshold: Long = 100

  def props(period: Long, path: String): Props = Props(new Archive(period, path))
}

import java.text.SimpleDateFormat
import java.util.Date

import akka.actor.FSM.Failure
import trood.crossover.Streamers
import trood.crossover.flow.Archive._

import scala.concurrent.Future
import scala.util.{Success, Try}
import scala.concurrent.duration._

class Archive(period: Long, path: String) extends FSM[State, Data] {
  def dateToString(date: Long): String = {
    val formater = new SimpleDateFormat("YYYYMMddHHmmss")
    formater.format(new Date(date))
  }
  private val periodStr: String = dateToString(period)

  startWith(Opening, Buffer(Seq.empty, None, pendingFlush = false))

  when(Opening) {
    case Event(ArchiveIt(msg, src), b: Buffer) => 
      context.parent ! Archiver.Archived(msg, src)
      stay using b.copy(buf = b.buf :+ msg)

    case Event(Archiver.Flush, b @ Buffer(Nil, _, _)) => 
      context.parent ! Archiver.Flushed(None)
      stay using b.copy(pendingFlush = false)
    case Event(Archiver.Flush, b @ Buffer(_, _, false)) => stay using b.copy(pendingFlush = true)
    case Event(Archiver.Flush, b @ Buffer(_, _, true)) => stay

    case Event(Opened(o), b :Buffer) => 
      context.parent ! Ready(period)
      goto(Reading) using b.copy(os = Some(o))
    case Event(Failed(_: RecoveryInProgressException), _) =>
      import scala.concurrent.ExecutionContext.Implicits.global
      log.info("A re-opening attempt is made after 1 seconds")
      context.system.scheduler.scheduleOnce(1 second, new Runnable {
        override def run(): Unit = _open()
      })
      stay()
    case Event(f: Failed, _) =>
      context.parent ! f
      stop(Failure(f.reason))
  }

  onTransition {
    case Opening -> Reading => 
      stateData match {
        case Buffer(b, _, true) => self ! Archiver.Flush
        case Buffer(b, _, _) if b.length > flushThreshold => self ! Archiver.Flush
        case _ =>
      }
  }

  when(Reading) {
    case Event(ArchiveIt(msg, src), b @ Buffer(buf, o, _)) if buf.length + 1 > flushThreshold =>
      context.parent ! NeedFlush(msg, src)
      stay
    case Event(ArchiveIt(msg, src), b @ Buffer(buf, o, _)) => 
      context.parent ! Archiver.Archived(msg, src)
      stay using b.copy(buf = buf :+ msg)
    case Event(Archiver.Flush, Buffer(Nil, _, _)) => 
      context.parent ! Archiver.Flushed(None)
      stay
    case Event(Archiver.Flush, Buffer(buf, Some(o), _)) =>
      _flush(buf, o) match {
        case Success(dt) => 
          context.parent ! Archiver.Flushed(Some(dt))
          stay using Buffer(Seq.empty, Some(o), pendingFlush = false)
        case scala.util.Failure(r) => 
          context.parent ! Archiver.FlushFailed(r, Some(buf.last.deliveryTag))
          stay using Buffer(Seq.empty, Some(o), pendingFlush = false)
      }
    case Event(Close, Buffer(_, o, _)) => 
      o.foreach(_.close())
      stop()
  }

  private def _flush(b: Seq[Decoder.Message], o: RawJsonOutputStream): Try[Long] = Try{
        b.foreach(m => o.writeRawObject(m.stringValue))
        o.sync()
        b.last.deliveryTag
      }

  private def _open() = {
    import scala.concurrent.ExecutionContext.Implicits.global
    Future(Streamers.createJsonDownStream(path + "/archive_" + period + ".lz4", true)).onComplete{
       case Success(o) => self ! Opened(o)
       case scala.util.Failure(e) => self ! Failed(e)
    }
  }

  initialize()
  _open()

}
