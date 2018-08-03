import datetime

import factory
import faker

from factories import BaseFactory
from factories.employee import EmployeeFactory
from models.taskevent import TaskEvent


class TaskFactory(BaseFactory):
    task_id = factory.Sequence(lambda n: n+1)
    name = factory.LazyFunction(faker.Faker().name)
    description = factory.LazyFunction(lambda: faker.Faker().text(max_nb_chars=200, ext_word_list=None))
    executor_id = factory.LazyFunction(lambda: EmployeeFactory.build().employee_id)
    created = factory.LazyFunction(datetime.datetime.now)
    deadline = factory.LazyFunction(datetime.datetime.now)

    @classmethod
    def build(cls, *args, **kwargs) -> TaskEvent:
        return super(cls, TaskFactory).build(*args, **kwargs)

    class Meta:
        model = TaskEvent
