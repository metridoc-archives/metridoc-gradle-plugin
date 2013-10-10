package metridoc.gradle

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

/**
 * Created with IntelliJ IDEA on 10/10/13
 * @author Tommy Barker
 */
class DependencySpec extends Specification {

    @Rule
    TemporaryFolder folder = new TemporaryFolder()

    void "test updating a files dependencies"() {
        given:
        def file = folder.newFile("foo")
        file.write("""
            compile 'com.github.metridoc:metridoc-job-core:0.1'
        """)
        def dependency = new Dependency(
                dependencyName: "com.github.metridoc:metridoc-job-core",
                url: new URL("http://dl.bintray.com/upennlib/metridoc/com/github/metridoc/metridoc-job-core/maven-metadata.xml")
        )

        when:
        dependency.updateFile(file.path)

        then:
        file.text.contains("com.github.metridoc:metridoc-job-core:${dependency.latestVersion}")
    }
}
