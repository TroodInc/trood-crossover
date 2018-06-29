import datetime

from models import BaseModel


class Task(BaseModel):
    task_id: int = None
    name: str = None
    description: str = None
    responsible_id: int = None
    executor_id: int = None
    created: datetime.datetime = None
    deadline: datetime.datetime = None
    status_id: int = None
