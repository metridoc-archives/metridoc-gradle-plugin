package metridoc.gradle

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

    void "test searching for SNAPSHOT in a file"() {
        when:
        def hasSnapshotFile = temporaryFolder.newFile("hasSnapshot.txt")
        hasSnapshotFile.write("SNAPSHOT")
        boolean hasSnapshot = MetridocGradlePlugin.hasSNAPSHOT(hasSnapshotFile.path)

        then:
        hasSnapshot

        when:
        def noSnapshotFile = temporaryFolder.newFile("noSnapshot.txt")
        hasSnapshot = MetridocGradlePlugin.hasSNAPSHOT(noSnapshotFile.path)

        then:
        !hasSnapshot

        when:
        MetridocGradlePlugin.hasSNAPSHOT("/does/not/exist")

        then:
        thrown(AssertionError)
    }

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
}
