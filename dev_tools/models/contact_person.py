from models import BaseModel


class ContactPerson(BaseModel):
    id: int = None
    email: int = None
    name: str = None
    target_type: str = None
    target_id: int = None
