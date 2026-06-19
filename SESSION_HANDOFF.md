# SESSION HANDOFF — 2026-06-19

## MAIL-001 complete (runmyvenue.com on Stalwart)

**Status:** Mail **live on server** — receiving + lead send verified. **DNS DKIM selector misaligned** at OpenProvider (operator update required).

### What works now

- `runmyvenue.com` domain id `bv`, mailbox `hello@runmyvenue.com` id `e9`
- Port 25 RCPT → **250** for `hello@runmyvenue.com`
- Lead service POST `https://www.runmyvenue.com/api/lead` → `{"ok":true}` + email lands via SMTPS 465
- Existing mailboxes (`rickyscontrolcenter.com`, `shiftdeck.com`) unchanged
- Stalwart running under `stalwart.service`; cert valid for `mail.phonecasesforcharity.com`
- rDNS/PTR already correct
- Direct admin port 8082 blocked externally (iptables); HTTPS admin via Caddy still works

### Operator follow-ups

1. **OpenProvider DNS:** Replace DKIM `s1735219373` with Stalwart `v1-ed25519-20260619` TXT from JMAP `dnsZoneFile`; tighten SPF to `v=spf1 mx -all`
2. **Mailbox password:** see gitignored `build/proof/MAIL_RUNMYVENUE_2026-06-19.json`
3. **Deliverability:** send Gmail → hello@runmyvenue.com + mail-tester after DNS aligned
4. **SSH:** `pickit-vps` deploy key failed; root + `id_ed25519` used — fix deploy key if needed

### Runbooks updated

- `cursor-dispatch/runbooks/EMAIL_new_domain_on_stalwart.md`
- `cursor-dispatch/runbooks/MAIL_SETUP_AND_HARDENING.md`

### Branch

`feat/mail-001-runmyvenue-stalwart` — docs + runbooks (secrets in gitignored proof only)
