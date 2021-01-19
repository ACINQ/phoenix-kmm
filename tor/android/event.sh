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
MAKE_JOBS=8

# Locations
SCRIPT_DIR="$( cd "$( dirname "$0" )" && pwd )"
PREFIX="${SCRIPT_DIR}/build/${ARCH}"

cd ../libs/event

# We need gettext
# This extends the path to look in some common locations (for example, if installed via Homebrew)
PATH=$PATH:/usr/local/bin:/usr/local/opt/gettext/bin

if [[ ! -f ./configure ]]; then
  if [ "$(uname)" == "Darwin" ]; then
    export LIBTOOLIZE=glibtoolize
  fi
  ./autogen.sh
fi

# Get the correct toolchain for target platforms
export CC="$NDK/toolchains/llvm/prebuilt/$TOOLCHAIN/bin/${CHOST}21-clang"
export LD="$NDK/toolchains/llvm/prebuilt/$TOOLCHAIN/bin/$TARGET-ld"
export AR="$NDK/toolchains/llvm/prebuilt/$TOOLCHAIN/bin/$TARGET-ar"
export AS="$NDK/toolchains/llvm/prebuilt/$TOOLCHAIN/bin/$TARGET-as"
export RANLIB="$NDK/toolchains/llvm/prebuilt/$TOOLCHAIN/bin/$TARGET-ranlib"
export STRIP="$NDK/toolchains/llvm/prebuilt/$TOOLCHAIN/bin/$TARGET-strip"
export CFLAGS="${HOST_FLAGS} ${OPT_FLAGS} -I${PREFIX}/include"
export LDFLAGS="${HOST_FLAGS}"

./configure \
  --host="${CHOST}" \
  --prefix="${PREFIX}" \
  --enable-static --disable-shared \
  --enable-gcc-hardening --disable-samples \
  cross_compiling="yes"

make clean
mkdir -p "${PREFIX}" &> /dev/null
make -j"${MAKE_JOBS}"
make install
