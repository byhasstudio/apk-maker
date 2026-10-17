#!/bin/bash
# ------------------------------------------------------------------
# fix-package.sh
# ------------------------------------------------------------------
# Otomatis mengganti package name di RemoteControlModule.kt supaya
# cocok dengan Package ID yang di-generate tiap build (misal
# com.budi.toko), dan memindahkan file ke folder yang sesuai struktur
# package Kotlin/Java (app/src/main/java/com/budi/toko/remote/...).
#
# DIPANGGIL DARI GITHUB ACTIONS, sebagai step SEBELUM step build
# Gradle/compile APK. Butuh 1 variabel environment: APP_ID (contoh:
# com.budi.toko) — ini SUDAH ADA di workflow kamu karena dipakai juga
# untuk AndroidManifest.xml applicationId, tinggal diteruskan ke sini.
#
# CARA PAKAI di workflow (.github/workflows/....yml), tambahkan step
# baru PERSIS SEBELUM step "Build APK" / "Assemble Release" / step
# Gradle apa pun yang mengompilasi project:
#
#   - name: Sesuaikan package RemoteControlModule
#     run: bash fix-package.sh
#     env:
#       APP_ID: ${{ env.APP_ID }}   # ganti sesuai nama variabel Package ID di workflow kamu
#
# (Taruh file fix-package.sh ini di root repo builder/, sejajar
# dengan file workflow-nya, atau sesuaikan path di "run:" di atas.)
# ------------------------------------------------------------------

set -euo pipefail

if [ -z "${APP_ID:-}" ]; then
  echo "ERROR: variabel APP_ID kosong. Pastikan workflow meneruskan Package ID ke script ini."
  exit 1
fi

# Validasi sederhana: harus format package Java/Kotlin yang valid (contoh: com.budi.toko)
if ! [[ "$APP_ID" =~ ^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$ ]]; then
  echo "ERROR: APP_ID '$APP_ID' bukan format package yang valid."
  exit 1
fi

OLD_PACKAGE="com.apkmaker.remote"
NEW_PACKAGE="${APP_ID}.remote"

# Sesuaikan path ini kalau lokasi file RemoteControlModule.kt di repo kamu beda
SRC_ROOT="app/src/main/java"
OLD_DIR="$SRC_ROOT/$(echo "$OLD_PACKAGE" | tr '.' '/')"
NEW_DIR="$SRC_ROOT/$(echo "$NEW_PACKAGE" | tr '.' '/')"
FILE_NAME="RemoteControlModule.kt"

if [ ! -f "$OLD_DIR/$FILE_NAME" ]; then
  echo "ERROR: file $OLD_DIR/$FILE_NAME tidak ditemukan. Pastikan RemoteControlModule.kt sudah ditaruh di path itu sebelum step ini jalan."
  exit 1
fi

echo "Mengganti package: $OLD_PACKAGE -> $NEW_PACKAGE"

mkdir -p "$NEW_DIR"

# Ganti baris "package ..." di dalam file
sed -i "s/^package ${OLD_PACKAGE}\$/package ${NEW_PACKAGE}/" "$OLD_DIR/$FILE_NAME"

# Pindahkan file ke folder yang sesuai struktur package barunya
mv "$OLD_DIR/$FILE_NAME" "$NEW_DIR/$FILE_NAME"

# Bersihkan folder lama kalau sudah kosong (naik sampai ke SRC_ROOT)
rmdir -p "$OLD_DIR" 2>/dev/null || true

# Sesuaikan juga referensi package di AndroidManifest.xml, kalau ada
# baris service yang masih menunjuk ke package lama secara eksplisit
# (biasanya cukup pakai ".remote.ScreenCaptureService" yang relatif
# ke applicationId, jadi baris ini jaga-jaga saja)
MANIFEST="app/src/main/AndroidManifest.xml"
if [ -f "$MANIFEST" ]; then
  sed -i "s/${OLD_PACKAGE}\.remote/${NEW_PACKAGE}/g" "$MANIFEST"
fi

echo "Selesai. RemoteControlModule.kt sekarang di: $NEW_DIR/$FILE_NAME (package $NEW_PACKAGE)"
