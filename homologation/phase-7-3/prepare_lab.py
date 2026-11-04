"""Create a disposable Phase 7.3 network in an explicitly named lab directory.

The script never starts containers and refuses to overwrite any prepared node.
Secrets and generated server configs live outside the Git checkout.
"""
from __future__ import annotations

import argparse
import base64
import hashlib
import json
import os
import re
import shutil
import subprocess
import uuid
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
PLUGINS = ROOT.parent


def copy(source: Path, target: Path) -> None:
    if not source.exists():
        raise FileNotFoundError(source)
    target.parent.mkdir(parents=True, exist_ok=True)
    if source.is_dir():
        shutil.copytree(source, target)
    else:
        shutil.copy2(source, target)


def put(path: Path, value: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(value, encoding="utf-8")


def replace_once(value: str, old: str, new: str) -> str:
    if value.count(old) != 1:
        raise ValueError("Expected exactly one config anchor: " + old[:45])
    return value.replace(old, new, 1)


def secret() -> str:
    return base64.b64encode(os.urandom(32)).decode("ascii")


def git_commit(path: Path) -> str:
    return subprocess.check_output(["git", "-C", str(path), "rev-parse", "HEAD"], text=True).strip()


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--lab", required=True, type=Path, help="Existing isolated lab with inputs/ and .secrets/")
    parser.add_argument("--operator", default="", help="Minecraft nickname granted lab-only diagnostic/admin permissions")
    args = parser.parse_args()
    lab = args.lab.resolve()
    if lab == ROOT or ROOT in lab.parents or lab == PLUGINS or PLUGINS in lab.parents:
        parser.error("lab must be outside source checkouts")
    if not (lab / "inputs" / "arena-1.yml").is_file():
        parser.error("lab inputs missing; never auto-source production files")
    password_file = lab / ".secrets" / "mysql-lab.txt"
    root_password_file = lab / ".secrets" / "mysql-root.txt"
    if not password_file.is_file() or not root_password_file.is_file():
        parser.error("dedicated lab MySQL credentials are missing")
    if any((lab / node).exists() for node in ("proxy", "lobby", "bedwars-01", "bedwars-02")):
        parser.error("prepared node already exists; refusing overwrite")
    password = password_file.read_text(encoding="utf-8").strip()
    if len(password) < 32 or "'" in password:
        parser.error("lab MySQL password has invalid format")
    if args.operator and not re.fullmatch(r"[A-Za-z0-9_]{3,16}", args.operator):
        parser.error("operator must be a valid Minecraft Java nickname")
    inputs = lab / "inputs"
    login = PLUGINS / "StarfinLogin"
    minigames = PLUGINS / "StarfinMinigames"
    social = PLUGINS / "StarfinSocial"
    keys = {name: (secret(), secret()) for name in ("lobby", "bedwars-01", "bedwars-02")}

    proxy = lab / "proxy"
    copy(inputs / "BungeeCord.jar", proxy / "BungeeCord.jar")
    for source, filename in (
        (login / "starfinlogin-bungee/target/StarfinLogin-Bungee.jar", "StarfinLogin-Bungee.jar"),
        (minigames / "starfinminigames-bungee/target/StarfinMinigames-Bungee.jar", "StarfinMinigames-Bungee.jar"),
        (social / "starfinsocial-bungee/target/StarfinSocial-Bungee.jar", "StarfinSocial-Bungee.jar"),
    ):
        copy(source, proxy / "plugins" / filename)
    operator_groups = "groups:\n  {}:\n  - phase73-operator\n".format(args.operator) if args.operator else "groups: {}\n"
    operator_permissions = "  phase73-operator:\n  - starfinminigames.admin\n  - starfinminigames.ranked.admin\n" if args.operator else ""
    put(proxy / "config.yml", """network_compression_threshold: 256
remote_ping_timeout: 5000
online_mode: false
listeners:
- query_port: 25577
  motd: 'Starfin Phase 7.3 isolated lab'
  tab_list: GLOBAL_PING
  query_enabled: false
  proxy_protocol: false
  forced_hosts: {}
  ping_passthrough: false
  priorities:
  - lobby
  bind_local_address: false
  host: 0.0.0.0:25577
  max_players: 20
  tab_size: 60
  force_default_server: false
disabled_commands: []
player_limit: -1
log_commands: false
connection_throttle: -1
{operator_groups}PLACEHOLDER
servers:
  lobby:
    motd: 'Authentication lobby'
    address: lobby:25566
    restricted: false
  bedwars-01:
    motd: 'BedWars 01'
    address: bedwars-01:25566
    restricted: false
  bedwars-02:
    motd: 'BedWars 02'
    address: bedwars-02:25566
    restricted: false
permissions:
  default: []
{operator_permissions}PLACEHOLDER
ip_forward: true
""".replace("{operator_groups}PLACEHOLDER", operator_groups.rstrip("\n"))
       .replace("{operator_permissions}PLACEHOLDER", operator_permissions.rstrip("\n")))
    put(proxy / "modules.yml", "modules: []\n")
    def mapping(index: int) -> str:
        return "\n".join("  {}: '{}'".format(name, pair[index]) for name, pair in keys.items())
    put(proxy / "plugins/StarfinLogin/config.yml", """ingress-gate:
  bind-address: 172.28.31.2
  port: 27880
auth-servers:
  - lobby
protected-servers:
  - bedwars-01
  - bedwars-02
redirect-after-auth-enabled: false
destination-after-auth: ''
auth-result-max-age-millis: 15000
guard-health-timeout-millis: 350
guard-health-ports:
  lobby: 27881
  bedwars-01: 27881
  bedwars-02: 27881
backend-secrets:
""" + mapping(0) + "\nguard-secrets:\n" + mapping(1) + "\n")
    mini_proxy = (minigames / "starfinminigames-bungee/src/main/resources/config.yml").read_text(encoding="utf-8")
    mini_proxy = replace_once(mini_proxy, "network:\n  enabled: false\n  network-id: starfin-network\n  lobby-server: auth", "network:\n  enabled: true\n  network-id: phase73-lab\n  lobby-server: lobby")
    mini_proxy = replace_once(mini_proxy, "    bedwars-01: survival", "    bedwars-01: bedwars-01\n    bedwars-02: bedwars-02")
    mini_proxy = replace_once(mini_proxy, "mysql:\n  enabled: false\n  host: localhost\n  port: 3306\n  database: starfin\n  username: root\n  password: ''\n  ssl-mode: PREFERRED", "mysql:\n  enabled: true\n  host: mysql\n  port: 3306\n  database: starfin_phase73\n  username: starfin_lab\n  password: '{}'\n  ssl-mode: PREFERRED".format(password))
    put(proxy / "plugins/StarfinMinigames/config.yml", mini_proxy)
    social_proxy = (social / "starfinsocial-bungee/src/main/resources/config.yml").read_text(encoding="utf-8")
    social_proxy = replace_once(social_proxy, "proxy-id: proxy-1", "proxy-id: phase73-proxy")
    social_proxy = replace_once(social_proxy, "host: localhost\n  port: 3306\n  database: starfin\n  username: starfin\n  password: CHANGE_ME\n  ssl-mode: PREFERRED", "host: mysql\n  port: 3306\n  database: starfin_phase73\n  username: starfin_lab\n  password: '{}'\n  ssl-mode: PREFERRED".format(password))
    put(proxy / "plugins/StarfinSocial/config.yml", social_proxy)

    for name in keys:
        node = lab / name
        copy(inputs / "server.jar", node / "server.jar")
        copy(inputs / "paper-cache", node / "cache")
        copy(login / "starfinlogin-guard/target/StarfinGuard.jar", node / "plugins/StarfinGuard.jar")
        copy(minigames / "starfinminigames-spigot/target/StarfinMinigames-Spigot.jar", node / "plugins/StarfinMinigames-Spigot.jar")
        copy(social / "starfinsocial-spigot/target/StarfinSocial-Spigot.jar", node / "plugins/StarfinSocial-Spigot.jar")
        copy(inputs / "StarfinCore-Spigot.jar", node / "plugins/StarfinCore-Spigot.jar")
        copy(inputs / "core-libraries", node / "plugins/StarfinCore/libraries")
        if name == "lobby":
            copy(login / "starfinlogin-spigot/target/StarfinLogin-Spigot.jar", node / "plugins/StarfinLogin-Spigot.jar")
        else:
            copy(ROOT / "target/StarfinBedWars-1.0.0-SNAPSHOT.jar", node / "plugins/StarfinBedWars.jar")
            copy(inputs / "Vault.jar", node / "plugins/Vault.jar")
            copy(inputs / "EssentialsX-2.19.3.jar", node / "plugins/EssentialsX.jar")
            copy(inputs / "Speedway", node / "Speedway")
            copy(inputs / "arena-template-1", node / "plugins/StarfinBedWars/templates/1")
            arena = (inputs / "arena-1.yml").read_text(encoding="utf-8")
            arena = replace_once(arena, "id: '1'", "id: '1'\ngroup: casual")
            put(node / "plugins/StarfinBedWars/arenas/1.yml", arena)
            stats = (ROOT / "src/main/resources/stats.yml").read_text(encoding="utf-8")
            stats = replace_once(stats, "enabled: false", "enabled: true")
            for old, new in (("host: '127.0.0.1'", "host: 'mysql'"), ("database: 'starfin_network'", "database: 'starfin_phase73'"), ("username: 'starfinbedwars'", "username: 'starfin_lab'"), ("password: ''", "password: '{}'".format(password))):
                stats = replace_once(stats, old, new)
            put(node / "plugins/StarfinBedWars/stats.yml", stats)
            network = (ROOT / "src/main/resources/network.yml").read_text(encoding="utf-8")
            network = replace_once(network, "enabled: false", "enabled: true")
            network = replace_once(network, "server-id: 'bedwars-01'", "server-id: '{}'".format(name))
            network = replace_once(network, "lobby-server: 'auth'", "lobby-server: 'lobby'")
            put(node / "plugins/StarfinBedWars/network.yml", network)
        put(node / "eula.txt", "eula=true\n")
        if args.operator:
            digest = hashlib.md5(("OfflinePlayer:" + args.operator).encode("utf-8")).digest()
            operator_id = str(uuid.UUID(bytes=digest, version=3))
            put(node / "ops.json", json.dumps([{"uuid": operator_id, "name": args.operator,
                                               "level": 4, "bypassesPlayerLimit": True}]) + "\n")
        put(node / "server.properties", "server-port=25565\nserver-ip=127.0.0.1\nonline-mode=false\nmax-players=20\nallow-nether=false\ngenerate-structures=false\nspawn-monsters=false\nspawn-animals=false\nlevel-name={}\nmotd=Starfin Phase 7.3 {}\n".format("world" if name == "lobby" else "Speedway", name))
        spigot = (inputs / "spigot.yml").read_text(encoding="utf-8")
        spigot = re.sub(r"bungeecord: (true|false)", "bungeecord: true", spigot)
        spigot = re.sub(r"(?m)^(  log: )(true|false)$", r"\g<1>false", spigot)
        put(node / "spigot.yml", spigot)
        backend_key, guard_key = keys[name]
        put(node / "plugins/StarfinGuard/config.yml", """server-name: {name}
require-authenticated-access: {auth}
proof-timeout-millis: 5000
health-check:
  bind-address: 0.0.0.0
  port: 27881
pre-admission:
  bind-address: 0.0.0.0
  port: 25566
  backend-host: 127.0.0.1
  backend-port: 25565
proxy-gate:
  host: 172.28.31.2
  port: 27880
  timeout-millis: 1500
proxy-secret: '{guard}'
proxy-addresses:
  - 172.28.31.2
""".format(name=name, auth="false" if name == "lobby" else "true", guard=guard_key))
        mini = (minigames / "starfinminigames-spigot/src/main/resources/config.yml").read_text(encoding="utf-8")
        mini = replace_once(mini, "server-id: local-01", "server-id: " + name)
        if name == "lobby":
            mini = replace_once(mini, "prevent-hunger: false", "prevent-hunger: true")
            mini = replace_once(mini, "level-spawn-view: false", "level-spawn-view: true")
        put(node / "plugins/StarfinMinigames/config.yml", mini)
        social_node = (social / "starfinsocial-spigot/src/main/resources/config.yml").read_text(encoding="utf-8")
        social_node = replace_once(social_node, "host: localhost\n  port: 3306\n  database: starfin\n  username: starfin\n  password: CHANGE_ME\n  ssl-mode: PREFERRED", "host: mysql\n  port: 3306\n  database: starfin_phase73\n  username: starfin_lab\n  password: '{}'\n  ssl-mode: PREFERRED".format(password))
        put(node / "plugins/StarfinSocial/config.yml", social_node)
        if name == "lobby":
            put(node / "plugins/StarfinLogin/config.yml", """storage:
  type: MYSQL
  mysql:
    host: mysql
    port: 3306
    database: starfin_phase73
    username: starfin_lab
    password: '{password}'
    parameters: sslMode=PREFERRED&useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC
security:
  login-timeout-seconds: 60
  max-login-attempts: 5
  command-cooldown-millis: 750
restrictions:
  block-movement: true
network:
  enabled: true
  server-name: lobby
  proxy-secret: '{backend}'
""".format(password=password, backend=backend_key))

    put(lab / "compose.yml", """services:
  mysql:
    image: mysql:8.0
    healthcheck:
      test: ["CMD-SHELL", "mysqladmin ping -h 127.0.0.1 --silent"]
      interval: 5s
      timeout: 5s
      retries: 20
    environment:
      MYSQL_ROOT_PASSWORD_FILE: /run/secrets/mysql_root
    volumes:
      - sbw-phase73-mysql-data:/var/lib/mysql
    secrets: [mysql_root]
    ports: ["127.0.0.1:33308:3306"]
    networks:
      edge:
        ipv4_address: 172.28.30.10
      backend:
        ipv4_address: 172.28.31.10
  proxy:
    image: eclipse-temurin:17-jre
    working_dir: /srv
    command: ["java", "-Xmx512M", "-jar", "BungeeCord.jar"]
    volumes: ["./proxy:/srv"]
    ports: ["127.0.0.1:45577:25577"]
    depends_on:
      mysql:
        condition: service_healthy
      lobby:
        condition: service_healthy
      bedwars-01:
        condition: service_healthy
      bedwars-02:
        condition: service_healthy
    networks:
      edge:
        ipv4_address: 172.28.30.2
      backend:
        ipv4_address: 172.28.31.2
  lobby:
    image: eclipse-temurin:8-jre
    healthcheck:
      test: ["CMD-SHELL", "grep -q 'Done (' /srv/logs/latest.log && bash -c '</dev/tcp/127.0.0.1/27881'"]
      interval: 5s
      timeout: 5s
      retries: 20
    working_dir: /srv
    command: ["java", "-Xmx768M", "-jar", "server.jar", "nogui", "--nojline", "--noconsole"]
    volumes: ["./lobby:/srv"]
    depends_on:
      mysql:
        condition: service_healthy
    networks:
      backend:
        ipv4_address: 172.28.31.3
  bedwars-01:
    image: eclipse-temurin:8-jre
    healthcheck:
      test: ["CMD-SHELL", "grep -q 'Done (' /srv/logs/latest.log && bash -c '</dev/tcp/127.0.0.1/27881'"]
      interval: 5s
      timeout: 5s
      retries: 20
    working_dir: /srv
    command: ["java", "-Xmx1536M", "-jar", "server.jar", "nogui", "--nojline", "--noconsole"]
    volumes: ["./bedwars-01:/srv"]
    depends_on:
      mysql:
        condition: service_healthy
    networks:
      backend:
        ipv4_address: 172.28.31.4
  bedwars-02:
    image: eclipse-temurin:8-jre
    healthcheck:
      test: ["CMD-SHELL", "grep -q 'Done (' /srv/logs/latest.log && bash -c '</dev/tcp/127.0.0.1/27881'"]
      interval: 5s
      timeout: 5s
      retries: 20
    working_dir: /srv
    command: ["java", "-Xmx1536M", "-jar", "server.jar", "nogui", "--nojline", "--noconsole"]
    volumes: ["./bedwars-02:/srv"]
    depends_on:
      mysql:
        condition: service_healthy
    networks:
      backend:
        ipv4_address: 172.28.31.5
secrets:
  mysql_root:
    file: ./.secrets/mysql-root.txt
volumes:
  sbw-phase73-mysql-data:
    external: true
networks:
  edge:
    ipam:
      config:
        - subnet: 172.28.30.0/24
  backend:
    internal: true
    ipam:
      config:
        - subnet: 172.28.31.0/24
""")
    files = sorted((p for p in lab.rglob("*.jar") if "inputs" not in p.relative_to(lab).parts), key=lambda p: str(p))
    manifest = {
        "phase": "7.3", "minecraft": "1.8.8", "java": {"proxy": "17", "backends": "8"}, "lab": str(lab),
        "commits": {"bedwars": git_commit(ROOT), "minigames": git_commit(minigames), "social": git_commit(social), "login": git_commit(login)},
        "jars": {str(p.relative_to(lab)).replace("\\", "/"): sha256(p) for p in files},
        "host_port": "127.0.0.1:45577", "mysql_host_port": "127.0.0.1:33308",
        "schema": "starfin_phase73", "arenas": ["1: casual/solo (duplicated independently)"],
        "operator": args.operator or None,
        "enabled": ["login", "social", "casual matchmaking", "mysql"],
        "deferred": ["ranked arena/group", "tournament arena/group", "real Minecraft client checks"],
    }
    put(lab / "manifest.json", json.dumps(manifest, indent=2, ensure_ascii=False) + "\n")
    put(lab / ".phase73-lab", "isolated phase 7.3 lab\n")
    print("Prepared isolated lab:", lab)
    print("Nodes: proxy, lobby, bedwars-01, bedwars-02; MySQL via compose; host 127.0.0.1:45577")
    print("Stateful start requires explicit -ConfirmLab in start-lab.ps1")


if __name__ == "__main__":
    main()
