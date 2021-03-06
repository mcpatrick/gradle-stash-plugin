package nebula.plugin.stash

import nebula.plugin.stash.tasks.AddBuildStatusTask;
import nebula.plugin.stash.tasks.ClosePullRequestAfterBuildTask;
import nebula.plugin.stash.tasks.MergeBranchTask;
import nebula.plugin.stash.tasks.MergeBuiltPullRequestsTask;
import nebula.plugin.stash.tasks.PostPullRequestTask;
import nebula.plugin.stash.tasks.SyncNextPullRequestTask;

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;

class StashRestPlugin implements Plugin<Project> {
    void apply(Project project) {
			Logger log = project.logger
			log.lifecycle "gradle-stash tasks are enabled" 
            project.extensions.create("stash", StashPluginExtension)
            
            // these are all needed for the tasks
            // if any are not defined, groovy.lang.MissingPropertyException is thrown
            project.stash.stashRepo =  project.hasProperty('stashRepo') ? project.stashRepo : null
            project.stash.stashProject =  project.hasProperty('stashProject') ? project.stashProject : null
            project.stash.stashHost =  project.hasProperty('stashHost') ? project.stashHost : null
            project.stash.stashUser =  project.hasProperty('stashUser') ? project.stashUser : null
            project.stash.stashPassword =  project.hasProperty('stashPassword') ? project.stashPassword : null
            
			project.task("mergeBuiltPullRequests", 
                type : MergeBuiltPullRequestsTask, 
                description: "Any pending Pull Request that has been built prior will be merged or declined automatically.")
            project.mergeBuiltPullRequests.targetBranch = project.hasProperty('targetBranch') ? project.targetBranch : null
            
            project.task("syncNextPullRequest", 
                type : SyncNextPullRequestTask, 
                description: "Update a git directory to the branch where the next pull request originates from and apply any merge from master as necessary.")
            project.syncNextPullRequest.checkoutDir = project.hasProperty('checkoutDir') ? project.checkoutDir : null
            project.syncNextPullRequest.targetBranch = project.hasProperty('targetBranch') ? project.targetBranch : null
            
            project.task("closePullRequest",
                type : ClosePullRequestAfterBuildTask,
                description: "After a build this task should be run to apply comments and merge the pull request.")
            project.closePullRequest.pullRequestVersion = project.hasProperty('pullRequestVersion') ? project.pullRequestVersion.toInteger() : null
            project.closePullRequest.pullRequestId = project.hasProperty('pullRequestId') ? project.pullRequestId.toInteger() : null
            
            project.task("addBuildStatus", 
                type : AddBuildStatusTask, 
                description: "Add a build status to a commit")
            project.addBuildStatus.buildState = project.hasProperty('buildState') ? project.buildState : null
            project.addBuildStatus.buildKey = project.hasProperty('buildKey') ? project.buildKey : null
            project.addBuildStatus.buildName = project.hasProperty('buildName') ? project.buildName : null
            project.addBuildStatus.buildUrl = project.hasProperty('buildUrl') ? project.buildUrl : null
            project.addBuildStatus.buildDescription = project.hasProperty('buildDescription') ? project.buildDescription : null
            project.addBuildStatus.buildCommit = project.hasProperty('buildCommit') ? project.buildCommit : null
            
            project.task("postPullRequest",
                type : PostPullRequestTask,
                description : "post a new pull request")
            project.postPullRequest.prFromBranch = project.hasProperty('prFromBranch') ? project.prFromBranch : null
            project.postPullRequest.prToBranch = project.hasProperty('prToBranch') ? project.prToBranch : null
            project.postPullRequest.prTitle = project.hasProperty('prTitle') ? project.prTitle : null
            project.postPullRequest.prDescription = project.hasProperty('prDescription') ? project.prDescription : null
            
            project.task("mergeBranch",
                type : MergeBranchTask,
                description : "Merge any changes from one branch into another")
            project.mergeBranch.pullFromBranch = project.hasProperty('pullFromBranch') ? project.pullFromBranch : null
            project.mergeBranch.mergeToBranch = project.hasProperty('mergeToBranch') ? project.mergeToBranch : null
            project.mergeBranch.remoteName = project.hasProperty('remoteName') ? project.remoteName : null
            project.mergeBranch.repoUrl = project.hasProperty('repoUrl') ? project.repoUrl : null
            project.mergeBranch.workingPath = project.hasProperty('workingPath') ? project.workingPath : null
            project.mergeBranch.autoMergeBranch = project.hasProperty('autoMergeBranch') ? project.autoMergeBranch : null
            project.mergeBranch.mergeMessage = project.hasProperty('mergeMessage') ? project.mergeMessage : null
            project.mergeBranch.repoName = project.hasProperty('repoName') ? project.repoName : null
            project.mergeBranch.acceptFilter = project.hasProperty('acceptFilter') ? project.acceptFilter : null
    }
}

class StashPluginExtension {
    String stashRepo
    String stashProject
    String stashHost
    String stashUser
    String stashPassword
}