# Bringing up a real WireGuard relay (example, no secrets)

Without this step the app has nothing to connect to. Nothing below contains a
real key; every value is a placeholder or generated locally on your own server.

## 1. On the relay (Linux, root)

```bash
apt install -y wireguard
umask 077
wg genkey | tee /etc/wireguard/server.key | wg pubkey > /etc/wireguard/server.pub

cat >/etc/wireguard/wg0.conf <<'CONF'
[Interface]
Address = 10.66.0.1/24
ListenPort = 51820
PrivateKey = <contents of /etc/wireguard/server.key>
# Optional: MTU 1380 for PPPoE / mobile carriers
CONF

sysctl -w net.ipv4.ip_forward=1
sysctl -w net.ipv6.conf.all.forwarding=1
iptables -t nat -A POSTROUTING -o eth0 -j MASQUERADE
wg-quick up wg0
```

## 2. Get the device public key from the app

App → Settings → **Device identity** → *Copy public key*.
(The private half is generated on the phone and never leaves it unencrypted.)

## 3. Register that peer on the relay

```bash
wg set wg0 peer <DEVICE_PUBLIC_KEY> allowed-ips 10.66.0.2/32
wg-quick save wg0
```

## 4. Add the relay to the app

App → Relays → **Add relay**, paste a bundle:

```json
{
  "name": "fra-01",
  "region": "eu",
  "endpoint": "relay.example.com:51820",
  "serverPublicKey": "<contents of /etc/wireguard/server.pub>",
  "address": "10.66.0.2/32",
  "dns": "1.1.1.1",
  "mtu": 1420,
  "capacity": 150,
  "dedicated": false,
  "probeHost": "relay.example.com",
  "probePort": 443
}
```

## 5. Optional: give the relay a measurable probe target

The WireGuard UDP port answers nothing, so a "ping the relay" health check is
impossible by design. Expose one cheap TCP service on the relay to measure a real
round trip, e.g. a tiny TCP echo:

```bash
# 50 bytes echo on :9000, enough for an RTT measurement
ncat -lk 9000 --exec "/bin/cat" &
```

Then set `"probeHost": "relay.example.com", "probePort": 9000` in the bundle.
Without a probe target the relay is shown as **unverified** — never as "fast".

## Honest expectation

A relay helps when your ISP's route to the game server is bad and the relay's
route is better. It hurts when the relay is farther away. The Measure tab shows
both directions: measure the same target before and after connecting.
