from pathlib import Path
from tempfile import TemporaryDirectory
import unittest

from check_bundled_assets import ASSETS, check_assets, required_packs


class BundledAssetsTest(unittest.TestCase):
    def test_disabled_default_is_not_required(self):
        source = '''
            LanguageManager.DefaultPack("EN", "packs/en.zip"),
            // LanguageManager.DefaultPack("EL", "packs/el.zip"),
        '''
        self.assertEqual(["packs/en.zip"], required_packs(source))

    def test_missing_required_pack_fails_even_when_git_lists_it(self):
        with TemporaryDirectory() as temporary:
            problems = check_assets(Path(temporary), ["packs/en.zip"],
                                    {(ASSETS / "packs/en.zip").as_posix()})
        self.assertEqual([f"Missing default pack: {ASSETS.as_posix()}/packs/en.zip"], problems)

    def test_inactive_but_packaged_local_file_also_must_be_tracked(self):
        with TemporaryDirectory() as temporary:
            root = Path(temporary)
            path = root / ASSETS / "packs/inactive.zip"
            path.parent.mkdir(parents=True)
            path.write_bytes(b"fixture")
            relative = path.relative_to(root).as_posix()
            self.assertEqual([f"Untracked APK asset input: {relative}"],
                             check_assets(root, [], set()))
            self.assertEqual([], check_assets(root, [], {relative}))


if __name__ == "__main__":
    unittest.main()
