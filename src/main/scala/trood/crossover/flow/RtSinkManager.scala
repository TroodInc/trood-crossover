package trood.crossover.flow

import akka.actor.{ActorLogging, ActorRef, FSM, Props}
import org.apache.curator.framework.imps.CuratorFrameworkState
import org.apache.curator.framework.{CuratorFramework, CuratorFrameworkFactory}
import org.apache.curator.retry.BoundedExponentialBackoffRetry
import trood.crossover.conf.CubeConf
import scala.concurrent.duration._

object RtSinkManager {
    sealed trait State
    case object Stopped extends State
    case object Starting extends State
    case object Working extends State
    case object Reconfiguring extends State
    case object Stopping extends State

    sealed trait Data
    case object NoData extends Data
    final case class WorkingCtx(router: ActorRef, cubes: Seq[(CubeConf, ActorRef)], ver: Int) extends Data
    final case class StartingCtx(replyTo: ActorRef, workingCtx: WorkingCtx) extends Data
    final case class StoppingCtx(replyTo: ActorRef, numOfStopped: Int, mustBeStopped: Int, workingCtx: WorkingCtx) extends Data
    final case class ReconfiguringCtx(source: ActorRef, cubesToStop: Set[ActorRef], stopped: Set[ActorRef], workingCtx: WorkingCtx) extends Data

    //Messages
    final case class StartSink(cubesConf: Seq[CubeConf])
    final case class SinkStarted(sink: ActorRef)
    final case class CubesChanged(cubesConf: Seq[CubeConf])

    final case object StopSink
    final case object SinkStopped
    final case object SinkReconfigured


    def props(zkConnect: String): Props =  {
        val curator = CuratorFrameworkFactory.newClient(zkConnect, new BoundedExponentialBackoffRetry(100, 30000, 30))
        curator.start()
        Props(new RtSinkManager(curator))
    }
}

import trood.crossover.flow.RtSinkManager._
class RtSinkManager(curator: CuratorFramework) extends FSM[State, Data] with ActorLogging  {
    startWith(Stopped, NoData)

    when(Stopped) {
        case Event(StartSink(cubesConf), _) =>
            val ver = 1
            val cubes = cubesConf.map(conf => conf -> context.actorOf(RtCube.props(curator, conf), s"rt-cube-${conf.name}-$ver"))
            val router = context.actorOf(RtCubesRouter.props(), s"rt-router")
            router ! RtCubesRouter.Start(_routingTable(cubes))
            goto(Starting) using StartingCtx(sender(), WorkingCtx(router, cubes, ver))
    }

    when(Starting) {
        case Event(RtCubesRouter.RouterStarted, StartingCtx(replyTo, workingCtx)) =>
            replyTo ! SinkStarted(workingCtx.router)
            goto(Working) using workingCtx
    }

    when(Working) {
        case Event(StopSink, wCtx@WorkingCtx(router, cubes, _)) =>
            router ! RtCubesRouter.Stop
            goto(Stopping) using StoppingCtx(sender(), 0, cubes.size, wCtx)
        case Event(CubesChanged(newConf), WorkingCtx(router, cubes, curVer)) =>
            val curConf = cubes.map(_._1)
            val notChanged = newConf.intersect(curConf)
            val namesOfNotChanged = notChanged.map(_.name)
            log.info("Not changed cubes: {}", namesOfNotChanged)
            val (stayCubes, cubesToStop) = cubes.partition(t => namesOfNotChanged.contains(t._1.name))
            val newOrChanged = newConf.diff(notChanged)

            //preparing new cubs
            val ver = curVer + 1
            log.info("New or changed cubes: {}", newOrChanged.map(_.name))
            val newCubes = newOrChanged.map(conf =>
                conf -> context.actorOf(RtCube.props(curator, conf), s"rt-cube-${conf.name}-$ver")
            ) ++ stayCubes

            router ! RtCubesRouter.UpdateRouteTable(_routingTable(newCubes))
            goto(Reconfiguring) using ReconfiguringCtx(sender(), cubesToStop.map(_._2).toSet, Set.empty, WorkingCtx(router, newCubes, ver))
    }

    when(Reconfiguring) {
        case Event(RtCubesRouter.RouteTableUpdated,  ReconfiguringCtx(source, cubesToStop, _, workingCtx)) if cubesToStop.isEmpty =>
             source ! SinkReconfigured
             goto(Working) using workingCtx
        case Event(RtCubesRouter.RouteTableUpdated,  ReconfiguringCtx(source, cubesToStop, _, workingCtx)) =>
            log.info("Stopping changed and deleted cubes: {}", cubesToStop)
            cubesToStop.foreach(_ ! RtCube.StopCube)
            stay() forMax (20 seconds)
        case Event(RtCube.CubeStopped | RtCube.CubeFailed,  ctx@ReconfiguringCtx(source, cubesToStop, oldStopped, workingCtx)) =>
            val rest = cubesToStop - sender()
            val stopped = oldStopped + sender()
            if (rest.isEmpty) {
                stopped.foreach(context.stop)
                source ! SinkReconfigured
                goto(Working) using workingCtx
            } else {
                stay() using ctx.copy(cubesToStop = rest, stopped = stopped) forMax(20 seconds)
            }
        case Event(StateTimeout, ctx@ReconfiguringCtx(source, cubesToStop, stopped, workingCtx)) =>
            log.error("Stopping cubs timeout during reconfiguration.")
            (stopped ++ cubesToStop).foreach(context.stop)
            source ! SinkReconfigured
            goto(Working) using workingCtx
    }

    when(Stopping) {
        case Event(RtCubesRouter.RouterStopped, StoppingCtx(_, _, _, WorkingCtx(_, cubes, _))) =>
            cubes.foreach(_._2 ! RtCube.StopCube)
            stay() forMax(20 seconds)
        case Event(RtCube.CubeStopped | RtCube.CubeFailed, sCtx@StoppingCtx(_, numOfStopped, mustBeStopped, _)) =>
            val n = numOfStopped + 1
            if (n == mustBeStopped) {
                _stop(sCtx)
            } else {
                stay() using sCtx.copy(numOfStopped = n) forMax(20 seconds)
            }
        case Event(StateTimeout, sCtx: StoppingCtx) =>
            log.error("Stopping cubs timeout.")
            _stop(sCtx)
    }

    initialize()

    override def postStop(): Unit = {
        if (curator.getState != CuratorFrameworkState.STOPPED)
            curator.close()
    }

    private def _routingTable(cubes: Seq[(CubeConf, ActorRef)]): RtCubesRouter.RouteTable = RtCubesRouter.RouteTable(
        cubes.flatMap(cube => {
            cube._1.sources.keys.map(_ -> cube._2)
        }).groupBy(_._1).mapValues(_.map(_._2))
    )


    private def _stop(sCtx: StoppingCtx): FSM.State[RtSinkManager.State, Data] = {
        sCtx.workingCtx.cubes.foreach(t => context.stop(t._2))
        context.stop(sCtx.workingCtx.router)
        sCtx.replyTo ! SinkStopped
        log.info("Rt sink stopped")
        goto(Stopped) using NoData
    }
}
