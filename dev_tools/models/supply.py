import datetime

from models import BaseModel


class Supply(BaseModel):
    supply_id: int = None

    currency: str = None
    unit: int = None
    total: float = None

    base_order_id: int = None
    executor_id: int = None
    created: datetime.datetime = None
    deliver: datetime.datetime = None
    target_id: int = None
    target_type: str = None
