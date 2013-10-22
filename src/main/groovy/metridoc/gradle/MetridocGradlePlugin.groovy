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

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Upload
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.wrapper.Wrapper
import org.gradle.execution.taskgraph.DefaultTaskGraphExecuter

/**
 * Created with IntelliJ IDEA on 8/6/13
 * @author Tommy Barker
 */
class MetridocGradlePlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        addMavenSupportTo project
        addJavadocAndSourceArchivesTaskTo project
        addWrapperTaskTo project
        addBintrayTasksTo project
        addUpdateDependenciesTaskTo project
        addBumpVersionTaskTo project
    }

    static void addBumpVersionTaskTo(Project project) {
        project.task("bumpVersion") << {
            bumpVersion(getVersionFile(project))
        }
    }

    protected static String getVersion(Project project) {
        getVersionFile(project).text.trim()
    }

    protected static File getVersionFile(Project project) {
        new File(project.projectDir, "VERSION")
    }

    protected static void bumpVersion(File versionFile) {
        String version = versionFile.text.trim()
        def m = version =~ /^(\d+\.\d+)\.(\d+)$/
        assert m.matches(): "version $version is not in the [0.0.0] format"
        String majorVersionText = m.group(1)
        String minorVersionText = m.group(2)
        String newMinorVersionText = String.valueOf(Integer.valueOf(minorVersionText) + 1)
        String newVersion = "${majorVersionText}.${newMinorVersionText}-SNAPSHOT"
        versionFile.write(newVersion, "utf-8")
    }

    static void addUpdateDependenciesTaskTo(Project project) {
        project.task("updateDependencies") << {
            checkForFiles("build.gradle")
            updateDependencies(project)
        }
    }

    static void updateDependencies(Project project) {
        def dependencies = new File("DEPENDENCIES")
        if (!dependencies) {
            project.logger.warn "A DEPENDENCIES file was not found, will use the dependencies specified"
            return
        }

        dependencies.eachLine("utf-8") { String line ->
            Dependency dependency = getDependency(line)
            dependency.updateFile(project.buildFile)
        }
    }

    static Dependency getDependency(String line) {
        def dependencyName
        def metadataUrlPath
        (dependencyName, metadataUrlPath) = line.split(" ")

        assert dependencyName: "line [${line}] in DEPENDENCY file did not contain a dependencyName, eg " +
                "com.github.metridoc:metridoc-job-core"

        assert metadataUrlPath: "line [${line}] in DEPENDENCY file did not contain a dependencyName, eg " +
                "http://dl.bintray.com/upennlib/metridoc/com/github/metridoc/metridoc-job-core/maven-metadata.xml"

        URL metaDataUrl = new URL(metadataUrlPath)

        return new Dependency(dependencyName: dependencyName, url: metaDataUrl)
    }

    protected static void addBintrayTasksTo(Project project) {
        project.task("publishArchives", dependsOn: ["uploadArchives"]) << {
            def bintrayRepo = "https://api.bintray.com/content/upennlib/metridoc/" +
                    "${project.properties.archivesBaseName}/${getVersion(project)}/publish"
            project.logger.info "publishing to $bintrayRepo"
            new URI(bintrayRepo).toURL().openConnection().with {
                doOutput = true
                doInput = true
                // Add basic authentication header.
                def bintrayUsername = project.properties.bintrayUsername
                def bintrayPassword = project.properties.bintrayPassword
                setRequestProperty "Authorization", "Basic " + "$bintrayUsername:$bintrayPassword".getBytes().encodeBase64().toString()
                requestMethod = "POST"
                outputStream.flush()
                outputStream.close()
                project.logger.info inputStream.text
                inputStream.close()

                assert responseCode >= 200 && responseCode < 300
            }
        }

        // Lazy initialisation of generic upload task
        project.gradle.taskGraph.whenReady { DefaultTaskGraphExecuter graph ->
            graph.allTasks.each { task ->
                if (task instanceof GenericBintrayUpload) {
                    project.tasks.withType(GenericBintrayUpload).each { GenericBintrayUpload uploadDist ->
                        verifyAndSetProperty(project, task, 'bintrayUsername')
                        verifyAndSetProperty(project, task, 'bintrayPassword')
                        verifyAndSetProperty(project, task, 'bintrayRepo')
                    }
                }
            }
        }
    }

    protected static void addWrapperTaskTo(Project project) {
        project.task("wrapper", type: Wrapper) {
            gradleVersion = project.hasProperty("gradleWrapperVersion") ? project.gradleWrapperVersion : "1.7"
        }
    }

    protected static void addJavadocAndSourceArchivesTaskTo(Project project) {
        project.task("packageJavadoc", type: Jar, dependsOn: 'groovydoc') {
            from project.groovydoc.destinationDir
            classifier = 'javadoc'
        }

        project.task("packageSources", type: Jar, dependsOn: 'classes') {
            from project.sourceSets.main.allSource
            classifier = 'sources'
        }
    }

    protected static void addMavenSupportTo(Project project) {
        Upload installTask = project.tasks.withType(Upload).findByName('install');
        if (!installTask) {
            project.apply(plugin: "maven")
        }

        project.ext.set("enableMaven") { Closure configurePom ->
            project.uploadArchives {
                repositories {
                    mavenDeployer {
                        repository(
                                id: project.properties.mavenRepoId ?: "Metridoc bintray repo",
                                url: project.properties.mavenRepoUrl ?: "https://api.bintray.com/maven/upennlib/metridoc/${project.properties.archivesBaseName}",
                        ) {
                            authentication(userName: project.properties.bintrayUsername, password: project.properties.bintrayPassword)
                        }

                        if (configurePom) {
                            configurePom.clone().call(pom)
                        }
                    }
                }
            }

            project.install {
                repositories.mavenInstaller { installer ->
                    if (configurePom) {
                        configurePom.clone().call(pom)
                    }
                }
            }

            project.artifacts {
                archives(project.tasks.findByName("packageJavadoc")) {
                    type = 'javadoc'
                }

                archives(project.tasks.findByName("packageSources"))
            }
        }
    }

    static void verifyAndSetProperty(Project proj, GenericBintrayUpload upload, String name) {
        if (upload."$name") {
            return
        }
        if (!proj.hasProperty(name)) {
            throw new GradleException("You must define the project property '$name'")
        }
        upload."$name" = proj."$name"
    }

    static void checkForFiles(String... filesToFind) {
        filesToFind.each {
            def file = new File(it)
            assert file.exists(): "file $it does not exist"
            assert file.isFile(): "file $it is a directory"
        }
    }
}

class Dependency {
    URL url
    String dependencyName
    private String _latestVersion

    String getLatestVersion() {
        assert url: "url cannot be null"
        if (_latestVersion) return _latestVersion

        try {
            _latestVersion = new XmlSlurper().parse(url.newInputStream()).versioning.latest.text()
        }
        catch (Throwable throwable) {
            println "ERROR: Could not extract latest version from ${url}"
            throw throwable
        }

        return _latestVersion
    }

    void updateFile(String filePath) {
        updateFile(new File(filePath))
    }

    void updateFile(File filePath) {
        println "checking if ${dependencyName} version matches $latestVersion"

        def buildFileText = filePath.getText("utf-8")
        def hasDependency = buildFileText.contains("${dependencyName}:")
        if (hasDependency) {
            def pattern = /${dependencyName}:\d+\.\d+(\.\d+)?/
            def newText = buildFileText.replaceFirst(pattern, "${dependencyName}:$latestVersion")
            filePath.write(newText, "utf-8")
        }
        else {
            println "WARN: checked for ${dependencyName} dependency, but didn't find it"
        }
    }
}