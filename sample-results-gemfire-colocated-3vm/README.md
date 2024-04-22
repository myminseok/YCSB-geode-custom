

../bin/ycsb.sh load geode -P ../workloads/workloada -p geode.locator='192.168.0.78[55221]' -P ./ycsb.config -P ./gemfire.config -s &> ./output_gemfire_homelab-multi-10000_2t_load.txt

../bin/ycsb.sh run geode -P ../workloads/workloada -p geode.locator='192.168.0.78[55221]' -P ./ycsb.config -P ./gemfire.config -s &> ./output_gemfire_homelab-multi-10000_2t_run.txt
