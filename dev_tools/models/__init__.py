class BaseModel:
    def __init__(self, **kwargs):
        for key, value in kwargs.items():
            assert hasattr(self, key), "Object {} has no field {}".format(self.__class__.__name__, key)
            setattr(self, key, value)