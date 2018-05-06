package trood.crossover.flow

import akka.actor.{ActorLogging, ActorRef, FSM, Props}

import scala.concurrent.duration._


object Archiver {

  //States
  sealed trait State
  case object Uninitializated extends State
  case object Listening extends State
  case object Flushing extends State
  case object OpenningArchive extends State


  //Data
  sealed trait Data
  final case class NoArchives(flushSubs: Seq[ActorRef]) extends Data
  final case class Archives(period: Long, currentArc: ActorRef, prevArc: Option[ActorRef], flushSubs: Seq[ActorRef]) extends Data
  final case class WithPendingMessage(pendingMsg: PendingMessage, statshedState: Data) extends Data
  final case class FlushingState(arcCount: Int, maxDt: Option[Long], error: Option[Throwable], fSubs: Seq[ActorRef], stashedData: Data, recoverTo: State) extends Data

  //Sent events
  final case class Flushed(upperDt: Option[Long])
  final case class FlushFailed(reason: Throwable, upperDt: Option[Long])
  final case class PendingMessage(msg: Decoder.Message, src: ActorRef)

  //Move to Archive

  //Received events
  case object Flush
  final case class SubscribeOnFlush(sub: ActorRef)
  final case class Archived(msg: Decoder.Message, src: ActorRef)

  def props(path: String): Props = Props(new Archiver(path))
}

import Archiver._
class Archiver(path: String) extends FSM[State, Data] with ActorLogging {

  startWith(Uninitializated, NoArchives(Seq.empty))

  when(Uninitializated) {
    case Event(msg: Decoder.Message, nd) =>
      goto(OpenningArchive) using WithPendingMessage(PendingMessage(msg, sender), nd)
    case Event(SubscribeOnFlush(sub), NoArchives(subs)) => 
      stay using NoArchives(subs :+ sub)
    case _ => stay
  }

  onTransition {
    case _ -> OpenningArchive => 
      nextStateData match {
        case WithPendingMessage(pm, _) => 
          val period: Long = (pm.msg.ts / 86400000) * 86400000
          context.actorOf(Archive.props(period, path), "archive-" + period)
        case _ =>
      }
    case Listening -> Flushing => 
      stateData match {
        case Archives(_, ca, pa, _) =>
          ca ! Flush
          pa.foreach(_ ! Flush)
        case _ =>
      }
  }

  when(OpenningArchive, 30 seconds) {
    case Event(Archive.Ready(p), WithPendingMessage(pm, stashed)) =>
      if (log.isDebugEnabled)
        log.debug("Archive {} ready", p)
      self ! pm
      goto(Listening) using (stashed match {
        case NoArchives(fs) => Archives(p, sender, None, fs)
        case Archives(_, c, _, fs) => Archives(p, sender, Some(c), fs)
        case p => p
      })
    case Event(Archive.Failed(err), WithPendingMessage(pm, stashed)) =>
      log.error("Creating archive failed: {}", err)
      pm.src ! FlowMessages.Refuse(pm.msg.deliveryTag, requeue = true, err)
      goto(Listening) using stashed
    case Event(StateTimeout, WithPendingMessage(pm, stashed)) =>
      log.error("Creating archive timeout")
      pm.src ! FlowMessages.Refuse(pm.msg.deliveryTag, requeue = true, new IllegalStateException("Timeout while openning an archive."))
      goto(Listening) using stashed
  }

  private def _processMessage(msg: Decoder.Message, src: ActorRef, arcs: Archives) = {
    arcs match {
      case Archives(p, _, _, _) if msg.ts < (p - 86400000) =>
        sender ! FlowMessages.Refuse(msg.deliveryTag, requeue = false, new IllegalStateException("Message is to old: " + msg.ts))
        stay
      case Archives(p, _, None, _) if msg.ts < p =>
        sender ! FlowMessages.Refuse(msg.deliveryTag, requeue = false, new IllegalStateException("Message is to old: " + msg.ts))
        stay
      case Archives(p, _, Some(pa), _) if msg.ts < p =>
        pa ! Archive.ArchiveIt(msg, src)
        stay
      case Archives(p, ca, _, _) if msg.ts < (p + 86400000) =>
        ca ! Archive.ArchiveIt(msg, src)
        stay
      case Archives(p, _, None, _) if msg.ts < (p + 2 * 86400000) =>
        goto(OpenningArchive) using WithPendingMessage(PendingMessage(msg, src), arcs)
      case Archives(p, _, Some(pa), fs) if msg.ts < (p + 2 * 86400000) =>
        goto(Flushing) using FlushingState(2, None, None, fs, WithPendingMessage(PendingMessage(msg, src), arcs), OpenningArchive)
      case _ => 
        src ! FlowMessages.Refuse(msg.deliveryTag, requeue = true, new IllegalStateException("Message is to young: " + msg.ts))
        stay
    }
  }

  when(Listening) {
    case Event(PendingMessage(msg, src), arcs: Archives) => _processMessage(msg, src, arcs)
    case Event(msg: Decoder.Message, arcs: Archives) => _processMessage(msg, sender, arcs)
    case Event(Flush, arcs @ Archives(_, _, pa, fs)) => goto(Flushing) using FlushingState(if(pa.isEmpty) 1 else 2, None, None, fs, arcs, Listening)
    case Event(Archive.NeedFlush(msg, src), arcs @ Archives(_, _, pa, fs)) => 
      goto(Flushing) using FlushingState(if(pa.isEmpty) 1 else 2, None, None, fs, WithPendingMessage(PendingMessage(msg, src), arcs), Listening)
  }

  when(Flushing) {
    case Event(Flushed(dt), f@ FlushingState(2, _, _, _, _, _)) => stay using f.copy(arcCount = 1, maxDt = dt)
    case Event(FlushFailed(err, dt), f@ FlushingState(2, _, _, _, _, _)) => stay using f.copy(arcCount = 1, maxDt = dt, error = Some(err))

    case Event(Flushed(dt), FlushingState(1, pdt, Some(err), fs, stashed, _)) => 
      fs.foreach(_ ! Flushed(if(dt.exists(m => !pdt.exists(_ > m))) dt else pdt))
      stashed match {
        case WithPendingMessage(PendingMessage(msg, src), st) => 
          src ! FlowMessages.Refuse(msg.deliveryTag, requeue = true, err)
          goto(Listening) using st
        case _ => goto(Listening) using stashed
      }
    case Event(Flushed(dt), FlushingState(1, pdt, None, fs, stashed, OpenningArchive)) => 
      fs.foreach(_ ! Flushed(if(dt.exists(m => !pdt.exists(_ > m))) dt else pdt))
      goto(OpenningArchive) using stashed
    case Event(Flushed(dt), FlushingState(1, pdt, None, fs, stashed, Listening)) => 
      fs.foreach(_ ! Flushed(if(dt.exists(m => !pdt.exists(_ > m))) dt else pdt))
      stashed match {
        case WithPendingMessage(pm: PendingMessage, st) => 
          self ! pm
          goto(Listening) using st
        case _ => goto(Listening) using stashed
      }
    case Event(FlushFailed(err, dt), FlushingState(1, pdt, _, fs, stashed, _)) => 
      fs.foreach(_ ! FlushFailed(err, if(dt.exists(m => !pdt.exists(_ > m))) dt else pdt))
      stashed match {
        case WithPendingMessage(PendingMessage(msg, src), st) => 
          src ! FlowMessages.Refuse(msg.deliveryTag, requeue = true, err)
          goto(Listening) using st
        case _ => goto(Listening) using stashed
      }
    case Event(StateTimeout, FlushingState(_, _, _, fs, stashed, _)) => 
      val err = new IllegalStateException("Flushing timeout!")
      fs.foreach(_ ! FlushFailed(err, None))
      stashed match {
        case WithPendingMessage(PendingMessage(msg, src), st) => 
          src ! FlowMessages.Refuse(msg.deliveryTag, requeue = true, err)
          goto(Listening) using st
        case _ => goto(Listening) using stashed
      }

  }

  whenUnhandled {
    case Event(Archived(msg, src), _) => 
      src ! FlowMessages.Next(msg.deliveryTag)
      stay
  }

  initialize()

}
