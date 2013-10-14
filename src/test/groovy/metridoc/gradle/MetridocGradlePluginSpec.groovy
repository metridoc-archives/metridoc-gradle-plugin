package metridoc.gradle

import org.gradle.api.Project
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

/**
 * Created with IntelliJ IDEA on 10/9/13
 * @author Tommy Barker
 */
class MetridocGradlePluginSpec extends Specification {

    @Rule
    TemporaryFolder temporaryFolder = new TemporaryFolder()

    void "test that build files exist"() {
        when:
        def buildFile = temporaryFolder.newFile("buildFile.txt").path
        MetridocGradlePlugin.checkForFiles(buildFile)

        then:
        noExceptionThrown()

        when:
        MetridocGradlePlugin.checkForFiles("does/not/exist")

        then:
        thrown(AssertionError)

        when:
        def folder = temporaryFolder.newFolder("folder").path
        MetridocGradlePlugin.checkForFiles(folder)

        then:
        thrown(AssertionError)
    }

    void "test build Dependency from a line"() {
        given:
        def url = new URL("http://dl.bintray.com/upennlib/metridoc/com/github/metridoc/metridoc-job-core/maven-metadata.xml")
        String latest = new XmlSlurper().parse(url.newInputStream()).versioning.latest.text()

        when:
        String line = "com.github.metridoc:metridoc-job-core http://dl.bintray.com/upennlib/metridoc/com/github/metridoc/metridoc-job-core/maven-metadata.xml"
        Dependency dependency = MetridocGradlePlugin.getDependency(line)

        then:
        "http://dl.bintray.com/upennlib/metridoc/com/github/metridoc/metridoc-job-core/maven-metadata.xml" == dependency.url.toString()
        latest == dependency.latestVersion
        "com.github.metridoc:metridoc-job-core" == dependency.dependencyName
    }

    void "bump version test"() {
        given:
        def version = temporaryFolder.newFile("VERSION")
        version.write("1.3.1")

        when:
        MetridocGradlePlugin.bumpVersion(version)

        then:
        "1.3.2-SNAPSHOT" == version.text

        when:
        version.write("1.5")
        MetridocGradlePlugin.bumpVersion(version)

        then:
        thrown(AssertionError)

        when:
        version.write("1.5.0\n")
        MetridocGradlePlugin.bumpVersion(version)

        then: "new line should not matter"
        "1.5.1-SNAPSHOT" == version.text
    }

    void "getVersionTest"() {
        given:
        Project project = [
            getProjectDir: {
                temporaryFolder.root
            }
        ] as Project
        def versionFile = temporaryFolder.newFile("VERSION")
        versionFile.write("  0.1.1\n   ")

        when:
        def version = MetridocGradlePlugin.getVersion(project)

        then: "whitespace is removed"
        "0.1.1" == version
    }
}
