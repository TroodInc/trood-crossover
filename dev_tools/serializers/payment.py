from rest_framework import serializers

from serializers import DateJsTimestampField


class PaymentEventSerializer(serializers.Serializer):
    # own properties
    payment_id = serializers.IntegerField()
    planned_date = DateJsTimestampField()
    payed_date = DateJsTimestampField()
    currency = serializers.CharField()
    payer_type = serializers.CharField()
    payer_id = serializers.IntegerField()
    recipient_type = serializers.CharField()
    recipient_id = serializers.CharField()
    planned_amount = serializers.IntegerField()
    payed_amount = serializers.IntegerField()
    base_order_id = serializers.IntegerField()
    #

    base_order_status = serializers.CharField()
    base_order_state_id = serializers.IntegerField()

    executor_id = serializers.IntegerField()
    responsible_id = serializers.IntegerField()
    base_order_source_id = serializers.IntegerField()
    contractor_type_id = serializers.IntegerField()
    contractor_lead_status_id = serializers.IntegerField()
