#!/bin/bash
set -e

[[ -z "$NDK" ]] && echo "Please set the NDK variable" && exit 1
[[ -z "$ARCH" ]] && echo "Please set the ARCH variable" && exit 1

if [ "$ARCH" == "x86_64" ]; then
  SYS=x86_64
elif [ "$ARCH" == "x86" ]; then
  SYS=i686
elif [ "$ARCH" == "arm64-v8a" ]; then
  SYS=aarch64
elif [ "$ARCH" == "armeabi-v7a" ]; then
  SYS=armv7a
else
  echo "Unsupported ARCH: $ARCH"
  exit 1
fi

case "$(uname -s)" in
  Linux*) TOOLCHAIN=linux-x86_64 ;;
  Darwin*) TOOLCHAIN=darwin-x86_64 ;;
  MINGW*) TOOLCHAIN=windows-x86_64 ;;
  *) echo "Unsupported OS: $(uname -s)" ; exit 1 ;;
esac

CHOST=$SYS-linux-android
TARGET=$CHOST
if [ "$SYS" == "armv7a" ]; then
  CHOST=armv7a-linux-androideabi
  TARGET=arm-linux-androideabi
fi

HOST_FLAGS="-fpic"

# Compiler options
OPT_FLAGS="-O3 -g3"

# Locations
SCRIPT_DIR="$( cd "$( dirname "$0" )" && pwd )"
PREFIX="$(pwd)/build/android/${ARCH}"

cd "${SCRIPT_DIR}/../tor_in_thread"

CC="$NDK/toolchains/llvm/prebuilt/$TOOLCHAIN/bin/${CHOST}21-clang"
AR="$NDK/toolchains/llvm/prebuilt/$TOOLCHAIN/bin/$TARGET-ar"
RANLIB="$NDK/toolchains/llvm/prebuilt/$TOOLCHAIN/bin/$TARGET-ranlib"

mkdir -p build &> /dev/null
echo $CC ${HOST_FLAGS} ${OPT_FLAGS} -I"${PREFIX}/include" -c -o build/tor_in_thread.o tor_in_thread.c
$CC ${HOST_FLAGS} ${OPT_FLAGS} -I"${PREFIX}/include" -c -o build/tor_in_thread.o tor_in_thread.c

mkdir -p "${PREFIX}/lib" &> /dev/null
rm -f "${PREFIX}/lib/libtor_in_thread.a"
$AR r "${PREFIX}/lib/libtor_in_thread.a" "build/tor_in_thread.o"
$RANLIB -c "${PREFIX}/lib/libtor_in_thread.a"

mkdir -p "${PREFIX}/include" &> /dev/null
cp -v tor_in_thread.h "${PREFIX}/include"
