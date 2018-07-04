from factories import BaseFactory
from models.orders_stats import OrdersStats


class OrdersStatsFactory(BaseFactory):
    class Meta:
        model = OrdersStats

    @classmethod
    def build(cls, *args, **kwargs) -> OrdersStats:
        return super(cls, OrdersStatsFactory).build(*args, **kwargs)
