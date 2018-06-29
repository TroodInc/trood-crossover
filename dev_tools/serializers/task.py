from rest_framework import serializers

from . import JsTimestampField, DateJsTimestampField


class TaskEventSerializer(serializers.Serializer):
    responsible_id = serializers.IntegerField()
    task_id = serializers.IntegerField()
    executor_id = serializers.IntegerField()
    created = JsTimestampField()
    created_date = DateJsTimestampField(source='created')
    deadline = JsTimestampField()
    deadline_date = DateJsTimestampField(source='deadline')
    status_id = serializers.IntegerField()
