package nebula.plugin.stash.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction

import nebula.plugin.stash.StashRestApi;
import nebula.plugin.stash.StashRestApiImpl;
import nebula.plugin.stash.util.ExternalProcess
import nebula.plugin.stash.util.ExternalProcessImpl

import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional

class AddBuildStatusTask extends DefaultTask{

    StashRestApi stash
    ExternalProcess cmd = new ExternalProcessImpl()
    @Input String buildState
    @Input String buildKey
    @Input String buildName
    @Input String buildUrl
    @Input String buildDescription
    @Optional String buildCommit
    
    /**
     * Find the hash of the current commit in your current working directory
     * @return The commit hash if found, Null if not
     */
    def getCurrentCommit() {
        project.logger.info("getting the sha for the HEAD of the current directory")
        def currentSha = cmd.execute("git rev-parse HEAD", System.getProperty("user.dir"))
        project.logger.info("currentSha : ${currentSha}")
        return currentSha
    }
    
    @TaskAction
    def addBuildStatus() throws GradleException {        
        // for unit testing, don't reset if one is passed in
        stash = !stash ? new StashRestApiImpl(project.stash.stashRepo, project.stash.stashProject, project.stash.stashHost, project.stash.stashUser, project.stash.stashPassword) : stash
        stash.logger = project.logger
        
        def commit
        
        if(buildCommit) {
            commit = buildCommit
        } else {
            project.logger.info("finding commit")        
            commit = getCurrentCommit()
            if(!commit) {
                throw new GradleException("unable to determine the commit hash")
            }
        }
        project.logger.info("using commit : ${commit}")
        stash.postBuildStatus(commit, [state:buildState, key:buildKey, name:buildName, url:buildUrl, description:project.buildDescription])
    } 
}