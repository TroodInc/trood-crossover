import datetime

from constants import TASK_STATUS
from factories.employee import EmployeeFactory
from factories.task import TaskFactory
from .base import BaseDataGenerator


class TasksGenerator(BaseDataGenerator):
    def get_data(self):
        date_from = datetime.date.today() - datetime.timedelta(days=7)
        dates = [date_from + datetime.timedelta(days=day_delta) for day_delta in range(0, 7)]

        tasks = []

        first_employee = EmployeeFactory.build()
        second_employee = EmployeeFactory.build()
        third_employee = EmployeeFactory.build()

        time = datetime.datetime.now().time()
        #
        tasks.append(
            TaskFactory.build(
                status_id=TASK_STATUS.ACTIVE,
                created=datetime.datetime.combine(dates[0], time),
                deadline=datetime.datetime.combine(dates[0] + datetime.timedelta(days=3), time),
                executor_id=first_employee.employee_id
            )
        )
        #
        tasks.append(
            TaskFactory.build(
                status_id=TASK_STATUS.ACTIVE,
                created=datetime.datetime.combine(dates[0], time),
                deadline=datetime.datetime.combine(dates[0] + datetime.timedelta(days=6), time),
                executor_id=second_employee.employee_id
            )
        )
        #
        task = TaskFactory.build(
            status_id=TASK_STATUS.ACTIVE,
            created=datetime.datetime.combine(dates[0], time),
            deadline=datetime.datetime.combine(dates[0] + datetime.timedelta(days=6), time),
            executor_id=second_employee.employee_id
        )
        tasks.append(task)
        tasks.append(TaskFactory.clone(task, status_id=TASK_STATUS.DONE))

        #

        tasks.append(
            TaskFactory.build(
                status_id=TASK_STATUS.ACTIVE,
                created=datetime.datetime.combine(dates[2], time),
                deadline=datetime.datetime.combine(dates[3] + datetime.timedelta(days=6), time),
                executor_id=second_employee.employee_id
            )
        )

        #

        task = TaskFactory.build(
            status_id=TASK_STATUS.ACTIVE,
            created=datetime.datetime.combine(dates[0], time),
            deadline=datetime.datetime.combine(dates[0] + datetime.timedelta(days=1), time),
            executor_id=second_employee.employee_id
        )
        tasks.append(task)
        tasks.append(TaskFactory.clone(task, status_id=TASK_STATUS.OVERDUE))

        return tasks
