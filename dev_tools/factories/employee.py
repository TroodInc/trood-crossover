import factory

from factories import BaseFactory
from models.employee import Employee


class EmployeeFactory(BaseFactory):
    employee_id = factory.Sequence(lambda n: n+1)

    @classmethod
    def build(cls, *args, **kwargs) -> Employee:
        return super(cls, EmployeeFactory).build(*args, **kwargs)

    class Meta:
        model = Employee
