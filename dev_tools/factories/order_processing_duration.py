from factories import BaseFactory
from models.order_processing_duration import OrderProcessingDuration


class OrderProcessingDurationFactory(BaseFactory):
    class Meta:
        model = OrderProcessingDuration

    @classmethod
    def build(cls, *args, **kwargs) -> OrderProcessingDuration:
        return super(cls, OrderProcessingDurationFactory).build(*args, **kwargs)
