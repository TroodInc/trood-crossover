package trood.crossover.api

import scala.concurrent.Future
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model._
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import trood.crossover.conf.ConfHolder
import trood.crossover.conf.CrossoverConf
import trood.crossover.conf.ConfValue
import com.typesafe.config.ConfigRenderOptions
import akka.util.ByteString
import akka.http.scaladsl.unmarshalling.Unmarshaller
import akka.http.scaladsl.model.ContentTypeRange
import akka.http.scaladsl.model.MediaType
import trood.crossover.conf.CubeConf
import com.typesafe.config.ConfigFactory
import trood.crossover.conf.ConfigTools._
import trood.crossover.conf.ProxyConfigImplicits._
import akka.actor.ActorRef
import akka.util.Timeout
import trood.crossover.conf.DistributedCrossoverConf

object ApiServer {

  def apply(ch: ActorRef, contextRoot: String, host: String = "localhost", port: Int = 8080)(implicit system: ActorSystem): Future[ApiServer] = {
    implicit val executionContext = system.dispatcher
    val srv = new ApiServer(ch, contextRoot, host, port)
    srv.start.map(_ => srv)
  }
  
}

import DistributedCrossoverConf._
class ApiServer(ch: ActorRef, contextRoot: String, host: String, port: Int)(implicit system: ActorSystem) {
  
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher
  private var binding :Future[ServerBinding] = _

  private[api] def start: Future[Unit] = {
    binding = Http().bindAndHandle(_route, host, port)
    binding.map(_ => Unit)
  }

  def stop: Future[Unit] = binding.flatMap(_.unbind()).map(_ => Unit)

  implicit val cubeUm: Unmarshaller[HttpEntity, CubeConf] = {
    Unmarshaller.stringUnmarshaller.forContentTypes(ContentTypeRange.apply(MediaTypes.`application/json`)).map{data =>
      ConfigFactory.parseString(data).resolve().root().as[CubeConf]
    }    
  }  

  import akka.pattern.ask
  import scala.concurrent.duration._
  implicit val timeout = Timeout(30 seconds)
  private def _route = path(contextRoot / "cube_conf") {
    get {
      parameter("name") {(name) => 
        onSuccess(ask(ch, DistributedCrossoverConf.Get).flatMap{
        case NoConfig => Future.failed(new IllegalStateException("No configuration"))
        case Config(c: CrossoverConf) => Future.successful(c)}) {conf =>
          conf.cubes.find(c => c.name == name) match {
            case None => complete((StatusCodes.NotFound, "not found"))
            case Some(c) => complete(HttpEntity(
              ContentTypes.`application/json`,
              ByteString(ConfValue.write(c).render(ConfigRenderOptions.concise()))
            ))
          }
        }
      }
    } ~ 
    put{
      entity(as[CubeConf]) { cube => 
        val future = ask(ch, DistributedCrossoverConf.Get).flatMap{
        case NoConfig => Future.failed(new IllegalStateException("No configuration"))
        case Config(c: CrossoverConf) => 
          ask(ch, DistributedCrossoverConf.UpdateConf(c.copy(cubes = c.cubes.filter(_.name != cube.name) :+ cube, version = c.version + 1)))
        }.flatMap{
        case Error(t) => Future.failed(t)
        case Updated(c: CrossoverConf) => Future.successful(c)}
        onSuccess(future){ _ => 
          complete((StatusCodes.Created))
        }
      }
    } ~ 
    delete {
      parameter("name") {(name) => 
        val future = ask(ch, DistributedCrossoverConf.Get).flatMap{
          case NoConfig => Future.failed(new IllegalStateException("No configuration"))
          case Config(c: CrossoverConf) => 
            if(c.cubes.exists(_.name == name)) {
              ask(ch, DistributedCrossoverConf.UpdateConf(c.copy(cubes = c.cubes.filter(_.name != name), version = c.version + 1)))
                .flatMap{
                  case Error(t) => Future.failed(t)
                  case Updated(c: CrossoverConf) => Future.successful(Unit)}
            }
            else { Future.successful(Unit) }
        }
        onSuccess(future){ _ => 
          complete((StatusCodes.NoContent))
        }
      }
    }
  } ~ path(contextRoot / "cube_confs") {
    get {
      onSuccess(ask(ch, DistributedCrossoverConf.Get).flatMap{
        case NoConfig => Future.failed(new IllegalStateException("No configuration"))
        case Config(c: CrossoverConf) => Future.successful(c)}) {conf =>
        complete(HttpEntity(
          ContentTypes.`application/json`,
          ByteString(ConfValue.write(conf.cubes).render(ConfigRenderOptions.concise()))
        ))
        }
    }
  }

}
