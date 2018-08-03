import datetime

from models import BaseModel


class OrderEvent(BaseModel):
    name: int = None
    base_order_id: int = None
    contractor_id: int = None
    contractor_type_id: int = None
    contractor_lead_status_id: int = None
    status_id: int = None
    executor_id: int = None
    responsible_id: int = None
    decline_reason_id: int = None
    region_id: str = None
    created: datetime.datetime = None
    state_id: int = None
    event_date: datetime.date = None
    source_id: int = None
