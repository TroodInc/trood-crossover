import datetime
from typing import List

from factories.order_processing_duration import OrderProcessingDurationFactory
from models.orderevent import OrderEvent
from models.order_processing_duration import OrderProcessingDuration
from .base import BaseDataGenerator


class OrderProcessingDurationsGenerator(BaseDataGenerator):
    def get_data(self, order_events: List[OrderEvent]) -> List[OrderProcessingDuration]:
        order_processing_durations = []
        for i in range(len(order_events)):
            order_event = order_events[i]
            # calculate duration
            duration = None
            try:
                if order_events[i + 1].base_order_id == order_event.base_order_id:
                    duration = (order_events[i + 1].event_date - order_event.event_date).days
            except IndexError:
                pass
            if duration is None:
                duration = (datetime.date.today() + datetime.timedelta(days=4) - order_event.event_date).days
            order_processing_durations.append(
                OrderProcessingDurationFactory.build(
                    duration=duration,
                    status_id=order_event.status_id,
                    base_order_id=order_event.base_order_id,
                    processing_start_date=order_event.event_date,
                    responsible_id=order_event.responsible_id
                )
            )
        return order_processing_durations
