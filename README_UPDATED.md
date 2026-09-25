# Sensei Tunnel — OpenVPN + V2Ray/Xray

This update keeps the multi-protocol VPN workflow and adds a cleaner Sensei Tunnel-style configuration UI.

## Included
- OpenVPN through `ai.bongotech:bongovpn:1.0.3`.
- Bundled `assets/japan.ovpn` fallback.
- Import multiple `.ovpn` files and keep their original display names.
- OVPN username/password fields are available when a profile needs `auth-user-pass`.
- Manual full `.ovpn` input.
- V2Ray/Xray file import and manual VLESS / VMess / Trojan / raw Xray JSON input.
- Clipboard import for both protocol types.
- Saved profile list with protocol, name, select/use and delete actions.
- Startup Toast showing saved/imported profile count and names.
- Sensei Tunnel-style header, quick actions, protocol switcher, config list and menu.
- Real Xray/libXray Invoke API and Android `VpnService` TUN bridge; no fake connected state.

## Important
A source project cannot guarantee that an arbitrary VPN server will accept a profile. Actual connection success depends on the supplied OpenVPN profile/credentials or valid VLESS/VMess/Trojan/Xray server details, Android VPN permission, and the remote server being reachable.

The project uses the documented libXray API v3 and passes the Android TUN file descriptor through `xray.tun.fd` before `runXray`.
