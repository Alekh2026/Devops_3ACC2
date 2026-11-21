#!/bin/sh
# Usage: k8s-tunnel <vm-port>-<minikube-port>
# Forwards <vm-port> on every VM interface to the minikube node.
set -eu
MINIKUBE_IP="${MINIKUBE_IP:-192.168.49.2}"
VM_PORT="${1%%-*}"
NODE_PORT="${1#*-}"
exec /usr/bin/socat "TCP-LISTEN:${VM_PORT},fork,reuseaddr" "TCP:${MINIKUBE_IP}:${NODE_PORT}"
