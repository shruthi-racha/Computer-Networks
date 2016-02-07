
h1 mkdir q1Latency
h1 rm q1Latency/*.txt

h1 cd q1Latency

h1 ping -c 2 10.0.0.2 
h2 ping -c 2 10.0.0.3 
h3 ping -c 2 10.0.0.4 
h2 ping -c 2 10.0.0.5 
h3 ping -c 2 10.0.0.6 


h1 ping -c 20 10.0.0.2 | tail -3 >> latency_L1.txt
h2 ping -c 20 10.0.0.3 | tail -3 >> latency_L2.txt
h3 ping -c 20 10.0.0.4 | tail -3 >> latency_L3.txt
h2 ping -c 20 10.0.0.5 | tail -3 >> latency_L4.txt
h3 ping -c 20 10.0.0.6 | tail -3 >> latency_L5.txt

h1 cd ..
