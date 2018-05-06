package trood.crossover.flow

import java.util.concurrent.{ExecutorService, Executors, ThreadFactory}

import akka.actor.{ActorRef, FSM, Props}
import com.rabbitmq.client._
import trood.crossover.conf.RabbitConnConf
import trood.crossover.util.NetUtil

import scala.util.{Failure, Success, Try}

object RabbitSource {
    //States
    sealed trait State
    case object NotConnected extends State
    case object Disconnecting extends State
    case object Connected extends State

    //Data
    sealed trait Data
    case object NoConnection extends Data
    case class ActiveConnection(executor: ExecutorService, connection: Connection, consumers: Seq[ActorRef]) extends Data
    case class StoppingConsumers(requester: ActorRef, executor: ExecutorService, connection: Connection, counter: Int) extends Data

    //Sent event
    final case class ConnectionFailed(e: Throwable)
    final case object SuccessfulConnected
    final case object SuccessfulDisconnected
    final case class CreateConsumerFailed(e: Throwable)
    final case class ConsumerCreated(consumer: ActorRef)

    //Received events
    case object Connect
    case object Disconnect
    case class CreateConsumer(qName: String, prefetch: Int, archiver: ActorRef)


    def props(conf: RabbitConnConf): Props = {
        val factory = new ConnectionFactory()
        factory.setUsername(conf.username)
        factory.setPassword(conf.password)
        factory.setVirtualHost(conf.virtualHost)
        conf.connectionTimeout.foreach(factory.setConnectionTimeout)
        conf.ssl.foreach(if (_) factory.useSslProtocol())
        val addresses = conf.hosts.map(_.split(":")).map(parts => new Address(parts(0), parts(1).toInt)).toArray
        Props(new RabbitSource(factory, addresses))
    }
}

import RabbitSource._

class RabbitSource(factory: ConnectionFactory, addresses: Array[Address]) extends FSM[State, Data] {
    startWith(NotConnected, NoConnection)

    when(NotConnected) {
        case Event(Connect, _) =>
            val executor = Executors.newFixedThreadPool(1, new ThreadFactory {
                override def newThread(r: Runnable): Thread = new Thread(r, s"Rabbit connection consumer dispatcher")
            })
            Try(factory.newConnection(executor, addresses, s"amq.conn-crossover_${NetUtil.hostname}")) match {
                case Success(connection) =>
                    goto(Connected) using ActiveConnection(executor, connection, Seq.empty) replying SuccessfulConnected
                case Failure(e) =>
                    executor.shutdown()
                    stay() replying ConnectionFailed(e)
            }
    }

    when(Connected) {
        case Event(CreateConsumer(qName, prefetch, archiver), ac@ActiveConnection(_, connection, consumers)) =>
            val id = consumers.length + 1
            Try(context.actorOf(RabbitConsumer.props(connection, qName, prefetch, archiver), s"consumer-$id")) match {
                case Success(consumer) =>
                    stay() using ac.copy(consumers = consumers :+ consumer) replying ConsumerCreated(consumer)
                case Failure(e) =>
                    stay() replying CreateConsumerFailed(e)
            }

        case Event(Disconnect, ActiveConnection(es, con, consumers)) =>
            consumers.foreach(_ ! RabbitConsumer.Stop)
            val len = consumers.length
            if (len != 0) {
                goto(Disconnecting) using StoppingConsumers(sender(), es, con, len)
            } else {
                goto(NotConnected) replying SuccessfulDisconnected
            }
    }

    when(Disconnecting) {
        case Event(RabbitConsumer.ConsumerStopped, StoppingConsumers(requester, es, con, counter)) => {
            val leftToStop = counter - 1
            if(leftToStop == 0) {
                con.close()
                es.shutdown()
                goto(NotConnected) replying SuccessfulDisconnected
            } else {
                stay() using StoppingConsumers(requester, es, con, leftToStop)
            }
        }
    }


}
