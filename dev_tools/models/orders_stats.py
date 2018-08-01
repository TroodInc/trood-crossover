from models import BaseModel


class OrdersStats(BaseModel):
    target_type: str = None
    target_id: int = None
    status_id: int = None
    count: int = None
    date: int = None
    responsible_id: int = None