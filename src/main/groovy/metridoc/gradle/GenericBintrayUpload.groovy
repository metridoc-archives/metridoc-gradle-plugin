package metridoc.gradle

import groovy.json.JsonSlurper
import org.gradle.api.*
import org.gradle.api.tasks.*

/**
 * Copied from the lazybones project
 *
 * Task for uploading artifacts to a generic Bintray repository.
 */
class GenericBintrayUpload extends DefaultTask {
    /** The location on the local filesystem of the artifact to publish. */
    @InputFile
    File artifactFile

    /**
     * The base URL of the Bintray repository to publish to. For example:
     * https://api.bintray.com/content/pledbrook/lazybones-templates
     */
    @Input
    String bintrayRepo

    /**
     * The username of the account to publish as. The account must of course
     * have permission to publish to the target repository.
     */
    String bintrayUsername

    /** The Bintray API key for the {@link username} account. */
    String bintrayPassword

    @TaskAction
    def publish() {

        boolean doUpload = !alreadyPublishedOrInvalid()

        if (doUpload) {
            logger.lifecycle "Streaming artifact to Bintray at URL ${bintrayRepo}"
            new URI(bintrayRepo).toURL().openConnection().with {
                // Add basic authentication header.
                setRequestProperty "Authorization", "Basic " + "$bintrayUsername:$bintrayPassword".getBytes().encodeBase64().toString()
                doOutput = true
                fixedLengthStreamingMode = artifactFile.size()
                requestMethod = "PUT"

                def inputStream = artifactFile.newInputStream()
                try {
                    outputStream << inputStream
                }
                finally {
                    inputStream.close()
                    outputStream.close()
                }

                assert responseCode >= 200 && responseCode < 300
            }

            def shouldPublish = project.hasProperty("publish") && Boolean.valueOf(project.properties.publish)
            if (shouldPublish) {
                def bintrayRepo = "https://api.bintray.com/content/upennlib/metridoc-distributions/" +
                        "${project.properties.archivesBaseName}/$project.version/publish"
                project.logger.info "publishing to $bintrayRepo"
                new URI(bintrayRepo).toURL().openConnection().with {
                    doOutput = true
                    doInput = true
                    // Add basic authentication header.
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
    }

    boolean alreadyPublishedOrInvalid() {
        if (!project.version || "unspecified" == project.version) {
            project.logger.warn "a project version is required to upload to bintray, skipping upload to bintray"
            return true
        }

        if (project.version.toString().contains("SNAPSHOT")) {
            project.logger.warn "bintray does not support SNAPSHOTs, skipping upload to bintray"
            return true
        }

        assert bintrayUsername && bintrayPassword : "bintray user name and / or password have not been set"


        def url = "https://api.bintray.com/packages/upennlib/metridoc-distributions/${project.properties.archivesBaseName}"
        println "checking url $url to see if ${project.version} has already been released}"
        def json = new URL(url).text
        def slurper = new JsonSlurper()
        def versions = slurper.parseText(json).versions
        def versionAlreadyDeployed = versions.contains(project.version.toString())

        if (versionAlreadyDeployed) {
            println "version $project.version has already been deployed to bintray, skipping upload to bintray"
            return true
        }

        return false
    }
}


