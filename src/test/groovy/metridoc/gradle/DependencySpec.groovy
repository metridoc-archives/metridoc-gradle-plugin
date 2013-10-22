/*
 * Copyright 2013 Trustees of the University of Pennsylvania Licensed under the
 * 	Educational Community License, Version 2.0 (the "License"); you may
 * 	not use this file except in compliance with the License. You may
 * 	obtain a copy of the License at
 *
 * http://www.osedu.org/licenses/ECL-2.0
 *
 * 	Unless required by applicable law or agreed to in writing,
 * 	software distributed under the License is distributed on an "AS IS"
 * 	BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * 	or implied. See the License for the specific language governing
 * 	permissions and limitations under the License.
 */



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
