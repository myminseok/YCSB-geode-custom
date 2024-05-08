## Target gemfire cluster
- gemfire for TAS
- colocated 3 VM plan
- 2 core, 4GB mem per VM.

## YCSB workload 
```
recordcount=10000
threadcount=20
operationcount=10000
readproportion=0.5
updateproportion=0.5
#fieldlength=100 # default
```
## Sample command 
../bin/ycsb.sh load geode -P ../workloads/workloada -p geode.locator='192.168.0.78[55221]' -P ./ycsb.config -P ./gemfire.config -s &> ./output_gemfire_homelab-multi-10000_5t_load.txt

../bin/ycsb.sh run geode -P ../workloads/workloada -p geode.locator='192.168.0.78[55221]' -P ./ycsb.config -P ./gemfire.config -s &> ./output_gemfire_homelab-multi-10000_2t_run.txt
