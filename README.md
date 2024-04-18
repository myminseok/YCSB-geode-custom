folked from https://github.com/brianfrankcooper/YCSB/ and Modified geode module for testing.

added authentication and SSL to geode. 
- https://gemfire.dev/blog/security-manager-basics-authentication-and-authorization/
- https://geode.apache.org/docs/guide/114/managing/security/implementing_authentication.html
- https://lists.apache.org/thread/sfp0h3fz8kkwfzft8mrf46kcqyh8q7kv

and removed other components for smaller size.


## build YCSB 
```
git clone https://github.com/myminseok/YCSB-geode-custom.git

cd YCSB-geode-custom

mvn -Psource-run clean  package -DskipTests

```

## Test on Local machine

#### Prepare Gemfire Cluster locally.

1. Run Locator

edit /etc/hosts 
```
  127.0.0.1 gf-locator 
  127.0.0.1 gf-server
```

```
docker run --rm  -e 'ACCEPT_TERMS=y' --name gf-locator -p 10334:10334 -p 7070:7070 gemfire/gemfire:10.0 gfsh start locator --name=locator --mcast-port=0 --port=10334 --hostname-for-clients=gf-locator
```

2. Run Server
add  machine real IP for Gemfire cluster to /etc/hosts 
```
  192.168.188.1 gf-locator 
  192.168.188.1 gf-server
```

```
docker run --rm -e 'ACCEPT_TERMS=y' --name gf-server  -p 40404:40404 gemfire/gemfire:10.0 gfsh start server --name=server --locators='gf-locator[10334]' --server-port=40404 --hostname-for-clients=gf-server
```


### RUN YCSB locally


prepare gemfire ssl config to local machine
```
./keystore.jks
./truststore.jks
./gemfire.config
```

overrides workload settings: 
```
./ycsb.config
```

prepare gemfire region via gfsh:
```
destroy region --name=usertable

create region --name=usertable --type=PARTITION

describe region --name=usertable
```


generate and load data to geode
```
./bin/ycsb.sh load geode -P workloads/workloada -p geode.locator='127.0.0.1[10334]' -P ../ycsb.config 
```

run test with the loaded data.
```
./bin/ycsb.sh run basic -P workloads/workloada -P ../ycsb.config 
```


## testing on gemfire for TAS

create a service instance
```
cf create-service p-cloudcache co-multivm1 my-cloudcache -c '{"tls": true, "num_servers": 3}' 
cf create-service p-cloudcache co-multivm1 my-cloudcache -c '{"tls": true, "num_servers": 3, "service_gateway": true, "distributed_system_id": 2}'
```

and generate key, access credentials.
```
cf create-service-key my-cloudcache test-key
cf service-key my-cloudcache test-key
```

set access credentials. and SSL config.
```
./keystore.jks
./truststore.jks
./gemfire.config
```


and setup gemfire region via gfsh:

```
export JAVA_HOME=/var/vcap/packages/jdk

/var/vcap/packages/gemfire/apache-geode/bin/gfsh

destroy region --name=usertable

create region --name=usertable --type=PARTITION

describe region --name=usertable

```


load ycsb data. it will ask trustStoreType, then type in "JKS".
```
./bin/ycsb.sh load geode -P workloads/workloada -p geode.locator='10.1.5.44[55221]' -P ./ycsb.config -P ./gemfire.config -s -p measurementtype=timeseries

Loading workload...
Starting test.
...
[info 2024/04/18 06:09:11.387 KST <Thread-3> tid=0xd] Running in client mode

[info 2024/04/18 06:09:11.422 KST <Thread-3> tid=0xd] Requesting cluster configuration

[info 2024/04/18 06:09:11.426 KST <Thread-3> tid=0xd] Loading previously deployed jars

Please enter the trustStoreType (javax.net.ssl.trustStoreType) : JKS
Please enter the trustStoreType (javax.net.ssl.trustStoreType) : JKS

[info 2024/04/18 06:09:16.845 KST <Thread-3> tid=0xd] AutoConnectionSource UpdateLocatorListTask started with interval=10,000 ms.

[info 2024/04/18 06:09:16.848 KST <Thread-3> tid=0xd] Pool DEFAULT started with multiuser-authentication=false

...


[INSERT], Operations, 50
[INSERT], AverageLatency(us), 25754.64
[INSERT], MinLatency(us), 3840
[INSERT], MaxLatency(us), 901119
[INSERT], 95thPercentileLatency(us), 10631
[INSERT], 99thPercentileLatency(us), 901119
[INSERT], Return=OK, 50

```
then, verify loaded data in gemfire region
and run actual testing.
```

./bin/ycsb.sh run geode -P workloads/workloada -p geode.locator='10.1.5.44[55221]' -P ./ycsb.config -P ./gemfire.config -s -p measurementtype=timeseries

...
[OVERALL], RunTime(ms), 29
[OVERALL], Throughput(ops/sec), 3448.2758620689656
[TOTAL_GCS_PS_Scavenge], Count, 0
[TOTAL_GC_TIME_PS_Scavenge], Time(ms), 0
[TOTAL_GC_TIME_%_PS_Scavenge], Time(%), 0.0
[TOTAL_GCS_PS_MarkSweep], Count, 0
[TOTAL_GC_TIME_PS_MarkSweep], Time(ms), 0
[TOTAL_GC_TIME_%_PS_MarkSweep], Time(%), 0.0
...

```

## troubleshooting.

```
org.apache.geode.distributed.internal.tcpserver.LocatorCancelException: Unrecognisable response received: object is null. This could be the result of trying to connect a non-SSL-enabled locator to an SSL-enabled locator.
```

```

Caused by: org.apache.geode.security.AuthenticationRequiredException: No security credentials are provided
```

https://geode.apache.org/docs/guide/114/managing/security/implementing_authentication.html


```
org.apache.geode.cache.client.ServerOperationException: remote server on 192.168.0.213(67939:loner):51909:0413a4ed: org.apache.geode.security.NotAuthorizedException: zRTg4MYJC6tqGZgh3gT9dA not authorized for DATA:WRITE:usertable:user6284781860667377211
```