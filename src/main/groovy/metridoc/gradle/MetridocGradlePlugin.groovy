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
        addMavenSupportTo project
        addJavadocAndSourceArchivesTaskTo project
        addWrapperTaskTo project
        addCheckSnapshotTaskTo project
        addGitHubReleaseTo project
        addBintrayUploadTaskTo project
        addReleaseTaskTo project
        addUpdateDependenciesTaskTo project
    }

    static void addUpdateDependenciesTaskTo(Project project) {
        project.task("updateDependencies") << {
            checkForFiles("build.gradle")
            updateDependencies(project)
        }
    }

    static void updateDependencies(Project project) {
        def dependencies = new File("DEPENDENCIES")
        if(!dependencies) {
            project.logger.warn "A DEPENDENCIES file was not found, will use the dependencies specified"
            return
        }

        dependencies.eachLine("utf-8") {String line ->
            Dependency dependency = getDependency(line)
            dependency.updateFile(project.buildFile)
        }
    }

    static Dependency getDependency(String line) {
        def dependencyName
        def metadataUrlPath
        (dependencyName, metadataUrlPath) = line.split(" ")

        assert dependencyName : "line [${line}] in DEPENDENCY file did not contain a dependencyName, eg " +
                "com.github.metridoc:metridoc-job-core"

        assert metadataUrlPath : "line [${line}] in DEPENDENCY file did not contain a dependencyName, eg " +
                "http://dl.bintray.com/upennlib/metridoc/com/github/metridoc/metridoc-job-core/maven-metadata.xml"

        URL metaDataUrl = new URL(metadataUrlPath)

        return new Dependency(dependencyName: dependencyName, url: metaDataUrl)
    }

    protected static void addGitHubReleaseTo(Project project) {
        project.task("prepareForGitHubTagging") << {
            def archiveBaseName = project.properties.archivesBaseName
            def versionToSearch = "/v${project.version}\""
            def tagUrl = "https://api.github.com/repos/metridoc/${archiveBaseName}/tags"
            println "checking if version ${project.version} has already been released"
            boolean disable
            try {
                disable = new URL(tagUrl).text.contains(versionToSearch)
                if(disable) {
                    println "version ${project.version} as already been released"

                }
            }
            catch (FileNotFoundException ignored) {
                project.logger.warn "project tag url ${tagUrl} probably does not exist"
                disable = true
            }

            if (disable) {
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

    protected static void addBintrayUploadTaskTo(Project project) {
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

    protected static void addReleaseTaskTo(Project project) {
        project.task ("release", dependsOn: ["checkForSnapshots", "releaseToGitHub", "updateDependencies"])
    }

    protected static void addCheckSnapshotTaskTo(Project project) {
        project.task("checkForSnapshots") << {
            def files = ["VERSION", "build.gradle"] as String[]
            checkForFiles(files)
            if(hasSNAPSHOT(files)) {
                project.logger.warn "SNAPSHOTS were detected in either VERSION or build.gradle, unable to release"
                project.tasks.findByName("releaseToGitHub").enabled = false
            }
        }
    }

    static void checkForFiles(String... filesToFind) {
        filesToFind.each {
            def file = new File(it)
            assert file.exists() : "file $it does not exist"
            assert file.isFile() : "file $it is a directory"
        }
    }

    protected static boolean hasSNAPSHOT(String... filePaths) {
        boolean hasSnapshot = false
        filePaths.each {filePath ->
            def file = new File(filePath)
            assert file.exists(): "The file $filePath does not exist"

            if(file.getText("utf-8").contains("SNAPSHOT")) {
                hasSnapshot = true
            }
        }

        return hasSnapshot
    }
}

class Dependency {
    URL url
    String dependencyName
    private String _latestVersion

    String getLatestVersion() {
        assert url : "url cannot be null"
        if(_latestVersion) return _latestVersion

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