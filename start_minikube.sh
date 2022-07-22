#!/usr/bin/env sh

#Start minikube cluster
# todo possibility to clear/delete minikube vm before starting
minikube delete
minikube start --vm-driver=virtualbox
minikube addons enable ingress
kubectl config use-context minikube
minikube dashboard > /dev/null 2>&1 &

#todo clear and apply configs not needed in case of minikube delete invoked
#kubectl delete -f ./k8s
./gradlew bootBuildImage
minikube image load io.jmix.samples.cluster/sample-cluster:latest
kubectl apply -f ./k8s
#todo OUTPUT in order to know that it works and does not hang