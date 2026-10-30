#!/usr/bin/env bash
# Turns an EXISTING WireGuard relay (wg0.conf already running) into the JSON
# bundle this app expects — without copying any private key to the phone.
#
# Run ON THE RELAY (root):
#   sudo bash server-to-bundle.sh --name fra-01 --region eu --device-public-key <KEY FROM APP>
#
# Read-only by default: it prints the bundle and the `wg set` command you need.
# Add --apply to also register the device peer right away.
set -euo pipefail
umask 077

WG_IF="wg0"
NAME="relay"
REGION=""
CLIENT_IP=""
ENDPOINT_HOST=""
CAPACITY="0"
DNS="1.1.1.1"
DEVICE_KEY=""
APPLY="no"

usage() {
  cat <<'TXT'
Usage: sudo bash server-to-bundle.sh [options]

  --if <iface>               wireguard interface (default: wg0)
  --name <name>              relay name in the app
  --region <region>          region tag (eu / na / as ...)
  --client-ip <a.b.c.d>      address assigned to the phone (default: <subnet>.2)
  --endpoint-host <host>     public address of the relay (default: auto-detect)
  --dns <server>             DNS pushed to the app (default: 1.1.1.1)
  --capacity <seats>         declared seat count (default: 0)
  --device-public-key <key>  phone public key -> prints/uses the peer command
  --apply                    actually run `wg set` to add the device peer
TXT
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --if) WG_IF="$2"; shift 2 ;;
    --name) NAME="$2"; shift 2 ;;
    --region) REGION="$2"; shift 2 ;;
    --client-ip) CLIENT_IP="$2"; shift 2 ;;
    --endpoint-host) ENDPOINT_HOST="$2"; shift 2 ;;
    --dns) DNS="$2"; shift 2 ;;
    --capacity) CAPACITY="$2"; shift 2 ;;
    --device-public-key) DEVICE_KEY="$2"; shift 2 ;;
    --apply) APPLY="yes"; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "unknown option: $1" >&2; usage; exit 1 ;;
  esac
done

CONF="/etc/wireguard/${WG_IF}.conf"
if [[ ! -f "$CONF" ]]; then
  echo "error: $CONF not found (interface $WG_IF)" >&2
  exit 1
fi

PORT="$(awk -F'=' '/^[[:space:]]*ListenPort/{gsub(/[[:space:]]/,"",$2); print $2}' "$CONF" | head -1)"
PORT="${PORT:-51820}"

IFACE_ADDR="$(awk -F'=' '/^[[:space:]]*Address/{gsub(/[[:space:]]/,"",$2); print $2}' "$CONF" | head -1)"
IFACE_ADDR="${IFACE_ADDR%%,*}"
SUBNET="$(echo "$IFACE_ADDR" | cut -d'/' -f1 | awk -F. '{print $1"."$2"."$3}')"
[[ -z "$CLIENT_IP" ]] && CLIENT_IP="${SUBNET}.2"

PUBKEY=""
if command -v wg >/dev/null 2>&1; then
  PUBKEY="$(wg show "$WG_IF" public-key 2>/dev/null || true)"
fi
if [[ -z "$PUBKEY" ]]; then
  PRIV="$(awk -F'=' '/^[[:space:]]*PrivateKey/{gsub(/[[:space:]]/,"",$2); print $2}' "$CONF" | head -1)"
  [[ -n "$PRIV" ]] && PUBKEY="$(printf '%s' "$PRIV" | wg pubkey)"
fi
if [[ -z "$PUBKEY" ]]; then
  echo "error: could not determine the relay public key" >&2
  exit 1
fi

if [[ -z "$ENDPOINT_HOST" ]]; then
  ENDPOINT_HOST="$(curl -fsS --max-time 8 https://api.ipify.org 2>/dev/null \
    || curl -fsS --max-time 8 https://ifconfig.me 2>/dev/null || true)"
fi
[[ -z "$ENDPOINT_HOST" ]] && ENDPOINT_HOST="REPLACE_WITH_SERVER_IP"

BUNDLE="/root/vz-server-bundle.json"
{
  echo "{"
  echo "  \"name\": \"${NAME}\","
  echo "  \"region\": \"${REGION}\","
  echo "  \"endpoint\": \"${ENDPOINT_HOST}:${PORT}\","
  echo "  \"serverPublicKey\": \"${PUBKEY}\","
  echo "  \"address\": \"${CLIENT_IP}/32\","
  echo "  \"allowedIps\": \"0.0.0.0/0, ::/0\","
  echo "  \"dns\": \"${DNS}\","
  echo "  \"mtu\": 1420,"
  echo "  \"capacity\": ${CAPACITY},"
  echo "  \"dedicated\": true,"
  echo "  \"notes\": \"generated from ${CONF} by server-to-bundle.sh\""
  echo "}"
} > "$BUNDLE"
chmod 600 "$BUNDLE"

echo "================ relay bundle ================"
cat "$BUNDLE"
echo "=============================================="
echo

if [[ -n "$DEVICE_KEY" ]]; then
  echo "Register the phone (peer):"
  echo "  wg set ${WG_IF} peer ${DEVICE_KEY} allowed-ips ${CLIENT_IP}/32"
  echo "  wg-quick save ${WG_IF}"
  if [[ "$APPLY" == "yes" ]]; then
    wg set "$WG_IF" peer "$DEVICE_KEY" allowed-ips "${CLIENT_IP}/32"
    wg-quick save "$WG_IF" || true
    echo "-> applied"
    wg show "$WG_IF"
  fi
else
  echo "No --device-public-key given: take it from the app (Settings -> Device identity) and run:"
  echo "  wg set ${WG_IF} peer <DEVICE_PUBLIC_KEY> allowed-ips ${CLIENT_IP}/32"
fi

echo
echo "Next: paste the JSON above into the app (Relays -> Add relay), then Connect."
