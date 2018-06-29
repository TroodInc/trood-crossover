import factory

from factories import BaseFactory
from models.payment import Payment


class PaymentFactory(BaseFactory):
    payment_id = factory.Sequence(lambda n: n+1)
    currency = 'RUB'

    class Meta:
        model = Payment

    @classmethod
    def build(cls, *args, **kwargs) -> Payment:
        return super(cls, PaymentFactory).build(*args, **kwargs)
