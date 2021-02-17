
import jetbrains.buildServer.configs.kotlin.v2018_2.*
//import jetbrains.buildServer.configs.kotlin.v2018_2.buildFeatures.Swabra
//import jetbrains.buildServer.configs.kotlin.v2018_2.buildFeatures.swabra
import jetbrains.buildServer.configs.kotlin.v2018_2.buildSteps.ScriptBuildStep
import jetbrains.buildServer.configs.kotlin.v2018_2.buildSteps.maven
import jetbrains.buildServer.configs.kotlin.v2018_2.triggers.vcs
import jetbrains.buildServer.configs.kotlin.v2018_2.vcs.GitVcsRoot
//import jetbrains.buildServer.configs.kotlin.v2018_2.buildSteps.script


/*
The settings script is an entry point for defining a TeamCity
project hierarchy. The script should contain a single call to the
project() function with a Project instance or an init function as
an argument.

VcsRoots, BuildTypes, Templates, and subprojects can be
registered inside the project using the vcsRoot(), buildType(),
template(), and subProject() methods respectively.

To debug settings scripts in command-line, run the

    mvnDebug org.jetbrains.teamcity:teamcity-configs-maven-plugin:generate

command and attach your debugger to the port 8000.

To debug in IntelliJ Idea, open the 'Maven Projects' tool window (View
-> Tool Windows -> Maven Projects), find the generate task node
(Plugins -> teamcity-configs -> teamcity-configs:generate), the
'Debug' option is available in the context menu for the task.
*/

version = "2020.1"

project {
    vcsRoot(AndreasSpringPetclinicTeamcityDsl)
    buildType(BuildAndTest)
    buildType(QADeploy)

}

object BuildAndTest : BuildType({
    name = "Build & Test"

    vcs {
        root(AndreasSpringPetclinicTeamcityDsl)
    }
    steps {
        maven {
            name = "Verify"
            goals = "clean verify"
        }

        maven {
            name = "Build Snapshot"
            goals = "clean deploy"
        }

    }
    triggers {
        vcs {
        }
    }
})

object QADeploy : BuildType({
    name = "QA Deploy"

    vcs {
        root(AndreasSpringPetclinicTeamcityDsl)
    }
    steps {
        DeployBuildStep("CI-RS", "ci-rs")
        DeployBuildStep("PP-RS", "pp-rs")

    }
    triggers {
        vcs {
        }
    }
})

class DeployBuildStep(private val envName: String, private val env: String) : ScriptBuildStep({
    name = "Deploy to $envName"
    scriptContent = "otpl-deploy -u $env %opl-build-tag%"

    param("org.jfrog.artifactory.selectedDeployableServer.downloadSpecSource", "Job configuration")
    param("org.jfrog.artifactory.selectedDeployableServer.useSpecs", "false")
    param("org.jfrog.artifactory.selectedDeployableServer.uploadSpecSource", "Job configuration")

})

object AndreasSpringPetclinicTeamcityDsl : GitVcsRoot({
    name = "andreas-spring-petclinic-teamcity-dsl"
    url = "git@github.com:ot-andreas/spring-petclinic-teamcity-dsl.git"
    authMethod = uploadedKey {
        uploadedKey = "guestcenter-tc"
    }
})

//fun wrapWithFeature(buildType: BuildType, featureBlock: BuildFeatures.() -> Unit): BuildType {
//    buildType.features {
//        featureBlock()
//    }
//    return buildType
//}