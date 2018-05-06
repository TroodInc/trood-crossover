package trood.crossover.conf

import ConfigTools._
import ProxyConfigImplicits._
import com.metamx.common.Granularity
import com.metamx.tranquility.beam.ClusteredBeamTuning
import com.metamx.tranquility.druid.{DruidDimensions, DruidRollup, SpecificDruidDimensions}
import com.typesafe.config.{ConfigFactory, ConfigRenderOptions, ConfigValue}
import io.druid.granularity.QueryGranularity
import io.druid.jackson.DefaultObjectMapper
import io.druid.query.aggregation._
import org.joda.time.Period

import scala.language.postfixOps
import scala.util.{Failure, Success, Try}
import io.druid.granularity.DurationGranularity

/**
  * Example of the full config:
{
    "zookeeper": {
        "connect": "172.25.0.160:2181"
    },
    "rabbit": {
        "connection": {
            virtual-host: "/",
            hosts: ["127.0.0.1:5672"],
            username: "guest",
            password: "guest",
            port: 5672,
            connection-timeout: 3000,
            ssl: false
        }
    },
    "archiver": {
        "path": "events/archive"
    },
    "cubes": [<cube1>, <cube2> ...],
    "ver": 1
}
  *
  * Example of the cube config:
cube: {
	"name": "Hello world!",
	"rollup": {
		"dimensions":  ["empId", "depId"],
		"queryGranularity": "HOUR",
		"aggregates": [
			{"type": "count", "name": "count"},
			{"type": "doubleSum", "fieldName": "x", "name": "x"}
		]
	},
	"tuning": {
		"segmentGranularity": "DAY",
		"warmingPeriod": "",
		"windowPeriod": "",
		"partitions": 1,
		"replicants": 1
	}, //in our case the type of granularity always will be uniform (only uniform supports realtime)
	"sources": {
  		//how to map some event to dementions and metrics
        // mapping event's fields to dimensions and metrics for particular event's class
		"sdf": {
			"empId": "empId",
			"depId": "depId"
		}
	}
}
  *
  *
  *
  */


object CubeConf {
    private val objectMapper = new DefaultObjectMapper
    private val defaultTune = new ClusteredBeamTuning()

    private def DruidConfigReader[T](implicit m: Manifest[T]) = new ConfigReader[T] {
        override def read(conf: ConfigValue): ConfigResult[T] =
            Try(objectMapper.readValue(conf.render(ConfigRenderOptions.concise()), m.runtimeClass.asInstanceOf[Class[T]])) match {
                case Success(t) => ConfigSuccess(t)
                case Failure(e) => ConfigError(e.getMessage)
            }
    }

    private def DruidConfigWriter[T](implicit m: Manifest[T]) = new ConfigWriter[T] {
      override def write(t: T): ConfigValue = ConfigFactory.parseString(objectMapper.writeValueAsString(t)).root()
    }

    implicit def aggregatorConfReader = DruidConfigReader[AggregatorFactory]
    implicit def aggregatorConfWriter = DruidConfigWriter[AggregatorFactory]

    implicit def rollupConfReader = new ConfigReader[DruidRollup] {
        override def read(conf: ConfigValue): ConfigResult[DruidRollup] = (
            (conf \ "dimensions").read[Seq[String]].map[DruidDimensions](SpecificDruidDimensions(_)) and
                (conf \ "aggregators").read[Seq[AggregatorFactory]] and
                (conf \ "indexGranularity").read[String].map(QueryGranularity.fromString) $).lift(DruidRollup.apply)
    }

    val granularityMills = Map(
      1000L -> "SECOND", 
      60 * 1000L -> "MINUTE", 
      15 * 60 * 1000L -> "FIFTEEN_MINUTE", 
      30 * 60 * 1000L -> "THIRTY_MINUTE", 
      3600 * 1000L -> "HOUR", 
      24 * 3600 * 1000L -> "DAY")

    implicit def rollupConfWriter = new ConfigWriter[DruidRollup] {
      override def write(dr: DruidRollup): ConfigValue = (
        (__ \ "dimensions").write[DruidDimensions, Seq[String]](d => d.asInstanceOf[SpecificDruidDimensions].dimensions) and
          (__ \ "aggregators").write[Seq[AggregatorFactory]] and
          (__ \ "indexGranularity").write[QueryGranularity, String](q => granularityMills(q.asInstanceOf[DurationGranularity].getDuration())) $).unlift((d: DruidRollup) => Some(d.dimensions, d.aggregators, d.indexGranularity))(dr)
    }

    implicit def tuneConfReader = new ConfigReader[ClusteredBeamTuning] {
        override def read(conf: ConfigValue): ConfigResult[ClusteredBeamTuning] = (
            (conf \ "segmentGranularity").read[String].map(Granularity.valueOf) and
                (conf \ "warmingPeriod").read[Option[String]].map(_.fold(new Period(0))(new Period(_))) and
                (conf \ "windowPeriod").read[Option[String]].map(_.fold(new Period("PT10M"))(new Period(_))) and
                (conf \ "partitions").read[Option[Int]].map(_.getOrElse(1)) and
                (conf \ "replicants").read[Option[Int]].map(_.getOrElse(1)) and
                (conf \ "minSegmentsPerBeam").read[Option[Int]].map(_.getOrElse(1)) and
                (conf \ "maxSegmentsPerBeam").read[Option[Int]].map(_.getOrElse(1)) $).lift(ClusteredBeamTuning.apply)
    }

    implicit def tuneConfWriter = new ConfigWriter[ClusteredBeamTuning] {
      override def write(cbt: ClusteredBeamTuning): ConfigValue = (
            (__ \ "segmentGranularity").write[Granularity, String](_.name) and
                (__ \ "warmingPeriod").write[Period, String](_.toString) and
                (__ \ "windowPeriod").write[Period, String](_.toString) and
                (__ \ "partitions").write[Int] and
                (__ \ "replicants").write[Int] and
                (__ \ "minSegmentsPerBeam").write[Int] and
                (__ \ "maxSegmentsPerBeam").write[Int] $).unlift(ClusteredBeamTuning.unapply)(cbt)
    }

    implicit def cubeConfReader = new ConfigReader[CubeConf] {
        override def read(conf: ConfigValue): ConfigResult[CubeConf] = (
            (conf \ "name").read[String] and
                (conf \ "rollup").read[DruidRollup] and
                (conf \ "tune").read[Option[ClusteredBeamTuning]].map(_.getOrElse(defaultTune)) and
                (conf \ "sources").read[Map[String, Map[String, String]]] $).lift(CubeConf.apply)
    }

    implicit def cubeConfWriter = new ConfigWriter[CubeConf] {
      override def write(cc: CubeConf): ConfigValue = (
        (__ \ "name").write[String] and 
          (__ \ "rollup").write[DruidRollup] and 
          (__ \ "tune").write[ClusteredBeamTuning] and 
          (__  \ "sources").write[Map[String, Map[String, String]]] $).unlift(CubeConf.unapply)(cc)
    }
}

case class CubeConf(name: String, rollup: DruidRollup, tune: ClusteredBeamTuning, sources: Map[String, Map[String, String]]) {
    override def equals(obj: scala.Any): Boolean = obj match {
        case CubeConf(n, r, t, s) => name == n &&
            rollup.dimensions == r.dimensions &&
            rollup.aggregators.sortBy(_.getName) == r.aggregators.sortBy(_.getName) &&
            rollup.indexGranularity == r.indexGranularity &&
            tune == t && sources == s
        case _ => false
    }

    override def hashCode(): Int = (name, (rollup.dimensions, rollup.aggregators.sortBy(_.getName), rollup.indexGranularity), tune, sources).##
}

object RabbitConnConf {
    val default = RabbitConnConf("/", Seq("127.0.0.1:5672"), "guest", "guest", None, None)
    implicit def cubeConfReader = new ConfigReader[RabbitConnConf] {
        override def read(conf: ConfigValue): ConfigResult[RabbitConnConf] = (
            (conf \ "virtual-host").readWithDefault("/") and
                (conf \ "hosts").readWithDefault(Seq("localhost")) and
                (conf \ "username").readWithDefault("guest") and
                (conf \ "password").readWithDefault("guest") and
                (conf \ "ssl").read[Option[Boolean]] and
                (conf \ "connectionTimeout").read[Option[Int]] $).lift(RabbitConnConf.apply)
    }

    implicit def cubeConfWriter = new ConfigWriter[RabbitConnConf] {
        override def write(rcc: RabbitConnConf): ConfigValue = (
            (__ \ "virtual-host").write[String] and
                (__ \ "hosts").write[Seq[String]] and
                (__ \ "username").write[String] and
                (__ \ "password").write[String] and
                (__ \ "ssl").write[Option[Boolean]] and
                (__ \ "connectionTimeout").write[Option[Int]] $).unlift(RabbitConnConf.unapply)(rcc)
    }
}

case class RabbitConnConf(virtualHost: String, hosts: Seq[String], username: String, password: String,
                          ssl: Option[Boolean], connectionTimeout: Option[Int])

object RabbitQueueConf {
    val nameDefault = "events"
    val default = RabbitQueueConf(nameDefault, 20)

    implicit def cubeConfReader = new ConfigReader[RabbitQueueConf] {
        override def read(conf: ConfigValue): ConfigResult[RabbitQueueConf] = (
                (conf \ "name").readWithDefault(nameDefault) and
                (conf \ "prefetch").readWithDefault(20) $).lift(RabbitQueueConf.apply)
    }

    implicit def cubeConfWriter = new ConfigWriter[RabbitQueueConf] {
        override def write(rqc: RabbitQueueConf): ConfigValue = (
                (__ \ "name").write[String] and
                (__ \ "prefetch").write[Int] $).unlift(RabbitQueueConf.unapply)(rqc)
    }
}

case class RabbitQueueConf(name: String, prefetch: Int)

object RabbitConf {
    val default = RabbitConf(RabbitConnConf.default, RabbitQueueConf.default)

    implicit def cubeConfReader = new ConfigReader[RabbitConf] {
        override def read(conf: ConfigValue): ConfigResult[RabbitConf] = (
            (conf \ "connection").readWithDefault(RabbitConnConf.default) and
                (conf \ "queue").readWithDefault(RabbitQueueConf.default) $).lift(RabbitConf.apply)
    }

    implicit def cubeConfWriter = new ConfigWriter[RabbitConf] {
        override def write(rc: RabbitConf): ConfigValue = (
            (__ \ "connection").write[RabbitConnConf] and
                (__ \ "queue").write[RabbitQueueConf] $).unlift(RabbitConf.unapply)(rc)
    }
}


case class RabbitConf(conn: RabbitConnConf, queue: RabbitQueueConf)


object ArchiverConf {
    val defaultPath = "events/archive"
    val default = ArchiverConf(defaultPath)

    implicit def archiveConfReader = new ConfigReader[ArchiverConf] {
        override def read(conf: ConfigValue): ConfigResult[ArchiverConf] =
            (conf \ "path").readWithDefault(defaultPath).map(ArchiverConf.apply)
    }

    implicit def archiveConfWriter = new ConfigWriter[ArchiverConf] {
        override def write(ac: ArchiverConf): ConfigValue = (
            (__ \ "path").write[String] $).unlift(ArchiverConf.unapply)(ac)
    }
}

case class ArchiverConf(path: String)

object CrossoverConf {

    implicit def cubeConfReader = new ConfigReader[CrossoverConf] {
        override def read(conf: ConfigValue): ConfigResult[CrossoverConf] = (
            (conf \ "rabbit").readWithDefault(RabbitConf.default) and
                (conf \ "zookeeper" \ "connect").read[String] and
                (conf \ "archiver").readWithDefault(ArchiverConf.default) and
                (conf \ "cubes").read[Seq[CubeConf]] and
                (conf \ "ver").readWithDefault(1) $).lift(CrossoverConf.apply)
    }

    implicit def cubeConfWriter = new ConfigWriter[CrossoverConf] {
        override def write(cc: CrossoverConf): ConfigValue = (
            (__ \ "rabbit").write[RabbitConf] and
                (__ \ "zookeeper" \ "connect").write[String] and
                (__ \ "archiver").write[ArchiverConf] and
                (__ \ "cubes").write[Seq[CubeConf]] and
                (__ \ "ver").write[Int] $).unlift(CrossoverConf.unapply)(cc)
    }

    def read(): CrossoverConf = ConfigFactory.defaultApplication().root().as[CrossoverConf]

}

case class CrossoverConf(rabbit: RabbitConf, zkConnect: String, archiver: ArchiverConf, cubes: Seq[CubeConf], version: Int)
