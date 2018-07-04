import json

from generators.order_processing_duration import OrderProcessingDurationsGenerator
from generators.orders import OrdersGenerator
from generators.orders_stats import OrdersStatsGenerator
from generators.payment import PaymentsGenerator
from generators.supply import SuppliesGenerator
from generators.task import TasksGenerator
from generators.tasks_stats import TasksStatsGenerator
from serializers.order import OrderEventSerializer
from serializers.order_processing_duration import OrderProcessingDurationSerializer
from serializers.orders_stats import OrdersStatsSerializer
from serializers.payment import PaymentEventSerializer
from serializers.supply import SupplyEventSerializer
from serializers.task import TaskEventSerializer
from serializers.tasks_stats import TasksStatsSerializer


class MasterGenerator:
    @classmethod
    def store(cls, data, filename):
        file = open(filename, 'w+')
        file.write(json.dumps(data, indent=4))

    @classmethod
    def generate(cls):
        order_events = OrdersGenerator().get_data()
        cls.store([OrderEventSerializer(x).data for x in order_events], 'order_events.json')

        payment_events = PaymentsGenerator().get_data(order_events)
        cls.store([PaymentEventSerializer(x).data for x in payment_events], 'payment_events.json')

        task_events = TasksGenerator().get_data()
        cls.store([TaskEventSerializer(x).data for x in task_events], 'task_events.json')

        supply_events = SuppliesGenerator().get_data(order_events)
        cls.store([SupplyEventSerializer(x).data for x in supply_events], 'supply_events.json')

        orders_stats = OrdersStatsGenerator().get_data(order_events)
        cls.store([OrdersStatsSerializer(x).data for x in orders_stats], 'orders_stats.json')

        tasks_stats = TasksStatsGenerator().get_data(task_events)
        cls.store([TasksStatsSerializer(x).data for x in tasks_stats], 'tasks_stats.json')

        order_processing_durations = OrderProcessingDurationsGenerator().get_data(order_events)
        cls.store([OrderProcessingDurationSerializer(x).data for x in order_processing_durations],
                  'order_processing_durations.json')
