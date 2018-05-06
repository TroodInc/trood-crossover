package trood.crossover.flow

import trood.crossover.conf.CubeConf

object RtSinksRouter {
    case class UpdateCubes(cubesConf: Seq[CubeConf])
}