package metridoc.gradle

import groovy.json.JsonSlurper
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Exec
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
        enableMavenSupport(project)
        enableJavadocAndSourceArchives(project)
        enableDependencyShortCuts(project)
        enableWrapper(project)
        enableBintrayUpload(project)
        enableMetridocJobCoreDepUpdate(project)
        enableMetridocToolGormDepUpdate(project)
        enableMetridocGradlePluginDepUpdate(project)
        enableGitHubRelease(project)
    }

    protected void enableGitHubRelease(Project project) {
        project.task("prepareForGitHubTagging") {
            def archiveBaseName = project.properties.archivesBaseName
            def versionToSearch = "/v${project.version}\""
            def tagUrl = "https://api.github.com/repos/metridoc/${archiveBaseName}/tags"
            println "checking if version ${project.version} has already been released"
            boolean alreadyExists = new URL(tagUrl).text.contains(versionToSearch)
            if(alreadyExists) {
                println "version ${project.version} as already been released"
                def tagRepoLocally = project.tasks.findByName("tagRepoLocally")
                def releaseToGithub = project.tasks.findByName("tagRepoRemotely")
                def tasks = [tagRepoLocally, releaseToGithub]
                tasks*.enabled = false
            }
        }

        project.task(type: Exec, dependsOn: "prepareForGitHubTagging", "tagRepoLocally")  {
            commandLine 'git', 'tag', '-a', "v${project.version}", '-m', "'releasing ${project.version} to github'"
            //in case the tag was already made
            ignoreExitValue = true
        }

        project.task(type: Exec, dependsOn: "tagRepoLocally", "tagRepoRemotely") {
            commandLine 'git', 'push', 'origin', "v${project.version}"
        }

        project.task("releaseToGitHub", dependsOn: ["prepareForGitHubTagging", "tagRepoLocally", "tagRepoRemotely"])
    }

    protected void enableMetridocToolGormDepUpdate(Project project) {
        project.task("updateMetridocToolGormVersion") << {
            updateDependencyHelper(project, "metridoc-tool-gorm")
        }
    }

    protected void enableMetridocJobCoreDepUpdate(Project project) {
        project.task("updateMetridocJobCoreVersion") << {
            updateDependencyHelper(project, "metridoc-job-core")
        }
    }

    protected void enableMetridocGradlePluginDepUpdate(Project project) {
        project.task("updateMetridocGradlePluginVersion") << {
            updateDependencyHelper(project, "metridoc-gradle-plugin")
        }
    }

    protected static void updateDependencyHelper(Project project, String dependency) {
        def url = new URL("http://dl.bintray.com/upennlib/metridoc/com/github/metridoc/${dependency}/maven-metadata.xml")
        def xml = new XmlSlurper().parse(url.newInputStream())
        def latestVersion = xml.versioning.latest.text()
        println "checking if ${dependency} version matches $latestVersion"

        def buildFile = project.buildFile
        def buildFileText = buildFile.getText("utf-8")
        def hasDependency = buildFileText.contains("com.github.metridoc:${dependency}:")
        if (hasDependency) {
            def pattern = /${dependency}:\d+\.\d+(\.\d+)?/
            def newText = buildFileText.replaceFirst(pattern, "${dependency}:$latestVersion")
            buildFile.write(newText, "utf-8")
        }
        else {
            project.logger.warn "checked for ${dependency} dependency, but didn't find it"
        }
    }

    protected void enableBintrayUpload(Project project) {
        project.task("uploadToBintray", dependsOn: ["prepareForBintrayUpload", "uploadArchives", "publishArtifacts"])
        project.task("prepareForBintrayUpload") << {

            def uploadArchives = project.tasks.findByName("uploadArchives")
            def publishArtifacts = project.tasks.findByName("publishArtifacts")
            def tasks = [uploadArchives, publishArtifacts]

            if (!project.version || "unspecified" == project.version) {
                project.logger.warn "a project version is required to upload to bintray, [uploadToBintray] task has been skipped"
                tasks*.enabled = false
                return
            }

            if (project.version.toString().contains("SNAPSHOT")) {
                println "bintray does not support SNAPSHOTs, skipping upload to bintray"
                tasks*.enabled = false
                return
            }

            if (!project.hasProperty("bintrayUsername") || !project.hasProperty("bintrayPassword")) {
                println "bintray credentials not setup, skipping upload to bintray"
                tasks*.enabled = false
                return
            }

            def json = new URL("https://api.bintray.com/packages/upennlib/metridoc/${project.properties.archivesBaseName}").text
            def slurper = new JsonSlurper()
            def versions = slurper.parseText(json).versions
            def versionAlreadyDeployed = versions.contains(project.version.toString())

            if (versionAlreadyDeployed) {
                println "version $project.version has already been deployed to bintray, skipping upload to bintray"
                tasks*.enabled = false
            }
        }
        def uploadArchives = project.tasks.findByName("uploadArchives")

        uploadArchives.dependsOn("prepareForBintrayUpload")

        project.task("publishArtifacts", dependsOn: ["prepareForBintrayUpload", "uploadArchives"]) << {
            def shouldPublish = project.hasProperty("publish") && Boolean.valueOf(project.properties.publish)
            if (shouldPublish) {
                def bintrayRepo = "https://api.bintray.com/content/upennlib/metridoc/" +
                        "${project.properties.archivesBaseName}/$project.version/publish"
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
            else {
                project.logger.warn "artifacts may have been uploaded, but they have not been published"
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

    protected void enableWrapper(Project project) {
        project.task("wrapper", type: Wrapper) {
            gradleVersion = project.hasProperty("gradleWrapperVersion") ? project.gradleWrapperVersion : "1.7"
        }
    }

    protected void enableDependencyShortCuts(Project project) {
        project.ext.set("metridocJobCore") {
            if (it) {
                return "com.github.metridoc:metridoc-job-core:$it"
            }
            else {
                return 'com.github.metridoc:metridoc-job-core:latest.integration'
            }
        }

        project.ext.set("metridocToolGorm") {
            if (it) {
                return "com.github.metridoc:metridoc-tool-gorm:$it"
            }
            else {
                return 'com.github.metridoc:metridoc-tool-gorm:latest.integration'
            }
        }
    }

    protected void enableJavadocAndSourceArchives(Project project) {
        project.task("packageJavadoc", type: Jar, dependsOn: 'groovydoc') {
            from project.groovydoc.destinationDir
            classifier = 'javadoc'
        }

        project.task("packageSources", type: Jar, dependsOn: 'classes') {
            from project.sourceSets.main.allSource
            classifier = 'sources'
        }
    }

    protected void enableMavenSupport(Project project) {
        Upload installTask = project.tasks.withType(Upload).findByName('install');
        if (!installTask) {
            project.apply(plugin: "maven")
            installTask = project.tasks.withType(Upload).findByName('install');
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
}