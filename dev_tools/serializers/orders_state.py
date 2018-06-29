from rest_framework import serializers

from serializers import DateJsTimestampField


class OrdersStateSerializer(serializers.Serializer):
    target_type = serializers.CharField()
    target_id = serializers.IntegerField()
    status_id = serializers.IntegerField()
    count = serializers.IntegerField()
    ts = DateJsTimestampField(source='date')
