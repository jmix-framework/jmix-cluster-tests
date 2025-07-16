#!/usr/bin/env sh

#Start minikube cluster
# todo possibility to clear/delete minikube vm before starting
#minikube delete
echo 'Starting cluster...'
# "--network builtin" - optional
minikube start --vm-driver=qemu2 --network builtin
minikube addons enable ingress
kubectl config use-context minikube
minikube dashboard > /dev/null 2>&1 &

#todo clear and apply configs not needed in case of minikube delete invoked
echo 'Clearing deployment configs...'
kubectl delete -f ./k8s
#kubectl delete namespace jmix-cluster-tests
kubectl create namespace jmix-cluster-tests
echo 'Building app image...'
./gradlew bootBuildImage
echo 'Pushing image to gitlab repository'
docker push docker.haulmont.com/platform/jmix-kube-tests/sample-cluster:jmix_1_x
echo 'Loading image...'
minikube image load docker.haulmont.com/platform/jmix-kube-tests/sample-cluster:jmix_1_x
echo 'Applying configs...'
kubectl apply -f ./k8s
echo 'Done!'