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
  CONFIG="no-async zlib-dynamic ios64-xcrun"
elif [ "$ARCH" == "x86_64" ]; then
  SDK="iphonesimulator"
  HOST_FLAGS="-arch x86_64 -mios-simulator-version-min=${MIN_IOS_VERSION} -isysroot $(xcrun --sdk ${SDK} --show-sdk-path)"
  CONFIG="no-asm iossimulator-xcrun"
else
  echo "Unsupported ARCH: $ARCH"
  exit 1
fi

# Locations
SCRIPT_DIR="$( cd "$( dirname "$0" )" && pwd )"
PREFIX="$(pwd)/build/ios/${ARCH}"

cd "${SCRIPT_DIR}/../libs/openssl"

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

./Configure \
  no-shared \
  --prefix="${PREFIX}" \
  enable-ec_nistp_64_gcc_128 \
  $CONFIG

make clean
make depend
mkdir -p "${PREFIX}" &> /dev/null
make -j"${MAKE_JOBS}" build_libs
make install_dev
make distclean
