metridoc-gradle-plugin
======================

gradle plugin for common metridoc build tasks

Installing
----------
```groovy
buildscript {
    repositories {
        maven {
            url = "http://dl.bintray.com/upennlib/metridoc"
        }
    }

    dependencies {
        classpath "com.github.metridoc:metridoc-gradle-plugin:<version>"
    }
}

apply plugin: "metridoc"
```

Although the plugin depends on the `maven` plugin, it will install it if it hasn't already been installed.  You can see 
the available versions [here](https://bintray.com/upennlib/metridoc/metridoc-gradle-plugin).

Project Extensions
------------------

* `metridocJobCore([version])` - returns the dependency coordinate for the metridoc-job-core library.  If a version is
not povided, then the latest version is provided.  So `metridocJobCore()` becomes 
`com.github.metridoc:metridoc-job-core:latest.integration` and `metridocJobCore(0.1)` becomes 
`com.github.metridoc:metridoc-job-core:0.1`
* `enableMaven([closure])` - enables maven related tools.  Configures the project to create source, docs and binary 
artifacts.  
