package com.strumenta.mpsserver

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.FileVisitDetails
import org.gradle.api.file.FileVisitor
import org.gradle.api.tasks.TaskAction

import java.nio.file.Files

class ResolveMpsArtifacts extends DefaultTask {

    void checkMpsArtifactsImpl() {
        def conf = project.configurations.getAll().find { it.name == 'mpsArtifacts' }
        def found = conf != null
        if (!found) {
            println("adding mpsArtifacts to configurations")
            project.configurations {
                mpsArtifacts
            }
            conf = project.configurations.getAll().find { it.name == 'mpsArtifacts' }
            found = conf != null
            if (!found) {
                throw new GradleException("no mpsArtifacts configuration")
            }
        }
        if (conf.getAllDependencies().isEmpty()) {
            println("no mpsArtifacts configuration dependency, adding default one")
            project.dependencies {
                mpsArtifacts "com.strumenta.mpsserver:mpsserver-core:${project.mpsserver.getMpsServerVersion()}"
                mpsArtifacts "com.strumenta.mpsserver:mpsserver-launcher:${project.mpsserver.getMpsServerVersion()}"
                mpsArtifacts "com.strumenta.mpsserver:mpsserver-extensionkit:${project.mpsserver.getMpsServerVersion()}"
            }
        } else {
            println("mpsArtifacts configuration is not empty, using existing values")
        }
    }

    @TaskAction
    def execute() {
        println("MpsServer > ResolveMpsArtifacts")
        checkMpsArtifactsImpl()
        def conf = project.configurations.getAll().find { it.name == 'mpsArtifacts' }
        def found = conf != null
        if (!found) {
            throw new GradleException("no mpsArtifacts configuration")
        }
        if (conf.resolve().size() == 0) {
            throw new GradleException('mpsArtifacts configuration present but empty')
        }
        def res = project.configurations.mpsArtifacts.resolve().collect { project.zipTree(it) }
        println("mpsArtifacts to solve: ${res.size()}")

        res.each {
            it.visit(new FileVisitor() {
                @Override
                void visitDir(FileVisitDetails fileVisitDetails) {
                }

                @Override
                void visitFile(FileVisitDetails fileVisitDetails) {
                    File dstFile = new File("${project.mpsserver.artifactsDir(project).getAbsolutePath()}${File.separator}${fileVisitDetails.relativePath}")
                    if (!dstFile.parentFile.exists()) {
                        dstFile.parentFile.mkdirs()
                    }
                    if (!dstFile.exists()) {
                        Files.copy(fileVisitDetails.file.toPath(), dstFile.toPath())
                    }
                }
            })
        }
    }
}
