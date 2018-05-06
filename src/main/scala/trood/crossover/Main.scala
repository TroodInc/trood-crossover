package trood.crossover


import akka.actor.ActorSystem
import akka.pattern.ask
import akka.util.Timeout
import org.slf4j.LoggerFactory
import trood.crossover.api.ApiServer
import trood.crossover.conf.{CrossoverConf, DistributedCrossoverConf}
import trood.crossover.flow.FlowController

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}
import scala.concurrent.ExecutionContext.Implicits.global


final case class LaunchArgs(hadoopConfigs: Option[Seq[String]], confPath: String, host: String, port: Int, ctxPath: String)

object Main {
    private val logger = LoggerFactory.getLogger("trood.crossover.main")

    private val argsParser = new scopt.OptionParser[LaunchArgs]("Crossover") {
        head("Crossover", "0.1.0")
        opt[Seq[String]]("hr")
            .optional()
            .valueName("<resource1>,<resource2>...")
            .action( (c, args) => args.copy(hadoopConfigs = Some(c)) )
            .text("Paths to Hadoop configuration files: core-site.xml, hdfs-site.xml and so on.")
        opt[String]('c', "conf").optional()
            .action( (c, args) => args.copy(confPath = c) )
            .text("is a file path in HDFS to the configuration file. Default value is \"configuration/crossover.conf\"")
        opt[String]("http.host").optional()
            .action( (h, args) => args.copy(host = h) )
            .text("is a host name to binding. Default value is \"localhost\"")
        opt[Int]("http.port").optional()
            .action( (p, args) => args.copy(port = p) )
            .text("is a port to binding. Default value is \"8080\"")
        opt[String]("http.context-path").optional()
            .action( (cp, args) => args.copy(ctxPath = cp) )
            .text("is a context path of API. Default value is \"api\"")
        help("help").text("prints this usage text")
    }

    def main(args: Array[String]): Unit = {
        argsParser.parse(args, LaunchArgs(None, "configuration/crossover.conf", "localhost", 8080, "api")) match {
            case Some(launchArgs) => launch(launchArgs)
            case None => println("Arguments are bad.")
        }
    }

    def launch(launchArgs: LaunchArgs): Unit = {
        implicit val system: ActorSystem = ActorSystem("Crossover")
        implicit val timeout: Timeout = Timeout(5 minutes)
        logger.info("Initializing configuration ...")
        launchArgs.hadoopConfigs.foreach(paths => System.setProperty("hadoop.conf.paths", paths.mkString(",")))
        val conf = system.actorOf(DistributedCrossoverConf.props[CrossoverConf](launchArgs.confPath), "configuration")
        //TODO: waiting config will be ready
        logger.info("Initializing flow  ...")
        val flowController = system.actorOf(FlowController.props(conf), name = "flow")
        Try(Await.result(flowController ? FlowController.StartFlow, timeout.duration)) match {
            case Success(FlowController.FlowStarted) =>
                val api = ApiServer(conf, launchArgs.ctxPath, launchArgs.host, launchArgs.port)
                Runtime.getRuntime.addShutdownHook(new Thread() {
                    override def run(): Unit = {
                        logger.info("Stopping API server ...")
                        api.flatMap(_.stop).onComplete(_ => logger.info("API server has stopped"))
                        logger.info("Stopping flow ...")
                        Try(Await.result(flowController ? FlowController.StopFlow, timeout.duration)) match {
                            case Success(FlowController.FlowStopped) =>
                                logger.info("Flow has stopped")
                            case Success(r) =>
                                logger.error("Flow stopping error. Unknown response from 'FlowController': {}", r)
                            case Failure(e) =>
                                logger.error("Flow stopping error:", e)
                        }
                        logger.info("Terminating actor system ...")
                        system.terminate()
                        logger.info("System is finished")
                    }
                })
                logger.info("Flow has started")
            case Success(FlowController.StartFailed(e)) =>
                logger.error("Flow start error:", e)
                system.terminate()
            case Failure(e) =>
                logger.error("Flow start error:", e)
                system.terminate()
        }
    }

}
