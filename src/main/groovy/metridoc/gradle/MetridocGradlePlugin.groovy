package metridoc.gradle

import groovy.json.JsonSlurper
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Upload
import org.gradle.api.tasks.bundling.Jar

/**
 * Created with IntelliJ IDEA on 8/6/13
 * @author Tommy Barker
 */
class MetridocGradlePlugin implements Plugin<Project>{
    @Override
    void apply(Project project) {
        Upload installTask = project.tasks.withType(Upload).findByName('install');
        if(!installTask) {
            project.apply(plugin: "maven")
            installTask = project.tasks.withType(Upload).findByName('install');
            println installTask
        }

        project.ext.set("enableMaven") {Closure configurePom ->
            project.uploadArchives{
                repositories {
                    mavenDeployer {
                        repository(
                                id: project.properties.mavenRepoId ?: "Metridoc bintray repo",
                                url: project.properties.mavenRepoUrl ?: "https://api.bintray.com/maven/upennlib/metridoc/metridoc-job-core",
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

        project.task("packageJavadoc", type: Jar, dependsOn: 'groovydoc') {
            from project.groovydoc.destinationDir
            classifier = 'javadoc'
        }

        project.task("packageSources", type: Jar, dependsOn: 'classes') {
            from project.sourceSets.main.allSource
            classifier = 'sources'
        }

        project.ext.set("metridocJobCore") {
            if(it) {
                return "com.github.metridoc:metridoc-job-core:$it"
            }
            else {
                return 'com.github.metridoc:metridoc-job-core:latest.integration'
            }
        }

        project.ext.set("metridocToolGorm") {
            if(it) {
                return "com.github.metridoc:metridoc-tool-gorm:$it"
            }
            else {
                return 'com.github.metridoc:metridoc-tool-gorm:latest.integration'
            }
        }

        project.task("uploadToBintray", dependsOn: ["prepareForBintrayUpload", "uploadArchives"])

        project.task("prepareForBintrayUpload") << {

            def uploadArchives = project.tasks.findByName("uploadArchives")

            if(!project.version || "unspecified" == project.version) {
                project.logger.warn "a project version is required to upload to bintray, [uploadToBintray] task has been skipped"
                uploadArchives.enabled = false
                return
            }

            if (project.version.toString().contains("SNAPSHOT")) {
                println "bintray does not support SNAPSHOTs, skipping upload to bintray"
                uploadArchives.enabled = false
                return
            }

            if (!project.hasProperty("bintrayUsername") ||  !project.hasProperty("bintrayPassword")) {
                println "bintray credentials not setup, skipping upload to bintray"
                uploadArchives.enabled = false
                return
            }

            def json = new URL("https://api.bintray.com/packages/upennlib/metridoc/metridoc-job-core").text
            def slurper = new JsonSlurper()
            def versions = slurper.parseText(json).versions
            def versionAlreadyDeployed = versions.contains(project.version.toString())

            if (versionAlreadyDeployed) {
                println "version $project.version has already been deployed to bintray, skipping upload to bintray"
                uploadArchives.enabled = false
            }
        }
    }
}