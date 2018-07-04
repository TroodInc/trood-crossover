import datetime
from typing import List

from factories.orders_stats import OrdersStatsFactory
from models.orderevent import OrderEvent
from models.orders_stats import OrdersStats
from .base import BaseDataGenerator


class OrdersStatsGenerator(BaseDataGenerator):
    def _get_key(self, order_event: OrderEvent):
        return '_'.join([order_event.target_type, str(order_event.target_id), str(order_event.status_id),
                         order_event.event_date.isoformat(), str(order_event.responsible_id)])

    def get_data(self, order_events: List[OrderEvent]) -> List[OrdersStats]:
        date_from = datetime.date.today() - datetime.timedelta(days=7)
        dates = [date_from + datetime.timedelta(days=day_delta) for day_delta in range(0, 7)]

        orders_stats = {}
        for date in dates:
            processed_orders = {}
            for order_event in reversed(order_events):
                if order_event.base_order_id not in processed_orders:
                    if order_event.event_date <= date:
                        key = self._get_key(order_event)
                        if key not in orders_stats:
                            orders_stats[key] = OrdersStatsFactory.build(
                                target_type=order_event.target_type,
                                target_id=order_event.target_id,
                                status_id=order_event.status_id,
                                count=0,
                                date=date,
                                responsible_id=order_event.responsible_id
                            )
                        orders_stats[key].count += 1
                        processed_orders[order_event.base_order_id] = True
        return list(orders_stats.values())
