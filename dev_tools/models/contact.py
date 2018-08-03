from models import BaseModel


class Contact(BaseModel):
    id: int = None
    name: str = None
    target_type: str = None
    target_id: str = None
    type: str = None
    value: str = None
