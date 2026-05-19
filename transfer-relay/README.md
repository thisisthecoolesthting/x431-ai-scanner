# TCW free transfer drop (self-hosted)

No paid third-party upload API. Run this tiny HTTP receiver on **your** VPS or shop PC.

## Cost

- **$0** in API fees (you only pay for hosting you already have, e.g. Hostinger VPS).
- Tablet **Share** mode needs no server at all.

## Quick start

```bash
cd transfer-relay
node server.mjs
# Listens on 0.0.0.0:8765 — saves uploads to ./inbox/
```

In the app: **Settings → Vehicle data export → Your server**, set URL e.g. `http://YOUR_VPS_IP:8765`.

Open firewall port **8765** on the server. Use HTTPS in production (reverse-proxy with Caddy).

## Protocol (same as LAN receiver)

- `GET /health` → JSON `{ savePath, freeBytes, version }`
- `POST /upload?name=…&size=…&sha256=…` → raw zip body

See `scripts/lan-export-receiver.ps1` for the Windows PC variant.
