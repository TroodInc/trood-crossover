import datetime
from typing import List

from factories.tasks_stats import TasksStatsFactory
from models.task_stats import TasksStats
from models.taskevent import TaskEvent
from .base import BaseDataGenerator


class TasksStatsGenerator(BaseDataGenerator):
    def _get_key(self, task_event: TaskEvent):
        return '_'.join([str(task_event.task_id), str(task_event.status_id), task_event.created.date().isoformat(),
                         str(task_event.executor_id)])

    def get_data(self, task_events: List[TaskEvent]) -> List[TasksStats]:
        date_from = datetime.date.today() - datetime.timedelta(days=7)
        dates = [date_from + datetime.timedelta(days=day_delta) for day_delta in range(0, 7)]

        task_stats = {}
        for date in dates:
            processed_tasks = {}
            for task_event in reversed(task_events):
                if task_event.task_id not in processed_tasks:
                    if task_event.created.date() <= date:
                        key = self._get_key(task_event)
                        if key not in task_stats:
                            task_stats[key] = TasksStatsFactory.build(
                                status_id=task_event.status_id,
                                count=0,
                                date=date,
                                executor_id=task_event.executor_id
                            )
                        task_stats[key].count += 1
                        processed_tasks[task_event.task_id] = True
        return list(task_stats.values())
