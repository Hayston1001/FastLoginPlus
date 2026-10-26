# SPDX-License-Identifier: MIT
# Copyright (c) 2026 Hayston1001

from email import policy
from email.parser import BytesParser
import hashlib
import json
import os
from pathlib import Path
import re
import tempfile
import sys
import unittest
from unittest.mock import patch

import release_tools as release


class ReleaseNotesTest(unittest.TestCase):
    def test_exact_version_and_hotfix_heading(self):
        source = "## v0.3.10\nWrong\n## v0.3.1 (Hotfix)\nRight\n## v0.3.0\nOld\n"
        notes = release.release_notes(source, "v0.3.1", "owner/repo")
        self.assertTrue(notes.startswith(
            "Changelog: [English](https://github.com/owner/repo/blob/v0.3.1/CHANGELOG.md) | "
            "[简体中文](https://github.com/owner/repo/blob/v0.3.1/CHANGELOG_zh.md)\n"))
        self.assertTrue(notes.endswith("## v0.3.1 (Hotfix)\nRight\n"))
        self.assertNotIn("Wrong", notes)
        self.assertNotIn("Old", notes)

    def test_last_section_keeps_last_line(self):
        notes = release.release_notes("## v0.0.1\nLast line", "v0.0.1", "owner/repo")
        self.assertTrue(notes.endswith("Last line\n"))

    def test_missing_duplicate_empty_and_invalid_tags_fail(self):
        for source, tag in [
            ("## v0.1.0\nText", "v0.1.1"),
            ("## v0.1.0\nText\n## v0.1.0\nDuplicate", "v0.1.0"),
            ("## v0.1.0\n\n## v0.0.1\nOld", "v0.1.0"),
            ("## v0.1.0\nText", "v0x1x0"),
        ]:
            with self.subTest(source=source, tag=tag), self.assertRaises(ValueError):
                release.release_notes(source, tag, "owner/repo")

    def test_relative_docs_links_become_absolute(self):
        notes = release.release_notes(
            "## v1.0.0\n[Design](docs/en/DESIGN.md)", "v1.0.0", "owner/repo")
        self.assertIn(
            "[Design](https://github.com/owner/repo/blob/v1.0.0/docs/en/DESIGN.md)", notes)

    def test_repository_changelogs_have_matching_versions(self):
        english = (release.ROOT / "CHANGELOG.md").read_text(encoding="utf-8")
        chinese = (release.ROOT / "CHANGELOG_zh.md").read_text(encoding="utf-8")
        headings = re.findall(r"^## (v\S+)", english, re.MULTILINE)
        self.assertEqual(headings, re.findall(r"^## (v\S+)", chinese, re.MULTILINE))
        self.assertEqual(len(headings), len(set(headings)))
        for tag in headings:
            with self.subTest(tag=tag):
                notes = release.release_notes(english, tag, "owner/repo")
                self.assertEqual(1, len(re.findall(r"^## ", notes, re.MULTILINE)))
                self.assertIsNone(re.search(r"[\u4e00-\u9fff]", notes.split("\n", 1)[1]))
                release.release_notes(chinese, tag, "owner/repo")


class ModrinthTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.directory = Path(self.temporary.name)
        self.config = json.loads((release.ROOT / ".github/modrinth.json").read_text(encoding="utf-8"))
        self.jar = self.directory / "FastLoginPlusBukkit-0.7.0-123abcd.jar"
        self.jar.write_bytes(b"test jar\x00\xff")
        _, self.metadata = release.prepare_upload(
            self.config, "Bukkit", "v0.7.0", self.directory, "English notes")
        self.existing = {
            "id": "existing",
            "version_number": self.metadata["version_number"],
            "loaders": self.metadata["loaders"],
            "game_versions": self.metadata["game_versions"],
            "version_type": "release",
            "status": "listed",
            "files": [{"hashes": {"sha512": hashlib.sha512(self.jar.read_bytes()).hexdigest()}}],
        }

    def test_four_platforms_select_their_own_jar_and_labels(self):
        self.assertEqual({"Bukkit", "Folia", "Bungee", "Velocity"}, set(self.config["platforms"]))
        expected_loaders = {
            "Bukkit": ["bukkit", "paper", "spigot"], "Folia": ["folia"],
            "Bungee": ["bungeecord"], "Velocity": ["velocity"],
        }
        for platform, loaders in expected_loaders.items():
            with self.subTest(platform=platform):
                jar = self.directory / f"FastLoginPlus{platform}-0.7.0-123abcd.jar"
                jar.write_bytes(b"test jar")
                selected, metadata = release.prepare_upload(
                    self.config, platform, "v0.7.0", self.directory, "English notes")
                self.assertEqual(jar, selected)
                self.assertEqual(loaders, metadata["loaders"])
                self.assertEqual("0.7.0-123abcd", metadata["version_number"])
                self.assertIn(platform if platform != "Bungee" else "BungeeCord", metadata["name"])
                self.assertEqual("English notes", metadata["changelog"])
                self.assertEqual("dmpkLjdL", metadata["project_id"])

    def test_missing_plain_stale_and_ambiguous_jars_fail(self):
        self.jar.unlink()
        for filenames in [[], ["FastLoginPlusBukkit-0.7.0-123abcd-plain.jar"],
                          ["FastLoginPlusBukkit-0.6.0-123abcd.jar"],
                          ["FastLoginPlusBukkit-0.7.0-123abcd.jar", "FastLoginPlusBukkit-0.7.0-abcdef1.jar"]]:
            with self.subTest(filenames=filenames):
                for name in filenames:
                    (self.directory / name).write_bytes(b"jar")
                with self.assertRaises(ValueError):
                    release.prepare_upload(self.config, "Bukkit", "v0.7.0", self.directory, "notes")
                for name in filenames:
                    (self.directory / name).unlink()

    def test_prerelease_is_not_marked_stable(self):
        self.jar.rename(self.directory / "FastLoginPlusBukkit-0.7.0-beta.1-123abcd.jar")
        _, metadata = release.prepare_upload(
            self.config, "Bukkit", "v0.7.0-beta.1", self.directory, "notes")
        self.assertEqual("beta", metadata["version_type"])

    def test_identical_file_is_skipped_even_with_other_platform_versions(self):
        other = dict(self.existing, loaders=["velocity"])
        self.assertEqual("existing", release.existing_upload([other, self.existing], self.metadata, self.jar))
        self.assertIsNone(release.existing_upload([other], self.metadata, self.jar))

    def test_conflicting_file_metadata_and_duplicates_fail(self):
        modifications = [
            {"files": [{"hashes": {"sha512": "different"}}]},
            {"game_versions": ["1.13"]}, {"version_type": "beta"},
            {"status": "draft"}, {"loaders": ["paper"]},
        ]
        for changes in modifications:
            with self.subTest(changes=changes), self.assertRaises(ValueError):
                release.existing_upload([dict(self.existing, **changes)], self.metadata, self.jar)
        with self.assertRaises(ValueError):
            release.existing_upload([self.existing, self.existing], self.metadata, self.jar)

    def test_multipart_contains_json_and_exact_jar_bytes(self):
        metadata = dict(self.metadata, changelog="Changelog: [简体中文](https://example.com)")
        body, content_type = release.multipart(metadata, self.jar)
        message = BytesParser(policy=policy.default).parsebytes(
            f"Content-Type: {content_type}\r\nMIME-Version: 1.0\r\n\r\n".encode() + body)
        parts = list(message.iter_parts())
        self.assertEqual(2, len(parts))
        self.assertEqual("data", parts[0].get_param("name", header="content-disposition"))
        self.assertEqual(metadata, json.loads(parts[0].get_payload(decode=True)))
        self.assertEqual("file", parts[1].get_param("name", header="content-disposition"))
        self.assertEqual(self.jar.name, parts[1].get_filename())
        self.assertEqual(self.jar.read_bytes(), parts[1].get_payload(decode=True))

    @patch.object(release, "request_json")
    def test_rerun_does_not_post(self, request):
        request.return_value = [self.existing]
        release.publish(self.metadata, self.jar, "test-token")
        self.assertEqual(1, request.call_count)
        self.assertIsNone(request.call_args.args[2] if len(request.call_args.args) > 2 else None)

    @patch.object(release, "request_json")
    def test_new_upload_posts_once(self, request):
        request.side_effect = [[], {"id": "new"}]
        release.publish(self.metadata, self.jar, "test-token")
        self.assertEqual(2, request.call_count)
        self.assertEqual("/version", request.call_args.args[0])

    @patch.object(release, "request_json")
    def test_conflict_does_not_post(self, request):
        request.return_value = [dict(self.existing, status="draft")]
        with self.assertRaises(ValueError):
            release.publish(self.metadata, self.jar, "test-token")
        self.assertEqual(1, request.call_count)

    @patch.object(release, "request_json")
    def test_failed_post_is_not_retried(self, request):
        request.side_effect = [[], RuntimeError("upload failed")]
        with self.assertRaisesRegex(RuntimeError, "upload failed"):
            release.publish(self.metadata, self.jar, "test-token")
        self.assertEqual(2, request.call_count)

    @patch.object(release, "request_json")
    @patch("builtins.print")
    def test_cli_dry_run_needs_no_token_and_makes_no_requests(self, output, request):
        with patch.dict(os.environ, {"MODRINTH_TOKEN": ""}), patch.object(
            sys, "argv", ["release_tools.py", "--tag", "v0.7.0", "modrinth", "Bukkit",
                          "--dry-run", "--directory", str(self.directory)]
        ):
            release.main()
        request.assert_not_called()
        self.assertEqual("0.7.0-123abcd", json.loads(output.call_args_list[0].args[0])["version_number"])

    @patch.object(release, "urlopen")
    def test_http_failure_is_not_retried_or_leaking_token(self, urlopen):
        from urllib.error import HTTPError
        urlopen.side_effect = HTTPError("https://api.modrinth.com/v2/version", 401, "Unauthorized", {}, None)
        with self.assertRaisesRegex(RuntimeError, "HTTP 401: Unauthorized") as caught:
            release.request_json("/version", "private-token", b"upload", "multipart/form-data")
        self.assertNotIn("private-token", str(caught.exception))
        self.assertEqual(1, urlopen.call_count)


if __name__ == "__main__":
    unittest.main()
