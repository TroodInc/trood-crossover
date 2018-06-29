import datetime

from models import BaseModel


class Order(BaseModel):
    base_order_id: int = None
    target_type: str = None
    target_id: int = None
    status_id: int = None
    responsible_id: int = None
    executor_id: int = None
    decline_reason_id: int = None
    region_id: str = None
    created: datetime.datetime = None
    state_id: int = None
    event_date: datetime.date = None
