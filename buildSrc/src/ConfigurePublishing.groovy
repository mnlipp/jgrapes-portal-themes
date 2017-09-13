import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.*
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.bundling.Jar

class ConfigurePublishing implements Plugin<Project> {
	
	void apply(Project project) {

		project.extensions.create("configurePublishing", ConfigurePublishingExtension)
				
		if (project.hasProperty("signing.keyId")) {
			project.signing.sign(project.tasks.jar)
			project.signing.sign(project.tasks.sourcesJar)
			project.signing.sign(project.tasks.javadocJar)
		}

		project.publishing {
			publications {
				mavenJava(MavenPublication) {
					artifactId = project.archivesBaseName
					from(project.components.java)
					artifact(project.tasks.sourcesJar)
					artifact(project.tasks.javadocJar)
					pom.packaging = "jar"
					
					// Until https://github.com/gradle/gradle/issues/1232 is fixed:
					pom.withXml {
						// Generate map of resolved versions
						Map resolvedVersionMap = [:]
						Set<ResolvedArtifact> resolvedArtifacts = project.configurations.compile.getResolvedConfiguration().getResolvedArtifacts()
						resolvedArtifacts.each {
							ModuleVersionIdentifier mvi = it.getModuleVersion().getId();
							resolvedVersionMap.put("${mvi.getGroup()}:${mvi.getName()}", mvi.getVersion())
						}
						Set<ResolvedArtifact> testResolved = project.configurations.testCompile.getResolvedConfiguration().getResolvedArtifacts()
						testResolved.each {
							ModuleVersionIdentifier mvi = it.getModuleVersion().getId();
							resolvedVersionMap.put("${mvi.getGroup()}:${mvi.getName()}", mvi.getVersion())
						}

						// Update dependencies with resolved versions
						if (asNode().dependencies) {
							asNode().dependencies.first().each {
								def groupId = it.get("groupId").first().value().first()
								def artifactId = it.get("artifactId").first().value().first()
								it.get("version").first().value = resolvedVersionMap.get("${groupId}:${artifactId}")
							}
						}
					}

					def projectName = project.name
					def projectDescription = project.description
					if (projectDescription == null || projectDescription == "") {
						projectDescription = "(No description)"
					}
					pom.withXml {
						asNode().with {
							appendNode('name', projectName)
							appendNode('description', projectDescription)
						}
					}
					pom.withXml(project.configurePublishing.withPomXml)
					
					if (project.hasProperty("signing.keyId")) {
						// Add signature files
						[project.tasks.signJar, project.tasks.signSourcesJar,
							project.tasks.signJavadocJar].each {
							it.signatureFiles.each {
								artifact(it) {
									def matcher = it.file =~ /-(sources|javadoc)\.jar\.asc$/
									if (matcher.find()) {
										classifier = matcher.group(1)
									} else {
										classifier = null
									}
									extension = 'jar.asc'
								}
							}
						}
	
						// Sign and add the pom
						pom.withXml {
							def pomFile = project.file("${project.buildDir}/generated-pom.xml")
							writeTo(pomFile)
							def pomAscFile = project.signing.sign(pomFile).signatureFiles[0]
							artifact(pomAscFile) {
								classifier = null
								extension = 'pom.asc'
							}
							pomFile.delete()
						}
					}
				}
			}
		}
		
		if (project.hasProperty("signing.keyId")) {
			project.model {
				tasks.publishMavenJavaPublicationToMavenLocal {
					dependsOn(project.tasks.signJar)
					dependsOn(project.tasks.signSourcesJar)
					dependsOn(project.tasks.signJavadocJar)
				}
				tasks.publishMavenJavaPublicationToSnapshotRepository {
					dependsOn(project.tasks.signJar)
					dependsOn(project.tasks.signSourcesJar)
					dependsOn(project.tasks.signJavadocJar)
				}
				tasks.publishMavenJavaPublicationToReleaseRepository {
					dependsOn(project.tasks.signJar)
					dependsOn(project.tasks.signSourcesJar)
					dependsOn(project.tasks.signJavadocJar)
				}
			}
		}
	
	}

}