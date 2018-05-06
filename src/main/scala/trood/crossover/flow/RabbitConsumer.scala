package trood.crossover.flow

import java.util

import akka.actor.{ActorLogging, ActorRef, FSM, Props}
import com.rabbitmq.client._
import trood.crossover.flow.FlowMessages.{Next, Refuse}
import trood.crossover.util.NetUtil

import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success, Try}

object RabbitConsumer {

    //States
    sealed trait State
    case object NotStarted extends State
    case object Listening extends State
    case object WaitingAck extends State
    case object FlushingOnRefuse extends State
    case object ForceFlushing extends State

    //Data
    sealed trait Data
    case object NoSubscriber extends Data
    case class EmptyQueue(sub: ActorRef, answerTo: Option[Long]) extends Data
    case class PushingData(sub: ActorRef, queue: mutable.Queue[AMQPMessage], pushed: AMQPMessage, answerTo: Option[Long]) extends Data

    //Receive events
    case object Stop
    final case class StartConsumer(subs: ActorRef)

    //Sent event
    case object ConsumerStarted
    case object ConsumerStopped
    final case class StartConsumerFailed(e: Throwable)
    final case class AMQPMessage(value: Array[Byte], envelope: Envelope, properties: AMQP.BasicProperties)

    //Self events
    private[RabbitConsumer] case class ConsumerIsCanceled(requester: ActorRef)
    private[RabbitConsumer] case class TryToStop(requester: ActorRef, rdCount: Int)

    def props(connection: Connection, qName: String, prefetch: Int, archiver: ActorRef): Props = {
        val ch = connection.createChannel()
        val rejectedExchange = s"${qName}_rejected"
        ch.exchangeDeclare(rejectedExchange, "direct")
        val args = new util.HashMap[String, Object]()
        args.put("x-dead-letter-exchange", rejectedExchange)
        ch.queueDeclare(qName, true, false, false, args)
        Props(new RabbitConsumer(ch, qName, archiver))
    }
}

import trood.crossover.flow.RabbitConsumer._
class RabbitConsumer(ch: Channel, qName: String, archiver: ActorRef) extends DefaultConsumer(ch) with FSM[State, Data] with ActorLogging {
    private val ctag = s"amq.ctag-crossover_${NetUtil.hostname}_${System.currentTimeMillis()}"

    startWith(NotStarted, NoSubscriber)

    when(NotStarted) {
        case Event(StartConsumer(sub), NoSubscriber) =>
            Try(ch.basicConsume(qName, false, ctag, this)) match {
                case Success(_) =>
                    goto(Listening) using EmptyQueue(sub, None) replying ConsumerStarted
                case Failure(e) =>
                    stay() replying StartConsumerFailed(e)
            }
        case Event(Stop, _) =>
            sender() ! ConsumerStopped
            stay()
    }

    when(Listening) {
        case Event(message: AMQPMessage, EmptyQueue(sub, answerTo)) =>
            log.debug("Got message: {}. Start processing", message.envelope.getDeliveryTag)
            sub ! message
            goto(WaitingAck) using PushingData(sub, mutable.Queue(), message, answerTo)
        case Event(TryToStop(requester, _), _) =>
            requester ! ConsumerStopped
            goto(NotStarted)
    }

    when(ForceFlushing) {
        case Event(Archiver.Flushed(None), p:PushingData) =>
            log.debug("Nothing to flush")
            _nextMessage(p)
        case Event(Archiver.Flushed(Some(maxDTag)), p:PushingData) =>
            ch.basicAck(maxDTag, true)
            log.debug("All messages up to '{}' are acknowledged", maxDTag)
            _nextMessage(p.copy(answerTo = Some(maxDTag)))
        case Event(Archiver.FlushFailed(_, maybeMaxDTag), p:PushingData) =>
            maybeMaxDTag match {
                case Some(maxDTag) =>
                    ch.basicNack(maxDTag, true, true)
                    _nextMessage(p.copy(answerTo = Some(maxDTag)))
                case None => _nextMessage(p)
            }
    }

    private def _nextMessage(p: PushingData): State = {
        if (p.queue.isEmpty) {
            log.debug("Message queue is empty")
            if (p.answerTo.contains(p.pushed.envelope.getDeliveryTag)) {
                goto(Listening) using EmptyQueue(p.sub, p.answerTo)
            } else {
                //flushing before
                log.debug("There are messages to flush. Currently flushed up to {}. Try to flush up to {}", p.answerTo, p.pushed.envelope.getDeliveryTag)
                archiver ! Archiver.Flush
                goto(ForceFlushing)
            }
        } else {
            val msg = p.queue.dequeue()
            p.sub ! msg
            goto(WaitingAck) using p.copy(pushed = msg)
        }
    }

    def _refuseMessage(dTagToRefuse: Long, p: PushingData, reason: Throwable): State = {
        log.error(reason, "Refusing message: {}", dTagToRefuse)
        if (p.answerTo.forall(_ + 1 == dTagToRefuse)) {
            ch.basicNack(dTagToRefuse, false, false)
            _nextMessage(p)
        } else {
            log.debug("Need to flush previously messages before")
            archiver ! Archiver.Flush
            goto(FlushingOnRefuse)
        }
    }

    when(WaitingAck) {
        case Event(Next(_), p: PushingData) => _nextMessage(p)
        case Event(Archiver.Flushed(None), p: PushingData) => stay()
        case Event(Archiver.Flushed(Some(maxDTag)), p: PushingData) =>
            ch.basicAck(maxDTag, true)
            log.debug("All messages up to '{}' are acknowledged", maxDTag)
            stay() using p.copy(answerTo = Some(maxDTag))
        case Event(Archiver.FlushFailed(_, maybeMaxDTag), p: PushingData) =>
            maybeMaxDTag match {
                case Some(maxDTag) =>
                    ch.basicNack(maxDTag, true, true)
                    stay() using p.copy(answerTo = Some(maxDTag))
                case None => stay()
            }
        case Event(Refuse(dTagToRefuse, _, reason), p:PushingData) => _refuseMessage(dTagToRefuse, p, reason)
        case Event(StateTimeout, p: PushingData) => _refuseMessage(p.pushed.envelope.getDeliveryTag, p, new IllegalStateException("Timeout!"))

    }

    when(FlushingOnRefuse) {
        case Event(Archiver.Flushed(None), p:PushingData) =>
            log.debug("Nothing to flush")
            ch.basicNack(p.pushed.envelope.getDeliveryTag, false, false)
            _nextMessage(p)
        case Event(Archiver.Flushed(Some(maxDTag)), p:PushingData) =>
            ch.basicAck(maxDTag, true)
            log.debug("All messages up to '{}' are acknowledged {}", maxDTag)
            ch.basicNack(p.pushed.envelope.getDeliveryTag, false, false)
            log.debug("Message '{}' is refused", p.pushed.envelope.getDeliveryTag)
            _nextMessage(p.copy(answerTo = Some(maxDTag)))
        case Event(Archiver.FlushFailed(_, maybeMaxDTag), p:PushingData) =>
            maybeMaxDTag match {
                case Some(maxDTag) =>
                    ch.basicNack(maxDTag, true, true)
                    log.error("All messages up to '{}' are refused", maxDTag)
                    _nextMessage(p.copy(answerTo = Some(maxDTag)))
                case None => _nextMessage(p)
            }
    }


    whenUnhandled {
        case Event(Stop, _) =>
            ch.basicCancel(ctag)
            /** schedule because handleDelivery can processing concurrently **/
            context.system.scheduler.scheduleOnce(1 second, self, ConsumerIsCanceled(sender()))
            stay()
        case Event(ConsumerIsCanceled(requester), PushingData(_, q, _, _)) =>
            q.lastOption.foreach(m => ch.basicNack(m.envelope.getDeliveryTag, true, true))
            self ! TryToStop(requester, 0)
            stay()
        case Event(TryToStop(requester, rdCount), _) if rdCount <= 30 =>
            context.system.scheduler.scheduleOnce(1 second, self, TryToStop(requester, rdCount + 1))
            stay()
        case Event(TryToStop(_, rdCount), _) if rdCount > 30 =>
            throw new RuntimeException(s"Exceeded the limit of attempts to stop ($rdCount). Can't graceful stop rabbit consumer. ")
        case Event(message: AMQPMessage, PushingData(_, q, _, _)) =>
            q.enqueue(message)
            stay()
    }

    initialize()

    override def handleDelivery(cTag: String, env: Envelope, props: AMQP.BasicProperties, body: Array[Byte]): Unit = {
        self ! AMQPMessage(body, env, props)
    }

    override def preStart(): Unit = {
        archiver ! Archiver.SubscribeOnFlush(self)
    }

    override def postStop(): Unit = {
        ch.close()
    }
}
