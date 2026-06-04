#!/bin/bash
#
# AutoTweaker
# Copyright (C) 2026  WhiteElephant-abc
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program.  If not, see <https://www.gnu.org/licenses/>.
#

set -euo pipefail

REMOTE_VERSION=$(curl -fsSL https://autotweaker.github.io/index/ | jq -r '.core.version')
LOCAL_VERSION=$(autotweaker version 2>/dev/null | awk '{print $NF}')

if [ -z "$REMOTE_VERSION" ]; then
    echo "Failed to fetch remote version" >&2
    exit 1
fi

remote_base="${REMOTE_VERSION%%+*}"
local_base="${LOCAL_VERSION%%+*}"

if printf '%s\n%s\n' "$remote_base" "$local_base" | sort -V | tail -1 | grep -q "$local_base"; then
    echo "Already up to date: $LOCAL_VERSION"
else
    echo "New version available: $REMOTE_VERSION (current: $LOCAL_VERSION). Updating..."
    curl -fsSL "$(curl -fsSL https://autotweaker.github.io/index/ | jq -r '.core.deb_url')" -o /tmp/autotweaker.deb && apt install /tmp/autotweaker.deb
fi
