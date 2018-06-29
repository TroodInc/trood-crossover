import json

from generators.orders import OrdersGenerator
from generators.orders_state import OrdersStateGenerator
from generators.payment import PaymentsGenerator
from generators.supply import SuppliesGenerator
from generators.task import TasksGenerator
from serializers.order import OrderEventSerializer
from serializers.orders_state import OrdersStateSerializer
from serializers.payment import PaymentEventSerializer
from serializers.supply import SupplyEventSerializer
from serializers.task import TaskEventSerializer


class MasterGenerator:
    @classmethod
    def store(cls, data, filename):
        file = open(filename, 'w+')
        file.write(json.dumps(data, indent=4))

    @classmethod
    def generate(cls):
        order_events = OrdersGenerator().get_data()
        payment_events = PaymentsGenerator().get_data(order_events)
        task_events = TasksGenerator().get_data()
        supply_events = SuppliesGenerator().get_data(order_events)
        orders_states = OrdersStateGenerator().get_data(order_events)
        cls.store([OrderEventSerializer(x).data for x in order_events], 'order_events.json')
        cls.store([PaymentEventSerializer(x).data for x in payment_events], 'payment_events.json')
        cls.store([TaskEventSerializer(x).data for x in task_events], 'task_events.json')
        cls.store([SupplyEventSerializer(x).data for x in supply_events], 'supply_events.json')
        cls.store([OrdersStateSerializer(x).data for x in orders_states], 'orders_states.json')
