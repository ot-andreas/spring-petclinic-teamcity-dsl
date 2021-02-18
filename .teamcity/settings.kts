import jetbrains.buildServer.configs.kotlin.v10.toExtId
import jetbrains.buildServer.configs.kotlin.v2018_2.*
import jetbrains.buildServer.configs.kotlin.v2018_2.buildSteps.MavenBuildStep
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

enum class Environment(val value: String) {
    CI_RS("ci-rs"),
    PP_RS("pp_rs"),
    PROD("prod")
}

version = "2020.1"

project {
    vcsRoot(AndreasSpringPetclinicTeamcityDsl)

    sequence {
        build(BuildAndTest)
        parallel {
            sequence {
                build(OtplDeploy(Environment.CI_RS))
                build(K8sDeploy(Environment.CI_RS))
                build(K8sDeploy(Environment.PP_RS))
                build(OtplDeploy(Environment.PP_RS))
            }
        }
        // smoke test here

        //potentially parallel with canary?
        build(OtplDeploy(Environment.PROD))
    }
}

object BuildAndTest : BuildType({
    id("PetClinic_Build_and_test".toExtId())
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

            runnerArgs = "-Dotpl.docker.tag=%system.build.number% -U -e"
            mavenVersion = auto()
            userSettingsSelection = "arch-java-settings.xml"
            localRepoScope = MavenBuildStep.RepositoryScope.MAVEN_DEFAULT
            jdkHome = "%env.JDK_11_x64%"
            jvmArgs = "-Xmx256m"
            param("org.jfrog.artifactory.selectedDeployableServer.defaultModuleVersionConfiguration", "GLOBAL")
        }

    }
    triggers {
        vcs {
        }
    }
})

class OtplDeploy(private val env: Environment) : BuildType({
    id("PetClinic_${env.name}_OTPL_Deploy".toExtId())
    name = "OTPL deploy ${env.name}"

    vcs {
        root(AndreasSpringPetclinicTeamcityDsl)
    }
    steps {
        script {
            name = "OTPL deploy to ${env.name}"
            scriptContent = "otpl-deploy -u ${env.value} %otpl-build-tag%"

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
        Environment.PROD -> throw Exception("Not supported to deploy K8S for PROD")
    }))
    id("PetClinic_K8S_${env.name}_Deploy".toExtId())
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