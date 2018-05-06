package trood.crossover.flow

import akka.actor.{ActorLogging, ActorRef, FSM, Props}
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule

import scala.util.{Failure, Success, Try}
import scala.concurrent.duration._


object Decoder {
    private val CLASS_NAME = "cl"
    private val TS_NAME = "ts"
    private val mapper = new ObjectMapper()
    mapper.registerModule(DefaultScalaModule)

    //States
    sealed trait State
    case object Listening extends State
    case object WaitingAck extends State

    //Data
    sealed trait Data
    case object NoData extends Data
    case class PushedMessage(in: ActorRef, msg: Message) extends Data

    //Sent message
    case class Message(cl: String, ts: Long, value: Map[String, AnyRef], deliveryTag: Long, stringValue: String)

    object Message {
        def apply(in: RabbitConsumer.AMQPMessage): Message = {
            val rawJson = new String(in.value, "UTF-8")
            val value = mapper.readValue(rawJson, classOf[Map[String, AnyRef]])
            val cl = value.get(CLASS_NAME) match {
                case Some(s: String) => s
                case Some(x) => throw new IllegalArgumentException(s"Class filed has wrong type. Expected: String. Found: ${x.getClass.getSimpleName}")
                case None => throw new IllegalArgumentException(s"Class filed [$CLASS_NAME] not found")
            }
            val ts = value.get(TS_NAME) match {
                case Some(n:Number) => n.longValue()
                case Some(x) => throw new IllegalArgumentException(s"Time filed has wrong type. Expected: Long. Found: ${x.getClass.getSimpleName}")
                case None => throw new IllegalArgumentException(s"Time filed [$TS_NAME] not found")
            }
            Message(cl, ts, value, in.envelope.getDeliveryTag, rawJson)
        }
    }

    def props(out: ActorRef): Props = Props(new Decoder(out))
}

import Decoder._
class Decoder(out: ActorRef) extends FSM[State, Data] with ActorLogging {
    startWith(Listening, NoData)

    when(Listening) {
        case Event(amqp:RabbitConsumer.AMQPMessage, NoData) =>
            Try(Message(amqp)) match {
                case Success(msg) =>
                    out ! msg
                    goto(WaitingAck) using PushedMessage(sender(), msg) forMax(30 seconds)
                case Failure(e) =>
                    sender() ! FlowMessages.Refuse(amqp.envelope.getDeliveryTag, requeue = false, e)
                    stay()
            }
        case Event(e: FlowMessages.Refuse, _) => stay()
        case Event(e: FlowMessages.Next, _) => stay()
    }

    when(WaitingAck) {
        case Event(amqp:RabbitConsumer.AMQPMessage, NoData) =>
            sender() ! FlowMessages.Refuse(amqp.envelope.getDeliveryTag, requeue = true, new IllegalStateException("BUSY"))
            stay()
        case Event(cb, PushedMessage(in, msg)) =>
            in ! cb
            goto(Listening) using NoData
    }
}
