#!/bin/bash
set -e

[[ -z "$NDK" ]] && echo "Please set the NDK variable" && exit 1
[[ -z "$ARCH" ]] && echo "Please set the ARCH variable" && exit 1

if [ "$ARCH" == "x86_64" ]; then
  CONFIG="enable-ec_nistp_64_gcc_128 android-x86_64"
elif [ "$ARCH" == "x86" ]; then
  CONFIG="android-x86"
elif [ "$ARCH" == "arm64-v8a" ]; then
  CONFIG="enable-ec_nistp_64_gcc_128 android-arm64"
elif [ "$ARCH" == "armeabi-v7a" ]; then
  CONFIG="android-arm"
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

# Compiler options
MAKE_JOBS=8

# Locations
SCRIPT_DIR="$( cd "$( dirname "$0" )" && pwd )"
PREFIX="$(pwd)/build/android/${ARCH}"

cd "${SCRIPT_DIR}/../libs/openssl"

export PATH="$NDK/toolchains/llvm/prebuilt/$TOOLCHAIN/bin:$NDK/toolchains/arm-linux-androideabi-4.9/prebuilt/$TOOLCHAIN/bin:$PATH"
export ANDROID_NDK_HOME=$NDK

./Configure \
  no-shared \
  --prefix="${PREFIX}" \
  $CONFIG \
  -D__ANDROID_API__=21

make clean
make depend
mkdir -p "${PREFIX}" &> /dev/null
make -j"${MAKE_JOBS}" build_libs
make install_dev
make distclean
