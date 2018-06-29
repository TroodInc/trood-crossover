import datetime
import time

from rest_framework import serializers


class JsTimestampField(serializers.Field):
    def to_representation(self, value):
        return time.mktime(value.timetuple()) * 1000


class DateJsTimestampField(serializers.Field):
    def to_representation(self, value):
        if isinstance(value, datetime.datetime):
            return time.mktime(value.date().timetuple()) * 1000
        else:
            return time.mktime(value.timetuple()) * 1000
