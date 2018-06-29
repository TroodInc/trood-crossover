from factories import BaseFactory
from models.orders_state import OrdersState


class OrdersStateFactory(BaseFactory):
    class Meta:
        model = OrdersState

    @classmethod
    def build(cls, *args, **kwargs) -> OrdersState:
        return super(cls, OrdersStateFactory).build(*args, **kwargs)
