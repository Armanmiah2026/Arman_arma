# Sensei Tunnel – fixed UI/protocol build

This source keeps the existing Sensei-style UI and wires the visible actions to real handlers.

## Protocols
- OpenVPN: bundled `assets/japan.ovpn`, imported `.ovpn`, clipboard import, manual config, per-profile username/password when `auth-user-pass` is present.
- V2Ray/Xray: imported text/JSON, clipboard import, VLESS/VMess/Trojan share links, Xray JSON, and legacy `vmess://Base64(JSON)` compatibility.
- Xray uses libXray `Invoke` API v3 and Android `VpnService` TUN FD integration.

## UI actions
Settings, Speed Test, Diagnostics, Connection History, Account, APN Settings, 4G/5G settings, Battery Optimization, Reload Configs, Help Center and About are wired to actual handlers or Android system settings.

## Verification
- XML resources parsed successfully.
- Referenced layout IDs checked against the layout.
- Java delimiter/brace checks performed.
- ZIP integrity checked with `unzip -t`.

A live VPN connection still depends on the supplied server/config/credentials and the device network; source-level checks cannot guarantee a particular remote server is reachable.
