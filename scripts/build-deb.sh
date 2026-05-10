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

set -e

VERSION="${1:?Usage: $0 <version>}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

DEB_VERSION=$(echo "$VERSION" | tr "-" "~")
PKG_ROOT="${PROJECT_DIR}/.temp/deb/autotweaker_${DEB_VERSION}_amd64"

rm -rf "$PKG_ROOT"
mkdir -p "$PKG_ROOT/DEBIAN"
mkdir -p "$PKG_ROOT/usr/share/autotweaker"
mkdir -p "$PKG_ROOT/usr/share/doc/autotweaker"
mkdir -p "$PKG_ROOT/usr/share/metainfo"
mkdir -p "$PKG_ROOT/usr/share/icons/hicolor/256x256/apps"
mkdir -p "$PKG_ROOT/usr/bin"

# 产物（installDist 由 buildDeb task 管理依赖）
cp -r core/build/install/autotweaker/* "$PKG_ROOT/usr/share/autotweaker/"

# CLI 脚本和服务
mkdir -p "$PKG_ROOT/usr/share/autotweaker/scripts"
cp "$SCRIPT_DIR/autotweaker" "$PKG_ROOT/usr/share/autotweaker/scripts/"
cp "$SCRIPT_DIR/autotweakerd" "$PKG_ROOT/usr/share/autotweaker/scripts/"
cp "$SCRIPT_DIR/autotweaker.service" "$PKG_ROOT/usr/share/autotweaker/scripts/"
chmod 755 "$PKG_ROOT/usr/share/autotweaker/scripts/autotweaker" "$PKG_ROOT/usr/share/autotweaker/scripts/autotweakerd"

# 元数据
cp "$SCRIPT_DIR/deb/copyright" "$PKG_ROOT/usr/share/doc/autotweaker/copyright"
cp "$SCRIPT_DIR/deb/io.github.autotweaker.core.metainfo.xml" "$PKG_ROOT/usr/share/metainfo/"
cp "$PROJECT_DIR/icon.png" "$PKG_ROOT/usr/share/icons/hicolor/256x256/apps/io.github.autotweaker.core.png"

sed "s/\${DEB_VERSION}/$DEB_VERSION/g" "$SCRIPT_DIR/deb/control" > "$PKG_ROOT/DEBIAN/control"
cp "$SCRIPT_DIR/deb/postinst" "$PKG_ROOT/DEBIAN/postinst"
cp "$SCRIPT_DIR/deb/postrm" "$PKG_ROOT/DEBIAN/postrm"
chmod 755 "$PKG_ROOT/DEBIAN/postinst" "$PKG_ROOT/DEBIAN/postrm"

dpkg-deb --build --root-owner-group "$PKG_ROOT"

echo "${PKG_ROOT}.deb"
