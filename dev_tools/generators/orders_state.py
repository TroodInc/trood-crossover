import datetime
from typing import List

from factories.orders_state import OrdersStateFactory
from models.order import Order
from models.orders_state import OrdersState
from .base import BaseDataGenerator


class OrdersStateGenerator(BaseDataGenerator):
    def _get_key(self, order_event: Order):
        return '{}_{}_{}_{}'.format(order_event.target_type, order_event.target_id, order_event.status_id,
                                    order_event.event_date.isoformat())

    def get_data(self, order_events: List[Order]) -> List[OrdersState]:
        date_from = datetime.date.today() - datetime.timedelta(days=5)
        dates = [date_from + datetime.timedelta(days=day_delta) for day_delta in range(0, 7)]

        order_states = {}
        for date in dates:
            processed_orders = {}
            for order_event in reversed(order_events):
                if order_event.base_order_id not in processed_orders:
                    if order_event.event_date <= date:
                        key = self._get_key(order_event)
                        if key not in order_states:
                            order_states[key] = OrdersStateFactory.build(
                                target_type=order_event.target_type,
                                target_id=order_event.target_id,
                                status_id=order_event.status_id,
                                count=0,
                                date=date
                            )
                        order_states[key].count += 1
                        processed_orders[order_event.base_order_id] = True
        return list(order_states.values())
