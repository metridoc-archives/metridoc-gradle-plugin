#!/bin/sh

#don't need this in this project, but others might need this
#
#if grep -q "\-SNAPSHOT" "build.gradle"; then
#    echo "build file has SNAPSHOT in it, skipping release"
#    exit 0
#fi

if grep -q "\-SNAPSHOT" "VERSION"; then
    echo "VERSION file has SNAPSHOT in it, skipping release"
    exit 0
fi

echo ""
echo "Testing the application before releasing"
echo ""

#test before release
if ./gradlew test; then
    echo "all tests pass"
else
    exit $?
fi



#releases to github
PROJECT_VERSION=`cat VERSION`
echo ""
echo "Releasing ${PROJECT_VERSION} to GitHub"
echo ""
if git tag -a v${PROJECT_VERSION} -m"tagging release"; then
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
echo ""
echo "Releasing ${PROJECT_VERSION} to BinTray"
echo ""
if ./gradlew publishArchives bumpVersion; then
    echo "archives published"
else
    exit $?
fi