import datetime

import factory

from factories import BaseFactory
from models.order import Order


class OrderFactory(BaseFactory):
    base_order_id = factory.Sequence(lambda n: n+1)
    created = factory.LazyFunction(datetime.datetime.now)

    class Meta:
        model = Order

    @classmethod
    def build(cls, *args, **kwargs) -> Order:
        return super(cls, OrderFactory).build(*args, **kwargs)
