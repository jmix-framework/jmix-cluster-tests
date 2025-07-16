#!/usr/bin/env sh

echo 'Clearing deployment configs...'
kubectl delete -f ./k8s -n='jmix-cluster-tests'
echo 'Building app image...'
./gradlew bootBuildImage --imagePlatform linux/arm64
echo 'Reloading image...'
minikube image rm docker.haulmont.com/platform/jmix-kube-tests/sample-cluster:jmix_1_x
minikube image load docker.haulmont.com/platform/jmix-kube-tests/sample-cluster:jmix_1_x
echo 'Applying configs...'
kubectl apply -f ./k8s  -n='jmix-cluster-tests'
echo 'Done!'