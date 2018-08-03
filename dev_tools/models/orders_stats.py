from models import BaseModel


class OrdersStats(BaseModel):
    contractor_type_id: int = None
    status_id: int = None
    count: int = None
    date: int = None
    executor_id: int = None
