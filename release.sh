#!/bin/sh

#don't need this in this project, but others might need this
#
#if grep -q "\-SNAPSHOT" "build.gradle"; then
#    echo "build file has SNAPSHOT in it, skipping release"
#    exit 0
#fi

systemCall() {
    echo "running $1"
    if eval $1; then
		echo "command ran"
	else
		echo "command failed"
		exit $?
	fi
}

if grep -q "\-SNAPSHOT" "VERSION"; then
    echo "VERSION file has SNAPSHOT in it, skipping release"
    exit 0
fi

echo ""
echo "Testing the application before releasing"
echo ""

systemCall "./gradlew test"

#releases to github
PROJECT_VERSION=`cat VERSION`
echo ""
echo "Releasing ${PROJECT_VERSION} to GitHub"
echo ""

systemCall "git tag -a v${PROJECT_VERSION} -m 'tagging release'"
systemCall "git push origin v${PROJECT_VERSION}"

#release to bintray
echo ""
echo "Releasing ${PROJECT_VERSION} to BinTray"
echo ""

systemCall "./gradlew publishArchives bumpVersion"
systemCall "git add VERSION"
systemCall "git commit -m 'committing a new version'"
systemCall "git push origin master"