import jetbrains.buildServer.configs.kotlin.v2018_2.*
import jetbrains.buildServer.configs.kotlin.v2018_2.buildSteps.maven
import jetbrains.buildServer.configs.kotlin.v2018_2.buildSteps.script
import jetbrains.buildServer.configs.kotlin.v2018_2.triggers.vcs
import jetbrains.buildServer.configs.kotlin.v2018_2.vcs.GitVcsRoot

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

enum class Environment {
    CI_RS, PP_RS
}

version = "2020.1"

project {
    vcsRoot(AndreasSpringPetclinicTeamcityDsl)
    buildType(BuildAndTest)
    buildType(OtplDeploy("CI-RS", "ci-rs"))
    buildType(K8sDeploy(Environment.CI_RS))
    buildType(K8sDeploy(Environment.PP_RS))

    buildType(OtplDeploy("PP-RS", "pp-rs"))
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

class OtplDeploy(private val envName: String, private val env: String) : BuildType({
    id("PetClinic-$env-OTPL-Deploy")
    name = "otpl deploy to $envName"

    vcs {
        root(AndreasSpringPetclinicTeamcityDsl)
    }
    steps {
        script {
            name = "otpl deploy to $envName"
            scriptContent = "otpl-deploy -u $env %otpl-build-tag%"

            param("org.jfrog.artifactory.selectedDeployableServer.downloadSpecSource", "Job configuration")
            param("org.jfrog.artifactory.selectedDeployableServer.useSpecs", "false")
            param("org.jfrog.artifactory.selectedDeployableServer.uploadSpecSource", "Job configuration")
        }
    }
})

class K8sDeploy(private val env: Environment) : BuildType({
    templates(AbsoluteId(when (env) {
        Environment.CI_RS -> "K8sDeploymentCentralCiRs"
        Environment.PP_RS -> "K8sDeploymentCentralPpRs"
    }))
    id("PetClinic-$env-K8S-Deploy")
    name = "K8S-$env Deploy"

    params {
        text("image.tag",
                "%dep.Umami_RestaurantLifeCycle_GcUsersManagementService_BuildSnapshot.otpl-build-tag%",
                display = ParameterDisplay.PROMPT,
                allowEmpty = false
        )
    }

    vcs {
        root(AndreasSpringPetclinicTeamcityDsl)
    }

    steps {
        step {
            type = "K8sHelmDeploy"
        }
    }
//
//    triggers {
//        finishBuildTrigger {
//            id = "TRIGGER_2786"
//            buildType = "Umami_RestaurantLifeCycle_GcUsersManagementService_BuildSnapshot"
//            successfulOnly = true
//        }
//    }
//
//        dependencies {
//            snapshot(AbsoluteId("Umami_RestaurantLifeCycle_GcUsersManagementService_BuildSnapshot")) {
//                onDependencyFailure = FailureAction.FAIL_TO_START
//                synchronizeRevisions = false
//            }
//        }
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