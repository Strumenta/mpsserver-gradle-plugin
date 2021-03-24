package com.strumenta.mpsserver

import groovy.xml.MarkupBuilder
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
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
				mpsArtifacts "com.strumenta.mpsserver:mpsserver-core:${project.mpsserver.mpsServerVersion}"
				mpsArtifacts "com.strumenta.mpsserver:mpsserver-launcher:${project.mpsserver.mpsServerVersion}"
				mpsArtifacts "com.strumenta.mpsserver:mpsserver-extensionkit:${project.mpsserver.mpsServerVersion}"
			}
		} else {
			println("mpsArtifacts configuration is not empty, using existing values")
		}
	}

	@TaskAction
	def execute() {
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
					File dstFile = new File("${project.mpsserver.artifactsDir(project).getAbsolutePath()}/${fileVisitDetails.relativePath}")
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

class MpsServerGradlePluginExtension {
    String mpsVersion = '2019.3.1'
	String mpsServerVersion = '2019.3.9'
	String antVersion = '1.10.1'
	File customMpsProjectPath = null
	
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

class MpsServerGradlePlugin implements Plugin<Project> {

	void checkAntLibImpl(project) {
		def antLibConf = project.configurations.getAll().find { it.name == 'ant_lib' }
		def antLibFound = antLibConf != null
		if (!antLibFound) {
			println("adding ant_lib to configurations")
			project.configurations {
				ant_lib
			}
			antLibConf = project.configurations.getAll().find { it.name == 'ant_lib' }
			antLibFound = antLibConf != null
			if (!antLibFound) {
				throw new GradleException("no ant_lib configuration")
			}
		}
		if (antLibConf.getAllDependencies().isEmpty()) {
			println("no ant_lib configuration dependency, adding default one")
			project.dependencies {
				ant_lib "org.apache.ant:ant-junit:${project.mpsserver.antVersion}"
			}
		}
	}

	void checkMpsImpl(project) {
		if (!project.hasProperty('mpsPath')) {
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
	}

    void apply(Project project) {

		def extension = project.extensions.create('mpsserver', MpsServerGradlePluginExtension)

		println("<Applying MpsServer plugin to project>")
		project.task('checkAntLib') {
			doLast {
				checkAntLibImpl(project)
			}
		}

		project.task('resolveMps', type: ResolveMps)
		project.task('resolveMpsArtifacts', type: ResolveMpsArtifacts)
        project.task('setuplocal') {
        	dependsOn project.resolveMps, project.resolveMpsArtifacts
        }
		project.task('generateAntBuildForMpsServer') {
			dependsOn project.resolveMps, project.resolveMpsArtifacts
			doLast { 
			    if (!project.buildDir.exists()){
			        project.buildDir.mkdir();
			    }
				project.mpsserver.mpsServerAntBuildScript(project).withWriter { writer ->
					def xml = new MarkupBuilder(new IndentPrinter(writer, "    ", true))
					def mpsDir = project.mpsserver.mpsDir(project)
					def mpsServerCoreDir = project.mpsserver.mpsServerCoreDir(project)
					def mpsServerExtensionDir = project.mpsserver.mpsServerExtensionDir(project)
					def mpsServerLauncherDir = project.mpsserver.mpsServerLauncherDir(project)
					xml.doubleQuotes = true
					xml.project(name:"mpsserver-launcher", default:"run.com.strumenta.mpsserver.launcher") {
						path(id:'path.mps.ant.path') {
				  			pathelement(location:"${mpsDir.getAbsolutePath()}/lib/ant/lib/ant-mps.jar")
				  			pathelement(location:"${mpsDir.getAbsolutePath()}/lib/jdom.jar")
				  			pathelement(location:"${mpsDir.getAbsolutePath()}/lib/log4j.jar")
						}
						target(name: 'declare-mps-tasks') {				
						 	taskdef(resource:'jetbrains/mps/build/ant/antlib.xml', classpathref: 'path.mps.ant.path')
						}
						target(name: 'run.com.strumenta.mpsserver.launcher', depends: 'declare-mps-tasks') {
							echo(message:"Running code from com.strumenta.mpsserver.launcher solution in MPS")
							runMPS(solution:"3228605e-7b74-4057-911c-041cdcc16947(com.strumenta.mpsserver.launcher)", 
									startClass:"com.strumenta.mpsserver.launcher.MainClass", startMethod:"mpsMain") {

								plugin( path:"${mpsServerCoreDir}/mpsserver.core.plugin", id:"mpsserver.core.plugin")
								plugin( path:"${mpsDir.getAbsolutePath()}/plugins/mps-build", id:"jetbrains.mps.build")
								plugin( path:"${mpsDir.getAbsolutePath()}/plugins/mps-core", id:"jetbrains.mps.core")
								plugin( path:"${mpsDir.getAbsolutePath()}/plugins/mps-testing", id:"jetbrains.mps.testing")

								library(file:"${mpsServerCoreDir}/mpsserver.core.plugin/languages/mpsserver.core.group/com.strumenta.mpsserver.deps.jar")
								library(file:"${mpsServerCoreDir}/mpsserver.core.plugin/languages/mpsserver.core.group/com.strumenta.mpsserver.server.jar")
								library(file:"${mpsServerExtensionDir}/mpsserver.extensionkit.plugin/languages/mpsserver.extensionkit.group/com.strumenta.mpsserver.extensionkit.jar")

								library(file:"${mpsDir.getAbsolutePath()}/languages/baseLanguage/closures.runtime.jar")
								library(file:"${mpsDir.getAbsolutePath()}/languages/baseLanguage/collections.runtime.jar")
								library(file:"${mpsDir.getAbsolutePath()}/languages/baseLanguage/jetbrains.mps.baseLanguage.blTypes.jar")
								library(file:"${mpsDir.getAbsolutePath()}/languages/baseLanguage/jetbrains.mps.baseLanguage.classifiers.jar")
								library(file:"${mpsDir.getAbsolutePath()}/languages/baseLanguage/jetbrains.mps.baseLanguage.closures.jar")
								library(file:"${mpsDir.getAbsolutePath()}/languages/baseLanguage/jetbrains.mps.baseLanguage.collections.jar")
								library(file:"${mpsDir.getAbsolutePath()}/languages/baseLanguage/jetbrains.mps.baseLanguage.jar")
								library(file:"${mpsDir.getAbsolutePath()}/languages/baseLanguage/jetbrains.mps.baseLanguage.javadoc.jar")
								library(file:"${mpsDir.getAbsolutePath()}/languages/baseLanguage/jetbrains.mps.baseLanguage.jdk7.jar")
								library(file:"${mpsDir.getAbsolutePath()}/languages/baseLanguage/jetbrains.mps.baseLanguage.jdk8.jar")
								library(file:"${mpsDir.getAbsolutePath()}/languages/baseLanguage/jetbrains.mps.baseLanguage.logging.jar")
								library(file:"${mpsDir.getAbsolutePath()}/languages/baseLanguage/jetbrains.mps.baseLanguage.logging.runtime.jar")
								library(file:"${mpsDir.getAbsolutePath()}/languages/baseLanguage/jetbrains.mps.baseLanguage.references.runtime.jar")
								library(file:"${mpsDir.getAbsolutePath()}/languages/baseLanguage/jetbrains.mps.baseLanguage.regexp.jar")
								library(file:"${mpsDir.getAbsolutePath()}/languages/baseLanguage/jetbrains.mps.baseLanguage.regexp.runtime.jar")
								library(file:"${mpsDir.getAbsolutePath()}/languages/baseLanguage/jetbrains.mps.baseLanguage.scopes.jar")
								library(file:"${mpsDir.getAbsolutePath()}/languages/baseLanguage/jetbrains.mps.baseLanguage.tuples.jar")
								library(file:"${mpsDir.getAbsolutePath()}/languages/baseLanguage/jetbrains.mps.baseLanguage.tuples.runtime.jar")
								library(file:"${mpsDir.getAbsolutePath()}/languages/baseLanguage/jetbrains.mps.baseLanguageInternal.jar")
								library(file:"${mpsDir.getAbsolutePath()}/languages/editor/jetbrains.mps.editor.runtime.jar")
								library(file:"${mpsDir.getAbsolutePath()}/languages/editor/jetbrains.mps.editorlang.runtime.jar")

								library(file:"${mpsDir.getAbsolutePath()}/languages/languageDesign/jetbrains.mps.baseLanguage.lightweightdsl.jar")
								library(file:"${mpsDir.getAbsolutePath()}/languages/languageDesign/jetbrains.mps.lang.access.jar")
								library(file:"${mpsDir.getAbsolutePath()}/languages/languageDesign/jetbrains.mps.lang.actions.jar")
								library(file:"${mpsDir.getAbsolutePath()}/languages/languageDesign/jetbrains.mps.lang.behavior.jar")
								library(file:"${mpsDir.getAbsolutePath()}/languages/languageDesign/jetbrains.mps.lang.checkedName.jar")
								library(file:"${mpsDir.getAbsolutePath()}/languages/languageDesign/jetbrains.mps.lang.constraints.jar")
								library(file:"${mpsDir.getAbsolutePath()}/languages/languageDesign/jetbrains.mps.lang.constraints.rules.jar")
								library(file:"${mpsDir.getAbsolutePath()}/languages/languageDesign/jetbrains.mps.lang.constraints.rules.kinds.jar")
								library(file:"${mpsDir.getAbsolutePath()}/languages/languageDesign/jetbrains.mps.lang.constraints.rules.skeleton.jar")
								library(file:"${mpsDir.getAbsolutePath()}/languages/languageDesign/jetbrains.mps.lang.context.defs.jar")
								library(file:"${mpsDir.getAbsolutePath()}/languages/languageDesign/jetbrains.mps.lang.context.jar")
								library(file:"${mpsDir.getAbsolutePath()}/languages/languageDesign/jetbrains.mps.lang.core.jar")
								library(file:"${mpsDir.getAbsolutePath()}/languages/languageDesign/jetbrains.mps.lang.dataFlow.jar")
								library(file:"${mpsDir.getAbsolutePath()}/languages/languageDesign/jetbrains.mps.lang.editor.jar")
								library(file:"${mpsDir.getAbsolutePath()}/languages/languageDesign/jetbrains.mps.lang.findUsages.jar")
								library(file:"${mpsDir.getAbsolutePath()}/languages/languageDesign/jetbrains.mps.lang.generator.generationContext.jar")
								library(file:"${mpsDir.getAbsolutePath()}/languages/languageDesign/jetbrains.mps.lang.generator.jar")
								library(file:"${mpsDir.getAbsolutePath()}/languages/languageDesign/jetbrains.mps.lang.intentions.jar")
								library(file:"${mpsDir.getAbsolutePath()}/languages/languageDesign/jetbrains.mps.lang.migration.jar")
								library(file:"${mpsDir.getAbsolutePath()}/languages/languageDesign/jetbrains.mps.lang.modelapi.jar")
								library(file:"${mpsDir.getAbsolutePath()}/languages/languageDesign/jetbrains.mps.lang.pattern.jar")
								library(file:"${mpsDir.getAbsolutePath()}/languages/languageDesign/jetbrains.mps.lang.plugin.jar")
								library(file:"${mpsDir.getAbsolutePath()}/languages/languageDesign/jetbrains.mps.lang.project.jar")
								library(file:"${mpsDir.getAbsolutePath()}/languages/languageDesign/jetbrains.mps.lang.quotation.jar")
								library(file:"${mpsDir.getAbsolutePath()}/languages/languageDesign/jetbrains.mps.lang.refactoring.jar")
								library(file:"${mpsDir.getAbsolutePath()}/languages/languageDesign/jetbrains.mps.lang.resources.jar")
								library(file:"${mpsDir.getAbsolutePath()}/languages/languageDesign/jetbrains.mps.lang.scopes.runtime.jar")
								library(file:"${mpsDir.getAbsolutePath()}/languages/languageDesign/jetbrains.mps.lang.script.jar")
								library(file:"${mpsDir.getAbsolutePath()}/languages/languageDesign/jetbrains.mps.lang.sharedConcepts.jar")
								library(file:"${mpsDir.getAbsolutePath()}/languages/languageDesign/jetbrains.mps.lang.smodel.jar")
								library(file:"${mpsDir.getAbsolutePath()}/languages/languageDesign/jetbrains.mps.lang.smodel.query.jar")
								library(file:"${mpsDir.getAbsolutePath()}/languages/languageDesign/jetbrains.mps.lang.structure.jar")
								library(file:"${mpsDir.getAbsolutePath()}/languages/languageDesign/jetbrains.mps.lang.textGen.jar")
								library(file:"${mpsDir.getAbsolutePath()}/languages/languageDesign/jetbrains.mps.lang.traceable.jar")
								library(file:"${mpsDir.getAbsolutePath()}/languages/languageDesign/jetbrains.mps.lang.typesystem.jar")
								library(file:"${mpsDir.getAbsolutePath()}/languages/languageDesign/jetbrains.mps.refactoring.participant.jar")
								library(file:"${mpsDir.getAbsolutePath()}/languages/languageDesign/jetbrains.mps.typesystemEngine.jar")

								library(file:"${mpsDir.getAbsolutePath()}/languages/make/jetbrains.mps.make.facets.jar")
								library(file:"${mpsDir.getAbsolutePath()}/languages/make/jetbrains.mps.make.runtime.jar")
								library(file:"${mpsDir.getAbsolutePath()}/languages/make/jetbrains.mps.smodel.resources.jar")
								library(file:"${mpsDir.getAbsolutePath()}/languages/mps-stubs.jar")
								library(file:"${mpsDir.getAbsolutePath()}/languages/plaf/jetbrains.mps.baseLanguage.search.jar")
								library(file:"${mpsDir.getAbsolutePath()}/languages/plaf/jetbrains.mps.baseLanguage.util.jar")
								library(file:"${mpsDir.getAbsolutePath()}/languages/plaf/jetbrains.mps.ide.platform.jar")
								library(file:"${mpsDir.getAbsolutePath()}/languages/plaf/jetbrains.mps.ide.refactoring.platform.jar")
								library(file:"${mpsDir.getAbsolutePath()}/languages/runtimes/jetbrains.mps.analyzers.runtime.jar")
								library(file:"${mpsDir.getAbsolutePath()}/languages/runtimes/jetbrains.mps.dataFlow.runtime.jar")
								library(file:"${mpsDir.getAbsolutePath()}/languages/runtimes/jetbrains.mps.findUsages.runtime.jar")
								library(file:"${mpsDir.getAbsolutePath()}/languages/runtimes/jetbrains.mps.lang.behavior.api.jar")
								library(file:"${mpsDir.getAbsolutePath()}/languages/runtimes/jetbrains.mps.lang.behavior.runtime.jar")
								library(file:"${mpsDir.getAbsolutePath()}/languages/runtimes/jetbrains.mps.lang.feedback.context.jar")
								library(file:"${mpsDir.getAbsolutePath()}/languages/runtimes/jetbrains.mps.lang.migration.runtime.jar")
								library(file:"${mpsDir.getAbsolutePath()}/languages/runtimes/jetbrains.mps.lang.smodel.query.runtime.jar")
								library(file:"${mpsDir.getAbsolutePath()}/languages/runtimes/jetbrains.mps.refactoring.runtime.jar")

								library(file:"${mpsDir.getAbsolutePath()}/languages/text/jetbrains.mps.lang.text.jar")
								library(file:"${mpsDir.getAbsolutePath()}/languages/tools/jetbrains.mps.core.tool.environment.jar")
								library(file:"${mpsDir.getAbsolutePath()}/languages/tools/jetbrains.mps.tool.common.jar")
								library(file:"${mpsDir.getAbsolutePath()}/languages/util/jetbrains.mps.java.stub.jar")
								library(file:"${mpsDir.getAbsolutePath()}/languages/util/jetbrains.mps.kernel.jar")
								library(file:"${mpsDir.getAbsolutePath()}/languages/util/jetbrains.mps.project.jar")
								library(file:"${mpsDir.getAbsolutePath()}/languages/util/jetbrains.mps.refactoring.jar")
								library(file:"${mpsDir.getAbsolutePath()}/languages/util/jetbrains.mps.runtime.jar")
								library(file:"${mpsDir.getAbsolutePath()}/plugins/mps-testing/languages/baseLanguage/jetbrains.mps.baseLanguage.unitTest.jar")
								library(file:"${mpsDir.getAbsolutePath()}/plugins/mps-testing/languages/languageDesign/jetbrains.mps.lang.test.jar")
								library(file:"${mpsDir.getAbsolutePath()}/plugins/mps-testing/languages/languageDesign/jetbrains.mps.lang.test.matcher.jar")
								library(file:"${mpsDir.getAbsolutePath()}/plugins/mps-testing/languages/languageDesign/jetbrains.mps.lang.test.runtime.jar")
								library(file:"${mpsServerLauncherDir}/com.strumenta.mpsserver.launcher.jar")

								library(file:"${mpsDir.getAbsolutePath()}/plugins/mps-httpsupport/solutions/jetbrains.mps.ide.httpsupport.runtime.jar")
								library(file:"${mpsDir.getAbsolutePath()}/plugins/mps-httpsupport/solutions/jetbrains.mps.ide.httpsupport.nodeaccess.jar")
								library(file:"${mpsDir.getAbsolutePath()}/plugins/mps-httpsupport/languages/jetbrains.mps.ide.httpsupport.jar")
								library(file:"${mpsDir.getAbsolutePath()}/plugins/mps-httpsupport/lib/httpsupport.jar")
								library(file:"${mpsDir.getAbsolutePath()}/plugins/mps-vcs/lib/jetbrains.mps.ide.vcs.platform.jar")
								library(file:"${mpsDir.getAbsolutePath()}/plugins/mps-vcs/lib/jetbrains.mps.ide.vcs.core.jar")
								library(file:"${mpsDir.getAbsolutePath()}/plugins/mps-vcs/lib/jetbrains.mps.ide.vcs.jar")
								library(file:"${mpsDir.getAbsolutePath()}/plugins/mps-vcs/lib/vcs-ide.jar")
								library(file:"${mpsDir.getAbsolutePath()}/plugins/mps-vcs/lib/vcs-platform.jar")
								library(file:"${mpsDir.getAbsolutePath()}/plugins/mps-vcs/lib/jetbrains.mps.vcs.mergehints.runtime.jar")
								library(file:"${mpsDir.getAbsolutePath()}/plugins/mps-vcs/languages/jetbrains.mps.ide.vcs.modelmetadata.jar")
								library(file:"${mpsDir.getAbsolutePath()}/plugins/mps-vcs/languages/jetbrains.mps.vcs.mergehints-generator.jar")
								library(file:"${mpsDir.getAbsolutePath()}/plugins/mps-vcs/languages/jetbrains.mps.devkit.aspect.vcs.jar")
								library(file:"${mpsDir.getAbsolutePath()}/plugins/mps-vcs/languages/jetbrains.mps.vcs.mergehints.jar")	
							}
						}
					}
				}
			}
		}
		project.task('launchMpsServer', dependsOn: [project.resolveMps, project.resolveMpsArtifacts, project.generateAntBuildForMpsServer]) {
			dependsOn project.checkAntLib
			doLast {
				project.javaexec {
					environment('MPSSERVER_PROJECT_FILE_PATH', project.mpsserver.mpsProjectPath(project))
					main = "org.apache.tools.ant.launch.Launcher"
		            workingDir = project.rootDir
		            classpath(project.mpsserver.buildScriptClasspath(project))
		            args(project.mpsserver.antScriptArgs(project))
		            args("-buildfile", project.mpsserver.mpsServerAntBuildScript(project))
		            args(['run.com.strumenta.mpsserver.launcher'])
				}
			}
		}
		project.task('justLaunchMpsServer', dependsOn: []) {
			dependsOn project.checkAntLib
			doLast {
				project.javaexec {
					environment('MPSSERVER_PROJECT_FILE_PATH', project.mpsserver.mpsProjectPath(project))
					main = "org.apache.tools.ant.launch.Launcher"
					workingDir = project.rootDir
					classpath(project.mpsserver.buildScriptClasspath(project))
					args(project.mpsserver.antScriptArgs(project))
					args("-buildfile", project.mpsserver.mpsServerAntBuildScript(project))
					args(['run.com.strumenta.mpsserver.launcher'])
				}
			}
		}
    }
}