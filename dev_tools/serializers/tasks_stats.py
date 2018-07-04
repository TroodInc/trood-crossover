from rest_framework import serializers

from serializers import DateJsTimestampField


class TasksStatsSerializer(serializers.Serializer):
    status_id = serializers.IntegerField()
    count = serializers.IntegerField()
    date = DateJsTimestampField()
    responsible_id = serializers.IntegerField()
