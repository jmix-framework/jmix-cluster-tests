#!/usr/bin/env sh

#Start minikube cluster
# todo possibility to clear/delete minikube vm before starting
#minikube delete
echo 'Starting cluster...'
minikube start --vm-driver=virtualbox
minikube addons enable ingress
kubectl config use-context minikube
minikube dashboard > /dev/null 2>&1 &

#todo clear and apply configs not needed in case of minikube delete invoked
echo 'Clearing deployment configs...'
kubectl delete -f ./k8s
echo 'Building app image...'
./gradlew bootBuildImage
echo 'Loading image...'
minikube image load io.jmix.samples.cluster/sample-cluster:latest
echo 'Applying configs...'
kubectl apply -f ./k8s
echo 'Done!'
#todo OUTPUT in order to know that it works and does not hang