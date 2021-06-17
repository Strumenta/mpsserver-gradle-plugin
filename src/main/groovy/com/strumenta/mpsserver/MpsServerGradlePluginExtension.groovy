package com.strumenta.mpsserver

import org.gradle.api.GradleException

class MpsServerGradlePluginExtension {

    private class PluginsEntry {
        File dir;
        List<String> idsToExclude;
        PluginsEntry(File dir, List<String> idsToExclude) {
            this.dir = dir;
            this.idsToExclude = idsToExclude;
        }
    }

    String mpsVersion = '2020.3.3'
    String mpsServerVersion = '2020.3-12'
    String antVersion = '1.10.1'
    List<String> jvmArgs = []
    List<Object> additionalLibraries = []
    List<PluginConf> additionalPlugins = []
    List<PluginsEntry> additionalPluginsDirs = []
    File customMpsProjectPath = null
    boolean openNoProject = false
    boolean makeProject = false

    void addLibrary(String library) {
        additionalLibraries.add(library)
    }

    void addPluginDir(File dir) {
        addPluginDir(dir, Collections.emptyList())
    }

    void addPluginDir(File dir, List<String> idsToExclude) {
        additionalPluginsDirs.add(new PluginsEntry(dir, idsToExclude))
    }

    void addPlugin(String path, String id) {
        def pc = new PluginConf()
        pc.path = path
        pc.id = id
        additionalPlugins.add(pc)
    }

    void addLibraryDir(File dir) {
        additionalLibraries.add(dir)
    }

    File artifactsDir(project) {
        return new File(project.rootDir, 'artifacts')
    }

    File mpsDir(project) {
        return project.hasProperty('mpsPath') ? new File(project.property('mpsPath')) : new File(artifactsDir(project), 'mps')
    }

    File mpsServerCoreDir(project) {
        return new File("${artifactsDir(project).getAbsolutePath()}/mpsserver-core")
    }

    File mpsServerExtensionDir(project) {
        return new File("${artifactsDir(project).getAbsolutePath()}/mpsserver-extensionkit")
    }

    File mpsServerLauncherDir(project) {
        return new File("${artifactsDir(project).getAbsolutePath()}/mpsserver-launcher")
    }

    File mpsServerAntBuildScript(project) {
        return new File("${project.buildDir}/build-launcher.xml")
    }

    File mpsProjectPath(project) {
        if (openNoProject) {
            return null
        }
        if (customMpsProjectPath != null) {
            return customMpsProjectPath;
        }
        return new File("${project.rootDir}")
    }

    Object buildScriptClasspath(project) {
        return project.configurations.ant_lib.fileCollection({
            true
        }) + project.files("${jdkHome(project)}/lib/tools.jar")
    }

    Object antScriptArgs(project) {
        return [
                "-Dartifacts.mpsserver-core=${mpsServerCoreDir(project)}",
                "-Dartifacts.mpsserver-launcher=${mpsServerLauncherDir(project)}",
                "-Dartifacts.mpsserver-extensionkit=${mpsServerExtensionDir(project)}",
                "-Dartifacts.mps=${mpsDir(project)}",
                "-Dartifacts.root=${artifactsDir(project).getAbsolutePath()}",
        ]
    }

    File jdkHome(project) {
        if (!project.hasProperty("jdk_home")) {
            def java_home = System.properties['java.home']
            def jdk_home = java_home

            // In JDK >=11 we look for javac
            if (!project.file("$jdk_home/bin/javac").isFile() && !file("$jdk_home/bin/javac.exe").isFile()) {
                // In JDK <11 we look for the tools.jar
                if (!file("$jdk_home/lib/tools.jar").isFile()) {
                    jdk_home = jdk_home + "/.."
                }
                if (!file("$jdk_home/lib/tools.jar").isFile()) {
                    throw new GradleException("Not finding the JDK...")
                }
            }

            new File(jdk_home)
        } else {
            return new File(project.jdk_home)
        }
    }
}