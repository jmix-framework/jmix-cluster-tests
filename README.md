# Jmix Cluster Tests

Project to run k8s cluster tests on Jmix 2+ app.

## Prerequisites

Common:

- JDK 21
- Docker
- kubectl

Rancher Desktop:

- Rancher Desktop installed and running with Kubernetes enabled
- Container engine: `dockerd (moby)` so locally built images are shared with k3s
- `rancher-desktop` kubectl context available

Minikube:

- minikube with `qemu2` driver
- about 6 GB RAM and 2 CPUs

Remote cluster:

- `KUBECONFIG_CONTENT` contains the remote kubeconfig file content
- `kubetestcred` exists in `jmix-cluster-tests`

## Choosing the cluster

The tests use the remote cluster when the `KUBECONFIG_CONTENT` environment variable is set,
and the local kubeconfig otherwise.

If `KUBECONFIG_CONTENT` is set in your shell but the run must go to a local cluster, add
`-DlocalCluster=true`:

```bash
./gradlew test -DlocalCluster=true --tests io.jmix.samples.cluster2.TestRunner.clusterTests
```

To forward the pod debug port `5006` to local ports starting from `50001`, add
`-DdebugPods=true`.

## Rancher Desktop

Make sure Rancher Desktop is running and the `rancher-desktop` kubectl context exists. Then build the app image and deploy:

```bash
./rancher_cluster.sh
```

The script does NOT push to the registry by default — k3s pulls the freshly built image directly from the shared local Docker daemon, and the deployment's
`imagePullPolicy` is patched to `IfNotPresent` at deploy time.

Rebuild and redeploy only (skip namespace/manifests re-apply):

```bash
./rancher_cluster.sh --skip-deploy
```

Run tests:

```bash
./gradlew test --tests io.jmix.samples.cluster2.TestRunner.clusterTests
```

Cleanup cluster:

```bash
kubectl delete -f ./k8s --namespace=jmix-cluster-tests --ignore-not-found=true
kubectl delete namespace jmix-cluster-tests
```

## Minikube

Full setup if the cluster is not installed yet:

```bash
./minikube_cluster.sh
```

OR: Rebuild and redeploy the image into an existing cluster:

```bash
./minikube_cluster.sh -r
```

Run tests:

```bash
./gradlew test --tests io.jmix.samples.cluster2.TestRunner.clusterTests
```

## Remote cluster

Put the remote kubeconfig file content into `KUBECONFIG_CONTENT`, then run:

```bash
./remote_cluster.sh --apply
./gradlew test --tests io.jmix.samples.cluster2.TestRunner.clusterTests
```
