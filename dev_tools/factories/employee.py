import factory
from custodian.records.model import Record
from faker import Faker

from custodian_objects import Employee as EmployeeObject
from factories import BaseFactory
from models.employee import Employee


class EmployeeFactory(BaseFactory):
    employee_id = factory.Sequence(lambda n: n + pow(1000, 2))
    name = factory.LazyFunction(Faker().name)

    @classmethod
    def _factory_custodian_record(cls, obj):
        return Record(
            EmployeeObject,
            id=obj.employee_id,
            name=obj.name,
            user_id=0,
            position='',
            phone='',
            account="1",
            role='SALES_MANAGER'
        )

    @classmethod
    def build(cls, *args, **kwargs) -> Employee:
        employee = super(cls, EmployeeFactory).build(*args, **kwargs)
        assert isinstance(employee, Employee)
        cls._create_custodian_record(employee)
        return employee

    class Meta:
        model = Employee
