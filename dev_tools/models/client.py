from models import BaseModel


class Client(BaseModel):
    client_id: int = None
    name: str = None
    source_id: int = None
    executor_id: int = None