from factories import BaseFactory
from models.orders_stats import OrdersStats
from models.task_stats import TasksStats


class TasksStatsFactory(BaseFactory):
    class Meta:
        model = OrdersStats

    @classmethod
    def build(cls, *args, **kwargs) -> TasksStats:
        return super(cls, TasksStatsFactory).build(*args, **kwargs)
