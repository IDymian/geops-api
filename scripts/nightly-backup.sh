#!/usr/bin/env bash
# Nightly backup of unfinished work to the `wip/nightly` branch.
#
# - Snapshots the working tree (tracked + untracked, honoring .gitignore)
#   without touching your branches, index or files.
# - Pushes the snapshot to origin/wip/nightly only if something changed.
# - Never modifies main, develop or feature/* branches.
# - Aborts if the snapshot contains sensitive files or secrets.
#
# Usage: scripts/nightly-backup.sh [path-to-repo]
set -euo pipefail

BACKUP_BRANCH="wip/nightly"
REMOTE="origin"
LOG_DIR="${HOME}/.geops-backup"
mkdir -p "$LOG_DIR"
LOG_FILE="$LOG_DIR/nightly-backup.log"

REPO="${1:-$(cd "$(dirname "$0")/.." && pwd)}"
cd "$REPO"
NAME="$(basename "$(git rev-parse --show-toplevel)")"

log() { echo "$(date '+%Y-%m-%d %H:%M:%S') [$NAME] $*" | tee -a "$LOG_FILE"; }

# Files that must never be pushed. Portable ERE (macOS grep has no -P).
SENSITIVE_PATHS='(^|/)\.env(\.[^/]+)?$|\.(jks|keystore|p12|pfx|pem|key)$|(^|/)key\.properties$|(^|/)google-services\.json$|(^|/)GoogleService-Info\.plist$|firebase-service-account[^/]*\.json$'
ALLOWED_PATHS='(^|/)\.env\.example$'
# Secret-looking content in changed lines.
SENSITIVE_CONTENT='-----BEGIN [A-Z ]*PRIVATE KEY-----|sk_live_[0-9A-Za-z]+|rk_live_[0-9A-Za-z]+|AKIA[0-9A-Z]{16}|gh[pousr]_[0-9A-Za-z]{30,}'

tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT
export_index() { GIT_INDEX_FILE="$tmp_dir/index" "$@"; }

# 1. Build a snapshot tree in a temporary index (your real index is untouched).
if git rev-parse -q --verify HEAD >/dev/null; then
  export_index git read-tree HEAD
  head_tree="$(git rev-parse 'HEAD^{tree}')"
  unpushed="$(git rev-list -n 1 HEAD --not --remotes)"
else
  head_tree=""
  unpushed=""
fi
export_index git add -A
tree="$(export_index git write-tree)"

# 2. Nothing uncommitted and nothing unpushed -> do nothing.
if [ "$tree" = "$head_tree" ] && [ -z "$unpushed" ]; then
  log "No changes to back up."
  exit 0
fi

# 3. Same content as the last backup -> do nothing.
git fetch -q "$REMOTE" "+refs/heads/$BACKUP_BRANCH:refs/remotes/$REMOTE/$BACKUP_BRANCH" 2>/dev/null || true
last_backup="$(git rev-parse -q --verify "refs/remotes/$REMOTE/$BACKUP_BRANCH" || true)"
if [ -n "$last_backup" ] && [ "$tree" = "$(git rev-parse "$last_backup^{tree}")" ]; then
  log "No changes since the last backup."
  exit 0
fi

# 4. Safety check: abort if sensitive files or secrets would be pushed.
bad_files="$(git ls-tree -r --name-only "$tree" | grep -E "$SENSITIVE_PATHS" | grep -v -E "$ALLOWED_PATHS" || true)"
if [ -n "$head_tree" ]; then
  bad_content="$(git diff "$head_tree" "$tree" | grep '^+' | grep -E -o -e "$SENSITIVE_CONTENT" | head -n 5 || true)"
else
  bad_content=""
fi
if [ -n "$bad_files" ] || [ -n "$bad_content" ]; then
  log "ABORTED: sensitive data found, nothing was pushed."
  [ -n "$bad_files" ] && log "  files: $(echo "$bad_files" | tr '\n' ' ')"
  [ -n "$bad_content" ] && log "  content matches a secret pattern (value not logged)"
  exit 2
fi

# 5. Commit the snapshot on top of the last backup and push it.
parents=()
[ -n "$last_backup" ] && parents+=(-p "$last_backup")
git rev-parse -q --verify HEAD >/dev/null && parents+=(-p HEAD)
message="chore(wip): nightly backup $(date +%F)"
commit="$(git commit-tree "$tree" "${parents[@]}" -m "$message")"
git push -q "$REMOTE" "$commit:refs/heads/$BACKUP_BRANCH"
log "Pushed $message ($(git rev-parse --short "$commit")) to $REMOTE/$BACKUP_BRANCH."
