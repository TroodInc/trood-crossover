import datetime

from models import BaseModel


class Payment(BaseModel):
    payment_id: int = None
    planned_date: datetime.date = None
    payed_date: datetime.date = None
    currency: str = None
    payer_type: str = None
    payer_id: int = None
    recipient_type: str = None
    recipient_id: int = None
    planned_amount: float = None
    payed_amount: float = None
    base_order_id: int = None
    base_order_status: int = None
    base_order_state_id: int = None
    base_order_decline_reason_id: int = None
    executor_id: int = None
    target_type: str = None
    target_id: int = None
    base_order_source_id: int = None
    lead_status_id: int = None
