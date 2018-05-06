package trood.crossover.flow

import akka.actor.{ActorRef, FSM, Props, Terminated}

object RtCubesRouter {

    sealed trait State
    case object Stopped extends State
    case object Routing extends State

    sealed trait Data
    case object NoData extends Data
    final case class RouteTable(map: Map[String, Seq[ActorRef]]) extends Data

    final case class Start(rt: RouteTable)
    final case class UpdateRouteTable(rt: RouteTable)
    final case object Stop

    final case object RouterStarted
    final case object RouterStopped
    final case object RouteTableUpdated

    def props(): Props = Props(new RtCubesRouter())

}

import RtCubesRouter._
class RtCubesRouter extends FSM[State, Data] {
    startWith(Stopped, NoData)

    when(Stopped) {
        case Event(Start(rt@RouteTable(map)), NoData) =>
            map.values.flatten.toSet[ActorRef].foreach(context watch)
            goto(Routing) using rt replying RouterStarted
    }

    when(Routing) {
        case Event(msg: Decoder.Message, RouteTable(map)) =>
            map.get(msg.cl).foreach(_.foreach(_ ! msg))
            stay()
        case Event(UpdateRouteTable(newRt@RouteTable(newMap)), RouteTable(curMap)) =>
            curMap.values.flatten.toSet[ActorRef].foreach(context unwatch)
            newMap.values.flatten.toSet[ActorRef].foreach(context watch)
            stay() using newRt replying RouteTableUpdated
    }

    whenUnhandled {
        case Event(Terminated(a), RouteTable(map))  =>
            stay() using RouteTable(map.filterNot(_ == a))
        case Event(RtCube.CubeFailed(e), RouteTable(map))  =>
            val sndr = sender()
            stay() using RouteTable(map.filterNot(_ == sndr))
        case Event(Stop, _) =>
            goto(Stopped) using NoData replying RouterStopped
    }

    initialize()
}


