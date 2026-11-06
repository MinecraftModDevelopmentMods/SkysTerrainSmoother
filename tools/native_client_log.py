"""Check Forge 64 completion, native settings and unexpected errors."""

import argparse
import gzip
import json
from pathlib import Path
import re
import tomllib
from native_registry_notices import audit_text, profile_for


def offline_client_notices(text):
    # The offline probe uses token 0. Bare Forge produces these same notices
    # before the local world opens; a different exception still fails.
    rows = (
        (r'Download-\d+', 'net.minecraft.client.Minecraft/', 'Failed to fetch user properties',
         'com.mojang.authlib.exceptions.InvalidCredentialsException: Status: 401'),
        (r'Download-\d+', 'com.mojang.realmsclient.client.RealmsClient/', 'Failed to fetch Realms feature flags',
         "com.mojang.realmsclient.exception.RealmsServiceException: Realms authentication error with message "
         "'java.lang.RuntimeException: Failed to parse into SignedJWT: 0'"),
        (r'IO-Worker-\d+', 'com.mojang.realmsclient.RealmsAvailability/', "Couldn't connect to realms",
         "com.mojang.realmsclient.exception.RealmsServiceException: Realms authentication error with message "
         "'java.lang.RuntimeException: Failed to parse into SignedJWT: 0'"),
    )
    lines = text.splitlines()
    notices = set()
    kept = []
    for index, line in enumerate(lines):
        for thread, logger, message, exception in rows:
            header = r'\[[^\r\n]+\] \[' + thread + r'/ERROR\](?: \[' + re.escape(logger) + r'\])?: '
            if (re.fullmatch(header + re.escape(message), line)
                    and index + 1 < len(lines) and lines[index + 1] == exception):
                if message in notices:
                    raise ValueError('Repeated offline authentication notice: ' + message)
                notices.add(message)
                break
        else:
            kept.append(line)
    return '\n'.join(kept), sorted(notices)


def audit_complete_logs(directory, packaged_offline=False):
    """Check rolled logs too, so a clean reload cannot hide an earlier failure."""
    paths = sorted(path for path in (Path(directory) / "logs").rglob("*")
                   if path.is_file() and (path.name.endswith(".log") or path.name.endswith(".log.gz")))
    if not paths:
        raise ValueError("Complete native log evidence is missing")
    for path in paths:
        if path.name.endswith(".gz"):
            with gzip.open(path, "rt", encoding="utf-8") as source:
                text = source.read()
        else:
            text = path.read_text(encoding="utf-8")
        if packaged_offline:
            text, _ = offline_client_notices(text)
        try:
            audit_text(text, profile_for(directory, forge64=True))
        except ValueError as failure:
            raise ValueError(str(path) + ": " + str(failure)) from failure
    return len(paths)


def classify(log, unchanged_defaults, profile=None, packaged_offline=False):
    """Require a complete Forge client login without unexpected errors."""
    offline_notices = []
    if packaged_offline:
        log, offline_notices = offline_client_notices(log)
    lines = log.splitlines()
    startup = next((index for index, line in enumerate(lines)
                    if "Starting integrated minecraft server version 26.1.2" in line), -1)
    server_info = r"\[[^\r\n]+\] \[Server thread/INFO\](?: \[[^\r\n]+\])?: "
    player = None
    joined = False
    for line in lines[startup + 1:] if startup >= 0 else ():
        login = re.fullmatch(server_info
                             + r"([A-Za-z0-9_]{1,16})\[local:E:[0-9a-f]+\] logged in with entity id \d+ at \(.+\)",
                             line)
        if login:
            player = login[1]
        elif player and re.fullmatch(server_info + re.escape(player) + " joined the game", line):
            joined = True
            break
    if not joined:
        raise ValueError("Native Forge login did not complete normally")
    if not unchanged_defaults:
        raise ValueError("Native Forge settings changed")
    notices = offline_notices + audit_text(log, profile)
    return {"empty_handshakes": 0, "native_config_notices": 0,
            "notices": notices, "unchanged_native_defaults": True}


def audit(directory, phase, control=False, packaged_offline=False):
    directory = Path(directory)
    checked_logs = audit_complete_logs(directory, packaged_offline)
    log = (directory / "logs/latest.log").read_text(encoding="utf-8")
    defaults = Path(__file__).resolve().parent.parent / "src/bootstrapTest/defaultconfigs"
    servers = list((directory / "saves").glob("*/serverconfig/forge-server.toml"))
    pairs = [(defaults / "config/forge-client.toml", directory / "config/forge-client.toml")]
    pairs.extend((defaults / "defaultconfigs/forge-server.toml", server) for server in servers)
    unchanged = len(servers) == 1 and all(
        actual.is_file() and tomllib.loads(expected.read_text(encoding="utf-8"))
        == tomllib.loads(actual.read_text(encoding="utf-8")) for expected, actual in pairs)
    if not unchanged:
        raise ValueError("Native Forge settings differ from the controlled defaults")
    receipt = classify(log, unchanged, profile_for(directory, forge64=True), packaged_offline)
    required = (("FORGE61_CLIENT_CONTROL_PASS joined=true sky_mods=false",) if control else
                ("TERRAIN_SMOOTHER_CLIENT_WORLD_PASS", "TERRAIN_SMOOTHER_CLIENT_PASS",
                 "SKY_CONFIG_SCREENS_PASS", "TERRAIN_FINE_GRASS_MODELS_PASS",
                 "SKY_RECIPE_PREVIEW_MODELS_PASS", "TERRAIN_SMOOTHER_CLIENT_TINT_PASS"))
    if any(marker not in log for marker in required):
        raise ValueError("Native client completion checks are missing")
    if control and receipt["empty_handshakes"] != 0:
        raise ValueError("The Forge-only control must complete without handshake errors")
    if list((directory / "crash-reports").glob("*")):
        raise ValueError("Native client produced a crash report")
    receipt["phase"] = phase
    receipt["complete_log_files"] = checked_logs
    receipt["control_without_sky_mods"] = control
    (directory / (phase + "-log-audit.json")).write_text(
        json.dumps(receipt, indent=2) + "\n", encoding="utf-8")
    return receipt


def classify_server(log, debug, unchanged_defaults, profile=None):
    """The Forge 64 server control completes without severe notices."""
    lines = log.splitlines()
    startup = any('Preparing level ' in line or 'Preparing start region for dimension ' in line
                  for line in lines)
    if not unchanged_defaults or not startup or 'Done (' not in log:
        raise ValueError('Native server defaults or startup sequence changed')
    notices = audit_text(log, profile)
    if not debug.strip():
        raise ValueError('Native server debug evidence is missing')
    return {'native_server_config_notices': 0, 'notices': notices,
            'unchanged_native_defaults': True, 'debug_identity_verified': True}

def audit_server(directory, phase):
    directory = Path(directory)
    checked_logs = audit_complete_logs(directory)
    defaults = Path(__file__).resolve().parent.parent / 'src/bootstrapTest/defaultconfigs'
    expected = defaults / 'defaultconfigs/forge-server.toml'
    actual = directory / 'world/serverconfig/forge-server.toml'
    unchanged = actual.is_file() and tomllib.loads(expected.read_text(encoding='utf-8')) == tomllib.loads(
        actual.read_text(encoding='utf-8'))
    receipt = classify_server((directory / 'logs/latest.log').read_text(encoding='utf-8'),
                              (directory / 'logs/debug.log').read_text(encoding='utf-8'), unchanged, profile_for(directory, forge64=True))
    if list((directory / 'crash-reports').glob('*')):
        raise ValueError('Native server produced a crash report')
    receipt['phase'] = phase
    receipt['complete_log_files'] = checked_logs
    (directory / (phase + '-server-log-audit.json')).write_text(
        json.dumps(receipt, indent=2) + '\n', encoding='utf-8')
    return receipt


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--directory", type=Path, required=True)
    parser.add_argument("--phase", required=True)
    parser.add_argument("--control", action="store_true")
    parser.add_argument("--server", action="store_true")
    parser.add_argument("--offline-client", action="store_true",
                        help="Accept only the exact native notices from the probe's dummy token")
    options = parser.parse_args()
    if options.server and options.offline_client:
        parser.error("--offline-client is only for the local client probe")
    result = (audit_server(options.directory, options.phase) if options.server
              else audit(options.directory, options.phase, options.control,
                         packaged_offline=options.offline_client))
    print("FORGE64_NATIVE_SERVER_AUDIT_PASS" if options.server else "FORGE64_NATIVE_CLIENT_AUDIT_PASS", json.dumps(result, sort_keys=True))
