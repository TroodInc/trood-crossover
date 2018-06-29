from rest_framework import serializers

from . import JsTimestampField


class PaymentEventSerializer(serializers.Serializer):
    payment_id = serializers.IntegerField()
    planned_date = JsTimestampField()
    payed_date = JsTimestampField()
    currency = serializers.CharField()
    payer_type = serializers.CharField()
    payer_id = serializers.IntegerField()
    recipient_type = serializers.CharField()
    recipient_id = serializers.CharField()
    planned_amount = serializers.IntegerField()
    payed_amount = serializers.IntegerField()
    base_order_id = serializers.IntegerField()
    base_order_status = serializers.CharField()
    base_order_state_id = serializers.IntegerField()

    executor_id = serializers.IntegerField()
