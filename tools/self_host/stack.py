"""Prepare an optional private Supabase stack on a Linux/WSL2 PC with Docker Compose."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import subprocess
from urllib.request import urlopen

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[1]
LOCK = json.loads((HERE / "upstream.lock.json").read_text())
MARKER = ".ironvellum-stack"
BASE = "docker-compose.yml"


def new_directory(path):
    path = path.expanduser().resolve()
    if path == REPO or REPO in path.parents or any((p / ".git").exists() for p in (path, *path.parents)):
        raise ValueError("Choose a new private directory outside a checkout")
    path.mkdir(mode=0o700, parents=False, exist_ok=False)
    if os.name == "posix":
        info = path.stat()
        if info.st_uid != os.geteuid() or info.st_mode & 0o077:
            raise ValueError("Use a filesystem that enforces private directory permissions")
    return path


def write_verified(target, data, expected):
    if hashlib.sha256(data).hexdigest() != expected:
        raise ValueError("Upstream checksum mismatch: " + target.name)
    target.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
    with target.open("xb") as output:
        output.write(data)


def configure_mount_access(work):
    # Docker projects each bind individually below the private install root.
    # The pinned Postgres entrypoint switches user before opening the init SQL.
    volumes = work / "volumes"
    inputs = [*volumes.glob("db/*.sql"), *volumes.glob("api/envoy/*"), volumes / "pooler/pooler.exs"]
    for file in inputs:
        file.chmod(0o644)
    functions = volumes / "functions"
    functions.chmod(0o755)
    for path in functions.rglob("*"):
        path.chmod(0o755 if path.is_dir() else 0o644)
    # The storage writer runs as container root; imgproxy reads via UID 999.
    (volumes / "storage").chmod(0o755)
    (volumes / "snippets").chmod(0o700)  # Studio writes as container root.


def prepare(path):
    if os.name != "posix":
        raise ValueError("Use Linux or a Linux home directory inside WSL2")
    if not all(shutil.which(tool) for tool in ("sh", "node", "openssl", "sed")):
        raise ValueError("Prepare requires sh, Node.js 16+, openssl and sed")
    if int(subprocess.check_output(["node", "-p", "process.versions.node.split('.')[0]"])) < 16:
        raise ValueError("Prepare requires Node.js 16+")
    os.umask(0o077)
    work = new_directory(path)
    for name, digest in LOCK["sha256"].items():
        url = f"https://raw.githubusercontent.com/supabase/supabase/{LOCK['revision']}/docker/{name}"
        with urlopen(url, timeout=30) as reply:
            write_verified(work / name, reply.read(2_000_001), digest)
    configure_mount_access(work)
    # Keep upstream's internal ports and service addresses. Only publish host ports on loopback.
    compose = (work / BASE).read_text()
    for port in ("${API_GW_HTTP_PORT:-${KONG_HTTP_PORT:-8000}}:8000/tcp",
                 "${POSTGRES_PORT}:5432", "${POOLER_PROXY_PORT_TRANSACTION}:6543"):
        entry = "      - " + port
        if compose.count(entry) != 1:
            raise ValueError("Review host ports for the pinned Compose revision")
        compose = compose.replace(entry, '      - "127.0.0.1:' + port + '"')
    (work / BASE).write_text(compose)
    env = (work / ".env.example").read_text()
    env = env.replace("POOLER_TENANT_ID=your-tenant-id", "POOLER_TENANT_ID=" + secrets.token_hex(12))
    env = env.replace("OPENAI_API_KEY=sk-proj-xxxxxxxx", "OPENAI_API_KEY=")
    (work / ".env").write_text(env)
    # Both verified upstream key generators print secrets. Keep their streams private.
    for script in ("generate-keys.sh", "add-new-auth-keys.sh"):
        subprocess.run(["sh", "utils/" + script, "--update-env"], cwd=work, stdin=subprocess.DEVNULL,
                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=True)
    # The upstream script can return success with a warning after a failed Compose edit.
    active = (work / BASE).read_text()
    for key in ("GOTRUE_JWT_KEYS", "API_JWT_JWKS", "JWT_JWKS", "SUPABASE_JWKS"):
        if not re.search(r"^      " + key + ":", active, re.MULTILINE):
            raise ValueError("Incomplete asymmetric key configuration")
    for suffix in (".old", ".bak"):
        (work / (".env" + suffix)).unlink(missing_ok=True)
    project = "iv-" + secrets.token_hex(8)
    (work / MARKER).write_text(project)
    print(f"Prepared {work}. Review its private .env before starting.")


def fresh_schema_sql():
    baseline = (REPO / "supabase/migrations/0001_baseline.sql").read_text(encoding="utf-8")
    # The baseline's final migrated section has its own BEGIN and ends in COMMIT.
    # Enclose its earlier statements and the freshness check in that same transaction.
    if baseline.count("\nbegin;\n") != 1 or not baseline.rstrip().endswith("\ncommit;"):
        raise ValueError("Review baseline transaction boundaries before applying")
    return (HERE / "fresh.sql").read_text() + "\n" + baseline.replace("\nbegin;\n", "\n")


def operate(work, project, action):
    # Constrain Compose to a local Unix socket and this project's explicitly named files.
    socket = os.environ.get("DOCKER_HOST", "unix:///var/run/docker.sock")
    if not socket.startswith("unix:///"):
        raise ValueError("A local Docker Unix socket is required")
    cmd = ["docker", "--host", socket, "compose", "--project-directory", str(work), "-p", project,
           "--env-file", str(work / ".env"), "-f", str(work / BASE)]
    env = {key: os.environ[key] for key in ("PATH", "HOME", "XDG_RUNTIME_DIR", "DOCKER_CONFIG") if key in os.environ}
    fresh = work / ".fresh-start"
    confirmed = "healthy " + project
    recorded = None
    if action in ("start", "schema"):
        try:
            recorded = fresh.read_text()
        except FileNotFoundError:
            pass
    if recorded is not None and recorded != confirmed:
        raise ValueError("Unconfirmed start record. Inspect the installation and follow the recovery guide.")
    if action == "start" and recorded is None:
        # Exclusive creation also fences concurrent first starts. A hard interruption
        # leaves an unconfirmed file and requires inspection before retrying.
        starting = work / ".starting"
        with starting.open("x") as pending:
            pending.write(confirmed)
        try:
            data = work / "volumes/db/data"
            storage = work / "volumes/storage"
            if (data.exists() and any(data.iterdir())) or any(p.name != ".gitkeep" for p in storage.iterdir()):
                raise ValueError("Existing database or storage files found. Follow the backup and upgrade guide.")
            subprocess.run([*cmd, "up", "-d", "--wait"], cwd=work, env=env, check=True)
            starting.replace(fresh)
        except BaseException:
            starting.unlink(missing_ok=True)
            raise
        return
    if action == "schema":
        if recorded is None:
            raise ValueError("Schema setup requires a fresh stack started by this helper")
        subprocess.run([*cmd, "exec", "-T", "db", "psql", "-X", "-U", "supabase_admin", "-d", "postgres",
                        "-v", "ON_ERROR_STOP=1"], cwd=work, env=env, input=fresh_schema_sql().encode(), check=True)
    else:
        args = {"start": ["up", "-d", "--wait"], "stop": ["stop"], "status": ["ps"],
                "check": ["config", "--quiet"]}[action]
        subprocess.run([*cmd, *args], cwd=work, env=env, check=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("prepare", "check", "start", "status", "schema", "stop"))
    parser.add_argument("directory", type=Path)
    options = parser.parse_args()
    work = options.directory.expanduser().resolve()
    if options.action == "prepare":
        prepare(work)
        return
    project = (work / MARKER).read_text().strip()
    if not re.fullmatch(r"iv-[0-9a-f]{16}", project):
        raise ValueError("Unrecognized stack directory")
    operate(work, project, options.action)


if __name__ == "__main__":
    main()
