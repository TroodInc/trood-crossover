import factory
from custodian.records.model import Record
from faker import Faker

from custodian_objects import Contact as ContactObject
from factories import BaseFactory
from models.contact import Contact


class ContactFactory(BaseFactory):
    id = factory.Sequence(lambda n: n + pow(1000, 2))
    name = factory.LazyFunction(Faker().name)
    value = factory.Sequence(lambda n: n + 79273520000)

    @classmethod
    def build(cls, *args, **kwargs) -> Contact:
        obj = super(cls, ContactFactory).build(*args, **kwargs)
        cls._create_custodian_record(obj)
        return obj

    @classmethod
    def _factory_custodian_record(cls, obj):
        return Record(
            ContactObject,
            id=obj.id,
            target_type="contact_person",
            target_id=obj.target_id,
            type='PHONE',
            value=obj.value
        )

    class Meta:
        model = Contact
