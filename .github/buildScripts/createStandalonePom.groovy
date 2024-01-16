
/**
 * Creates a new pom-standalone.xml based on pom.xml but with new artifact (jirainstancemanager-standalone) name and
 * additional build steps for shading based on shadingConf.xml
 */
String projectBasePath = project.basedir
File origPom = new File(projectBasePath + "/pom.xml")
File shadingConf = new File(projectBasePath + "/.github/buildScripts/shadingConf.xml")

String newPomBody = origPom.text.replace("<plugins>", "<plugins>\n" + shadingConf.text)

newPomBody = newPomBody.replaceFirst("<artifactId>jirainstancemanager<\\/artifactId>", "<artifactId>jirainstancemanager-standalone<\\/artifactId>")

File standalonePom = new File(projectBasePath + "/pom-standalone.xml")
standalonePom.createNewFile()
standalonePom.text = newPomBody