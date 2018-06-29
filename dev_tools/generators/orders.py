import datetime

from constants import ORDER_STATUS, ORDER_REGION, ORDER_STATE, ORDER_DECLINE_REASON
from factories.client import ClientFactory
from factories.lead import LeadFactory
from factories.order import OrderFactory
from factories.staff import StaffFactory
from .base import BaseDataGenerator


class OrdersGenerator(BaseDataGenerator):
    def get_data(self):
        date_from = datetime.date.today() - datetime.timedelta(days=5)
        dates = [date_from + datetime.timedelta(days=day_delta) for day_delta in range(0, 7)]

        client = ClientFactory.build()
        executor = StaffFactory.build()
        responsible = StaffFactory.build()

        lead = LeadFactory.build()
        orders = []

        # 1st order
        order = OrderFactory.build(
            target_type='client',
            target_id=client.client_id,
            status_id=ORDER_STATUS.REQUEST_NOT_HANDLED,
            responsible_id=responsible.staff_id,
            executor_id=executor.staff_id,
            decline_reason_id=None,
            region_id=ORDER_REGION.RUSSIA,
            created=datetime.datetime.combine(dates[0], datetime.time(hour=14, minute=35)),
            state_id=ORDER_STATE.ACTIVE,
            event_date=dates[0]
        )
        orders.append(order)
        orders.append(
            OrderFactory.clone(
                order,
                status_id=ORDER_STATUS.AGREEMENT,
                event_date=order.created.date() + datetime.timedelta(days=2)
            )
        )
        orders.append(
            OrderFactory.clone(
                order,
                status_id=ORDER_STATUS.SHIPPED,
                event_date=order.created.date() + datetime.timedelta(days=3)
            )
        )
        orders.append(
            OrderFactory.clone(
                order,
                status_id=ORDER_STATUS.DONE,
                event_date=order.created.date() + datetime.timedelta(days=6)
            )
        )

        # 2nd order
        order = OrderFactory.build(
            target_type='client',
            target_id=client.client_id,
            status_id=ORDER_STATUS.REQUEST_NOT_HANDLED,
            responsible_id=responsible.staff_id,
            executor_id=executor.staff_id,
            decline_reason_id=None,
            region_id=ORDER_REGION.RUSSIA,
            created=datetime.datetime.combine(dates[2], datetime.time(hour=11, minute=23)),
            state_id=ORDER_STATE.ACTIVE,
            event_date=dates[2]
        )
        orders.append(order)
        orders.append(
            OrderFactory.clone(
                order,
                status_id=ORDER_STATUS.AGREEMENT,
                event_date=order.created.date() + datetime.timedelta(days=6)
            )
        )
        orders.append(
            OrderFactory.clone(
                order,
                status_id=ORDER_STATUS.DECLINED,
                decline_reason_id=ORDER_DECLINE_REASON.PRICE_NOT_SATISFIED,
                event_date=order.created.date() + datetime.timedelta(days=6)
            )
        )

        # 3rd order
        order = OrderFactory.build(
            target_type='lead',
            target_id=lead.lead_id,
            status_id=ORDER_STATUS.REQUEST_NOT_HANDLED,
            responsible_id=responsible.staff_id,
            executor_id=executor.staff_id,
            decline_reason_id=None,
            region_id=ORDER_REGION.RUSSIA,
            created=datetime.datetime.combine(dates[4], datetime.time(hour=11, minute=23)),
            state_id=ORDER_STATE.ACTIVE,
            event_date=dates[4]
        )
        orders.append(order)
        orders.append(
            OrderFactory.clone(
                order,
                status_id=ORDER_STATUS.AGREEMENT,
                event_date=order.created.date() + datetime.timedelta(days=4)
            )
        )
        orders.append(
            OrderFactory.clone(
                order,
                status_id=ORDER_STATUS.DECLINED,
                decline_reason_id=ORDER_DECLINE_REASON.QUALITY_NOT_SATISFIED,
                event_date=order.created.date() + datetime.timedelta(days=5)
            )
        )

        # 4th order
        order = OrderFactory.build(
            target_type='client',
            target_id=client.client_id,
            status_id=ORDER_STATUS.REQUEST_NOT_HANDLED,
            responsible_id=responsible.staff_id,
            executor_id=executor.staff_id,
            decline_reason_id=None,
            region_id=ORDER_REGION.RUSSIA,
            created=datetime.datetime.combine(dates[6], datetime.time(hour=11, minute=23)),
            state_id=ORDER_STATE.ACTIVE,
            event_date=dates[6]
        )
        orders.append(order)
        orders.append(
            OrderFactory.clone(
                order,
                status_id=ORDER_STATUS.AGREEMENT,
                event_date=order.created.date() + datetime.timedelta(days=5)
            )
        )
        orders.append(
            OrderFactory.clone(
                order,
                status_id=ORDER_STATUS.DECLINED,
                decline_reason_id=ORDER_DECLINE_REASON.TERM_NOT_SATISFIED,
                event_date=order.created.date() + datetime.timedelta(days=6)
            )
        )

        # 5th order
        order = OrderFactory.build(
            target_type='client',
            target_id=client.client_id,
            status_id=ORDER_STATUS.REQUEST_NOT_HANDLED,
            responsible_id=responsible.staff_id,
            executor_id=executor.staff_id,
            decline_reason_id=None,
            region_id=ORDER_REGION.RUSSIA,
            created=datetime.datetime.combine(dates[5], datetime.time(hour=11, minute=23)),
            state_id=ORDER_STATE.ACTIVE,
            event_date=dates[5]
        )
        orders.append(order)
        orders.append(
            OrderFactory.clone(
                order, status_id=ORDER_STATUS.AGREEMENT,
                event_date=order.created.date() + datetime.timedelta(days=3)
            )
        )
        orders.append(
            OrderFactory.clone(
                order,
                status_id=ORDER_STATUS.IN_WORK,
                event_date=order.created.date() + datetime.timedelta(days=4)
            )
        )

        # 6th order
        order = OrderFactory.build(
            target_type='lead',
            target_id=lead.lead_id,
            status_id=ORDER_STATUS.REQUEST_NOT_HANDLED,
            responsible_id=responsible.staff_id,
            executor_id=executor.staff_id,
            decline_reason_id=None,
            region_id=ORDER_REGION.RUSSIA,
            created=datetime.datetime.combine(dates[6], datetime.time(hour=11, minute=23)),
            state_id=ORDER_STATE.ACTIVE,
            event_date=dates[6]
        )
        orders.append(order)
        orders.append(
            OrderFactory.clone(
                order,
                status_id=ORDER_STATUS.AGREEMENT,
                event_date=order.created.date() + datetime.timedelta(days=1)
            )
        )
        orders.append(
            OrderFactory.clone(
                order,
                status_id=ORDER_STATUS.DOCUMENTS,
                event_date=order.created.date() + datetime.timedelta(days=1)
            )
        )

        return orders
