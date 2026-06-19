# New domain on Stalwart (v0.16.5) — working CLI/JMAP procedure

**Verified:** 2026-06-19 (`runmyvenue.com` / MAIL-001)

## Server facts

| Item | Value |
|---|---|
| VPS | `187.124.246.154` |
| Binary | `/usr/local/bin/stalwart` (v0.16.5) |
| Bootstrap config | `/usr/local/etc/config.json` → RocksDB at `/var/lib/stalwart/` |
| **Ignore** | `/opt/stalwart/*` (stale decoy) |
| systemd | `stalwart.service` (User `stalwart`, `CAP_NET_BIND_SERVICE`) |
| Admin auth | `/usr/local/etc/stalwart.env` → `STALWART_RECOVERY_ADMIN=admin:<secret>` |
| JMAP | `http://127.0.0.1:8082/jmap` (Basic auth) |
| mgmt `accountId` | `d333333` (recovery admin) |
| Mail hostname / cert / PTR | `mail.phonecasesforcharity.com` |
| Web admin | `https://mail.phonecasesforcharity.com` (Caddy → 127.0.0.1:8082) |

**Do not** use REST `/api/principal` or `/api/domain` (404). **Do not** use `Principal/set` (notRequest). Use `x:Domain/set` and `x:Account/set`.

## 1. Read credentials

```bash
sudo grep STALWART_RECOVERY_ADMIN /usr/local/etc/stalwart.env
# format: admin:PASSWORD
```

## 2. Backup before edits

```bash
sudo mkdir -p /root/backups/mail-$(date +%F)
sudo cp -a /usr/local/etc/config.json /usr/local/etc/stalwart.env /root/backups/mail-$(date +%F)/
sudo tar czf /root/backups/mail-$(date +%F)/stalwart-data.tar.gz -C /var/lib stalwart
```

## 3. Create domain (JMAP)

```bash
ADMIN=$(grep STALWART_RECOVERY_ADMIN /usr/local/etc/stalwart.env | cut -d= -f2-)
USER="${ADMIN%%:*}"; PASS="${ADMIN#*:}"

curl -s -u "$USER:$PASS" -X POST http://127.0.0.1:8082/jmap \
  -H 'Content-Type: application/json' -d '{
  "using":["urn:ietf:params:jmap:core","urn:stalwart:jmap"],
  "methodCalls":[["x:Domain/set",{"accountId":"d333333","create":{"d1":{"name":"runmyvenue.com"}}},"0"]]
}'
# => {"created":{"d1":{"id":"bv"}}}
```

## 4. Create mailbox

```bash
DOMAIN_ID=bv   # from step 3
MAILBOX_PASS='generate-strong-password'

curl -s -u "$USER:$PASS" -X POST http://127.0.0.1:8082/jmap \
  -H 'Content-Type: application/json' -d "{
  \"using\":[\"urn:ietf:params:jmap:core\",\"urn:stalwart:jmap\"],
  \"methodCalls\":[[\"x:Account/set\",{\"accountId\":\"d333333\",\"create\":{\"a1\":{
    \"@type\":\"User\",
    \"name\":\"hello\",
    \"domainId\":\"$DOMAIN_ID\",
    \"roles\":{\"@type\":\"User\"},
    \"credentials\":{\"0\":{\"@type\":\"Password\",\"secret\":\"$MAILBOX_PASS\"}}
  }}},\"0\"]]
}"
# => {"created":{"a1":{"id":"e9"}}}
```

**Notes:** `name` is local-part only. Do **not** send `emailAddress` (server-computed).

## 5. Get DNS zone (DKIM selectors)

```bash
curl -s -u "$USER:$PASS" -X POST http://127.0.0.1:8082/jmap \
  -H 'Content-Type: application/json' -d "{
  \"using\":[\"urn:ietf:params:jmap:core\",\"urn:stalwart:jmap\"],
  \"methodCalls\":[[\"x:Domain/get\",{\"accountId\":\"d333333\",\"ids\":[\"$DOMAIN_ID\"]},\"0\"]]
}" | python3 -c "import sys,json; print(json.load(sys.stdin)['methodResponses'][0][1]['list'][0]['dnsZoneFile'])"
```

Publish MX, SPF (`v=spf1 mx -all`), both DKIM TXT records, DMARC. MX may point at shared host `mail.phonecasesforcharity.com` (valid PTR + cert).

## 6. Verify receiving (port 25)

```bash
python3 << 'PY'
import socket, time
def cmd(s, line):
    s.sendall((line+"\r\n").encode())
    data=b""
    while True:
        data+=s.recv(4096)
        if data.decode().split("\r\n")[-2][:4] if len(data.decode().split("\r\n"))>1 else False: break
        if b"250 " in data or b"550 " in data or b"553 " in data:
            if data.decode().strip().split("\r\n")[-1][3]==" ": break
    return data.decode().strip()
s=socket.create_connection(("127.0.0.1",25))
cmd(s,"EHLO verify.local"); cmd(s,"MAIL FROM:<test@example.com>")
print(cmd(s,"RCPT TO:<hello@runmyvenue.com>"))  # expect 250 2.1.5 OK
cmd(s,"QUIT"); s.close()
PY
```

## 7. App send path (lead service / nodemailer)

**587 = OAuth only** on this server. Apps must use **465 SMTPS** with PLAIN/LOGIN:

```
SMTP_HOST=127.0.0.1
SMTP_PORT=465
SMTP_USER=hello@runmyvenue.com
SMTP_PASS=<mailbox password>
```

Nodemailer: `secure: port === 465` — do **not** set `requireTLS` on 465.

PM2 (www-data): `PM2_HOME=/var/www/.pm2`, **cwd** must be `/opt/factory/runmyvenue-lead` so `require('nodemailer')` resolves.

```bash
sudo -u www-data env PM2_HOME=/var/www/.pm2 pm2 start /opt/factory/runmyvenue-lead/lead-server.js \
  --name runmyvenue-lead --cwd /opt/factory/runmyvenue-lead \
  --update-env --env SMTP_HOST=127.0.0.1 --env SMTP_PORT=465 \
  --env SMTP_USER=hello@runmyvenue.com --env SMTP_PASS='…' \
  --env MAIL_TO=hello@runmyvenue.com
sudo -u www-data env PM2_HOME=/var/www/.pm2 pm2 save
```

## 8. Reload / restart

```bash
sudo systemctl restart stalwart.service   # preferred — never kill -HUP
sudo systemctl status stalwart.service
```

## Live example: runmyvenue.com (2026-06-19)

| Object | ID |
|---|---|
| Domain `runmyvenue.com` | `bv` |
| Account `hello@runmyvenue.com` | `e9` |
| DKIM selector (ed25519) | `v1-ed25519-20260619` |

See `build/proof/MAIL_RUNMYVENUE_2026-06-19.json` for full verification output (gitignored; contains mailbox password).
