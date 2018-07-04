from models import BaseModel


class Recipient(BaseModel):
    recipient_id: int = None
    name: str = None
