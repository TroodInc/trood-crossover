import calendar
import datetime

import pytz
from rest_framework import serializers


class DateTimeJsTimestampField(serializers.Field):
    def to_representation(self, value):
        value = pytz.UTC.localize(value)
        return calendar.timegm(value.timetuple()) * 1000


class DateJsTimestampField(serializers.Field):
    def to_representation(self, value):
        value = pytz.UTC.localize(
            datetime.datetime.combine(value, datetime.time(hour=0, minute=0, second=0, microsecond=0)))
        return calendar.timegm(value.timetuple()) * 1000
