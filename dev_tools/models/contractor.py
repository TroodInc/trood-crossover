from models import BaseModel


class Contractor(BaseModel):
    contractor_id: int = None
    name: str = None
    source_id: int = None
    type_id: int = None
    lead_status_id: int = None
    executor_id: int = None
    responsible_id: int = None
