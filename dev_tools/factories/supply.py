import datetime

import factory

from constants import CURRENCY
from factories import BaseFactory
from models.supply import Supply


class SupplyFactory(BaseFactory):
    supply_id = factory.Sequence(lambda n: n + 1)
    created = factory.LazyFunction(datetime.datetime.now)
    deliver = factory.LazyFunction(datetime.datetime.now)
    unit = "KILO"
    currency = CURRENCY.RUB

    @classmethod
    def build(cls, *args, **kwargs) -> Supply:
        return super(cls, SupplyFactory).build(*args, **kwargs)

    class Meta:
        model = Supply
