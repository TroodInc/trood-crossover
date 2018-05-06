package trood.crossover.conf

import java.util.concurrent.atomic.AtomicReference
import com.typesafe.config.ConfigFactory
import ProxyConfigImplicits._
import trood.crossover.HdfsInputStream
import java.io.InputStreamReader
import com.typesafe.config.ConfigValue
import trood.crossover.HdfsOutputStream
import com.typesafe.config.ConfigRenderOptions

trait ConfHolder[T] {
    def get(): T
    def set(t: T): Unit
}

object VersioningConf {

    implicit def versioningConfReader[T](implicit reader: ConfigReader[T]) = new ConfigReader[VersioningConf[T]] {
        override def read(conf: ConfigValue): ConfigResult[VersioningConf[T]] = 
                (conf \ "versions").read[Seq[T]].map(VersioningConf.apply[T])
    }

    implicit def versioningConfWriter[T](implicit writer: ConfigWriter[T]) = new ConfigWriter[VersioningConf[T]] {
        override def write(vc: VersioningConf[T]): ConfigValue = (
                (__ \ "versions").write[Seq[T]] $).unlift(VersioningConf.unapply[T])(vc)
    }
}

case class VersioningConf[T](versions: Seq[T])

class HDFSConfHolder[T](path: String)(implicit reader: ConfigReader[VersioningConf[T]], writer: ConfigWriter[VersioningConf[T]]) extends ConfHolder[T] {
    private val current = new AtomicReference(_load())

    def get(): T = current.get().versions.last
    override def set(t: T) = _save(t)

    private def _load() = {
      val his = new HdfsInputStream(path)
      val dataReader = new InputStreamReader(his, "UTF-8")
      val res = ConfigFactory.parseReader(dataReader).resolve().root().as(reader)
      his.close()
      res
    }

    private def _save(t: T) {
      val vc = VersioningConf(current.get.versions :+ t)
      val data = ConfValue.write(vc).render(ConfigRenderOptions.concise()).getBytes("UTF-8")
      val hos = new HdfsOutputStream(path, true)
      hos.write(data)
      hos.sync()
      hos.close()
      current.set(vc)
    }

}

class FileConfHolder[T](implicit reader: ConfigReader[T]) extends ConfHolder[T] {
    private val current = new AtomicReference(ConfigFactory.defaultApplication().root().as(reader))
    def get(): T = current.get()
    override def set(t: T): Unit = {
      new UnsupportedOperationException("set is unsupported")
    }
}

class CrossoverConfHolder extends FileConfHolder[CrossoverConf]()
class DestributedCrossoverConfHolder(p: String) extends HDFSConfHolder[CrossoverConf](p)

import akka.actor.ActorRef
import akka.actor.FSM
import akka.actor.Props

object DistributedCrossoverConf {
  sealed trait State
  case object Uninitializated extends State
  case object Failed extends State
  case object Ready extends State

  sealed trait Data
  final case class NoConfig(subs: Set[ActorRef]) extends Data
  final case class FailedConfig(t: Throwable, subs: Set[ActorRef]) extends Data

  case object Load
  case object Get
  final case class Config[T](t: T)
  final case class UpdateConf[T](t: T)
  final case class Updated[T](t: T)
  final case class Error(t: Throwable)
  final case class SubscribeMe(ar: ActorRef)

  def props[T](path: String)(implicit reader: ConfigReader[VersioningConf[T]], writer: ConfigWriter[VersioningConf[T]]): Props =
    Props(new DistributedCrossoverConf[T](path))
}

import DistributedCrossoverConf._
import scala.util.Try
import scala.util.Success
import scala.util.Failure

class DistributedCrossoverConf[T](path: String)(implicit reader: ConfigReader[VersioningConf[T]], writer: ConfigWriter[VersioningConf[T]]) extends FSM[State, Data] {

  private def _load(): Try[VersioningConf[T]] = Try{
    val his = new HdfsInputStream(path)
    val dataReader = new InputStreamReader(his, "UTF-8")
    val res = ConfigFactory.parseReader(dataReader).resolve().root().as(reader)
    his.close()
    res
  }

  private def _save(current: VersioningConf[T], t: T): Try[VersioningConf[T]] = Try{
    val vc = VersioningConf(current.versions :+ t)
    val data = ConfValue.write(vc).render(ConfigRenderOptions.concise()).getBytes("UTF-8")
    val hos = new HdfsOutputStream(path, true)
    hos.write(data)
    hos.sync()
    hos.close()
    vc
  }

  final case class ConfigData(conf: VersioningConf[T], subs: Set[ActorRef]) extends Data

  startWith(Uninitializated, NoConfig(Set.empty))

  when(Uninitializated) {
    case Event(Load, nc: NoConfig) => 
      _load() match {
        case Success(c) => goto(Ready) using ConfigData(c, nc.subs)
        case Failure(t) => goto(Failed) using FailedConfig(t, nc.subs)
      }
    case Event(SubscribeMe(s), nc: NoConfig) => stay using NoConfig(nc.subs + s)
  }

  onTransition {
    case Ready -> Failed => self ! Load
  }

  when(Ready) {
    case Event(Get, ConfigData(c, _)) => 
      sender() ! Config(c.versions.last)
      stay
    case Event(UpdateConf(nc), ConfigData(c, fs)) =>
      log.info("Got update conf from {}", sender())
      _save(c, nc.asInstanceOf[T]) match {
        case Success(c) => 
          val conf = c.versions.last
          sender ! Updated(conf)
          fs.foreach(_ ! Updated(conf))
          goto(Ready) using ConfigData(c, fs)
        case Failure(c) => 
          sender ! Error(c)
          goto(Failed) using FailedConfig(c, fs)
      }
    case Event(SubscribeMe(s), cd: ConfigData) => stay using cd.copy(subs = cd.subs + s)
  }

  when(Failed) {
    case Event(Load, nc: NoConfig) => 
      _load() match {
        case Success(c) => goto(Ready) using ConfigData(c, nc.subs)
        case Failure(c) => goto(Failed) using FailedConfig(c, nc.subs)
      }
    case Event(SubscribeMe(s), nc: NoConfig) => stay using NoConfig(nc.subs + s)
    case Event(Get, fc @ FailedConfig(t, _)) => 
      sender ! Error(t)
      stay using fc
  }

  initialize()
  self ! Load
}
