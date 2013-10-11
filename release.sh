#!/bin/sh

if grep -q "\-SNAPSHOT" "build.gradle"; then
    echo "build file has SNAPSHOT in it, skipping release"
    exit 0
fi

if grep -q "\-SNAPSHOT" "VERSION"; then
    echo "VERSION file has SNAPSHOT in it, skipping release"
    exit 0
fi

#test before release
if ./gradlew test; then
    echo "all tests pass"
else
    exit $?
fi

#releases to github
PROJECT_VERSION=`cat VERSION`
echo "releasing ${PROJECT_VERSION}"
if git tag -av${PROJECT_VERSION} -m"tagging release"; then
    echo "tagged project locally"
else
    exit $?
fi

if git push origin v${PROJECT_VERSION}; then
    echo "tagged project locally"
else
    exit $?
fi

#release to bintray
if ./gradlew publishArchives; then
    echo "archives published"
else
    exit $?
fi