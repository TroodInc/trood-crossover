package trood.crossover.flow

object FlowMessages {
    final case class Next(deliveryTag: Long)
    final case class Refuse(deliveryTag: Long, requeue: Boolean, reason: Throwable)
}
