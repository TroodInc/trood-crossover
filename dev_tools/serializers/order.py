from rest_framework import serializers

from . import JsTimestampField, DateJsTimestampField


class OrderEventSerializer(serializers.Serializer):
    base_order_id = serializers.IntegerField()
    target_type = serializers.CharField()
    target_id = serializers.IntegerField()
    status_id = serializers.IntegerField()
    responsible_id = serializers.IntegerField()
    executor_id = serializers.IntegerField()
    decline_reason_id = serializers.IntegerField()
    region_id = serializers.CharField()
    created = JsTimestampField()
    created_date = DateJsTimestampField(source='created')
    state_id = serializers.IntegerField()
