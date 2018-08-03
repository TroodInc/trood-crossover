import datetime

from models import BaseModel


class TaskEvent(BaseModel):
    task_id: int = None
    name: str = None
    description: str = None
    executor_id: int = None
    created: datetime.datetime = None
    deadline: datetime.datetime = None
    status_id: int = None
