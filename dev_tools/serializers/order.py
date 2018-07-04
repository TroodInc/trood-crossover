from rest_framework import serializers

from serializers import DateTimeJsTimestampField


class OrderEventSerializer(serializers.Serializer):
    base_order_id = serializers.IntegerField()
    target_type = serializers.CharField()
    target_id = serializers.IntegerField()
    status_id = serializers.IntegerField()
    responsible_id = serializers.IntegerField()
    executor_id = serializers.IntegerField()
    decline_reason_id = serializers.IntegerField()
    region_id = serializers.CharField()
    state_id = serializers.IntegerField()
    created = DateTimeJsTimestampField()
    source_id = serializers.IntegerField()
    lead_status_id = serializers.IntegerField()
