import time

from rest_framework import serializers


class DateTimeJsTimestampField(serializers.Field):
    def to_representation(self, value):
        return time.mktime(value.timetuple()) * 1000


class DateJsTimestampField(serializers.Field):
    def to_representation(self, value):
        return time.mktime(value.timetuple()) * 1000
