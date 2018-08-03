import factory
from custodian.records.model import Record

from custodian_objects import Payment as PaymentObject
from factories import BaseFactory
from models.payment import Payment


class PaymentFactory(BaseFactory):
    payment_id = factory.Sequence(lambda n: n + pow(1000, 2))
    currency = 'RUB'

    class Meta:
        model = Payment

    @classmethod
    def build(cls, *args, **kwargs) -> Payment:
        payment = super(cls, PaymentFactory).build(*args, **kwargs)
        cls._create_custodian_record(payment)
        return payment

    @classmethod
    def _factory_custodian_record(cls, obj):
        return Record(
            PaymentObject,
            id=obj.payment_id if obj.payment_id else None,
            planed_date=obj.planned_date,
            payed_date=obj.payed_date,
            planed_amount=obj.planned_amount,
            recipient_target_id=obj.recipient_id,
            recipient_target_type=obj.recipient_type,
            payer_target_id=obj.payer_id,
            payer_target_type=obj.payer_type,
            payed_amount=obj.payed_amount,
            target_id=obj.base_order_id,
            target_type='base_order',
            currency='RUB'
        )
