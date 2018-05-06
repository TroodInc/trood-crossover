package trood.crossover.conf

import java.io.{ByteArrayOutputStream, File}
import java.net.URL

import com.typesafe.config._

import scala.annotation.implicitNotFound
import scala.collection.JavaConversions._
import scala.collection.generic.CanBuildFrom
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}


object ProxyConfigImplicits {

  trait ConfValue {
    val c: ConfigValue

    def as[T](implicit reader: ConfigReader[T]): T =
      reader.read(c).fold[T](e => throw new Error(e.mkString(",")))(v => v)

    def \(field: String): ConfigValue = new ConfigValueUndef(field, Some(c.origin()))

    def read[T](implicit reader: ConfigReader[T]): ConfigResult[T] = reader.read(c)
    def readWithDefault[T](d: T)(implicit reader: ConfigReader[Option[T]]): ConfigResult[T] = reader.read(c).map(_.getOrElse(d))
    def read[T, R](t: T => R)(implicit reader: ConfigReader[T]): ConfigResult[R] = reader.read(c).map(t)
  }

  class SimpleConfValue(override val c: ConfigValue) extends ConfValue

  def undefinedSimpleConf(f: String, o: Option[ConfigOrigin]) = new SimpleConfValue(new ConfigValueUndef(f, o))

  class ObjConfValue(override val c: ConfigObject) extends ConfValue {
    override def \(field: String): ConfigValue = if(c.containsKey(field)) c.get(field) else new ConfigValueUndef(field, Some(c.origin()))
  }

  import scala.language.implicitConversions
  implicit def configValue2ReadableConf(cv: ConfigValue): ConfValue = cv match {
    case u: ConfigValueUndef => new SimpleConfValue(u)
    case c => c.valueType() match {
      case ConfigValueType.STRING => new SimpleConfValue(cv);
      case ConfigValueType.NUMBER => new SimpleConfValue(cv)
      case ConfigValueType.BOOLEAN => new SimpleConfValue(cv)
      case ConfigValueType.LIST => new SimpleConfValue(cv)
      case ConfigValueType.OBJECT => new ObjConfValue(cv.asInstanceOf[ConfigObject])
      case ConfigValueType.NULL => undefinedSimpleConf("<null>", Some(cv.origin()))
    }
  }

  class Conf(c: Config) {
    def \(node: String): ConfValue = if(c.hasPath(node)) c.getValue(node) else undefinedSimpleConf(node, Some(c.origin()))
  }
  implicit def Config2ReadableConf(c: Config): Conf = new Conf(c)

}

object ConfValue {
  def write[T](o: T)(implicit writer: ConfigWriter[T]): ConfigValue = writer.write(o)
}

@implicitNotFound("No Config reader found for type ${T}. Try to implement an implicit ConfigReader.")
trait ConfigReader[T] {

  def read(conf: ConfigValue): ConfigResult[T]

}

object ConfigReader extends DefaultConfigReaders

trait DefaultConfigReaders {

  implicit object RawCfgReader extends ConfigReader[Config] {
    def read(c: ConfigValue): ConfigResult[Config] = c.valueType() match {
      case ConfigValueType.OBJECT => ConfigSuccess(c.asInstanceOf[ConfigObject].toConfig)
      case t => ConfigError(s"expected: Config, got: $t.(line: ${c.origin().lineNumber()}})")
    }
  }

  implicit object StringCfgReader extends ConfigReader[String] {
    def read(c: ConfigValue): ConfigResult[String] = c.valueType() match {
      case ConfigValueType.STRING => ConfigSuccess(c.unwrapped().asInstanceOf[String])
      case t => ConfigError(s"expected: String, got: ${t}.(line: ${c.origin().lineNumber()}})")
    }
  }

  implicit object LongCfgReader extends ConfigReader[Long] {
    def read(c: ConfigValue): ConfigResult[Long] = c.valueType() match {
      case ConfigValueType.NUMBER => ConfigSuccess(c.unwrapped().asInstanceOf[Number].longValue())
      case t => ConfigError(s"expected: Long, got: ${t}.(line: ${c.origin().lineNumber()}})")
    }
  }

  implicit object IntCfgReader extends ConfigReader[Int] {
    def read(c: ConfigValue): ConfigResult[Int] = c.valueType() match {
      case ConfigValueType.NUMBER => ConfigSuccess(c.unwrapped().asInstanceOf[Number].intValue())
      case t => ConfigError(s"expected: Int, got: ${t}.(line: ${c.origin().lineNumber()}})")
    }
  }

  implicit object JavaFloatCfgReader extends ConfigReader[java.lang.Float] {
    def read(c: ConfigValue): ConfigResult[java.lang.Float] = c.valueType() match {
      case ConfigValueType.NUMBER => ConfigSuccess(c.unwrapped().asInstanceOf[Number].floatValue())
      case t => ConfigError(s"expected: Float, got: ${t}.(line: ${c.origin().lineNumber()}})")
    }
  }

  implicit object BooleanCfgReader extends ConfigReader[Boolean] {
    def read(c: ConfigValue): ConfigResult[Boolean] = c.valueType() match {
      case ConfigValueType.BOOLEAN => ConfigSuccess(c.unwrapped().asInstanceOf[Boolean])
      case t => ConfigError(s"expected: Boolean, got: ${t}.(line: ${c.origin().lineNumber()}})")
    }
  }

  implicit def optionCfgReader[T](implicit reader: ConfigReader[T]) = new ConfigReader[Option[T]]{
    def read(c: ConfigValue): ConfigResult[Option[T]] = c match {
      case _: ConfigValueUndef => ConfigSuccess(None)
      case cv => reader.read(cv) match {
        case ConfigSuccess(res) => ConfigSuccess(Option(res))
        case e: ConfigError => e
      }
    }
  }

  implicit def arrayCfgReader[T : ClassTag](implicit reader: ConfigReader[T]): ConfigReader[Array[T]] = new ConfigReader[Array[T]] {
    import scala.collection.JavaConverters._
    import ProxyConfigImplicits._
    def read(c: ConfigValue): ConfigResult[Array[T]] = c.valueType() match {
      case ConfigValueType.LIST => ConfigSuccess(c.asInstanceOf[ConfigList].asScala.map(e => e.as[T](reader)).toArray[T])
      case t => ConfigError(s"expected: List, got: ${t}.(line: ${c.origin().lineNumber()}})")
    }
  }

  implicit def mapCfgReader[T: ClassTag](implicit reader: ConfigReader[T]): ConfigReader[Map[String,T]] = new ConfigReader[Map[String,T]] {
    import scala.collection.JavaConverters._
    import ProxyConfigImplicits._
    def read(c: ConfigValue): ConfigResult[Map[String,T]] = c.valueType() match {
      case ConfigValueType.OBJECT => ConfigSuccess(c.asInstanceOf[ConfigObject].asScala.map(e => e._1 -> e._2.as[T](reader)).toMap[String, T])
      case t => ConfigError(s"expected: Map, got: ${t}.(line: ${c.origin().lineNumber()}})")
    }
  }

  implicit val higherKinded = scala.language.higherKinds
  implicit def traversableCfgReader[I[_], A](implicit cb: CanBuildFrom[I[_], A, I[A]], jr: ConfigReader[A]) = new ConfigReader[I[A]] {
    import scala.collection.JavaConverters._
    def read(c: ConfigValue): ConfigResult[I[A]] = {
      c match {
        case _: ConfigValueUndef => ConfigSuccess(cb().result())
        case _ => c.valueType() match {
          case ConfigValueType.LIST => {
            val res = c.asInstanceOf[ConfigList].asScala.zipWithIndex.map { case (jv, idx) => jr.read(jv) match {
              case ConfigSuccess(j) => Right(j)
              case ConfigError(e) => Left("element(" + idx + "): " + e.mkString("; "))
            }
            }

            val errors = res.filter(_.isLeft)
            if (errors.isEmpty) {
              val builder = cb()
              res.collect{ case Right(e) => builder += e }
              ConfigSuccess(builder.result())
            }
            else {
              ConfigError(errors.collect{ case Left(e) => e }.toSeq)
            }
          }
          case ConfigValueType.STRING => jr.read(c) match {
            case ConfigSuccess(j) => ConfigSuccess((cb() += j).result())
            case ConfigError(er) => ConfigError(er)
          }
          case ConfigValueType.NUMBER => jr.read(c) match {
            case ConfigSuccess(j) => ConfigSuccess((cb() += j).result())
            case ConfigError(er) => ConfigError(er)
          }
          case ConfigValueType.BOOLEAN => jr.read(c) match {
            case ConfigSuccess(j) => ConfigSuccess((cb() += j).result())
            case ConfigError(er) => ConfigError(er)
          }
          case t => ConfigError(s"expected: List, got: ${t}.(line: ${c.origin().lineNumber()}})")
        }
      }
    }
  }

  implicit def contentCfgReader = new ConfigReader[Content] {
    def read(c: ConfigValue): ConfigResult[Content] = c.valueType() match {
      case ConfigValueType.STRING =>
        def readUrl(u: String): Try[Content] = Try(Content(new URL(u)))
        val url = c.unwrapped().asInstanceOf[String]
        readUrl(url) match {
          case Success(d) => ConfigSuccess(d)
          case Failure(e) =>
            ConfigError(s"error [${e.getMessage}] while reading contend from URL [$url](line: ${c.origin().lineNumber()}})")
        }
      case t => ConfigError(s"expected: String, got: ${t}.(line: ${c.origin().lineNumber()}})")
    }
  }

}

case class Content(url: URL) {

  def data: Array[Byte] = {
    val in = url.openStream()
    val output = new ByteArrayOutputStream()
    val buffer = new Array[Byte](8192)
    var n = 0
    while(-1 != {n = in.read(buffer); n}) {
      output.write(buffer, 0, n)
    }
    in.close()
    output.toByteArray
  }

}

trait CApplicative

case class CApplicative1[A1](t: ConfigResult[A1]) extends CApplicative { def lift[T](f: A1 => T): ConfigResult[T] = t map f }
case class CApplicative2[A1, A2](t: ConfigResult[(A1, A2)]) extends CApplicative { def lift[T](f: (A1, A2) => T): ConfigResult[T] = t map f.tupled }
case class CApplicative3[A1, A2, A3](t: ConfigResult[(A1, A2, A3)]) extends CApplicative { def lift[T](f: (A1, A2, A3) => T): ConfigResult[T] = t map f.tupled }
case class CApplicative4[A1, A2, A3, A4](t: ConfigResult[(A1, A2, A3, A4)]) extends CApplicative { def lift[T](f: (A1, A2, A3, A4) => T): ConfigResult[T] = t map f.tupled }
case class CApplicative5[A1, A2, A3, A4, A5](t: ConfigResult[(A1, A2, A3, A4, A5)]) extends CApplicative { def lift[T](f: (A1, A2, A3, A4, A5) => T): ConfigResult[T] = t map f.tupled }
case class CApplicative6[A1, A2, A3, A4, A5, A6](t: ConfigResult[(A1, A2, A3, A4, A5, A6)]) extends CApplicative { def lift[T](f: (A1, A2, A3, A4, A5, A6) => T): ConfigResult[T] = t map f.tupled }
case class CApplicative7[A1, A2, A3, A4, A5, A6, A7](t: ConfigResult[(A1, A2, A3, A4, A5, A6, A7)]) extends CApplicative { def lift[T](f: (A1, A2, A3, A4, A5, A6, A7) => T): ConfigResult[T] = t map f.tupled }
case class CApplicative8[A1, A2, A3, A4, A5, A6, A7, A8](t: ConfigResult[(A1, A2, A3, A4, A5, A6, A7, A8)]) extends CApplicative { def lift[T](f: (A1, A2, A3, A4, A5, A6, A7, A8) => T): ConfigResult[T] = t map f.tupled }
case class CApplicative9[A1, A2, A3, A4, A5, A6, A7, A8, A9](t: ConfigResult[(A1, A2, A3, A4, A5, A6, A7, A8, A9)]) extends CApplicative { def lift[T](f: (A1, A2, A3, A4, A5, A6, A7, A8, A9) => T): ConfigResult[T] = t map f.tupled }
case class CApplicative10[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10](t: ConfigResult[(A1, A2, A3, A4, A5, A6, A7, A8, A9, A10)]) extends CApplicative { def lift[T](f: (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10) => T): ConfigResult[T] = t map f.tupled }
case class CApplicative11[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11](t: ConfigResult[(A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11)]) extends CApplicative { def lift[T](f: (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11) => T): ConfigResult[T] = t map f.tupled }
case class CApplicative12[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12](t: ConfigResult[(A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12)]) extends CApplicative { def lift[T](f: (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12) => T): ConfigResult[T] = t map f.tupled }
case class CApplicative13[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13](t: ConfigResult[(A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13)]) extends CApplicative { def lift[T](f: (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13) => T): ConfigResult[T] = t map f.tupled }
case class CApplicative14[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14](t: ConfigResult[(A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14)]) extends CApplicative { def lift[T](f: (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14) => T): ConfigResult[T] = t map f.tupled }
case class CApplicative15[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15](t: ConfigResult[(A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15)]) extends CApplicative { def lift[T](f: (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15) => T): ConfigResult[T] = t map f.tupled }
case class CApplicative16[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16](t: ConfigResult[(A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16)]) extends CApplicative { def lift[T](f: (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16) => T): ConfigResult[T] = t map f.tupled }
case class CApplicative17[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17](t: ConfigResult[(A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17)]) extends CApplicative { def lift[T](f: (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17) => T): ConfigResult[T] = t map f.tupled }
case class CApplicative18[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18](t: ConfigResult[(A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18)]) extends CApplicative { def lift[T](f: (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18) => T): ConfigResult[T] = t map f.tupled }
case class CApplicative19[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19](t: ConfigResult[(A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19)]) extends CApplicative { def lift[T](f: (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19) => T): ConfigResult[T] = t map f.tupled }
case class CApplicative20[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20](t: ConfigResult[(A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20)]) extends CApplicative { def lift[T](f: (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20) => T): ConfigResult[T] = t map f.tupled }
case class CApplicative21[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, A21](t: ConfigResult[(A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, A21)]) extends CApplicative { def lift[T](f: (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, A21) => T): ConfigResult[T] = t map f.tupled }
case class CApplicative22[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, A21, A22](t: ConfigResult[(A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, A21, A22)]) extends CApplicative { def lift[T](f: (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, A21, A22) => T): ConfigResult[T] = t map f.tupled }

object ConfigTools {
  import scala.language.implicitConversions

  implicit def cBuilder2[A1, A2](a1: ConfigSuccess[A1], a2: ConfigSuccess[A2]) = (a1, a2) match { case (ConfigSuccess(v1), ConfigSuccess(v2)) => ConfigSuccess((v1, v2)) }
  implicit def cBuilder3[A1, A2, A3](a: ConfigSuccess[(A1, A2)], b: ConfigSuccess[A3]) = (a, b) match { case (ConfigSuccess(v1), ConfigSuccess(v2)) => ConfigSuccess((v1._1, v1._2, v2)) }
  implicit def jBuilder4[A1, A2, A3, A4](a:  ConfigSuccess[(A1, A2, A3)], b:  ConfigSuccess[A4]) = (a, b) match { case ( ConfigSuccess(v1),  ConfigSuccess(v2)) =>  ConfigSuccess((v1._1, v1._2, v1._3, v2)) }
  implicit def jBuilder5[A1, A2, A3, A4, A5](a:  ConfigSuccess[(A1, A2, A3, A4)], b:  ConfigSuccess[A5]) = (a, b) match { case ( ConfigSuccess(v1),  ConfigSuccess(v2)) =>  ConfigSuccess((v1._1, v1._2, v1._3, v1._4, v2)) }
  implicit def jBuilder6[A1, A2, A3, A4, A5, A6](a:  ConfigSuccess[(A1, A2, A3, A4, A5)], b:  ConfigSuccess[A6]) = (a, b) match { case ( ConfigSuccess(v1),  ConfigSuccess(v2)) =>  ConfigSuccess((v1._1, v1._2, v1._3, v1._4, v1._5, v2)) }
  implicit def jBuilder7[A1, A2, A3, A4, A5, A6, A7](a:  ConfigSuccess[(A1, A2, A3, A4, A5, A6)], b:  ConfigSuccess[A7]) = (a, b) match { case ( ConfigSuccess(v1),  ConfigSuccess(v2)) =>  ConfigSuccess((v1._1, v1._2, v1._3, v1._4, v1._5, v1._6, v2)) }
  implicit def jBuilder8[A1, A2, A3, A4, A5, A6, A7, A8](a:  ConfigSuccess[(A1, A2, A3, A4, A5, A6, A7)], b:  ConfigSuccess[A8]) = (a, b) match { case ( ConfigSuccess(v1),  ConfigSuccess(v2)) =>  ConfigSuccess((v1._1, v1._2, v1._3, v1._4, v1._5, v1._6, v1._7, v2)) }
  implicit def jBuilder9[A1, A2, A3, A4, A5, A6, A7, A8, A9](a:  ConfigSuccess[(A1, A2, A3, A4, A5, A6, A7, A8)], b:  ConfigSuccess[A9]) = (a, b) match { case ( ConfigSuccess(v1),  ConfigSuccess(v2)) =>  ConfigSuccess((v1._1, v1._2, v1._3, v1._4, v1._5, v1._6, v1._7, v1._8, v2)) }
  implicit def jBuilder10[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10](a:  ConfigSuccess[(A1, A2, A3, A4, A5, A6, A7, A8, A9)], b:  ConfigSuccess[A10]) = (a, b) match { case ( ConfigSuccess(v1),  ConfigSuccess(v2)) =>  ConfigSuccess((v1._1, v1._2, v1._3, v1._4, v1._5, v1._6, v1._7, v1._8, v1._9, v2)) }
  implicit def jBuilder11[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11](a:  ConfigSuccess[(A1, A2, A3, A4, A5, A6, A7, A8, A9, A10)], b:  ConfigSuccess[A11]) = (a, b) match { case ( ConfigSuccess(v1),  ConfigSuccess(v2)) =>  ConfigSuccess((v1._1, v1._2, v1._3, v1._4, v1._5, v1._6, v1._7, v1._8, v1._9, v1._10, v2)) }
  implicit def jBuilder12[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12](a:  ConfigSuccess[(A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11)], b:  ConfigSuccess[A12]) = (a, b) match { case ( ConfigSuccess(v1),  ConfigSuccess(v2)) =>  ConfigSuccess((v1._1, v1._2, v1._3, v1._4, v1._5, v1._6, v1._7, v1._8, v1._9, v1._10, v1._11, v2)) }
  implicit def jBuilder13[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13](a:  ConfigSuccess[(A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12)], b:  ConfigSuccess[A13]) = (a, b) match { case ( ConfigSuccess(v1),  ConfigSuccess(v2)) =>  ConfigSuccess((v1._1, v1._2, v1._3, v1._4, v1._5, v1._6, v1._7, v1._8, v1._9, v1._10, v1._11, v1._12, v2)) }
  implicit def jBuilder14[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14](a:  ConfigSuccess[(A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13)], b:  ConfigSuccess[A14]) = (a, b) match { case ( ConfigSuccess(v1),  ConfigSuccess(v2)) =>  ConfigSuccess((v1._1, v1._2, v1._3, v1._4, v1._5, v1._6, v1._7, v1._8, v1._9, v1._10, v1._11, v1._12, v1._13, v2)) }
  implicit def jBuilder15[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15](a:  ConfigSuccess[(A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14)], b:  ConfigSuccess[A15]) = (a, b) match { case ( ConfigSuccess(v1),  ConfigSuccess(v2)) =>  ConfigSuccess((v1._1, v1._2, v1._3, v1._4, v1._5, v1._6, v1._7, v1._8, v1._9, v1._10, v1._11, v1._12, v1._13, v1._14, v2)) }
  implicit def jBuilder16[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16](a:  ConfigSuccess[(A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15)], b:  ConfigSuccess[A16]) = (a, b) match { case ( ConfigSuccess(v1),  ConfigSuccess(v2)) =>  ConfigSuccess((v1._1, v1._2, v1._3, v1._4, v1._5, v1._6, v1._7, v1._8, v1._9, v1._10, v1._11, v1._12, v1._13, v1._14, v1._15, v2)) }
  implicit def jBuilder17[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17](a:  ConfigSuccess[(A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16)], b:  ConfigSuccess[A17]) = (a, b) match { case ( ConfigSuccess(v1),  ConfigSuccess(v2)) =>  ConfigSuccess((v1._1, v1._2, v1._3, v1._4, v1._5, v1._6, v1._7, v1._8, v1._9, v1._10, v1._11, v1._12, v1._13, v1._14, v1._15, v1._16, v2)) }
  implicit def jBuilder18[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18](a:  ConfigSuccess[(A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17)], b:  ConfigSuccess[A18]) = (a, b) match { case ( ConfigSuccess(v1),  ConfigSuccess(v2)) =>  ConfigSuccess((v1._1, v1._2, v1._3, v1._4, v1._5, v1._6, v1._7, v1._8, v1._9, v1._10, v1._11, v1._12, v1._13, v1._14, v1._15, v1._16, v1._17, v2)) }
  implicit def jBuilder19[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19](a:  ConfigSuccess[(A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18)], b:  ConfigSuccess[A19]) = (a, b) match { case ( ConfigSuccess(v1),  ConfigSuccess(v2)) =>  ConfigSuccess((v1._1, v1._2, v1._3, v1._4, v1._5, v1._6, v1._7, v1._8, v1._9, v1._10, v1._11, v1._12, v1._13, v1._14, v1._15, v1._16, v1._17, v1._18, v2)) }
  implicit def jBuilder20[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20](a:  ConfigSuccess[(A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19)], b:  ConfigSuccess[A20]) = (a, b) match { case ( ConfigSuccess(v1),  ConfigSuccess(v2)) =>  ConfigSuccess((v1._1, v1._2, v1._3, v1._4, v1._5, v1._6, v1._7, v1._8, v1._9, v1._10, v1._11, v1._12, v1._13, v1._14, v1._15, v1._16, v1._17, v1._18, v1._19, v2)) }
  implicit def jBuilder21[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, A21](a:  ConfigSuccess[(A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20)], b:  ConfigSuccess[A21]) = (a, b) match { case ( ConfigSuccess(v1),  ConfigSuccess(v2)) =>  ConfigSuccess((v1._1, v1._2, v1._3, v1._4, v1._5, v1._6, v1._7, v1._8, v1._9, v1._10, v1._11, v1._12, v1._13, v1._14, v1._15, v1._16, v1._17, v1._18, v1._19, v1._20, v2)) }
  implicit def jBuilder22[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, A21, A22](a:  ConfigSuccess[(A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, A21)], b:  ConfigSuccess[A22]) = (a, b) match { case ( ConfigSuccess(v1),  ConfigSuccess(v2)) =>  ConfigSuccess((v1._1, v1._2, v1._3, v1._4, v1._5, v1._6, v1._7, v1._8, v1._9, v1._10, v1._11, v1._12, v1._13, v1._14, v1._15, v1._16, v1._17, v1._18, v1._19, v1._20, v1._21, v2)) }

  implicit def capplicative1[A1](t: ConfigResult[A1]) = CApplicative1(t)
  implicit def capplicative2[A1, A2](t: ConfigResult[(A1, A2)]) = CApplicative2(t)
  implicit def capplicative3[A1, A2, A3](t: ConfigResult[(A1, A2, A3)]) = CApplicative3(t)
  implicit def capplicative4[A1, A2, A3, A4](t:  ConfigResult[(A1, A2, A3, A4)]) = CApplicative4(t)
  implicit def capplicative5[A1, A2, A3, A4, A5](t:  ConfigResult[(A1, A2, A3, A4, A5)]) = CApplicative5(t)
  implicit def capplicative6[A1, A2, A3, A4, A5, A6](t:  ConfigResult[(A1, A2, A3, A4, A5, A6)]) = CApplicative6(t)
  implicit def capplicative7[A1, A2, A3, A4, A5, A6, A7](t:  ConfigResult[(A1, A2, A3, A4, A5, A6, A7)]) = CApplicative7(t)
  implicit def capplicative8[A1, A2, A3, A4, A5, A6, A7, A8](t:  ConfigResult[(A1, A2, A3, A4, A5, A6, A7, A8)]) = CApplicative8(t)
  implicit def capplicative9[A1, A2, A3, A4, A5, A6, A7, A8, A9](t:  ConfigResult[(A1, A2, A3, A4, A5, A6, A7, A8, A9)]) = CApplicative9(t)
  implicit def capplicative10[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10](t:  ConfigResult[(A1, A2, A3, A4, A5, A6, A7, A8, A9, A10)]) = CApplicative10(t)
  implicit def capplicative11[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11](t:  ConfigResult[(A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11)]) = CApplicative11(t)
  implicit def capplicative12[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12](t:  ConfigResult[(A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12)]) = CApplicative12(t)
  implicit def capplicative13[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13](t:  ConfigResult[(A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13)]) = CApplicative13(t)
  implicit def capplicative14[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14](t:  ConfigResult[(A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14)]) = CApplicative14(t)
  implicit def capplicative15[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15](t:  ConfigResult[(A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15)]) = CApplicative15(t)
  implicit def capplicative16[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16](t:  ConfigResult[(A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16)]) = CApplicative16(t)
  implicit def capplicative17[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17](t:  ConfigResult[(A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17)]) = CApplicative17(t)
  implicit def capplicative18[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18](t:  ConfigResult[(A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18)]) = CApplicative18(t)
  implicit def capplicative19[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19](t:  ConfigResult[(A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19)]) = CApplicative19(t)
  implicit def capplicative20[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20](t:  ConfigResult[(A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20)]) = CApplicative20(t)
  implicit def capplicative21[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, A21](t:  ConfigResult[(A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, A21)]) = CApplicative21(t)
  implicit def capplicative22[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, A21, A22](t:  ConfigResult[(A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, A21, A22)]) = CApplicative22(t)

}



@implicitNotFound("No Config writer found for type ${T}. Try to implement an implicit ConfigWriter.")
trait ConfigWriter[-T] {

  def write(o: T): ConfigValue

}

import scala.language.higherKinds
import scala.annotation.tailrec

trait AndContainer[C[_], T] {
  self: C[T] =>
  def and[I, R](t: C[I])(implicit b: (C[T], C[I]) => C[R]) = b(self, t)
}

case class ConfNode[T](f: (T) => (CPath, ConfigValue)) {
  def apply(v: T): (CPath, ConfigValue) = f(v)
}

case class CPath(nodes: Seq[String]) {
  def append(n: String): CPath = CPath(nodes :+ n)
  def head: String = nodes.head
  def hasTail: Boolean = nodes.size > 1
  def tail: CPath = CPath(nodes.tail)
}

object CPath {
  def apply(n: String): CPath = new CPath(Seq(n))
}

case class ConfPath(path: CPath) {
  def write[T](implicit wrt: ConfigWriter[T]) = CF(ConfNode((a: T) => (path, wrt.write(a))))
  def write[T, J](t: T => J)(implicit wrt: ConfigWriter[J]) = CF(ConfNode((a: T) => (path, wrt.write(t(a)))))
  def \ (p: String) = ConfPath(path.append(p))
}

object __ {
  def \ (path: String) = ConfPath(CPath(path))
}

trait ConfApplicative[S]{ def unlift[T](f: T => Option[S])(o: T): ConfigValue }

case class CF[C](c: C) extends AndContainer[CF, C] {
  self =>
  def $[S](implicit a: (CF[C]) => S => ConfigObject) = new ConfApplicative[S] {
    override def unlift[T](f: (T) => Option[S])(o: T): ConfigValue = f(o) match {
      case Some(u) => a(self)(u)
      case None => new ConfigValueUndef("<None>", None)
    }
  }
}

object CF {

  implicit def builder2[T1, T2](a: CF[T1], b: CF[T2]): CF[(T1, T2)] = CF(a.c, b.c)
  implicit def builder3[T1, T2, T3](a: CF[(T1, T2)], b: CF[T3]): CF[(T1, T2, T3)] = CF(a.c._1, a.c._2, b.c)
  implicit def builder4[T1, T2, T3, T4](a: CF[(T1, T2, T3)], b: CF[T4]): CF[(T1, T2, T3, T4)] = CF(a.c._1, a.c._2, a.c._3, b.c)
  implicit def builder5[T1, T2, T3, T4, T5](a: CF[(T1, T2, T3, T4)], b: CF[T5]): CF[(T1, T2, T3, T4, T5)] = CF(a.c._1, a.c._2, a.c._3, a.c._4, b.c)
  implicit def builder6[T1, T2, T3, T4, T5, T6](a: CF[(T1, T2, T3, T4, T5)], b: CF[T6]): CF[(T1, T2, T3, T4, T5, T6)] = CF(a.c._1, a.c._2, a.c._3, a.c._4, a.c._5, b.c)
  implicit def builder7[T1, T2, T3, T4, T5, T6, T7](a: CF[(T1, T2, T3, T4, T5, T6)], b: CF[T7]): CF[(T1, T2, T3, T4, T5, T6, T7)] = CF(a.c._1, a.c._2, a.c._3, a.c._4, a.c._5, a.c._6, b.c)
  implicit def builder8[T1, T2, T3, T4, T5, T6, T7, T8](a: CF[(T1, T2, T3, T4, T5, T6, T7)], b: CF[T8]): CF[(T1, T2, T3, T4, T5, T6, T7, T8)] = CF(a.c._1, a.c._2, a.c._3, a.c._4, a.c._5, a.c._6, a.c._7, b.c)
  implicit def builder9[T1, T2, T3, T4, T5, T6, T7, T8, T9](a: CF[(T1, T2, T3, T4, T5, T6, T7, T8)], b: CF[T9]): CF[(T1, T2, T3, T4, T5, T6, T7, T8, T9)] = CF(a.c._1, a.c._2, a.c._3, a.c._4, a.c._5, a.c._6, a.c._7, a.c._8, b.c)
  implicit def builder10[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10](a: CF[(T1, T2, T3, T4, T5, T6, T7, T8, T9)], b: CF[T10]): CF[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10)] = CF(a.c._1, a.c._2, a.c._3, a.c._4, a.c._5, a.c._6, a.c._7, a.c._8, a.c._9, b.c)
  implicit def builder11[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11](a: CF[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10)], b: CF[T11]): CF[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11)] = CF(a.c._1, a.c._2, a.c._3, a.c._4, a.c._5, a.c._6, a.c._7, a.c._8, a.c._9, a.c._10, b.c)
  implicit def builder12[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12](a: CF[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11)], b: CF[T12]): CF[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12)] = CF(a.c._1, a.c._2, a.c._3, a.c._4, a.c._5, a.c._6, a.c._7, a.c._8, a.c._9, a.c._10, a.c._11, b.c)
  implicit def builder13[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13](a: CF[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12)], b: CF[T13]): CF[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13)] = CF(a.c._1, a.c._2, a.c._3, a.c._4, a.c._5, a.c._6, a.c._7, a.c._8, a.c._9, a.c._10, a.c._11, a.c._12, b.c)
  implicit def builder14[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14](a: CF[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13)], b: CF[T14]): CF[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14)] = CF(a.c._1, a.c._2, a.c._3, a.c._4, a.c._5, a.c._6, a.c._7, a.c._8, a.c._9, a.c._10, a.c._11, a.c._12, a.c._13, b.c)
  implicit def builder15[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15](a: CF[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14)], b: CF[T15]): CF[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15)] = CF(a.c._1, a.c._2, a.c._3, a.c._4, a.c._5, a.c._6, a.c._7, a.c._8, a.c._9, a.c._10, a.c._11, a.c._12, a.c._13, a.c._14, b.c)
  implicit def builder16[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16](a: CF[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15)], b: CF[T16]): CF[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16)] = CF(a.c._1, a.c._2, a.c._3, a.c._4, a.c._5, a.c._6, a.c._7, a.c._8, a.c._9, a.c._10, a.c._11, a.c._12, a.c._13, a.c._14, a.c._15, b.c)
  implicit def builder17[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17](a: CF[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16)], b: CF[T17]): CF[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17)] = CF(a.c._1, a.c._2, a.c._3, a.c._4, a.c._5, a.c._6, a.c._7, a.c._8, a.c._9, a.c._10, a.c._11, a.c._12, a.c._13, a.c._14, a.c._15, a.c._16, b.c)
  implicit def builder18[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18](a: CF[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17)], b: CF[T18]): CF[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18)] = CF(a.c._1, a.c._2, a.c._3, a.c._4, a.c._5, a.c._6, a.c._7, a.c._8, a.c._9, a.c._10, a.c._11, a.c._12, a.c._13, a.c._14, a.c._15, a.c._16, a.c._17, b.c)
  implicit def builder19[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19](a: CF[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18)], b: CF[T19]): CF[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19)] = CF(a.c._1, a.c._2, a.c._3, a.c._4, a.c._5, a.c._6, a.c._7, a.c._8, a.c._9, a.c._10, a.c._11, a.c._12, a.c._13, a.c._14, a.c._15, a.c._16, a.c._17, a.c._18, b.c)
  implicit def builder20[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20](a: CF[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19)], b: CF[T20]): CF[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20)] = CF(a.c._1, a.c._2, a.c._3, a.c._4, a.c._5, a.c._6, a.c._7, a.c._8, a.c._9, a.c._10, a.c._11, a.c._12, a.c._13, a.c._14, a.c._15, a.c._16, a.c._17, a.c._18, a.c._19, b.c)
  implicit def builder21[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21](a: CF[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20)], b: CF[T21]): CF[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21)] = CF(a.c._1, a.c._2, a.c._3, a.c._4, a.c._5, a.c._6, a.c._7, a.c._8, a.c._9, a.c._10, a.c._11, a.c._12, a.c._13, a.c._14, a.c._15, a.c._16, a.c._17, a.c._18, a.c._19, a.c._20, b.c)
  implicit def builder22[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22](a: CF[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21)], b: CF[T22]): CF[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22)] = CF(a.c._1, a.c._2, a.c._3, a.c._4, a.c._5, a.c._6, a.c._7, a.c._8, a.c._9, a.c._10, a.c._11, a.c._12, a.c._13, a.c._14, a.c._15, a.c._16, a.c._17, a.c._18, a.c._19, a.c._20, a.c._21, b.c)



  import scala.language.implicitConversions
  def toConfValue[A](elems: (CPath, A)*) = {
    def _drill(nodes: Seq[(CPath, A)]): ConfigObject = {
      val split = nodes.groupBy{
        case (_, _: ConfigValueUndef) => (-1, "")
        case (cp, v) if cp.hasTail => (1, cp.head)
        case _  => (0, "")
      }
      val obj = split.getOrElse((0, ""), Seq.empty).map{case (p, v) => (p.head, v)}.toMap
      val deeper = split.collect{case ((1, path), s) => (path, _drill(s.map{case (p, v) => (p.tail, v)}))}
      ConfigValueFactory.fromMap(mapAsJavaMap(obj ++ deeper))
    }
    _drill(elems)
  }
  implicit def applicative1[A1](t: CF[ConfNode[A1]]) = (a: A1) => toConfValue(t.c(a))
  implicit def applicative2[A1, A2](t: CF[(ConfNode[A1], ConfNode[A2])]) = (a: (A1, A2)) => toConfValue(t.c._1(a._1), t.c._2(a._2))
  implicit def applicative3[A1, A2, A3](t: CF[(ConfNode[A1], ConfNode[A2], ConfNode[A3])]) = (a: (A1, A2, A3)) => toConfValue(t.c._1(a._1), t.c._2(a._2), t.c._3(a._3))
  implicit def applicative4[A1, A2, A3, A4](t: CF[(ConfNode[A1], ConfNode[A2], ConfNode[A3], ConfNode[A4])]) = (a: (A1, A2, A3, A4)) => toConfValue(t.c._1(a._1), t.c._2(a._2), t.c._3(a._3), t.c._4(a._4))
  implicit def applicative5[A1, A2, A3, A4, A5](t: CF[(ConfNode[A1], ConfNode[A2], ConfNode[A3], ConfNode[A4], ConfNode[A5])]) = (a: (A1, A2, A3, A4, A5)) => toConfValue(t.c._1(a._1), t.c._2(a._2), t.c._3(a._3), t.c._4(a._4), t.c._5(a._5))
  implicit def applicative6[A1, A2, A3, A4, A5, A6](t: CF[(ConfNode[A1], ConfNode[A2], ConfNode[A3], ConfNode[A4], ConfNode[A5], ConfNode[A6])]) = (a: (A1, A2, A3, A4, A5, A6)) => toConfValue(t.c._1(a._1), t.c._2(a._2), t.c._3(a._3), t.c._4(a._4), t.c._5(a._5), t.c._6(a._6))
  implicit def applicative7[A1, A2, A3, A4, A5, A6, A7](t: CF[(ConfNode[A1], ConfNode[A2], ConfNode[A3], ConfNode[A4], ConfNode[A5], ConfNode[A6], ConfNode[A7])]) = (a: (A1, A2, A3, A4, A5, A6, A7)) => toConfValue(t.c._1(a._1), t.c._2(a._2), t.c._3(a._3), t.c._4(a._4), t.c._5(a._5), t.c._6(a._6), t.c._7(a._7))
  implicit def applicative8[A1, A2, A3, A4, A5, A6, A7, A8](t: CF[(ConfNode[A1], ConfNode[A2], ConfNode[A3], ConfNode[A4], ConfNode[A5], ConfNode[A6], ConfNode[A7], ConfNode[A8])]) = (a: (A1, A2, A3, A4, A5, A6, A7, A8)) => toConfValue(t.c._1(a._1), t.c._2(a._2), t.c._3(a._3), t.c._4(a._4), t.c._5(a._5), t.c._6(a._6), t.c._7(a._7), t.c._8(a._8))
  implicit def applicative9[A1, A2, A3, A4, A5, A6, A7, A8, A9](t: CF[(ConfNode[A1], ConfNode[A2], ConfNode[A3], ConfNode[A4], ConfNode[A5], ConfNode[A6], ConfNode[A7], ConfNode[A8], ConfNode[A9])]) = (a: (A1, A2, A3, A4, A5, A6, A7, A8, A9)) => toConfValue(t.c._1(a._1), t.c._2(a._2), t.c._3(a._3), t.c._4(a._4), t.c._5(a._5), t.c._6(a._6), t.c._7(a._7), t.c._8(a._8), t.c._9(a._9))
  implicit def applicative10[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10](t: CF[(ConfNode[A1], ConfNode[A2], ConfNode[A3], ConfNode[A4], ConfNode[A5], ConfNode[A6], ConfNode[A7], ConfNode[A8], ConfNode[A9], ConfNode[A10])]) = (a: (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10)) => toConfValue(t.c._1(a._1), t.c._2(a._2), t.c._3(a._3), t.c._4(a._4), t.c._5(a._5), t.c._6(a._6), t.c._7(a._7), t.c._8(a._8), t.c._9(a._9), t.c._10(a._10))
  implicit def applicative11[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11](t: CF[(ConfNode[A1], ConfNode[A2], ConfNode[A3], ConfNode[A4], ConfNode[A5], ConfNode[A6], ConfNode[A7], ConfNode[A8], ConfNode[A9], ConfNode[A10], ConfNode[A11])]) = (a: (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11)) => toConfValue(t.c._1(a._1), t.c._2(a._2), t.c._3(a._3), t.c._4(a._4), t.c._5(a._5), t.c._6(a._6), t.c._7(a._7), t.c._8(a._8), t.c._9(a._9), t.c._10(a._10), t.c._11(a._11))
  implicit def applicative12[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12](t: CF[(ConfNode[A1], ConfNode[A2], ConfNode[A3], ConfNode[A4], ConfNode[A5], ConfNode[A6], ConfNode[A7], ConfNode[A8], ConfNode[A9], ConfNode[A10], ConfNode[A11], ConfNode[A12])]) = (a: (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12)) => toConfValue(t.c._1(a._1), t.c._2(a._2), t.c._3(a._3), t.c._4(a._4), t.c._5(a._5), t.c._6(a._6), t.c._7(a._7), t.c._8(a._8), t.c._9(a._9), t.c._10(a._10), t.c._11(a._11), t.c._12(a._12))
  implicit def applicative13[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13](t: CF[(ConfNode[A1], ConfNode[A2], ConfNode[A3], ConfNode[A4], ConfNode[A5], ConfNode[A6], ConfNode[A7], ConfNode[A8], ConfNode[A9], ConfNode[A10], ConfNode[A11], ConfNode[A12], ConfNode[A13])]) = (a: (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13)) => toConfValue(t.c._1(a._1), t.c._2(a._2), t.c._3(a._3), t.c._4(a._4), t.c._5(a._5), t.c._6(a._6), t.c._7(a._7), t.c._8(a._8), t.c._9(a._9), t.c._10(a._10), t.c._11(a._11), t.c._12(a._12), t.c._13(a._13))
  implicit def applicative14[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14](t: CF[(ConfNode[A1], ConfNode[A2], ConfNode[A3], ConfNode[A4], ConfNode[A5], ConfNode[A6], ConfNode[A7], ConfNode[A8], ConfNode[A9], ConfNode[A10], ConfNode[A11], ConfNode[A12], ConfNode[A13], ConfNode[A14])]) = (a: (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14)) => toConfValue(t.c._1(a._1), t.c._2(a._2), t.c._3(a._3), t.c._4(a._4), t.c._5(a._5), t.c._6(a._6), t.c._7(a._7), t.c._8(a._8), t.c._9(a._9), t.c._10(a._10), t.c._11(a._11), t.c._12(a._12), t.c._13(a._13), t.c._14(a._14))
  implicit def applicative15[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15](t: CF[(ConfNode[A1], ConfNode[A2], ConfNode[A3], ConfNode[A4], ConfNode[A5], ConfNode[A6], ConfNode[A7], ConfNode[A8], ConfNode[A9], ConfNode[A10], ConfNode[A11], ConfNode[A12], ConfNode[A13], ConfNode[A14], ConfNode[A15])]) = (a: (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15)) => toConfValue(t.c._1(a._1), t.c._2(a._2), t.c._3(a._3), t.c._4(a._4), t.c._5(a._5), t.c._6(a._6), t.c._7(a._7), t.c._8(a._8), t.c._9(a._9), t.c._10(a._10), t.c._11(a._11), t.c._12(a._12), t.c._13(a._13), t.c._14(a._14), t.c._15(a._15))
  implicit def applicative16[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16](t: CF[(ConfNode[A1], ConfNode[A2], ConfNode[A3], ConfNode[A4], ConfNode[A5], ConfNode[A6], ConfNode[A7], ConfNode[A8], ConfNode[A9], ConfNode[A10], ConfNode[A11], ConfNode[A12], ConfNode[A13], ConfNode[A14], ConfNode[A15], ConfNode[A16])]) = (a: (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16)) => toConfValue(t.c._1(a._1), t.c._2(a._2), t.c._3(a._3), t.c._4(a._4), t.c._5(a._5), t.c._6(a._6), t.c._7(a._7), t.c._8(a._8), t.c._9(a._9), t.c._10(a._10), t.c._11(a._11), t.c._12(a._12), t.c._13(a._13), t.c._14(a._14), t.c._15(a._15), t.c._16(a._16))
  implicit def applicative17[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17](t: CF[(ConfNode[A1], ConfNode[A2], ConfNode[A3], ConfNode[A4], ConfNode[A5], ConfNode[A6], ConfNode[A7], ConfNode[A8], ConfNode[A9], ConfNode[A10], ConfNode[A11], ConfNode[A12], ConfNode[A13], ConfNode[A14], ConfNode[A15], ConfNode[A16], ConfNode[A17])]) = (a: (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17)) => toConfValue(t.c._1(a._1), t.c._2(a._2), t.c._3(a._3), t.c._4(a._4), t.c._5(a._5), t.c._6(a._6), t.c._7(a._7), t.c._8(a._8), t.c._9(a._9), t.c._10(a._10), t.c._11(a._11), t.c._12(a._12), t.c._13(a._13), t.c._14(a._14), t.c._15(a._15), t.c._16(a._16), t.c._17(a._17))
  implicit def applicative18[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18](t: CF[(ConfNode[A1], ConfNode[A2], ConfNode[A3], ConfNode[A4], ConfNode[A5], ConfNode[A6], ConfNode[A7], ConfNode[A8], ConfNode[A9], ConfNode[A10], ConfNode[A11], ConfNode[A12], ConfNode[A13], ConfNode[A14], ConfNode[A15], ConfNode[A16], ConfNode[A17], ConfNode[A18])]) = (a: (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18)) => toConfValue(t.c._1(a._1), t.c._2(a._2), t.c._3(a._3), t.c._4(a._4), t.c._5(a._5), t.c._6(a._6), t.c._7(a._7), t.c._8(a._8), t.c._9(a._9), t.c._10(a._10), t.c._11(a._11), t.c._12(a._12), t.c._13(a._13), t.c._14(a._14), t.c._15(a._15), t.c._16(a._16), t.c._17(a._17), t.c._18(a._18))
  implicit def applicative19[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19](t: CF[(ConfNode[A1], ConfNode[A2], ConfNode[A3], ConfNode[A4], ConfNode[A5], ConfNode[A6], ConfNode[A7], ConfNode[A8], ConfNode[A9], ConfNode[A10], ConfNode[A11], ConfNode[A12], ConfNode[A13], ConfNode[A14], ConfNode[A15], ConfNode[A16], ConfNode[A17], ConfNode[A18], ConfNode[A19])]) = (a: (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19)) => toConfValue(t.c._1(a._1), t.c._2(a._2), t.c._3(a._3), t.c._4(a._4), t.c._5(a._5), t.c._6(a._6), t.c._7(a._7), t.c._8(a._8), t.c._9(a._9), t.c._10(a._10), t.c._11(a._11), t.c._12(a._12), t.c._13(a._13), t.c._14(a._14), t.c._15(a._15), t.c._16(a._16), t.c._17(a._17), t.c._18(a._18), t.c._19(a._19))
  implicit def applicative20[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20](t: CF[(ConfNode[A1], ConfNode[A2], ConfNode[A3], ConfNode[A4], ConfNode[A5], ConfNode[A6], ConfNode[A7], ConfNode[A8], ConfNode[A9], ConfNode[A10], ConfNode[A11], ConfNode[A12], ConfNode[A13], ConfNode[A14], ConfNode[A15], ConfNode[A16], ConfNode[A17], ConfNode[A18], ConfNode[A19], ConfNode[A20])]) = (a: (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20)) => toConfValue(t.c._1(a._1), t.c._2(a._2), t.c._3(a._3), t.c._4(a._4), t.c._5(a._5), t.c._6(a._6), t.c._7(a._7), t.c._8(a._8), t.c._9(a._9), t.c._10(a._10), t.c._11(a._11), t.c._12(a._12), t.c._13(a._13), t.c._14(a._14), t.c._15(a._15), t.c._16(a._16), t.c._17(a._17), t.c._18(a._18), t.c._19(a._19), t.c._20(a._20))
  implicit def applicative21[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, A21](t: CF[(ConfNode[A1], ConfNode[A2], ConfNode[A3], ConfNode[A4], ConfNode[A5], ConfNode[A6], ConfNode[A7], ConfNode[A8], ConfNode[A9], ConfNode[A10], ConfNode[A11], ConfNode[A12], ConfNode[A13], ConfNode[A14], ConfNode[A15], ConfNode[A16], ConfNode[A17], ConfNode[A18], ConfNode[A19], ConfNode[A20], ConfNode[A21])]) = (a: (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, A21)) => toConfValue(t.c._1(a._1), t.c._2(a._2), t.c._3(a._3), t.c._4(a._4), t.c._5(a._5), t.c._6(a._6), t.c._7(a._7), t.c._8(a._8), t.c._9(a._9), t.c._10(a._10), t.c._11(a._11), t.c._12(a._12), t.c._13(a._13), t.c._14(a._14), t.c._15(a._15), t.c._16(a._16), t.c._17(a._17), t.c._18(a._18), t.c._19(a._19), t.c._20(a._20), t.c._21(a._21))
  implicit def applicative22[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, A21, A22](t: CF[(ConfNode[A1], ConfNode[A2], ConfNode[A3], ConfNode[A4], ConfNode[A5], ConfNode[A6], ConfNode[A7], ConfNode[A8], ConfNode[A9], ConfNode[A10], ConfNode[A11], ConfNode[A12], ConfNode[A13], ConfNode[A14], ConfNode[A15], ConfNode[A16], ConfNode[A17], ConfNode[A18], ConfNode[A19], ConfNode[A20], ConfNode[A21], ConfNode[A22])]) = (a: (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, A21, A22)) => toConfValue(t.c._1(a._1), t.c._2(a._2), t.c._3(a._3), t.c._4(a._4), t.c._5(a._5), t.c._6(a._6), t.c._7(a._7), t.c._8(a._8), t.c._9(a._9), t.c._10(a._10), t.c._11(a._11), t.c._12(a._12), t.c._13(a._13), t.c._14(a._14), t.c._15(a._15), t.c._16(a._16), t.c._17(a._17), t.c._18(a._18), t.c._19(a._19), t.c._20(a._20), t.c._21(a._21), t.c._22(a._22))

}

object ConfigWriter extends DefaultConfigWriters

trait DefaultConfigWriters {

  implicit object IntConfigWriter extends ConfigWriter[Int] {
    def write(o: Int): ConfigValue = ConfigValueFactory.fromAnyRef(Int.box(o))
  }

  implicit object LongConfigWriter extends ConfigWriter[Long] {
    def write(o: Long): ConfigValue =  ConfigValueFactory.fromAnyRef(Long.box(o))
  }

  implicit object StringConfigWriter extends ConfigWriter[String] {
    def write(o: String): ConfigValue =  ConfigValueFactory.fromAnyRef(o)
  }

  implicit object BooleanConfigWriter extends ConfigWriter[Boolean] {
    def write(o: Boolean): ConfigValue =  ConfigValueFactory.fromAnyRef(Boolean.box(o))
  }

  implicit def arrayConfigWriter[T](implicit writer: ConfigWriter[T]): ConfigWriter[Array[T]] = new ConfigWriter[Array[T]] {
    def write(o: Array[T]): ConfigValue = ConfigValueFactory.fromIterable(o.map(t => writer.write(t)).toSeq)
  }

  implicit def mapConfigWriter[T](implicit writer: ConfigWriter[T]): ConfigWriter[Map[String, T]] = new ConfigWriter[Map[String, T]] {
    def write(o: Map[String, T]): ConfigValue =  ConfigValueFactory.fromMap(o.map(e => (e._1, writer.write(e._2))))
  }

  implicit def traversableConfigWriter[T : ConfigWriter] = new ConfigWriter[Traversable[T]] {
    def write(o: Traversable[T]): ConfigValue = o match {
      case s if s.size == 1 => ConfigValueFactory.fromIterable(s.map(ConfValue.write(_)).toSeq)
      case t @ _ => ConfigValueFactory.fromIterable(t.map(ConfValue.write(_)).toSeq)
    }
  }

  implicit def optionConfigWrite[T : ConfigWriter] = new ConfigWriter[Option[T]] {
    def write(o: Option[T]): ConfigValue = o match {
      case Some(v) => ConfValue.write(v)
      case None => new ConfigValueUndef("<None>", None)
    }
  }

}

sealed trait ConfigResult[+T] {
  def get: T

  def fold[B](ef: Seq[String] => B)(sf: T => B): B = this match {
    case ConfigSuccess(s) => sf(s)
    case ConfigError(e) => ef(e)
  }

  def map[B](f: T => B): ConfigResult[B] = this match {
    case ConfigSuccess(v) => ConfigSuccess(f(v))
    case j: ConfigError => j
  }

  def flatMap[B](f: T => ConfigResult[B]) = this match {
    case ConfigSuccess(v) => f(v)
    case j: ConfigError => j
  }

  def and[Z >: T, B, R](s: ConfigResult[B])(implicit bld: (ConfigSuccess[Z], ConfigSuccess[B]) => ConfigSuccess[R]) = this match {
    case j: ConfigSuccess[Z] => j ++ s
    case j: ConfigError => j ++ s
  }

  def $[R <: CApplicative](implicit v: ConfigResult[T] => R) = v(this)
}

case class ConfigSuccess[V](value: V) extends ConfigResult[V] {

  override def get: V = value

  override def toString: String = "ConfigSuccess(" + value + ")"

  def ++[B, R](s: ConfigResult[B])(implicit bld: (ConfigSuccess[V], ConfigSuccess[B]) => ConfigSuccess[R]) = s match {
    case v: ConfigSuccess[B] => bld.apply(this, v)
    case e: ConfigError => e
  }

}

case class ConfigError(errors: Seq[String]) extends ConfigResult[Nothing] {

  override def get = throw new NoSuchElementException("JError.get")

  override def toString: String = "ConfigError(" + errors + ")"

  def ++[B, R](s: ConfigResult[B]) = s match {
    case ConfigSuccess(_) => this
    case ConfigError(e) => ConfigError(errors ++ e)
  }

}

object ConfigError {

  def apply(error: String) = new ConfigError(Seq(error))

}

class ConfigValueUndef(val field: String, val org: Option[ConfigOrigin]) extends ConfigValue {
  override def valueType(): ConfigValueType = throw new IllegalStateException(s"Undefined value for filed [${field}](line: ${org.getOrElse(null)})")
  override def withFallback(other: ConfigMergeable): ConfigValue = throw new UnsupportedOperationException
  override def unwrapped(): AnyRef = throw new UnsupportedOperationException
  override def atPath(path: String): Config = throw new UnsupportedOperationException
  override def render(): String = throw new UnsupportedOperationException
  override def render(options: ConfigRenderOptions): String = throw new UnsupportedOperationException
  override def atKey(key: String): Config = throw new UnsupportedOperationException
  override def origin(): ConfigOrigin = throw new UnsupportedOperationException
  override def withOrigin(origin: ConfigOrigin): ConfigValue = throw new UnsupportedOperationException
}
