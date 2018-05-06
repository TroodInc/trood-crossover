package trood.crossover.util

import java.net.InetAddress

import scala.util.Try

object NetUtil {
    val hostname = Try(InetAddress.getLocalHost.getHostName).getOrElse("unknown_host")
}
