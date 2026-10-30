#!/usr/bin/env bash
# VZ Network Booster — one-command WireGuard relay setup.
#
# Run this ON THE RELAY (a fresh Ubuntu/Debian VPS, root):
#
#   curl -fsSL <this-file> -o relay-setup.sh   # or copy it over with scp
#   sudo bash relay-setup.sh --name fra-01 --region eu --device-public-key <KEY FROM APP>
#
# It will:
#   1. install wireguard-tools,
#   2. generate the relay key pair (never printed),
#   3. write /etc/wireguard/wg0.conf with NAT + forwarding,
#   4. optionally register your phone as a peer,
#   5. print a "server bundle" JSON you paste into the app (Relays -> Add relay).
#
# The bundle contains only PUBLIC data (endpoint, relay public key, your assigned
# address). The relay private key stays in /etc/wireguard/server.key (mode 600).
set -euo pipefail
umask 077

NAME="relay"
REGION=""
PORT="51820"
SUBNET="10.66.0"
CLIENT_IP="10.66.0.2"
DEVICE_KEY=""
ENDPOINT_HOST=""
CAPACITY="0"
WG_DIR="/etc/wireguard"
WG_IF="wg0"

usage() {
  cat <<'TXT'
Usage: sudo bash relay-setup.sh [options]

  --name <name>              relay name shown in the app (default: relay)
  --region <region>          region tag used by Smart Routing, e.g. eu / na / as (default: none)
  --endpoint-host <host>     public host or IP of this relay (default: auto-detect)
  --port <port>              WireGuard UDP port (default: 51820)
  --subnet <a.b.c>           tunnel subnet without last octet (default: 10.66.0)
  --client-ip <a.b.c.d>      address assigned to the app (default: 10.66.0.2)
  --device-public-key <key>  app public key (Settings -> Device identity -> Copy)
  --capacity <seats>         declared seat count for the app (default: 0 = unknown)
  -h, --help                 show this help
TXT
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --name) NAME="$2"; shift 2 ;;
    --region) REGION="$2"; shift 2 ;;
    --endpoint-host) ENDPOINT_HOST="$2"; shift 2 ;;
    --port) PORT="$2"; shift 2 ;;
    --subnet) SUBNET="$2"; shift 2 ;;
    --client-ip) CLIENT_IP="$2"; shift 2 ;;
    --device-public-key) DEVICE_KEY="$2"; shift 2 ;;
    --capacity) CAPACITY="$2"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "unknown option: $1" >&2; usage; exit 1 ;;
  esac
done

if [[ "${EUID}" -ne 0 ]]; then
  echo "error: run as root (sudo bash relay-setup.sh ...)" >&2
  exit 1
fi

if ! command -v wg >/dev/null 2>&1; then
  echo "== installing wireguard"
  apt-get update -qq
  apt-get install -y --no-install-recommends wireguard-tools iptables curl >/dev/null
fi

mkdir -p "$WG_DIR"
chmod 700 "$WG_DIR"

if [[ ! -f "$WG_DIR/server.key" ]]; then
  echo "== generating relay key pair"
  wg genkey > "$WG_DIR/server.key"
  chmod 600 "$WG_DIR/server.key"
  wg pubkey < "$WG_DIR/server.key" > "$WG_DIR/server.pub"
  chmod 600 "$WG_DIR/server.pub"
else
  echo "== reusing existing relay key pair ($WG_DIR/server.key)"
fi
RELAY_PUB="$(cat "$WG_DIR/server.pub")"

if [[ -z "$ENDPOINT_HOST" ]]; then
  ENDPOINT_HOST="$(curl -fsS --max-time 8 https://api.ipify.org 2>/dev/null || curl -fsS --max-time 8 https://ifconfig.me 2>/dev/null || true)"
fi
if [[ -z "$ENDPOINT_HOST" ]]; then
  ENDPOINT_HOST="REPLACE_WITH_SERVER_IP_OR_HOST"
  echo "warning: could not auto-detect the public address; replace it in the bundle" >&2
fi

WAN_IF="$(ip route show default | awk '/default/ {print $5; exit}')"
WAN_IF="${WAN_IF:-eth0}"

echo "== writing $WG_DIR/$WG_IF.conf"
{
  echo "[Interface]"
  echo "Address = ${SUBNET}.1/24"
  echo "ListenPort = ${PORT}"
  echo "PrivateKey = $(cat "$WG_DIR/server.key")"
  echo "PostUp = sysctl -w net.ipv4.ip_forward=1"
  echo "PostUp = iptables -A FORWARD -i %i -j ACCEPT; iptables -A FORWARD -o %i -j ACCEPT"
  echo "PostUp = iptables -t nat -A POSTROUTING -o ${WAN_IF} -j MASQUERADE"
  echo "PostDown = iptables -D FORWARD -i %i -j ACCEPT; iptables -D FORWARD -o %i -j ACCEPT"
  echo "PostDown = iptables -t nat -D POSTROUTING -o ${WAN_IF} -j MASQUERADE"
  if [[ -n "$DEVICE_KEY" ]]; then
    echo
    echo "[Peer]"
    echo "# VZ Network Booster device"
    echo "PublicKey = ${DEVICE_KEY}"
    echo "AllowedIPs = ${CLIENT_IP}/32"
  fi
} > "$WG_DIR/$WG_IF.conf"
chmod 600 "$WG_DIR/$WG_IF.conf"

echo "net.ipv4.ip_forward=1" > /etc/sysctl.d/99-vz-network-booster.conf
sysctl -q -p /etc/sysctl.d/99-vz-network-booster.conf || true

systemctl enable "wg-quick@${WG_IF}" >/dev/null 2>&1 || true
wg-quick down "$WG_IF" >/dev/null 2>&1 || true
wg-quick up "$WG_IF"

if command -v ufw >/dev/null 2>&1 && ufw status 2>/dev/null | grep -qi active; then
  echo "== opening UDP ${PORT} in ufw"
  ufw allow "${PORT}/udp" >/dev/null || true
fi

BUNDLE_FILE="/root/vz-server-bundle.json"
{
  echo "{"
  echo "  \"name\": \"${NAME}\","
  if [[ -n "$REGION" ]]; then echo "  \"region\": \"${REGION}\","; else echo "  \"region\": \"\","; fi
  echo "  \"endpoint\": \"${ENDPOINT_HOST}:${PORT}\","
  echo "  \"serverPublicKey\": \"${RELAY_PUB}\","
  echo "  \"address\": \"${CLIENT_IP}/32\","
  echo "  \"allowedIps\": \"0.0.0.0/0, ::/0\","
  echo "  \"dns\": \"1.1.1.1\","
  echo "  \"mtu\": 1420,"
  echo "  \"capacity\": ${CAPACITY},"
  echo "  \"dedicated\": true,"
  echo "  \"notes\": \"created by scripts/relay-setup.sh\""
  echo "}"
} > "$BUNDLE_FILE"
chmod 600 "$BUNDLE_FILE"

echo
echo "================ relay is up ================"
echo "interface : ${WG_IF} (UDP ${PORT})"
echo "endpoint  : ${ENDPOINT_HOST}:${PORT}"
echo "relay pub : ${RELAY_PUB}"
echo "peer added: $( [[ -n "$DEVICE_KEY" ]] && echo yes || echo "no (add the app key later)" )"
echo "bundle    : ${BUNDLE_FILE}  (paste this into the app)"
echo "status    : wg show ${WG_IF}"
echo "============================================"
echo
cat "$BUNDLE_FILE"

if [[ -z "$DEVICE_KEY" ]]; then
  cat <<TXT

No device key was provided. To register the phone later:

  wg set ${WG_IF} peer <DEVICE_PUBLIC_KEY> allowed-ips ${CLIENT_IP}/32
  wg-quick save ${WG_IF}

TXT
fi

cat <<'TXT'
Checklist on the phone:
  1. Relays -> Add relay -> paste the JSON above
  2. Home -> Connect (accept the Android VPN prompt)
  3. Measure -> run the same target before and after connecting
TXT
