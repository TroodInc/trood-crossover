# Crossover
[![](https://identityblitz.ru/wp-content/uploads/2015/05/slide3_reaxoft.png)](https://identityblitz.ru/)

Crossover is a tool to prommote a messages from Rabbit MQ to Druid DB in Real-time. The tool eats messages in `JSON` format transforms them accordenently to the predefined rules and feeds transformed messages to Druid DB. Also the tool archived eaten messages in files on HDFS with `lz4` compression so allowing dayly re-ingestion. 

# Building
```sh
sbt universal:packageZipTarball
```

# Help
About running scripts JAVA parameters:
```sh
./crossover -help 
```
About Crossover parameters:
```sh
./crossover --help
```

# Configuration
Example of the Crossover configuration:
```json
{
  "versions": [
    {
      "zookeeper": {
        "connect": "172.25.0.160:2181"
      },
      "rabbit": {
        "connection": {
          "virtual-host": "/",
          "hosts": [
            "172.25.0.160:5672"
          ],
          "username": "admin",
          "password": "oracle_1",
          "connection-timeout": "3s",
          "ssl": false
        },
        "queue": {
          "name": "events",
          "prefetch": 50
        }
      },
      "cubes": [
        {
          "name": "sales",
          "rollup": {
            "dimensions":  ["empId", "depId"],
            "indexGranularity": "HOUR",
            "aggregators": [
              {"type": "count", "name": "count"},
              {"type": "doubleSum", "fieldName": "x", "name": "x"}
            ]
          },
          "tuning": {
            "segmentGranularity": "DAY",
            "warmingPeriod": "",
            "windowPeriod": "",
            "partitions": 1,
            "replicants": 1
          },
          "sources": {
            "purchase": {
              "empId": "empId",
              "depId": "depId",
              "x": "x"
            }
          }
        }
      ],
      "ver": 1
    }
  ]
}
```

# Running
```sh
./bin/crossover --hr <PATHS_TO_HADOOP_CONF_FILES> --conf <HDFS_PATH_TO_CROSSOVER_CONFIG>
```
`PATHS_TO_HADOOP_CONF_FILES` - paths must be separated by comma. Fo example: ./core-site.xml,./hdfs-site.xml.
`HDFS_PATH_TO_CROSSOVER_CONFIG` - default value is `configuration/crossover.conf`

# Running in Docker container
To build Docker image run 
```
sbt docker:publishLocal
```
After image built run it as a regular container
```
docker run crossover:latest --name <container-name>
```
By default containerized app expects RabbitMQ container to be available at 'crossover.rabbitmq'.