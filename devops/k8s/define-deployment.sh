kubectl create deployment demo --image=localhost:5001/memory-core-service-rsocket:0.0.1 --dry-run  -o yaml > memory-core-service-rsocket.yaml
echo --- >> memory-core-service-rsocket.yaml
kubectl create service clusterip demo --tcp=6790:6790 --tcp=6791:6791 --tcp=6792:6792 --dry-run -o=yaml >> memory-core-service-rsocket.yaml