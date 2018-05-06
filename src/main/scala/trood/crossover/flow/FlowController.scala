package trood.crossover.flow

import akka.actor.{Actor, ActorRef, FSM, Props}
import trood.crossover.conf.{CrossoverConf, DistributedCrossoverConf}
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.duration._
import scala.concurrent.Await
import scala.util.{Failure, Success, Try}
import scala.concurrent.ExecutionContext.Implicits.global

object FlowController {
    implicit val timeout: Timeout = Timeout(5 seconds)

    //States
    sealed trait State
    case object Stopped extends State
    case object NoConsumer extends State
    case object Starting extends State
    case object Stopping extends State
    case object InPipeline extends State
    case object Reconfiguring extends State

    //Data
    sealed trait Data
    case object Empty extends Data
    final case class PreparingConsumerCtx(crsrConf:CrossoverConf, replyTo: ActorRef, source: ActorRef, archiver: ActorRef) extends Data
    final case class WithConsumer(replyTo: ActorRef, archiver: ActorRef, source: ActorRef, consumer: ActorRef, rt: ActorRef) extends Data
    final case class Flow(source: ActorRef, archiver: ActorRef, rt: ActorRef) extends Data
    final case class StoppingCtx(replyTo: ActorRef, sourceDisconnected: Boolean, rtSinkStopped: Boolean) extends Data {
        def isComplete = sourceDisconnected && rtSinkStopped
    }

    //Received events
    case object StartFlow
    case object StopFlow

    //Response events
    final case class StartFailed(e: Throwable)
    case object FlowStarted
    case object FlowStopped

    def props(conf: ActorRef): Props = Props(new FlowController(conf: ActorRef))

}

import trood.crossover.flow.FlowController._
class FlowController(conf: ActorRef) extends FSM[State, Data] {
    startWith(Stopped, Empty)

    when(Stopped) {
        case Event(StartFlow, Empty) =>
            log.info("Getting configuration ...")
            Await.result(conf ? DistributedCrossoverConf.Get, 5 seconds) match {
                case DistributedCrossoverConf.Config(crsrConf: CrossoverConf) =>
                    log.info("Preparing RabbitMQ consumer ...")
                    val archiver = context.actorOf(Archiver.props(crsrConf.archiver.path), "archiver")
                    val source = context.actorOf(RabbitSource.props(crsrConf.rabbit.conn), "source")
                    source ! RabbitSource.Connect
                    goto(NoConsumer) using PreparingConsumerCtx(crsrConf, sender(), source, archiver)
                case DistributedCrossoverConf.Error(e) =>
                    log.error(e, "Can't start flow. Configuration is broken.")
                    stay() replying StartFailed(e)
            }
        case Event(msg: DistributedCrossoverConf.Updated[CrossoverConf], _) => stay()
    }

    when(NoConsumer) {
        case Event(RabbitSource.SuccessfulConnected, PreparingConsumerCtx(crsrConf, _, source, archiver)) =>
            log.info("Connection to RabbitMQ is created")
            source ! RabbitSource.CreateConsumer(crsrConf.rabbit.queue.name, crsrConf.rabbit.queue.prefetch, archiver)
            stay()
        case Event(RabbitSource.ConnectionFailed(e), PreparingConsumerCtx(_, replyTo, _, archiver)) =>
            log.error(e, "Connection to RabbitMQ is failed")
            replyTo ! StartFailed(e)
            goto(Stopped)
        case Event(RabbitSource.ConsumerCreated(consumer), PreparingConsumerCtx(crsrConf, replyTo, source, archiver)) =>
            log.info("Consumer is created. Creating real-time sink ...")
            Try(context.actorOf(RtSinkManager.props(crsrConf.zkConnect), "rt-manager")) match {
                case Success(rt) =>
                    rt ! RtSinkManager.StartSink(crsrConf.cubes)
                    goto(Starting) using WithConsumer(replyTo, archiver, sender(), consumer, rt)
                case Failure(e) =>
                    log.error(e, "Creating real-time sink manager failed")
                    source ! RabbitSource.Disconnect
                    replyTo ! StartFailed(e)
                    goto(Stopping) using StoppingCtx(Actor.noSender, sourceDisconnected = false, rtSinkStopped = true)
            }
        case Event(RabbitSource.CreateConsumerFailed(e), PreparingConsumerCtx(_, replyTo, source, archiver)) =>
            log.error(e, "Creating consumer failed")
            source ! RabbitSource.Disconnect
            replyTo ! StartFailed(e)
            goto(Stopping) using StoppingCtx(Actor.noSender, sourceDisconnected = false, rtSinkStopped = true)
    }

    when(Starting) {
        case Event(RtSinkManager.SinkStarted(sink), WithConsumer(_, archiver, _, consumer, _)) =>
            log.info("Real-time sink created. Starting consumer ...")
            val broadCaster = context.actorOf(BroadCaster.props(Seq(archiver, sink)), "broadCaster")
            val decoder = context.actorOf(Decoder.props(broadCaster), "decoder")
            consumer ! RabbitConsumer.StartConsumer(decoder)
            stay()
        case Event(RabbitConsumer.ConsumerStarted, WithConsumer(replyTo, archiver, source, _, rt)) =>
            log.info("Flow is started")
            conf ! DistributedCrossoverConf.SubscribeMe(self)
            replyTo ! FlowStarted
            goto(InPipeline) using Flow(source, archiver, rt)
        case Event(RabbitConsumer.StartConsumerFailed(e), WithConsumer(replyTo, archiver, source, _, rt)) =>
            log.error(e, "Failed to start consume")
            source ! RabbitSource.Disconnect
            rt ! RtSinkManager.StopSink
            replyTo ! StartFailed(e)
            goto(Stopping) using StoppingCtx(Actor.noSender, sourceDisconnected = false, rtSinkStopped = false)
    }

    when(InPipeline) {
        case Event(StopFlow, Flow(source, _, rt)) =>
            log.info("Stopping flow ...")
            source ! RabbitSource.Disconnect
            rt ! RtSinkManager.StopSink
            goto(Stopping) using StoppingCtx(sender(), sourceDisconnected = false, rtSinkStopped = false)
        case Event(DistributedCrossoverConf.Updated(crossoverConf: CrossoverConf), Flow(_, _, rt)) =>
            log.info("Configuration is changed. Reconfiguring real-time sink.")
            log.debug("New configuration: {}", crossoverConf)
            rt ! RtSinkManager.CubesChanged(crossoverConf.cubes)
            goto(Reconfiguring)
    }

    when(Reconfiguring) {
        case Event(RtSinkManager.SinkReconfigured, _) => goto(InPipeline)
    }

    when(Stopping) {
        case Event(RabbitSource.SuccessfulDisconnected, oldCtx:StoppingCtx) =>
            log.info("Connect to RabbitMQ is closed ...")
            val ctx = oldCtx.copy(sourceDisconnected = true)
            _tryToStop(ctx)
        case Event(RtSinkManager.SinkStopped, oldCtx: StoppingCtx) =>
            log.info("Real-time sink is stopped ...")
            val ctx = oldCtx.copy(rtSinkStopped = true)
            _tryToStop(ctx)
    }

    whenUnhandled{
        case Event(msg: DistributedCrossoverConf.Updated[CrossoverConf], _) =>
            context.system.scheduler.scheduleOnce(1 second, self, msg)
            stay()
    }

    onTransition {
        case _ -> Stopped => context.children.foreach(context.stop)
    }

    private def _tryToStop(ctx:StoppingCtx) = {
        if (ctx.isComplete) {
            log.info("Flow is stopped")
            ctx.replyTo ! FlowStopped
            goto(Stopped) using Empty
        } else {
            stay() using ctx.copy(rtSinkStopped = true)
        }
    }
}