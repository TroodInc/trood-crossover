import factory
import faker

from factories import BaseFactory
from models.lead import Lead


class LeadFactory(BaseFactory):
    lead_id = factory.Sequence(lambda n: n+1)
    name = factory.LazyFunction(faker.Faker().company)

    @classmethod
    def build(cls, *args, **kwargs) -> Lead:
        return super(cls, LeadFactory).build(*args, **kwargs)

    class Meta:
        model = Lead
