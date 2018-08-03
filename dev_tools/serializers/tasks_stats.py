from rest_framework import serializers

from serializers import DateJsTimestampField


class TasksStatsSerializer(serializers.Serializer):
    status_id = serializers.IntegerField()
    count = serializers.IntegerField()
    date = DateJsTimestampField()
    executor_id = serializers.IntegerField()
