package metridoc.gradle

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
    }
}


