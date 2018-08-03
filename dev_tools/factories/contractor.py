import factory
from custodian.records.model import Record
from faker import Faker

from constants import CONTRACTOR_TYPE
from custodian_objects import Contractor as ContractorObject
from factories import BaseFactory
from factories.contact_person import ContactPersonFactory
from models.contractor import Contractor


class ContractorFactory(BaseFactory):
    contractor_id = factory.Sequence(lambda n: n + pow(100, 2))
    name = factory.LazyFunction(Faker().name)

    @classmethod
    def build(cls, *args, **kwargs) -> Contractor:
        contractor = super(cls, ContractorFactory).build(*args, **kwargs)
        cls._create_custodian_record(contractor)
        return contractor

    @classmethod
    def _factory_custodian_record(cls, obj):
        contractor = Record(
            ContractorObject,
            id=obj.contractor_id,
            name=obj.name,
            type_id=obj.source_id,
            responsible=obj.responsible_id,
            executor=obj.executor_id,
            source="WEBSITE",
            contractor_type=CONTRACTOR_TYPE.REVERSED[obj.type_id],
            active_status='ACTIVE'
        )
        ContactPersonFactory.build(target_type='contractor', target_id=obj.contractor_id)
        return contractor

    class Meta:
        model = Contractor
