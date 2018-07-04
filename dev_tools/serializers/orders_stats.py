from rest_framework import serializers

from serializers import DateJsTimestampField


class OrdersStatsSerializer(serializers.Serializer):
    target_type = serializers.CharField()
    target_id = serializers.IntegerField()
    status_id = serializers.IntegerField()
    count = serializers.IntegerField()
    date = DateJsTimestampField()
    responsible_id = serializers.IntegerField()
