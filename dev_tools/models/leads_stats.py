from models import BaseModel


class TasksStats(BaseModel):
    status_id: int = None
    count: int = None
    date: int = None
    executor_id: int = None
