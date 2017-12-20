#!/bin/bash

# ENV variables to decide installation location
TUBE_VERSION=0.1-SNAPSHOT
INSTALLATION_DIR=/opt

# Installs OpenJDK 8
/usr/bin/sudo apt-get install -y default-jdk

# Installs 'unzip' utility
/usr/bin/sudo apt-get install -y unzip

# Creates the installation directory
/bin/mkdir -p $INSTALLATION_DIR

echo "About to install tube: ${TUBE_VERSION} to ${INSTALLATION_DIR}..."
/bin/tar xvfz $HOME/tube-${TUBE_VERSION}.tgz -C $INSTALLATION_DIR
echo "Installation done."

