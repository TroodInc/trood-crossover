class ORDER_STATUS:
    REQUEST_NOT_HANDLED = 0
    ESTIMATION_SENT = 1
    AGREEMENT = 2
    IN_WORK = 3
    SHIPPED = 4
    DOCUMENTS = 5
    DONE = 6
    PAUSED = 7
    DECLINED = 8

    REVERTED = {
        0: 'REQUEST_NOT_HANDLED',
        1: 'ESTIMATION_SENT',
        2: 'AGREEMENT',
        3: 'IN_WORK',
        4: 'SHIPPED',
        5: 'DOCUMENTS',
        6: 'DONE',
        7: 'PAUSED',
        8: 'DECLINED'
    }


class ORDER_DECLINE_REASON:
    PRICE_NOT_SATISFIED = 1
    TERM_NOT_SATISFIED = 2
    QUALITY_NOT_SATISFIED = 3
    PROJECT_ABORT = 4
    OTHER = 5


class ORDER_STATE:
    ACTIVE = 1
    DELETED = 2
    ARCHIVED = 3


class ORDER_REGION:
    CHINA = 'CHINA'
    RUSSIA = 'RUSSIA'


class TASK_STATUS:
    ACTIVE = 1
    OVERDUE = 2
    DONE = 3
    CANCELED = 4


class CURRENCY:
    RUB = 1
    USB = 2


class SOURCE:
    WEBSITE = 1
    RECOMMENDATION = 2
    OLD_CLIENT = 3
    ACTIVE_SEARCH = 4


class LEAD_STATUS:
    FIRST_CONTACT = 1
    REQUEST_AWAITING = 2
    REQUEST_RECEIVED = 3
    NEW_CLIENT = 4
    DECLINED = 5


class CONTRACTOR_TYPE:
    LEAD = 1
    NEW_CLIENT = 2
    REGULAR_CLIENT = 3

    REVERSED = {
        1: 'LEAD',
        2: 'NEW_CLIENT',
        3: 'REGULAR_CLIENT'
    }
