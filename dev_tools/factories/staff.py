import factory
import faker

from factories import BaseFactory
from models.staff import Staff


class StaffFactory(BaseFactory):
    staff_id = factory.Sequence(lambda n: n+1)
    name = factory.LazyFunction(faker.Faker().company)

    @classmethod
    def build(cls, *args, **kwargs) -> Staff:
        return super(cls, StaffFactory).build(*args, **kwargs)

    class Meta:
        model = Staff
