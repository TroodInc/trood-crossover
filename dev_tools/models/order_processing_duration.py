from datetime import date

from models import BaseModel


class OrderProcessingDuration(BaseModel):
    base_order_id: int = None
    status_id: int = None
    duration: int = None
    processing_start_date: date = None
    executor_id: int = None
