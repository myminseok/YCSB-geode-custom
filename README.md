
## WARNING 
there is a bug in YCSB geode module when running against Gemfire for TAS. in that if "threadcount" in workload config is more than 2, it fails to create more than 1 connection to gemfire server. for successful execution, set "threadcount=1" as following:

workload config
```
recordcount=1000
threadcount=1
operationcount=1000
```

## about 
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

https://hub.docker.com/r/gemfire/gemfire

```
docker network create gf-network

docker run -it -e 'ACCEPT_TERMS=y' --rm --name gf-locator --network=gf-network -p 10334:10334 -p 7070:7070 gemfire/gemfire:9.15.6 gfsh start locator --name=locator1

docker run -it -e 'ACCEPT_TERMS=y' --rm --name gf-server1 --network=gf-network -p 40404:40404 gemfire/gemfire:9.15.6 gfsh start server --name=server1 --locators='gf-locator[10334]'

docker run -it -e 'ACCEPT_TERMS=y' --rm --name gf-server2 --network=gf-network -p 40405:40405 gemfire/gemfire:9.15.6 gfsh start server --name=server2 --locators=g'gf-locator[10334]'

```

```
docker run -it -e 'ACCEPT_TERMS=y' --rm --network=gf-network gemfire/gemfire:9.15.6 gfsh

gfsh> connect --jmx-manager=gf-locator[1099]
```


To view GemFire locators and servers with Pulse:

In a compatible web browser, browse to http://localhost:7070/pulse/login.html.

When prompted for credentials, use the username admin and password admin to log in.




### RUN YCSB locally

#### add geode domain to /etc/hosts
find out domain name of geode component to be called from YCSB:
```
docker ps
4e7a8ac8ccfe   gemfire/gemfire:10.0   "sh /application/ent…"  ...   1099/tcp, 7070/tcp, 8080/tcp, 10334/tcp, 40404/tcp, 0.0.0.0:40405->40405/tcp      gf-server2
8571c0241150   gemfire/gemfire:10.0   "sh /application/ent…"  ...   1099/tcp, 7070/tcp, 8080/tcp, 10334/tcp, 0.0.0.0:40404->40404/tcp                 gf-server1
f4daa42ded08   gemfire/gemfire:10.0   "sh /application/ent…"  ...   1099/tcp, 0.0.0.0:7070->7070/tcp, 8080/tcp, 40404/tcp, 0.0.0.0:10334->10334/tcp   gf-locator
```
find out external IP address. 
```
 ifconfig | grep inet | grep 192
	inet 192.168.0.213 netmask 0xffff0000 broadcast 192.168.255.255
	inet 192.168.64.1 netmask 0xffffff00 broadcast 192.168.64.255
	inet 192.168.188.1 netmask 0xffffff00 broadcast 192.168.188.255
```

add geode domain to /etc/hosts
```
192.168.0.213 gf-locator
192.168.0.213 gf-server
192.168.0.213 f4daa42ded08
192.168.0.213 8571c0241150
192.168.0.213 4e7a8ac8ccfe
```

#### (optional) overrides YCSB workload settings: 
```
vi ./ycsb.config
```

#### prepare gemfire region via gfsh:
```
destroy region --name=usertable

create region --name=usertable --type=PARTITION

describe region --name=usertable
```


#### generate and load data to geode
```
./bin/ycsb.sh load geode -P workloads/workloada -p geode.locator='127.0.0.1[10334]' -P ./ycsb.config 


```

#### run test with the loaded data.
```
./bin/ycsb.sh run geode -P workloads/workloada -p geode.locator='127.0.0.1[10334]' -P ./ycsb.config 
```


## testing on gemfire for TAS

####  create a service instance
```
cf create-service p-cloudcache co-multivm1 my-cloudcache -c '{"tls": true, "num_servers": 3}' 

or

cf create-service p-cloudcache co-multivm1 my-cloudcache -c '{"tls": true, "num_servers": 3, "service_gateway": true, "distributed_system_id": 2}'
```

#### generate key, access credentials.
```
cf create-service-key my-cloudcache test-key
cf service-key my-cloudcache test-key
```

####  copy SSL config from gemfire locator (/var/vcap/jobs/gemfire-locator/config/)
- keystore.jks
- truststore.jks

#### edit ./gemfire.config by referencing:
- SSL config from /var/vcap/jobs/gemfire-locator/config/gfsecurity.properties.
- username/password from cf service-key my-cloudcache test-key

#### setup gemfire region via gfsh:
```
export JAVA_HOME=/var/vcap/packages/jdk

/var/vcap/packages/gemfire/apache-geode/bin/gfsh

destroy region --name=usertable

create region --name=usertable --type=PARTITION

describe region --name=usertable

```


#### load ycsb data. it will ask trustStoreType, then type in "JKS".
```
./bin/ycsb.sh load geode -P workloads/workloada -p geode.locator='10.1.5.44[55221]' -P ./ycsb.config -P ./gemfire.config -s

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
####  run actual workload testing.
```
./bin/ycsb.sh run geode -P workloads/workloada -p geode.locator='10.1.5.44[55221]' -P ./ycsb.config -P ./gemfire.config -s

./bin/ycsb.sh run geode -P workloads/workloada -p geode.locator='10.1.5.44[55221]' -P ./ycsb.config -P ./gemfire.config -s -p measurementtype=timeseries

./bin/ycsb.sh run geode -P workloads/workloada -p geode.locator='10.1.5.44[55221]' -P ./ycsb.config -P ./gemfire.config -s &> ./output_gemfire_10000_1t_load.txt


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
