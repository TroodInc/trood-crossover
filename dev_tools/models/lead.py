from models import BaseModel


class Lead(BaseModel):
    lead_id: int = None
    name: str = None
    source_id: int = None
    executor_id: int = None
