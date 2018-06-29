import datetime
from typing import List

from factories.employee import EmployeeFactory
from factories.supply import SupplyFactory
from models.order import Order
from .base import BaseDataGenerator


class SuppliesGenerator(BaseDataGenerator):
    def get_data(self, order_events: List[Order]):
        date_from = datetime.date.today() - datetime.timedelta(days=5)
        dates = [date_from + datetime.timedelta(days=day_delta) for day_delta in range(0, 7)]
        time = datetime.datetime.now().time()
        supply_events = []

        first_employee = EmployeeFactory.build()
        second_employee = EmployeeFactory.build()
        third_employee = EmployeeFactory.build()

        supply_events.append(
            SupplyFactory.build(
                created=datetime.datetime.combine(dates[0], time),
                deliver=datetime.datetime.combine(dates[1], time),
                target_type=order_events[6].target_type,
                target_id=order_events[6].target_id,
                executor_id=first_employee.employee_id,
                base_order_id=order_events[6].base_order_id
            )
        )

        supply_events.append(
            SupplyFactory.build(
                created=datetime.datetime.combine(dates[0], time),
                deliver=datetime.datetime.combine(dates[3], time),
                target_type=order_events[1].target_type,
                target_id=order_events[1].target_id,
                executor_id=second_employee.employee_id,
                base_order_id=order_events[1].base_order_id
            )
        )

        supply_events.append(
            SupplyFactory.build(
                created=datetime.datetime.combine(dates[0], time),
                deliver=datetime.datetime.combine(dates[2], time),
                target_type=order_events[10].target_type,
                target_id=order_events[10].target_id,
                executor_id=second_employee.employee_id,
                base_order_id=order_events[10].base_order_id
            )
        )

        supply_events.append(
            SupplyFactory.build(
                created=datetime.datetime.combine(dates[0], time),
                deliver=datetime.datetime.combine(dates[3], time),
                target_type=order_events[4].target_type,
                target_id=order_events[4].target_id,
                executor_id=third_employee.employee_id,
                base_order_id=order_events[4].base_order_id
            )
        )

        supply_events.append(
            SupplyFactory.build(
                created=datetime.datetime.combine(dates[0], time),
                deliver=datetime.datetime.combine(dates[2], time),
                target_type=order_events[3].target_type,
                target_id=order_events[3].target_id,
                executor_id=third_employee.employee_id,
                base_order_id=order_events[3].base_order_id
            )
        )
        return supply_events
