package trood.crossover.flow

import akka.actor.{ActorLogging, ActorRef, FSM, Props}

import scala.concurrent.duration._


object BroadCaster {

  sealed trait State
  case object Listening extends State
  case object WaitingAck extends State

  sealed trait Data
  case object Empty extends Data
  case class PendingMessage(msg: Decoder.Message, src: ActorRef) extends Data

  def props(outs: Seq[ActorRef]): Props = Props(new BroadCaster(outs))
}

import BroadCaster._
class BroadCaster(outs: Seq[ActorRef]) extends FSM[State, Data] with ActorLogging {

  startWith(Listening, Empty)

  when(Listening) {
    case Event(msg: Decoder.Message, _) =>
      outs.foreach(_ ! msg)
      goto(WaitingAck) using PendingMessage(msg, sender) forMax(30 seconds)
    case Event(_ :FlowMessages.Next, _) => stay
    case Event(_ :FlowMessages.Refuse, _) => stay
  }

  when(WaitingAck) {
    case Event(ack @ FlowMessages.Next(tag), PendingMessage(msg, src)) if tag == msg.deliveryTag =>
      src ! ack
      goto(Listening) using Empty
    case Event(ack @ FlowMessages.Refuse(tag, _, _), PendingMessage(msg, src)) if tag == msg.deliveryTag =>
      src ! ack
      goto(Listening) using Empty
    case Event(msg: Decoder.Message, _) => 
      sender ! FlowMessages.Refuse(msg.deliveryTag, requeue = true, new IllegalStateException("Busy!"))
      stay
    case Event(StateTimeout, PendingMessage(msg, src)) => 
      src ! FlowMessages.Refuse(msg.deliveryTag, requeue = true, new IllegalStateException("Timeout!"))
      goto(Listening) using Empty
  }

  initialize()

}
