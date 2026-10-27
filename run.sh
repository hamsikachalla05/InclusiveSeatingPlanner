#!/usr/bin/env sh
# Build / run / test / package on macOS or Linux.  Usage: ./run.sh [build|test|package]
set -e
cd "$(dirname "$0")"

build() {
  rm -rf out
  javac -encoding UTF-8 -d out \
    src/com/seatingplanner/*.java \
    src/com/seatingplanner/model/*.java \
    src/com/seatingplanner/service/*.java \
    src/com/seatingplanner/ui/*.java
  echo "Build OK - classes in ./out"
}

case "${1:-run}" in
  build) build ;;
  test)
    build
    javac -encoding UTF-8 -cp out -d out test/com/seatingplanner/*.java
    java -cp out com.seatingplanner.SelfTest ;;
  package)
    build
    jar --create --file InclusiveSeatingPlanner.jar --main-class com.seatingplanner.App -C out .
    echo "Created InclusiveSeatingPlanner.jar" ;;
  *)
    build
    java -cp out com.seatingplanner.App ;;
esac
