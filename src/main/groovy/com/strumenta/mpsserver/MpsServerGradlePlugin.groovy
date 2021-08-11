package com.strumenta.mpsserver

import groovy.xml.MarkupBuilder
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project

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

	private addTaskIfDoesNotExist(Project project, String name, Class<?> clazz, MpsServerGradlePluginExtension extension) {
		if (project.tasks.findByName(name) != null) {
			println("not adding $name task, as it exists already")
		} else {
			project.tasks.register(name, clazz)
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

		addTaskIfDoesNotExist(project, 'resolveMps', ResolveMps, extension)
		addTaskIfDoesNotExist(project, 'resolveMpsArtifacts', ResolveMpsArtifacts, extension)
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
				  			pathelement(location:"${mpsDir.getAbsolutePath()}${File.separator}lib${File.separator}ant${File.separator}lib${File.separator}ant-mps.jar")
				  			pathelement(location:"${mpsDir.getAbsolutePath()}${File.separator}lib${File.separator}jdom.jar")
				  			pathelement(location:"${mpsDir.getAbsolutePath()}${File.separator}lib${File.separator}log4j.jar")
						}
						target(name: 'declare-mps-tasks') {				
						 	taskdef(resource:"jetbrains${File.separator}mps${File.separator}build${File.separator}ant${File.separator}antlib.xml", classpathref: 'path.mps.ant.path')
						}
						target(name: 'run.com.strumenta.mpsserver.launcher', depends: 'declare-mps-tasks') {
							echo(message:"Running code from com.strumenta.mpsserver.launcher solution in MPS")
							runMPS(solution:"3228605e-7b74-4057-911c-041cdcc16947(com.strumenta.mpsserver.launcher)", 
									startClass:"com.strumenta.mpsserver.launcher.MainClass", startMethod:"mpsMain") {

								plugin( path:"${mpsServerCoreDir}${File.separator}mpsserver.core.plugin", id:"mpsserver.core.plugin")
								plugin( path:"${mpsDir.getAbsolutePath()}${File.separator}plugins${File.separator}mps-build", id:"jetbrains.mps.build")
								plugin( path:"${mpsDir.getAbsolutePath()}${File.separator}plugins${File.separator}mps-core", id:"jetbrains.mps.core")
								plugin( path:"${mpsDir.getAbsolutePath()}${File.separator}plugins${File.separator}mps-testing", id:"jetbrains.mps.testing")

								plugin( path:"${mpsDir.getAbsolutePath()}${File.separator}lib${File.separator}mps-workbench.jar", id:"com.intellij.modules.mps")
								plugin( path:"${mpsDir.getAbsolutePath()}${File.separator}plugins${File.separator}git4idea", id:"Git4Idea")
								plugin( path:"${mpsDir.getAbsolutePath()}${File.separator}plugins${File.separator}mps-httpsupport", id:"jetbrains.mps.ide.httpsupport")
								plugin( path:"${mpsDir.getAbsolutePath()}${File.separator}plugins${File.separator}mps-make", id:"jetbrains.mps.ide.make")
								plugin( path:"${mpsDir.getAbsolutePath()}${File.separator}plugins${File.separator}mps-vcs", id:"jetbrains.mps.vcs")
								plugin( path:"${mpsDir.getAbsolutePath()}${File.separator}plugins${File.separator}svn4idea", id:"Subversion")

								extension.additionalPlugins.forEach {
									def f = new File(it.path)
									if (f.exists()) {
										plugin(path: it.path, id: it.id)
									} else {
										logger.warn("Provided plugin does not exist: ${it.path} (${it.id})")
									}
								}

								extension.additionalPluginsDirs.forEach { entry ->
									if (entry.dir.exists()) {
										entry.dir.eachFileRecurse(groovy.io.FileType.FILES) { file ->
											if (file.name == "plugin.xml") {
												def xmlCode = new XmlSlurper().parseText(file.getText())
												def id = xmlCode.id.text()
												if (!entry.idsToExclude.contains(id)) {
													def dir = file.getParentFile().getParentFile()
													plugin(path: dir.getAbsolutePath(), id: id)
												}
											}
										}
									} else {
										logger.warn("Provided plugin dir does not exist: ${it}")
									}
								}

								library(file:"${mpsServerCoreDir}${File.separator}mpsserver.core.plugin${File.separator}languages${File.separator}mpsserver.core.group${File.separator}com.strumenta.mpsserver.deps.jar")
								library(file:"${mpsServerCoreDir}${File.separator}mpsserver.core.plugin${File.separator}languages${File.separator}mpsserver.core.group${File.separator}com.strumenta.mpsserver.server.jar")
								library(file:"${mpsServerCoreDir}${File.separator}mpsserver.core.plugin${File.separator}languages${File.separator}mpsserver.core.group${File.separator}com.strumenta.mpsserver.extensionkit.jar")


								library(file:"${mpsDir.getAbsolutePath()}${File.separator}languages${File.separator}baseLanguage${File.separator}closures.runtime.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}languages${File.separator}baseLanguage${File.separator}collections.runtime.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}languages${File.separator}baseLanguage${File.separator}jetbrains.mps.baseLanguage.blTypes.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}languages${File.separator}baseLanguage${File.separator}jetbrains.mps.baseLanguage.classifiers.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}languages${File.separator}baseLanguage${File.separator}jetbrains.mps.baseLanguage.closures.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}languages${File.separator}baseLanguage${File.separator}jetbrains.mps.baseLanguage.collections.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}languages${File.separator}baseLanguage${File.separator}jetbrains.mps.baseLanguage.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}languages${File.separator}baseLanguage${File.separator}jetbrains.mps.baseLanguage.javadoc.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}languages${File.separator}baseLanguage${File.separator}jetbrains.mps.baseLanguage.jdk7.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}languages${File.separator}baseLanguage${File.separator}jetbrains.mps.baseLanguage.jdk8.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}languages${File.separator}baseLanguage${File.separator}jetbrains.mps.baseLanguage.logging.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}languages${File.separator}baseLanguage${File.separator}jetbrains.mps.baseLanguage.logging.runtime.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}languages${File.separator}baseLanguage${File.separator}jetbrains.mps.baseLanguage.references.runtime.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}languages${File.separator}baseLanguage${File.separator}jetbrains.mps.baseLanguage.regexp.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}languages${File.separator}baseLanguage${File.separator}jetbrains.mps.baseLanguage.regexp.runtime.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}languages${File.separator}baseLanguage${File.separator}jetbrains.mps.baseLanguage.scopes.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}languages${File.separator}baseLanguage${File.separator}jetbrains.mps.baseLanguage.tuples.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}languages${File.separator}baseLanguage${File.separator}jetbrains.mps.baseLanguage.tuples.runtime.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}languages${File.separator}baseLanguage${File.separator}jetbrains.mps.baseLanguageInternal.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}languages${File.separator}editor${File.separator}jetbrains.mps.editor.runtime.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}languages${File.separator}editor${File.separator}jetbrains.mps.editorlang.runtime.jar")

								library(file:"${mpsDir.getAbsolutePath()}${File.separator}languages${File.separator}editor${File.separator}jetbrains.mps.ide.editor.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}languages${File.separator}editor${File.separator}typesystemIntegration.jar")

								library(file:"${mpsDir.getAbsolutePath()}${File.separator}languages${File.separator}languageDesign${File.separator}jetbrains.mps.baseLanguage.lightweightdsl.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}languages${File.separator}languageDesign${File.separator}jetbrains.mps.lang.access.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}languages${File.separator}languageDesign${File.separator}jetbrains.mps.lang.actions.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}languages${File.separator}languageDesign${File.separator}jetbrains.mps.lang.behavior.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}languages${File.separator}languageDesign${File.separator}jetbrains.mps.lang.checkedName.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}languages${File.separator}languageDesign${File.separator}jetbrains.mps.lang.constraints.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}languages${File.separator}languageDesign${File.separator}jetbrains.mps.lang.constraints.rules.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}languages${File.separator}languageDesign${File.separator}jetbrains.mps.lang.constraints.rules.kinds.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}languages${File.separator}languageDesign${File.separator}jetbrains.mps.lang.constraints.rules.skeleton.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}languages${File.separator}languageDesign${File.separator}jetbrains.mps.lang.context.defs.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}languages${File.separator}languageDesign${File.separator}jetbrains.mps.lang.context.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}languages${File.separator}languageDesign${File.separator}jetbrains.mps.lang.core.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}languages${File.separator}languageDesign${File.separator}jetbrains.mps.lang.dataFlow.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}languages${File.separator}languageDesign${File.separator}jetbrains.mps.lang.editor.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}languages${File.separator}languageDesign${File.separator}jetbrains.mps.lang.findUsages.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}languages${File.separator}languageDesign${File.separator}jetbrains.mps.lang.generator.generationContext.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}languages${File.separator}languageDesign${File.separator}jetbrains.mps.lang.generator.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}languages${File.separator}languageDesign${File.separator}jetbrains.mps.lang.intentions.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}languages${File.separator}languageDesign${File.separator}jetbrains.mps.lang.migration.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}languages${File.separator}languageDesign${File.separator}jetbrains.mps.lang.modelapi.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}languages${File.separator}languageDesign${File.separator}jetbrains.mps.lang.pattern.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}languages${File.separator}languageDesign${File.separator}jetbrains.mps.lang.plugin.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}languages${File.separator}languageDesign${File.separator}jetbrains.mps.lang.project.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}languages${File.separator}languageDesign${File.separator}jetbrains.mps.lang.quotation.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}languages${File.separator}languageDesign${File.separator}jetbrains.mps.lang.refactoring.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}languages${File.separator}languageDesign${File.separator}jetbrains.mps.lang.resources.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}languages${File.separator}languageDesign${File.separator}jetbrains.mps.lang.scopes.runtime.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}languages${File.separator}languageDesign${File.separator}jetbrains.mps.lang.script.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}languages${File.separator}languageDesign${File.separator}jetbrains.mps.lang.sharedConcepts.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}languages${File.separator}languageDesign${File.separator}jetbrains.mps.lang.smodel.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}languages${File.separator}languageDesign${File.separator}jetbrains.mps.lang.smodel.query.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}languages${File.separator}languageDesign${File.separator}jetbrains.mps.lang.structure.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}languages${File.separator}languageDesign${File.separator}jetbrains.mps.lang.textGen.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}languages${File.separator}languageDesign${File.separator}jetbrains.mps.lang.traceable.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}languages${File.separator}languageDesign${File.separator}jetbrains.mps.lang.typesystem.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}languages${File.separator}languageDesign${File.separator}jetbrains.mps.refactoring.participant.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}languages${File.separator}languageDesign${File.separator}jetbrains.mps.typesystemEngine.jar")

								library(file:"${mpsDir.getAbsolutePath()}${File.separator}languages${File.separator}make${File.separator}jetbrains.mps.make.facets.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}languages${File.separator}make${File.separator}jetbrains.mps.make.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}languages${File.separator}make${File.separator}jetbrains.mps.make.runtime.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}languages${File.separator}make${File.separator}jetbrains.mps.smodel.resources.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}languages${File.separator}mps-stubs.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}languages${File.separator}plaf${File.separator}jetbrains.mps.baseLanguage.search.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}languages${File.separator}plaf${File.separator}jetbrains.mps.baseLanguage.util.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}languages${File.separator}plaf${File.separator}jetbrains.mps.ide.platform.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}languages${File.separator}plaf${File.separator}jetbrains.mps.ide.refactoring.platform.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}languages${File.separator}plaf${File.separator}jetbrains.mps.ide.ui.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}languages${File.separator}runtimes${File.separator}jetbrains.mps.analyzers.runtime.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}languages${File.separator}runtimes${File.separator}jetbrains.mps.dataFlow.runtime.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}languages${File.separator}runtimes${File.separator}jetbrains.mps.findUsages.runtime.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}languages${File.separator}runtimes${File.separator}jetbrains.mps.lang.behavior.api.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}languages${File.separator}runtimes${File.separator}jetbrains.mps.lang.behavior.runtime.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}languages${File.separator}runtimes${File.separator}jetbrains.mps.lang.feedback.context.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}languages${File.separator}runtimes${File.separator}jetbrains.mps.lang.migration.runtime.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}languages${File.separator}runtimes${File.separator}jetbrains.mps.lang.smodel.query.runtime.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}languages${File.separator}runtimes${File.separator}jetbrains.mps.refactoring.runtime.jar")

								library(file:"${mpsDir.getAbsolutePath()}${File.separator}languages${File.separator}text${File.separator}jetbrains.mps.lang.text.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}languages${File.separator}tools${File.separator}jetbrains.mps.core.tool.environment.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}languages${File.separator}tools${File.separator}jetbrains.mps.tool.common.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}languages${File.separator}util${File.separator}jetbrains.mps.java.stub.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}languages${File.separator}util${File.separator}jetbrains.mps.kernel.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}languages${File.separator}util${File.separator}jetbrains.mps.project.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}languages${File.separator}util${File.separator}jetbrains.mps.refactoring.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}languages${File.separator}util${File.separator}jetbrains.mps.runtime.jar")

								library(file:"${mpsDir.getAbsolutePath()}${File.separator}languages${File.separator}workbench-stub.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}languages${File.separator}workbench${File.separator}jetbrains.mps.ide.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}plugins${File.separator}mps-make${File.separator}languages${File.separator}jetbrains.mps.ide.make.jar")

								library(file:"${mpsDir.getAbsolutePath()}${File.separator}plugins${File.separator}mps-testing${File.separator}languages${File.separator}baseLanguage${File.separator}jetbrains.mps.baseLanguage.unitTest.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}plugins${File.separator}mps-testing${File.separator}languages${File.separator}languageDesign${File.separator}jetbrains.mps.lang.test.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}plugins${File.separator}mps-testing${File.separator}languages${File.separator}languageDesign${File.separator}jetbrains.mps.lang.test.matcher.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}plugins${File.separator}mps-testing${File.separator}languages${File.separator}languageDesign${File.separator}jetbrains.mps.lang.test.runtime.jar")
								library(file:"${mpsServerLauncherDir}${File.separator}com.strumenta.mpsserver.launcher.jar")

								library(file:"${mpsDir.getAbsolutePath()}${File.separator}plugins${File.separator}mps-httpsupport${File.separator}solutions${File.separator}jetbrains.mps.ide.httpsupport.runtime.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}plugins${File.separator}mps-httpsupport${File.separator}solutions${File.separator}jetbrains.mps.ide.httpsupport.nodeaccess.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}plugins${File.separator}mps-httpsupport${File.separator}languages${File.separator}jetbrains.mps.ide.httpsupport.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}plugins${File.separator}mps-httpsupport${File.separator}lib${File.separator}httpsupport.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}plugins${File.separator}mps-vcs${File.separator}lib${File.separator}jetbrains.mps.ide.vcs.platform.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}plugins${File.separator}mps-vcs${File.separator}lib${File.separator}jetbrains.mps.ide.vcs.core.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}plugins${File.separator}mps-vcs${File.separator}lib${File.separator}jetbrains.mps.ide.vcs.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}plugins${File.separator}mps-vcs${File.separator}lib${File.separator}vcs-ide.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}plugins${File.separator}mps-vcs${File.separator}lib${File.separator}vcs-platform.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}plugins${File.separator}mps-vcs${File.separator}lib${File.separator}jetbrains.mps.vcs.mergehints.runtime.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}plugins${File.separator}mps-vcs${File.separator}languages${File.separator}jetbrains.mps.ide.vcs.modelmetadata.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}plugins${File.separator}mps-vcs${File.separator}languages${File.separator}jetbrains.mps.vcs.mergehints-generator.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}plugins${File.separator}mps-vcs${File.separator}languages${File.separator}jetbrains.mps.devkit.aspect.vcs.jar")
								library(file:"${mpsDir.getAbsolutePath()}${File.separator}plugins${File.separator}mps-vcs${File.separator}languages${File.separator}jetbrains.mps.vcs.mergehints.jar")

								extension.additionalLibraries.forEach {
									if (it instanceof String){
										logger.info(" -> library ${it}")
										def f = new File(it)
										if (f.exists()) {
											library(file: it)
										} else {
											logger.warn("Provided library does not exist: ${f.absolutePath}")
										}
									} else if (it instanceof File) {
										if (it.exists()) {
											it.eachFileRecurse { file ->
												logger.info(" -> considering library dir ${file} in ${it}")
												if (file.getName().endsWith(".jar")) {
													library(file: "${file.getAbsolutePath()}")
												}
											}
										} else {
											logger.warn("Provided library does not exist: ${it.absolutePath}")
										}
									} else {
										logger.error("ignoring additionalLibraries ${it}, as they are not a String or a File")
									}
								}

								jvmargs() {
									extension.jvmArgs.forEach {
										arg(value: it)
									}
								}
							}
						}
					}
				}
			}
		}
		project.task('launchMpsServer', dependsOn: [project.resolveMps, project.resolveMpsArtifacts, project.generateAntBuildForMpsServer]) {
			dependsOn project.checkAntLib
			doLast {
				println("[[ MPSServer configuration ]]")
				println("  MPSServer version : ${project.mpsserver.getMpsServerVersion()}")
				println("  project file      : ${project.mpsserver.mpsProjectPath(project)}")
				println("  make project?     : ${project.mpsserver.makeProject}")
				println("  extensions path   : ${project.mpsserver.extensionsPath}")
				println("  ant script args   : ${project.mpsserver.antScriptArgs(project)}")
				println("  classpath   : ${project.mpsserver.buildScriptClasspath(project)}")
				println()
				project.javaexec {
					environment('MPSSERVER_PROJECT_FILE_PATH', project.mpsserver.mpsProjectPath(project))
					environment('MPSSERVER_MAKEPROJECT', project.mpsserver.makeProject)
					if (project.mpsserver.extensionsPath != null) {
						environment('MPSSERVER_EXTENSION_PATH', project.mpsserver.extensionsPath)
					}
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
				println("make project? ${project.mpsserver.makeProject}")
				println("extensions path ${project.mpsserver.extensionsPath}")
				project.javaexec {
					environment('MPSSERVER_PROJECT_FILE_PATH', project.mpsserver.mpsProjectPath(project))
					environment('MPSSERVER_MAKEPROJECT', project.mpsserver.makeProject)
					if (project.mpsserver.extensionsPath != null) {
						environment('MPSSERVER_EXTENSION_PATH', project.mpsserver.extensionsPath)
					}
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