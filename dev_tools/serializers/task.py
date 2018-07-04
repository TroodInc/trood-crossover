from rest_framework import serializers

from serializers import DateTimeJsTimestampField


class TaskEventSerializer(serializers.Serializer):
    responsible_id = serializers.IntegerField()
    task_id = serializers.IntegerField()
    executor_id = serializers.IntegerField()
    created = DateTimeJsTimestampField()
    deadline = DateTimeJsTimestampField()
    status_id = serializers.IntegerField()
