#!/bin/bash
rm -rf /tmp/flutter_sdk
rm -f /tmp/flutter.tar.xz
wget https://storage.googleapis.com/flutter_infra_release/releases/stable/linux/flutter_linux_3.22.2-stable.tar.xz -O /tmp/flutter.tar.xz
mkdir /tmp/flutter_sdk
tar -xf /tmp/flutter.tar.xz -C /tmp/flutter_sdk
export PATH="$PATH:/tmp/flutter_sdk/flutter/bin"
cd calibre_flutter_app
flutter create .
