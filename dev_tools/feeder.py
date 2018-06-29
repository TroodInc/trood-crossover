import argparse
import json
import time

import pika


class ReportingPublisher:
    _connection = None
    _channel = None
    _queue_name = None

    def __init__(self, host, port, username, password, queue_name):
        self._connection = self._get_connection(host, port, username, password)
        self._queue_name = queue_name
        self._channel = self._setup_channel(self._connection)

    def __del__(self):
        self._connection.close()

    def publish(self, data):
        self._channel.basic_publish(
            exchange='',
            routing_key=self._queue_name,
            body=self._serialize_data(data)
        )

    @classmethod
    def _serialize_data(cls, data):
        return json.dumps(data)

    @classmethod
    def _get_connection(cls, host, port, username, password):
        connection = pika.BlockingConnection(
            pika.ConnectionParameters(
                host=host,
                port=port,
                credentials=pika.PlainCredentials(username, password) if username and password else
                pika.ConnectionParameters._DEFAULT
            )
        )
        return connection

    def _setup_channel(self, connection):
        channel = connection.channel()
        channel.queue_declare(
            queue=self._queue_name,
            durable=True,
            arguments={
                # 'x-message-ttl' : 1000,
                "x-dead-letter-exchange": "events_rejected"
            }
        )
        return channel


class EventReader:
    @staticmethod
    def get_events(filepath: str):
        file = open(filepath, 'r')
        events = json.loads(file.read())
        return events


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='Crossover data feeder')
    parser.add_argument('-f', '--filename', type=str, required=True, help='Name of file containing data to ingest')
    parser.add_argument('-ec', '--event-class', type=str, help='Event class', required=True)
    parser.add_argument('-rc', '--rabbit-connection-string', type=str, help='RabbitMQ connection string',
                        default='0.0.0.0:5672')
    args = parser.parse_args()
    host, port = args.rabbit_connection_string.split(':')
    publisher = ReportingPublisher(host, port, None, None, "events")

    for event in EventReader.get_events(args.filename):
        time.sleep(0.05)
        publisher.publish({'cl': args.event_class, 'ts': time.time() * 1000, **event})
