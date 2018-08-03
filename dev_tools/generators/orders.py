import datetime

from constants import ORDER_STATUS, ORDER_REGION, ORDER_STATE, ORDER_DECLINE_REASON, SOURCE, CONTRACTOR_TYPE, \
    LEAD_STATUS
from factories.contractor import ContractorFactory
from factories.employee import EmployeeFactory
from factories.order import BaseOrderEventFactory
from .base import BaseDataGenerator


class OrdersGenerator(BaseDataGenerator):
    def get_data(self):
        date_from = datetime.date.today() - datetime.timedelta(days=7)
        dates = [date_from + datetime.timedelta(days=day_delta) for day_delta in range(0, 7)]

        executor = EmployeeFactory.build()
        responsible = EmployeeFactory.build()

        client_contractor = ContractorFactory.build(
            source_id=SOURCE.OLD_CLIENT,
            executor_id=responsible.employee_id,
            responsible_id=responsible.employee_id,
            type_id=CONTRACTOR_TYPE.REGULAR_CLIENT,
            lead_status_id=LEAD_STATUS.NEW_CLIENT
        )
        lead_contractor = ContractorFactory.build(
            source_id=SOURCE.RECOMMENDATION,
            executor_id=executor.employee_id,
            responsible_id=responsible.employee_id,
            type_id=CONTRACTOR_TYPE.LEAD,
            lead_status_id=LEAD_STATUS.FIRST_CONTACT
        )
        orders = []

        # 1st order
        order = BaseOrderEventFactory.build(
            contractor_id=client_contractor.contractor_id,
            contractor_type_id=client_contractor.type_id,
            contractor_lead_status_id=client_contractor.lead_status_id,
            status_id=ORDER_STATUS.REQUEST_NOT_HANDLED,
            executor_id=executor.employee_id,
            responsible_id=responsible.employee_id,
            decline_reason_id=None,
            region_id=ORDER_REGION.RUSSIA,
            created=datetime.datetime.combine(dates[0], datetime.time(hour=14, minute=35)),
            state_id=ORDER_STATE.ACTIVE,
            event_date=dates[0],
            source_id=client_contractor.source_id
        )
        orders.append(order)
        orders.append(
            BaseOrderEventFactory.clone(
                order,
                status_id=ORDER_STATUS.AGREEMENT,
                event_date=order.event_date + datetime.timedelta(days=2)
            )
        )
        orders.append(
            BaseOrderEventFactory.clone(
                order,
                status_id=ORDER_STATUS.SHIPPED,
                event_date=order.event_date + datetime.timedelta(days=3)
            )
        )
        orders.append(
            BaseOrderEventFactory.clone(
                order,
                status_id=ORDER_STATUS.DONE,
                event_date=order.event_date + datetime.timedelta(days=6)
            )
        )

        # 2nd order
        order = BaseOrderEventFactory.build(
            contractor_id=client_contractor.contractor_id,
            contractor_type_id=client_contractor.type_id,
            contractor_lead_status_id=client_contractor.lead_status_id,
            status_id=ORDER_STATUS.REQUEST_NOT_HANDLED,
            executor_id=executor.employee_id,
            responsible_id=responsible.employee_id,
            decline_reason_id=None,
            region_id=ORDER_REGION.RUSSIA,
            created=datetime.datetime.combine(dates[2], datetime.time(hour=11, minute=23)),
            state_id=ORDER_STATE.ACTIVE,
            event_date=dates[2],
            source_id=client_contractor.source_id
        )
        orders.append(order)
        orders.append(
            BaseOrderEventFactory.clone(
                order,
                status_id=ORDER_STATUS.AGREEMENT,
                event_date=order.event_date + datetime.timedelta(days=5)
            )
        )
        orders.append(
            BaseOrderEventFactory.clone(
                order,
                status_id=ORDER_STATUS.DECLINED,
                decline_reason_id=ORDER_DECLINE_REASON.PRICE_NOT_SATISFIED,
                event_date=order.event_date + datetime.timedelta(days=5)
            )
        )

        # 3rd order
        order = BaseOrderEventFactory.build(
            contractor_id=lead_contractor.contractor_id,
            contractor_type_id=lead_contractor.type_id,
            contractor_lead_status_id=lead_contractor.lead_status_id,
            status_id=ORDER_STATUS.REQUEST_NOT_HANDLED,
            executor_id=responsible.employee_id,
            responsible_id=executor.employee_id,
            decline_reason_id=None,
            region_id=ORDER_REGION.RUSSIA,
            created=datetime.datetime.combine(dates[4], datetime.time(hour=11, minute=23)),
            state_id=ORDER_STATE.ACTIVE,
            event_date=dates[4],
            source_id=lead_contractor.source_id
        )
        orders.append(order)
        orders.append(
            BaseOrderEventFactory.clone(
                order,
                status_id=ORDER_STATUS.AGREEMENT,
                event_date=order.event_date + datetime.timedelta(days=2)
            )
        )
        orders.append(
            BaseOrderEventFactory.clone(
                order,
                status_id=ORDER_STATUS.DECLINED,
                decline_reason_id=ORDER_DECLINE_REASON.QUALITY_NOT_SATISFIED,
                event_date=order.event_date + datetime.timedelta(days=3)
            )
        )

        # 4th order
        order = BaseOrderEventFactory.build(
            contractor_id=client_contractor.contractor_id,
            contractor_type_id=client_contractor.type_id,
            contractor_lead_status_id=client_contractor.lead_status_id,
            status_id=ORDER_STATUS.REQUEST_NOT_HANDLED,
            executor_id=executor.employee_id,
            responsible_id=responsible.employee_id,
            decline_reason_id=None,
            region_id=ORDER_REGION.RUSSIA,
            created=datetime.datetime.combine(dates[5], datetime.time(hour=11, minute=23)),
            state_id=ORDER_STATE.ACTIVE,
            event_date=dates[5],
            source_id=client_contractor.source_id
        )
        orders.append(order)
        orders.append(
            BaseOrderEventFactory.clone(
                order,
                status_id=ORDER_STATUS.AGREEMENT,
                event_date=order.event_date + datetime.timedelta(days=1)
            )
        )
        orders.append(
            BaseOrderEventFactory.clone(
                order,
                status_id=ORDER_STATUS.DECLINED,
                decline_reason_id=ORDER_DECLINE_REASON.TERM_NOT_SATISFIED,
                event_date=order.event_date + datetime.timedelta(days=2)
            )
        )

        # 5th order
        order = BaseOrderEventFactory.build(
            contractor_id=client_contractor.contractor_id,
            contractor_type_id=client_contractor.type_id,
            contractor_lead_status_id=client_contractor.lead_status_id,
            status_id=ORDER_STATUS.REQUEST_NOT_HANDLED,
            executor_id=responsible.employee_id,
            responsible_id=executor.employee_id,
            decline_reason_id=None,
            region_id=ORDER_REGION.RUSSIA,
            created=datetime.datetime.combine(dates[5], datetime.time(hour=11, minute=23)),
            state_id=ORDER_STATE.ACTIVE,
            event_date=dates[5],
            source_id=client_contractor.source_id
        )
        orders.append(order)
        orders.append(
            BaseOrderEventFactory.clone(
                order, status_id=ORDER_STATUS.AGREEMENT,
                event_date=order.event_date + datetime.timedelta(days=1)
            )
        )
        orders.append(
            BaseOrderEventFactory.clone(
                order,
                status_id=ORDER_STATUS.IN_WORK,
                event_date=order.event_date + datetime.timedelta(days=2)
            )
        )

        # 6th order
        order = BaseOrderEventFactory.build(
            contractor_id=lead_contractor.contractor_id,
            contractor_type_id=lead_contractor.type_id,
            contractor_lead_status_id=lead_contractor.lead_status_id,
            status_id=ORDER_STATUS.REQUEST_NOT_HANDLED,
            executor_id=executor.employee_id,
            responsible_id=responsible.employee_id,
            decline_reason_id=None,
            region_id=ORDER_REGION.RUSSIA,
            created=datetime.datetime.combine(dates[6], datetime.time(hour=11, minute=23)),
            state_id=ORDER_STATE.ACTIVE,
            event_date=dates[6],
            source_id=lead_contractor.source_id
        )
        orders.append(order)
        orders.append(
            BaseOrderEventFactory.clone(
                order,
                status_id=ORDER_STATUS.AGREEMENT,
                event_date=order.event_date + datetime.timedelta(days=1)
            )
        )
        orders.append(
            BaseOrderEventFactory.clone(
                order,
                status_id=ORDER_STATUS.DOCUMENTS,
                event_date=order.event_date + datetime.timedelta(days=1)
            )
        )

        return orders
