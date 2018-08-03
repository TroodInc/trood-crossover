from rest_framework import serializers

from serializers import DateJsTimestampField


class OrderProcessingDurationSerializer(serializers.Serializer):
    base_order_id = serializers.IntegerField()
    status_id = serializers.IntegerField()
    duration = serializers.IntegerField()
    executor_id = serializers.IntegerField()
    processing_start_date = DateJsTimestampField()
