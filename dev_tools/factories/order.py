import datetime

import factory
from custodian.records.model import Record
from faker import Faker

from constants import ORDER_STATUS, CONTRACTOR_TYPE
from custodian_objects import BaseOrder as BaseOrderObject
from factories import BaseFactory
from models.orderevent import OrderEvent


class BaseOrderEventFactory(BaseFactory):
    base_order_id = factory.Sequence(lambda n: n + pow(100, 2))
    created = factory.LazyFunction(datetime.datetime.now)
    name = factory.LazyFunction(Faker().name)

    class Meta:
        model = OrderEvent

    @classmethod
    def build(cls, *args, **kwargs) -> OrderEvent:
        order_event = super(cls, BaseOrderEventFactory).build(*args, **kwargs)
        cls._create_custodian_record(order_event)
        return order_event

    @classmethod
    def _factory_custodian_record(cls, obj):
        return Record(
            BaseOrderObject,
            id=obj.base_order_id,
            name=obj.name,
            responsible=obj.responsible_id,
            target_type="lead" if obj.contractor_type_id == CONTRACTOR_TYPE.LEAD else "client",
            target_id=obj.contractor_id,
            executor=obj.executor_id,
            contractor=obj.contractor_id,
            status=ORDER_STATUS.REVERTED[obj.status_id],
            active_status='ACTIVE'
        )
