import copy

import factory
from custodian.exceptions import RecordAlreadyExistsException

from custodian_objects import client


class BaseFactory(factory.Factory):
    @classmethod
    def clone(cls, obj, **update_fields):
        cloned_object = copy.deepcopy(obj)
        for key, value in update_fields.items():
            assert hasattr(obj, key), "Object {} has no field {}".format(obj.__class__.__name__, key)
            setattr(cloned_object, key, value)
        cls._update_custodian_record(cloned_object)
        return cloned_object

    @classmethod
    def _create_custodian_record(cls, obj):
        try:
            client.records.create(cls._factory_custodian_record(obj))
        except RecordAlreadyExistsException:
            cls._update_custodian_record(obj)

    @classmethod
    def _update_custodian_record(cls, obj):
        client.records.update(cls._factory_custodian_record(obj))  if cls._factory_custodian_record(obj) else None

    @classmethod
    def _factory_custodian_record(cls, obj):
        # raise NotImplementedError
        return None
