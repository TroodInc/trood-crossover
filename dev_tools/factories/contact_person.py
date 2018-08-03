import factory
from custodian.records.model import Record
from faker import Faker

from custodian_objects import ContactPerson as ContactPersonObject
from factories import BaseFactory
from factories.contact import ContactFactory
from models.contact_person import ContactPerson


class ContactPersonFactory(BaseFactory):
    id = factory.Sequence(lambda n: n + pow(1000, 2))
    name = factory.LazyFunction(Faker().name)
    email = factory.LazyFunction(Faker().free_email)

    @classmethod
    def build(cls, *args, **kwargs) -> ContactPerson:
        obj = super(cls, ContactPersonFactory).build(*args, **kwargs)
        cls._create_custodian_record(obj)
        return obj

    @classmethod
    def _factory_custodian_record(cls, obj):
        contact_person = Record(
            ContactPersonObject,
            id=obj.id,
            name=obj.name,
            target_type=obj.target_type,
            target_id=obj.target_id,
            email=obj.email,
        )
        ContactFactory.build(
            target_id=obj.id
        )
        return contact_person

    class Meta:
        model = ContactPerson
