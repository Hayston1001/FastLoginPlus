# SPDX-License-Identifier: MIT
# Copyright (c) 2026 Hayston1001

"""English release notes and per-platform Modrinth uploads, using only the standard library."""

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import sys
from urllib.error import HTTPError
from urllib.parse import quote, urlencode
from urllib.request import Request, urlopen
import uuid


ROOT = Path(__file__).resolve().parents[2]
API = "https://api.modrinth.com/v2"
USER_AGENT = "Hayston1001/FastLoginPlus-release (https://github.com/Hayston1001/FastLoginPlus)"


def release_notes(changelog, tag, repository):
    if not re.fullmatch(r"v\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?", tag):
        raise ValueError(f"Invalid release tag: {tag}")
    sections = list(re.finditer(r"^## ([^\n]+)$", changelog, re.MULTILINE))
    matches = [i for i, section in enumerate(sections) if section[1].split()[0] == tag]
    if len(matches) != 1:
        raise ValueError(f"Expected exactly one changelog section for {tag}")
    index = matches[0]
    end = sections[index + 1].start() if index + 1 < len(sections) else len(changelog)
    body = changelog[sections[index].start():end].strip()
    if not changelog[sections[index].end():end].strip():
        raise ValueError(f"Empty changelog section for {tag}")
    # Pin links to the release tag so historical notes do not change with main.
    base = f"https://github.com/{repository}/blob/{tag}"
    body = re.sub(r"\]\((docs/[^)]+)\)", lambda match: f"]({base}/{match[1]})", body)
    links = (f"Changelog: [English]({base}/CHANGELOG.md) | "
             f"[简体中文]({base}/CHANGELOG_zh.md)")
    return f"{links}\n\n{body}\n"


def prepare_upload(config, platform, tag, directory, notes):
    entry = config["platforms"][platform]
    prefix = f"FastLoginPlus{platform}-"
    candidates = list(directory.glob(f"{prefix}*.jar"))
    pattern = rf"{prefix}{re.escape(tag.removeprefix('v'))}-[0-9a-f]{{7,40}}\.jar"
    if len(candidates) != 1 or not re.fullmatch(pattern, candidates[0].name):
        raise ValueError(f"Expected exactly one installable {platform} jar for {tag}")
    jar = candidates[0]
    version = jar.name[len(prefix):-4]
    metadata = {
        "project_id": config["project_id"],
        "name": f"FastLoginPlus {entry.get('name', platform)} {version}",
        "version_number": version,
        "changelog": notes,
        "dependencies": [],
        "game_versions": config["game_versions"][entry["game_versions"]],
        "version_type": "beta" if "-" in tag.removeprefix("v") else "release",
        "loaders": entry["loaders"],
        "featured": False,
        "status": "listed",
        "file_parts": ["file"],
        "primary_file": "file",
    }
    return jar, metadata


def existing_upload(versions, metadata, jar):
    matches = [version for version in versions
               if version["version_number"] == metadata["version_number"]
               and set(version["loaders"]) & set(metadata["loaders"])]
    if not matches:
        return None
    digest = hashlib.sha512(jar.read_bytes()).hexdigest()
    if (len(matches) == 1
            and set(matches[0]["loaders"]) == set(metadata["loaders"])
            and len(matches[0]["files"]) == 1
            and matches[0]["files"][0]["hashes"].get("sha512") == digest
            and set(matches[0]["game_versions"]) == set(metadata["game_versions"])
            and matches[0]["version_type"] == metadata["version_type"]
            and matches[0]["status"] == metadata["status"]):
        return matches[0]["id"]
    raise ValueError("This platform/version already exists with different files or metadata; refusing to overwrite")


def multipart(metadata, jar):
    boundary = uuid.uuid4().hex
    body = (
        f'--{boundary}\r\nContent-Disposition: form-data; name="data"\r\n'
        'Content-Type: application/json\r\n\r\n'
        f'{json.dumps(metadata, ensure_ascii=False)}\r\n'
        f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{jar.name}"\r\n'
        'Content-Type: application/java-archive\r\n\r\n'
    ).encode("utf-8")
    body += jar.read_bytes() + f"\r\n--{boundary}--\r\n".encode("ascii")
    return body, f"multipart/form-data; boundary={boundary}"


def request_json(path, token, body=None, content_type=None):
    headers = {"User-Agent": USER_AGENT, "Authorization": token}
    if content_type:
        headers["Content-Type"] = content_type
    request = Request(API + path, data=body, headers=headers)
    try:
        with urlopen(request, timeout=120) as response:
            return json.load(response)
    except HTTPError as error:
        # Do not print headers or the token. Do not blindly retry a POST: it may
        # have succeeded remotely. Rerunning first checks the uploaded file hash.
        raise RuntimeError(f"Modrinth HTTP {error.code}: {error.reason}") from None


def publish(metadata, jar, token):
    query = urlencode({"loaders": json.dumps(metadata["loaders"]), "include_changelog": "false"})
    versions = request_json(f"/project/{quote(metadata['project_id'], safe='')}/version?{query}", token)
    existing = existing_upload(versions, metadata, jar)
    if existing:
        print(f"Already published, verified file hash: {existing}")
        return
    body, content_type = multipart(metadata, jar)
    result = request_json("/version", token, body, content_type)
    print(f"Published: https://modrinth.com/project/{metadata['project_id']}/version/{result['id']}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    notes_command = commands.add_parser("notes")
    notes_command.add_argument("output", type=Path)
    upload_command = commands.add_parser("modrinth")
    upload_command.add_argument("platform", choices=["Bukkit", "Folia", "Bungee", "Velocity"])
    upload_command.add_argument("--dry-run", action="store_true", help="Print metadata without any API requests")
    upload_command.add_argument("--directory", type=Path, default=ROOT / "build/release")
    parser.add_argument("--tag", default=os.environ.get("GITHUB_REF_NAME", ""))
    args = parser.parse_args()
    repository = os.environ.get("GITHUB_REPOSITORY", "Hayston1001/FastLoginPlus")
    notes = release_notes((ROOT / "CHANGELOG.md").read_text(encoding="utf-8"), args.tag, repository)
    if args.command == "notes":
        args.output.write_text(notes, encoding="utf-8", newline="\n")
        return
    config = json.loads((ROOT / ".github/modrinth.json").read_text(encoding="utf-8"))
    jar, metadata = prepare_upload(config, args.platform, args.tag, args.directory, notes)
    if args.dry_run:
        print(json.dumps(metadata, ensure_ascii=False, indent=2))
        print(f"File: {jar}")
        return
    token = os.environ.get("MODRINTH_TOKEN", "").strip()
    if not token:
        raise ValueError("Set the MODRINTH_TOKEN repository secret before enabling Modrinth publishing")
    publish(metadata, jar, token)


if __name__ == "__main__":
    try:
        main()
    except (ValueError, RuntimeError, OSError) as error:
        sys.exit(str(error))
