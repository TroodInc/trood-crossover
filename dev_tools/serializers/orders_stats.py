from rest_framework import serializers

from serializers import DateJsTimestampField


class OrdersStatsSerializer(serializers.Serializer):
    contractor_type_id = serializers.IntegerField()
    status_id = serializers.IntegerField()
    count = serializers.IntegerField()
    date = DateJsTimestampField()
    executor_id = serializers.IntegerField()
