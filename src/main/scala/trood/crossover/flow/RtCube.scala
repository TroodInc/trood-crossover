package trood.crossover.flow

import java.util

import akka.actor.{ActorLogging, FSM, Props}
import com.metamx.tranquility.druid.{DruidBeams, DruidEnvironment, DruidLocation}
import com.metamx.tranquility.tranquilizer.Tranquilizer
import com.twitter.finagle.Status
import io.druid.data.input.impl.TimestampSpec
import org.apache.curator.framework.CuratorFramework
import org.joda.time.DateTime
import trood.crossover.conf.CubeConf

import scala.util.{Failure, Success, Try}


object RtCube {
    val FAILURE_SENSETIVITY = 500
    val MAX_NUM_FAILED = 20

    class Mapper(rulesMap: Map[String, String]) extends (Decoder.Message => java.util.Map[String, AnyRef]){
        private val rules: Seq[(String, String)] = rulesMap.toSeq

        override def apply(msg: Decoder.Message): java.util.Map[String, AnyRef] = {
            val map = new util.HashMap[String, AnyRef](msg.value.size + 1)
            map.put("ts", new java.lang.Long(msg.ts))
            rules.foldLeft(map)((acc, rule) => {
                msg.value.get(rule._1).foreach(acc.put(rule._2, _))
                acc
            })
        }
    }

    //State
    sealed trait State
    case object Stopped extends State
    case object Listening extends State
    case object Failed extends State

    //Data
    sealed trait Data
    case object NoData extends Data
    final case class FeedingCtx(tranquilizer: Tranquilizer[util.Map[String, AnyRef]], mappers: Map[String, Mapper], lastFailedTs: Long, numOfFailed: Long) extends Data
    final case class Cause(e: Throwable) extends Data

    //Messages
    final case object StopCube

    final case object CubeStopped
    final case class CubeFailed(e: Throwable)

    private[RtCube] final case class PushIsFailed(ts: Long, cause: Throwable)

    def props(curator: CuratorFramework, cube: CubeConf): Props = Props(new RtCube(curator, cube))
}

import trood.crossover.flow.RtCube._
class RtCube(curator: CuratorFramework, cube: CubeConf) extends FSM[State, Data] with ActorLogging {

    startWith(Stopped, NoData)

    when(Stopped) {
        case Event(msg: Decoder.Message, _) => Try {
            val tranquilizer = DruidBeams
                .builder((event: java.util.Map[String, AnyRef]) => new DateTime(event.get("ts")))
                .curator(curator)
                .location(DruidLocation(DruidEnvironment("druid/overlord"), cube.name))
                .rollup(cube.rollup)
                .tuning(cube.tune)
                .timestampSpec(new TimestampSpec("ts", "millis", null))
                .buildTranquilizer()
            tranquilizer.start()
            FeedingCtx(tranquilizer, cube.sources.mapValues(new Mapper(_)), 0, 0)
        } match {
            case Success(sinkCtx) =>
                self ! msg
                goto(Listening) using sinkCtx
            case Failure(e) =>
                goto(Failed) using Cause(e) replying CubeFailed(e)
        }
        case Event(StopCube, _) => stay() replying CubeStopped
        case Event(PushIsFailed(_, _), _) => stay()
    }

    when(Listening) {
        case Event(msg: Decoder.Message, FeedingCtx(tranquilizer, mappers, _, _)) =>
            val value = mappers(msg.cl)(msg)
            tranquilizer.send(value).onFailure(e => {
                log.error(e, "Failed to send message to druid [{}]", value)
                self ! PushIsFailed(System.currentTimeMillis(), e)
            }).onSuccess(vl=>{
                log.info("Sent message: %s", vl)
            })
            stay()
        case Event(StopCube, FeedingCtx(tranquilizer, _, _, _)) =>
            tranquilizer.flush()
            tranquilizer.stop()
            goto(Stopped) using NoData replying CubeStopped
        case Event(PushIsFailed(ts, e), FeedingCtx(tranquilizer, mappers, lastFailedDTag, numOfFailed)) if ts - lastFailedDTag <= FAILURE_SENSETIVITY =>
            val increasedNumOfFailed = numOfFailed + 1
            if (numOfFailed >= MAX_NUM_FAILED) {
                tranquilizer.stop()
                goto(Failed) using Cause(new RuntimeException(s"Exceeded maximum number [$MAX_NUM_FAILED] of real-time druid sink failures [$increasedNumOfFailed]"))
            } else {
                stay() using FeedingCtx(tranquilizer, mappers, ts, increasedNumOfFailed)
            }
        case Event(PushIsFailed(ts, _), FeedingCtx(tranquilizer, mappers, lastFailedDTag, _)) if ts - lastFailedDTag > FAILURE_SENSETIVITY =>
            stay() using FeedingCtx(tranquilizer, mappers, ts, 1)
    }

    when(Failed) {
        case Event(msg: Decoder.Message, Cause(e)) => stay() replying CubeFailed(e)
        case Event(StopCube, Cause(e)) => stay() replying CubeFailed(e)
    }

    initialize()

    override def postStop(): Unit = stateData match {
        case FeedingCtx(tranquilizer, _, _, _) => if(tranquilizer.status != Status.Closed) tranquilizer.stop()
        case _ =>
    }
}