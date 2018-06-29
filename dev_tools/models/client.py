from models import BaseModel


class Client(BaseModel):
    client_id: int = None
    name: str = None
