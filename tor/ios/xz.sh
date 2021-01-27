#!/bin/bash
set -e

[[ -z "$ARCH" ]] && echo "Please set the ARCH variable" && exit 1

# Compiler options
OPT_FLAGS="-O3 -g3 -fembed-bitcode"
MAKE_JOBS=8
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

cd "${SCRIPT_DIR}/../libs/xz"

# We need gettext
# This extends the path to look in some common locations (for example, if installed via Homebrew)
PATH=$PATH:/usr/local/bin:/usr/local/opt/gettext/bin

if [[ ! -f ./configure ]]; then
  ./autogen.sh
fi

# Ensure -fembed-bitcode builds, as workaround for libtool macOS bug
export MACOSX_DEPLOYMENT_TARGET="10.4"
# Get the correct toolchain for target platforms
CC=$(xcrun --find --sdk "${SDK}" clang)
export CC
AR=$(xcrun --find --sdk "${SDK}" ar)
export AR
RANLIB=$(xcrun --find --sdk "${SDK}" ranlib)
export RANLIB
export CFLAGS="${HOST_FLAGS} ${OPT_FLAGS}"
export LDFLAGS="${HOST_FLAGS}"

./configure \
  --host="${CHOST}" \
  --prefix="${PREFIX}" \
  --enable-static --disable-shared \
  --disable-doc --disable-scripts --disable-xz --disable-xzdec --disable-lzmadec --disable-lzmainfo --disable-lzma-links \
  cross_compiling="yes" ac_cv_func_clock_gettime="no"

make clean
mkdir -p "${PREFIX}" &> /dev/null
make -j"${MAKE_JOBS}"
make install
