import copy
import datetime
from typing import List

from factories.payment import PaymentFactory
from models.orderevent import OrderEvent
from models.recipient import Recipient
from .base import BaseDataGenerator

TOPLINE = Recipient(recipient_id="TOPLINE", name='ООО "Топлайн"')
VELITTO = Recipient(recipient_id="VELITTO", name='ООО "Велитто"')


class PaymentsGenerator(BaseDataGenerator):
    def _actualize_with_order_status_change(self, payments, order_status):
        copy_of_payments = copy.deepcopy(payments)
        processed_payments = []
        for payment in list(reversed(payments)):
            if payment.payment_id not in processed_payments:
                copy_of_payments.append(PaymentFactory.clone(payment, base_order_status=order_status))
                processed_payments.append(payment.payment_id)
        return copy_of_payments

    def _scenario_0(self, order_events: List[OrderEvent]):
        assert len(
            set([x.base_order_id for x in order_events])) == 1, "Order events are specified for more than 1 order"

        order_event = order_events[0]
        payments = []
        #
        payment = PaymentFactory.build(
            payer_id=order_event.target_id,
            payer_type=order_event.target_type,
            planned_date=order_event.created.date() + datetime.timedelta(days=2),
            payed_date=None,
            recipient_id=TOPLINE.recipient_id,
            recipient_type='recipient',
            planned_amount=70000,
            payed_amount=0,
            base_order_id=order_event.base_order_id,
            base_order_status=order_event.status_id,
            base_order_decline_reason_id=order_event.decline_reason_id,
            base_order_state_id=order_event.state_id,
            executor_id=order_event.executor_id,
            base_order_source_id=order_event.source_id,
            lead_status_id=order_event.lead_status_id
        )
        payments.append(payment)
        # this payment is actually payed but with delay of 1 day
        payments.append(
            PaymentFactory.clone(
                payment,
                payed_amount=70000,
                payed_date=(payment.planned_date + datetime.timedelta(days=1))
            )
        )
        # order`s status changed
        order_event = order_events[1]
        payments = self._actualize_with_order_status_change(payments, order_status=order_event.status_id)
        #
        payments.append(
            PaymentFactory.build(
                payer_id=order_event.target_id,
                payer_type=order_event.target_type,
                planned_date=order_event.created.date() + datetime.timedelta(days=6),
                payed_date=None,
                recipient_id=VELITTO.recipient_id,
                recipient_type='recipient',
                planned_amount=470000,
                payed_amount=0,
                base_order_id=order_event.base_order_id,
                base_order_status=order_event.status_id,
                base_order_decline_reason_id=order_event.decline_reason_id,
                base_order_state_id=order_event.state_id,
                executor_id=order_event.executor_id,
                base_order_source_id=order_event.source_id,
                lead_status_id=order_event.lead_status_id
            )
        )
        #
        payment = PaymentFactory.build(
            payer_id=order_event.target_id,
            payer_type=order_event.target_type,
            planned_date=order_event.created.date() + datetime.timedelta(days=7),
            payed_date=None,
            recipient_id=TOPLINE.recipient_id,
            recipient_type='recipient',
            planned_amount=800000,
            payed_amount=0,
            base_order_id=order_event.base_order_id,
            base_order_status=order_event.status_id,
            base_order_decline_reason_id=order_event.decline_reason_id,
            base_order_state_id=order_event.state_id,
            executor_id=order_event.executor_id,
            base_order_source_id=order_event.source_id,
            lead_status_id=order_event.lead_status_id
        )
        payments.append(payment)
        #
        # this payment is actually payed too
        # order`s status changed
        order_event = order_events[2]

        payments.append(
            PaymentFactory.clone(
                payment,
                payed_amount=800000,
                payed_date=payment.planned_date
            )
        )
        payments = self._actualize_with_order_status_change(payments, order_status=order_event.status_id)

        # process rest of order`s status changes
        for order_event in order_events[2:]:
            payments = self._actualize_with_order_status_change(payments, order_status=order_event.status_id)
        return payments

    def _scenario_1(self, order_events: List[OrderEvent]):
        assert len(
            set([x.base_order_id for x in order_events])) == 1, "Order events are specified for more than 1 order"

        order_event = order_events[0]
        payments = []
        #
        payment = PaymentFactory.build(
            payer_id=order_event.target_id,
            payer_type=order_event.target_type,
            planned_date=order_event.created.date() + datetime.timedelta(days=2),
            payed_date=None,
            recipient_id=TOPLINE.recipient_id,
            recipient_type='recipient',
            planned_amount=120000,
            payed_amount=0,
            base_order_id=order_event.base_order_id,
            base_order_status=order_event.status_id,
            base_order_decline_reason_id=order_event.decline_reason_id,
            base_order_state_id=order_event.state_id,
            executor_id=order_event.executor_id,
            base_order_source_id=order_event.source_id,
            lead_status_id=order_event.lead_status_id
        )
        payments.append(payment)
        # this payment is actually payed
        payments.append(
            PaymentFactory.clone(
                payment,
                payed_amount=payment.planned_amount,
                payed_date=payment.planned_date
            )
        )
        # order`s status changed
        order_event = order_events[1]
        payments = self._actualize_with_order_status_change(payments, order_status=order_event.status_id)
        #
        payments.append(
            PaymentFactory.build(
                payer_id=order_event.target_id,
                payer_type=order_event.target_type,
                planned_date=order_event.created.date() + datetime.timedelta(days=6),
                payed_date=None,
                recipient_id=VELITTO.recipient_id,
                recipient_type='recipient',
                planned_amount=470000,
                payed_amount=0,
                base_order_id=order_event.base_order_id,
                base_order_status=order_event.status_id,
                base_order_decline_reason_id=order_event.decline_reason_id,
                base_order_state_id=order_event.state_id,
                executor_id=order_event.executor_id,
                base_order_source_id=order_event.source_id,
                lead_status_id=order_event.lead_status_id
            )
        )
        payments.append(payment)
        #
        # this payment is actually payed too
        # order`s status changed

        payments.append(
            PaymentFactory.clone(
                payment,
                payed_amount=payment.planned_amount,
                payed_date=payment.planned_date
            )
        )
        order_event = order_events[2]
        payments = self._actualize_with_order_status_change(payments, order_status=order_event.status_id)

        # process rest of order`s status changes
        for order_event in order_events[2:]:
            payments = self._actualize_with_order_status_change(payments, order_status=order_event.status_id)
        return payments

    def _scenario_2(self, order_events: List[OrderEvent]):
        assert len(
            set([x.base_order_id for x in order_events])) == 1, "Order events are specified for more than 1 order"

        order_event = order_events[0]
        payments = []
        #
        payment = PaymentFactory.build(
            payer_id=order_event.target_id,
            payer_type=order_event.target_type,
            planned_date=order_event.created.date() + datetime.timedelta(days=2),
            payed_date=None,
            recipient_id=TOPLINE.recipient_id,
            recipient_type='recipient',
            planned_amount=50000,
            payed_amount=0,
            base_order_id=order_event.base_order_id,
            base_order_status=order_event.status_id,
            base_order_decline_reason_id=order_event.decline_reason_id,
            base_order_state_id=order_event.state_id,
            executor_id=order_event.executor_id,
            base_order_source_id=order_event.source_id,
            lead_status_id=order_event.lead_status_id
        )
        payments.append(payment)
        # this payment is actually payed
        payments.append(
            PaymentFactory.clone(
                payment,
                payed_amount=payment.planned_amount,
                payed_date=payment.planned_date
            )
        )
        order_event = order_events[1]
        payments = self._actualize_with_order_status_change(payments, order_status=order_event.status_id)

        # process rest of order`s status changes
        for order_event in order_events[1:]:
            payments = self._actualize_with_order_status_change(payments, order_status=order_event.status_id)
        return payments

    def _get_order_events_sets(self, order_events: List[OrderEvent]):
        order_events_set = {}
        for order_event in order_events:
            if order_event.base_order_id not in order_events_set:
                order_events_set[order_event.base_order_id] = []
            order_events_set[order_event.base_order_id].append(order_event)
        return order_events_set.values()

    def get_data(self, order_events):
        payments = []

        i = 0
        for order_events_set in self._get_order_events_sets(order_events):
            payments += getattr(self, '_scenario_{}'.format(i % 3))(order_events_set)
            i += 1
        return payments
