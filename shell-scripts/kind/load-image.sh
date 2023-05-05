#!/bin/bash
# Load Image to local KIND registry

docker image tag memory-core-service-rsocket:0.0.1  localhost:5001/memory-core-service-rsocket:0.0.2
docker image push localhost:5001/memory-core-service-rsocket:0.0.2