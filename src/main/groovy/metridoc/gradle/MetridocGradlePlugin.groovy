package metridoc.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task

/**
 * Created with IntelliJ IDEA on 8/6/13
 * @author Tommy Barker
 */
class MetridocGradlePlugin implements Plugin<Project>{
    @Override
    void apply(Project project) {

        project.ext.set("metridocCore") {
            if(it) {
                return "com.github.metridoc:metridoc-job-core:$it"
            }
            else {
                return "com.github.metridoc:metridoc-job-core:0.7"
            }
        }
        project.ext.set("metridocRepo") {
            project.repositories.maven {
                url "http://jcenter.bintray.com/"
            }
            project.repositories.maven {
                url "http://dl.bintray.com/upennlib/metridoc"
            }
            project.buildscript {

            }
        }

    }
}

class MetridocGradlePluginExtension {
    boolean includeGorm = false
    boolean includeCore = true


}


