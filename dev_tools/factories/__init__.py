import copy

import factory


class BaseFactory(factory.Factory):
    @classmethod
    def clone(cls, obj, **update_fields):
        cloned_object = copy.deepcopy(obj)
        for key, value in update_fields.items():
            assert hasattr(obj, key), "Object {} has no field {}".format(obj.__class__.__name__, key)
            setattr(cloned_object, key, value)
        return cloned_object