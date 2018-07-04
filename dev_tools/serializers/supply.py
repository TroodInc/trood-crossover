from rest_framework import serializers

from serializers import DateTimeJsTimestampField


class SupplyEventSerializer(serializers.Serializer):
    supply_id = serializers.IntegerField()
    currency = serializers.CharField()
    unit = serializers.CharField()
    executor_id = serializers.IntegerField()
    target_id = serializers.IntegerField()
    target_type = serializers.CharField()
    base_order_id = serializers.IntegerField()

    total = serializers.FloatField()
    created = DateTimeJsTimestampField()
    deliver = DateTimeJsTimestampField()
