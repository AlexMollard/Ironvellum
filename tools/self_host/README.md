# Run the optional backend on your PC

The Android training log and the [personal training file connector](../local_connector/README.md) run without this stack. Set up Supabase when you want your own accounts, sync, social features or signed-in web viewer.

Use Linux or a Linux home directory inside WSL2. Prepare needs Python 3.9+, Node 16+, OpenSSL, sh and sed. Starting the services needs a running local Docker Engine with the Compose plugin and enough space for the database and container images. The initial source and image downloads need internet access. Keep the installation in a private directory on a persistent local disk. The scripts use the local Docker Unix socket, including a rootless socket selected with `DOCKER_HOST=unix:///...`. The upstream stack uses fixed container names, so use a Docker engine without another Supabase stack occupying those names. See the [upstream installation and hardware guide](https://supabase.com/docs/guides/self-hosting/docker).

## First installation

From the Ironvellum checkout, choose a **new** directory under your home folder.

```sh
stack="$HOME/ironvellum-supabase"
python3 tools/self_host/stack.py prepare "$stack"
```

Prepare downloads the required files from the [pinned Supabase revision](https://github.com/supabase/supabase/tree/189adda99d1b56da55fc5a6ee3f849354aaea92a/docker) and verifies each file against [the SHA-256 lock](upstream.lock.json). Both upstream key generators run inside the newly created private directory. Keep its `.env` on the PC. It contains passwords, private signing keys and the service role credential. If preparation fails, inspect the partial directory and choose another new directory for a retry.

Edit `$stack/.env` privately. Set `SUPABASE_PUBLIC_URL` and `SITE_URL` to your chosen HTTPS origin and `API_EXTERNAL_URL` to that origin followed by `/auth/v1`. For confirmed signups and password recovery, supply working SMTP settings. A single-user private test installation can choose `ENABLE_EMAIL_AUTOCONFIRM=true`, which allows sign-up without proving ownership of the email address. Use the public client key `SUPABASE_PUBLISHABLE_KEY` for the app and viewer.

```sh
python3 tools/self_host/stack.py check "$stack"
python3 tools/self_host/stack.py start "$stack"
python3 tools/self_host/stack.py status "$stack"
python3 tools/self_host/stack.py schema "$stack"
```

The gateway listens on the PC's loopback port 8000. Database and pooler ports also bind only to loopback. For a phone, configure a reverse proxy that serves a phone-reachable HTTPS hostname. Forward only the API routes you intend to share, including `/auth/v1/` and `/rest/v1/`, to the gateway. Keep Studio, database and administrative endpoints private. A private network or VPN and a DNS name with a certificate already trusted by Android can serve this purpose. A free delegated hostname can work if you control certificate issuance for it. The phone's `localhost` is the phone. Keep TLS verification enabled. See the [Supabase HTTPS guide](https://supabase.com/docs/guides/self-hosting/self-hosted-proxy-https).

A first start records success after Compose reports the services ready. If startup fails or is interrupted, inspect the service logs and preserve partial database and storage files. The helper refuses first-start retries against populated data. For recovery, back up that installation and follow the existing-project upgrade path below. An interrupted start can leave a `.starting` file. Keep it with the recovery evidence. For a clean installation, prepare a new empty directory and resolve any existing container-name conflicts first.

Run `schema` once, after services become healthy and before creating an account. It checks the new installation, then checks for existing application objects, accounts, buckets and stored objects inside the transaction that applies Ironvellum's baseline. A refusal leaves the existing database intact. For existing installations, back up and use the [schema upgrade path](../../supabase/SELF_HOSTING.md#upgrading-an-existing-project). Connect from **Settings → Advanced → Use my own backend**, test the HTTPS URL and public key, then confirm the switch.

## Stop, restart and back up

```sh
python3 tools/self_host/stack.py stop "$stack"
python3 tools/self_host/stack.py start "$stack"
python3 tools/self_host/stack.py status "$stack"
```

These commands preserve the installed containers and volumes. Keep the installation directory, its private configuration, the database bind mount, stored files and the database configuration volume. For a backup, use an encrypted destination with room for a full copy. With the stack running, create a logical cluster dump as an extra recovery option. The following commands expect a new backup directory and access to the same local Docker engine.

```sh
umask 077
backup="$HOME/ironvellum-backup-$(date +%Y%m%d-%H%M%S)"
mkdir -m 700 "$backup" || exit 1
project=$(cat "$stack/.ironvellum-stack")
db=$(docker compose -p "$project" --env-file "$stack/.env" -f "$stack/docker-compose.yml" ps -q db)
test -n "$db" || exit 1
docker exec "$db" pg_dumpall -U supabase_admin > "$backup/cluster.sql" || exit 1
python3 tools/self_host/stack.py stop "$stack" || exit 1
db_image=$(docker inspect --format '{{.Image}}' "$db")
docker run --rm --network none --read-only --volumes-from "$db:ro" --entrypoint tar "$db_image" \
  -C /etc/postgresql-custom -cf - . > "$backup/db-config.tar" || exit 1
tar -C "$stack" -cpf "$backup/stack.tar" . || exit 1
python3 tools/self_host/stack.py start "$stack"
```

Use an OS backup account able to read Docker-owned database files and preserve permissions. If a copy fails, retain the stopped stack, correct access or disk space, and retry the backup. Protect the dump and archives like credentials. Test recovery into an isolated engine using the same image versions, restore the directory and database configuration volume with their permissions, then verify a disposable sign-in and workout. An existing backup already carries its schema.

The generated Compose configuration, keys and SQL guard were tested independently. A running container engine, phone-trusted TLS and email delivery are operator acceptance steps. The [hosted production MCP](../../server/production_mcp/README.md) is a separate optional service.
