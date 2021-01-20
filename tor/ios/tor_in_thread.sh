#!/bin/bash
set -e

[[ -z "$ARCH" ]] && echo "Please set the ARCH variable" && exit 1

# Compiler options
OPT_FLAGS="-O3 -g3 -fembed-bitcode"
MIN_IOS_VERSION=12.0

if [ "$ARCH" == "arm64" ]; then
  SDK="iphoneos"
  HOST_FLAGS="-arch arm64 -arch arm64e -miphoneos-version-min=${MIN_IOS_VERSION} -isysroot $(xcrun --sdk ${SDK} --show-sdk-path)"
  CHOST="arm-apple-darwin"
elif [ "$ARCH" == "x86_64" ]; then
  SDK="iphonesimulator"
  HOST_FLAGS="-arch x86_64 -mios-simulator-version-min=${MIN_IOS_VERSION} -isysroot $(xcrun --sdk ${SDK} --show-sdk-path)"
  CHOST="x86_64-apple-darwin"
else
  echo "Unsupported ARCH: $ARCH"
  exit 1
fi

# Locations
SCRIPT_DIR="$( cd "$( dirname "$0" )" && pwd )"
PREFIX="$(pwd)/build/ios/${ARCH}"

cd "${SCRIPT_DIR}/../tor_in_thread"

CC=$(xcrun --find --sdk "${SDK}" clang)
AR=$(xcrun --find --sdk "${SDK}" ar)
RANLIB=$(xcrun --find --sdk "${SDK}" ranlib)

mkdir -p build &> /dev/null
echo $CC ${HOST_FLAGS} ${OPT_FLAGS} -I"${PREFIX}/include" -c -o build/tor_in_thread.o tor_in_thread.c
$CC ${HOST_FLAGS} ${OPT_FLAGS} -I"${PREFIX}/include" -c -o build/tor_in_thread.o tor_in_thread.c

mkdir -p "${PREFIX}/lib" &> /dev/null
rm -f "${PREFIX}/lib/libtor_in_thread.a"
$AR r "${PREFIX}/lib/libtor_in_thread.a" "build/tor_in_thread.o"
$RANLIB -c "${PREFIX}/lib/libtor_in_thread.a"

mkdir -p "${PREFIX}/include" &> /dev/null
cp -v tor_in_thread.h "${PREFIX}/include"
