import datetime

import factory

from factories import BaseFactory
from models.orderevent import OrderEvent


class OrderEventFactory(BaseFactory):
    base_order_id = factory.Sequence(lambda n: n+1)
    created = factory.LazyFunction(datetime.datetime.now)

    class Meta:
        model = OrderEvent

    @classmethod
    def build(cls, *args, **kwargs) -> OrderEvent:
        return super(cls, OrderEventFactory).build(*args, **kwargs)
