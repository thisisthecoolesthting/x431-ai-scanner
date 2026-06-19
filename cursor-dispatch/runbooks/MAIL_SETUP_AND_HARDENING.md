# Mail setup and hardening (Stalwart factory server)

Authoritative procedure for provisioning a new domain on the shared Stalwart v0.16.5 instance.

**Domain-specific steps:** [EMAIL_new_domain_on_stalwart.md](./EMAIL_new_domain_on_stalwart.md)

## Pre-flight

1. SSH: `ssh pickit-vps` (deploy@187.124.246.154) — if key fails, use `root@187.124.246.154` with `~/.ssh/id_ed25519`
2. Confirm sudo: `sudo -n true && echo SUDO_OK`
3. Confirm Stalwart: `systemctl is-active stalwart.service` → `active`
4. Read `/usr/local/etc/stalwart.env` for recovery admin

## Provision checklist

- [ ] Backup config + RocksDB (`/root/backups/mail-YYYY-MM-DD/`)
- [ ] Create domain via JMAP `x:Domain/set`
- [ ] Create mailbox via JMAP `x:Account/set`
- [ ] Export `dnsZoneFile`; publish/update DNS (MX, SPF `-all`, DKIM, DMARC)
- [ ] Verify `RCPT TO:<user@domain>` on :25 → **250**
- [ ] Configure app SMTP on **465 SMTPS** (not 587 OAuth)
- [ ] Verify app send + inbound delivery

## Hardening checklist

| Control | Status / action |
|---|---|
| TLS (25 STARTTLS, 465, 993) | Valid Let's Encrypt cert for `mail.phonecasesforcharity.com` |
| Open relay | External→external RCPT must return **550** |
| SPF | `v=spf1 mx -all` after flow confirmed |
| DKIM | Stalwart selector in DNS matches `dnsZoneFile` |
| DMARC | Start `p=none`; plan `quarantine` → `reject` |
| Admin :8082 direct | iptables: `DROP` tcp/8082 except 127.0.0.1 (Caddy HTTPS admin still OK) |
| fail2ban | Optional `stalwart-smtp` jail on auth failures |
| Backups | config.json, stalwart.env, `/var/lib/stalwart/` tarball |
| rDNS/PTR | Should be `mail.phonecasesforcharity.com` (Hostinger ticket if wrong) |
| Monitoring | Include Stalwart in site-health / manual curl :25 banner check |

## Guardrails

- **Never break** existing domains (`rickyscontrolcenter.com`, `shiftdeck.com`, etc.)
- Use `systemctl restart stalwart.service` — **not** `kill -HUP`
- Never commit mailbox passwords; store in gitignored `build/proof/`

## Deliverability verification

1. External send (Gmail) → `hello@<domain>` — SPF/DKIM/DMARC pass in headers
2. Outbound → mail-tester.com — target ≥ 9/10
3. Lead/API path end-to-end if applicable

## DNS reconciliation (runmyvenue.com)

Published DNS used `mail.rickyscontrolcenter.com` MX and selector `s1735219373`. Stalwart authoritative zone uses:

- MX → `mail.phonecasesforcharity.com` (same VPS IP; either works if A record correct)
- DKIM → `v1-ed25519-20260619._domainkey.runmyvenue.com`
- SPF → `v=spf1 mx -all`

**Operator action:** Update OpenProvider records to match Stalwart `dnsZoneFile` for signing alignment.
