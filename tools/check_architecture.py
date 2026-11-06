"""Check the project's agreed source-level module and table boundaries (stdlib only)."""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
FEATURES = ("event", "browse", "registration", "engagement")
TABLE_OWNERS = {"events": "event", "registrations": "registration", "favorites": "engagement", "reminders": "engagement"}
errors = []

for module in FEATURES:
    folder = ROOT / "feature" / module
    build = (folder / "build.gradle.kts").read_text(encoding="utf-8-sig")
    if re.search(r'project\(\s*":(?:feature:|app")', build):
        errors.append(f"feature/{module} depends on another feature or app")
    for source in (folder / "src/main/java").rglob("*.java"):
        text = source.read_text(encoding="utf-8-sig")
        for other in FEATURES:
            if other != module and re.search(r"import\s+com\.example\.campusevents\." + other + r"\.", text):
                errors.append(f"{source.relative_to(ROOT)} imports {other} implementation")
        if re.search(r"import\s+com\.example\.campusevents\.(?:bootstrap|MainActivity|CampusApplication)", text):
            errors.append(f"{source.relative_to(ROOT)} imports the application shell")
        if "ui" in source.relative_to(folder).parts and re.search(r"import\s+(?:android\.database|com\.example\.campusevents\.platform\.data)", text):
            errors.append(f"{source.relative_to(ROOT)} exposes database infrastructure to UI")
        for table, owner in TABLE_OWNERS.items():
            if owner != module and re.search(r"(?:FROM|JOIN|UPDATE|INTO|TABLE)\s+" + table + r"\b", text, re.IGNORECASE):
                errors.append(f"{source.relative_to(ROOT)} accesses {table}, owned by {owner}")

for source in (ROOT / "core/contracts/src/main/java").rglob("*.java"):
    if re.search(r"import\s+(?:android|androidx)\.", source.read_text(encoding="utf-8-sig")):
        errors.append(f"{source.relative_to(ROOT)} imports Android into contracts")
for core in ("contracts", "platform"):
    if re.search(r'project\(\s*":(?:feature:|app")', (ROOT / "core" / core / "build.gradle.kts").read_text(encoding="utf-8-sig")):
        errors.append(f"core/{core} depends on app or feature")

if errors:
    print("Architecture boundary check failed")
    print("\n".join(f"- {error}" for error in errors))
    sys.exit(1)
print("Architecture boundary check passed (7 modules)")
