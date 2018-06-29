import factory
import faker

from factories import BaseFactory
from models.client import Client


class ClientFactory(BaseFactory):
    client_id = factory.Sequence(lambda n: n+1)
    name = factory.LazyFunction(faker.Faker().company)

    @classmethod
    def build(cls, *args, **kwargs) -> Client:
        return super(cls, ClientFactory).build(*args, **kwargs)

    class Meta:
        model = Client
