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
artifacts.  If a closure is provided, the pom will be passed so the build can add anything else required that the 
standard gradle / maven mapping doesn't handle

Project Tasks
-------------

* `GenericBintrayUpload` - used to upload to a generic bintray repo.  Handy for binaries contained in a zip file.  
the task requires the property `bintrayUsername`, `bintrayPassword` and `bintrayRepo` to be set

Examples
--------

Checkout the dummy project under the test folder located 
[here](https://github.com/metridoc/metridoc-gradle-plugin/tree/master/src/test/dummy)
