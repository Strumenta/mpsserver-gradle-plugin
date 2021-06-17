package com.strumenta.mpsserver

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.FileVisitDetails
import org.gradle.api.file.FileVisitor
import org.gradle.api.tasks.TaskAction

import java.nio.file.Files

class ResolveMps extends DefaultTask {

    void checkMpsImpl() {
        println "Downloading MPS in ${project.mpsserver.mpsDir(project).getAbsolutePath()}"
        def mpsConf = project.configurations.getAll().find { it.name == 'mps' }
        def mpsFound = mpsConf != null
        if (!mpsFound) {
            println("adding mps to configurations")
            project.configurations {
                mps
            }
            mpsConf = project.configurations.getAll().find { it.name == 'mps' }
            mpsFound = mpsConf != null
            if (!mpsFound) {
                throw new GradleException("no mps configuration")
            }
        }
        if (mpsConf.getAllDependencies().isEmpty()) {
            println("no mps configuration dependency, adding default one")
            project.dependencies {
                mps "com.jetbrains:mps:${project.mpsserver.mpsVersion}"
            }
        }
    }

    @TaskAction
    def execute() {
        println("MpsServer > ResolveMps")
        if (!project.hasProperty('mpsPath')) {
            println "Downloading MPS in ${project.mpsserver.mpsDir(project).getAbsolutePath()}"
            checkMpsImpl()
            def mpsConf = project.configurations.getAll().find { it.name == 'mps' }
            def mpsFound = mpsConf != null
            if (!mpsFound) {
                throw new GradleException("no mps configuration")
            }
            if (mpsConf.resolve().size() == 0) {
                throw new GradleException('mps configuration present but empty')
            }
            def res = project.configurations.mps.resolve().collect { project.zipTree(it) }
            res.each {
                it.visit(new FileVisitor() {
                    @Override
                    void visitDir(FileVisitDetails fileVisitDetails) {
                    }

                    @Override
                    void visitFile(FileVisitDetails fileVisitDetails) {
                        File dstFile = new File("${project.mpsserver.mpsDir(project).getAbsolutePath()}/${fileVisitDetails.relativePath}")
                        if (!dstFile.parentFile.exists()) {
                            dstFile.parentFile.mkdirs()
                        }
                        if (!dstFile.exists()) {
                            Files.copy(fileVisitDetails.file.toPath(), dstFile.toPath())
                        }
                    }
                })
            }
        } else {
            println "MPS already installed in ${project.mpsserver.mpsDir(project).getAbsolutePath()}"
        }
    }
}


