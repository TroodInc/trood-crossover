import java.util

import com.metamx.tranquility.beam.ClusteredBeamTuning
import com.metamx.tranquility.druid.DruidRollup
import io.druid.granularity.QueryGranularities
import io.druid.query.aggregation.{AggregatorFactory, DoubleMaxAggregatorFactory, LongMinAggregatorFactory}
import trood.crossover.conf.{CrossoverConfHolder, CubeConf}

val m1 = Map("test" -> "test")
val m2 = Map("test" -> "test")
m1 == m2
val dementions = new util.ArrayList[String]()
dementions.add("a")
dementions.add("b")
dementions.add("c")
val agregators = new util.ArrayList[AggregatorFactory]()
agregators.add(new DoubleMaxAggregatorFactory("aa", "a"))
agregators.add(new LongMinAggregatorFactory("ab", "b"))
val rollup = DruidRollup.create(dementions, agregators, QueryGranularities.DAY)

val cube1 = CubeConf("test", rollup, ClusteredBeamTuning(), Map("test" -> Map("a" -> "b")))


val dementions2 = new util.ArrayList[String]()
dementions2.add("a")
dementions2.add("b")
dementions2.add("c")
val agregators2 = new util.ArrayList[AggregatorFactory]()
agregators2.add(new LongMinAggregatorFactory("ab", "b"))
agregators2.add(new DoubleMaxAggregatorFactory("aa", "a"))
val rollup2 = DruidRollup.create(dementions2, agregators2, QueryGranularities.DAY)
val cube2 = CubeConf("test", rollup2, ClusteredBeamTuning(), Map("test" -> Map("a" -> "b")))


cube1.name == cube2.name
//order has matter
cube1.rollup.dimensions == cube2.rollup.dimensions
cube1.rollup.aggregators == cube2.rollup.aggregators
cube1.rollup.indexGranularity == cube2.rollup.indexGranularity
cube1.tune == cube2.tune
cube1.sources == cube2.sources

cube1.hashCode()
cube2.hashCode()

cube1 == cube2

val s1 = Seq("1", "3")
val s2 = Seq("2", "1")

s1 == s2

s1.diff(s2)
s2.diff(s1)
s1.intersect(s2)



