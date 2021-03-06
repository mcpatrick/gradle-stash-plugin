package nebula.plugin.stash.tasks

import org.gradle.testfixtures.ProjectBuilder
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.junit.Test
import org.junit.Before

import nebula.plugin.stash.StashRestApi;
import nebula.plugin.stash.tasks.SyncNextPullRequestTask;
import nebula.plugin.stash.util.ExternalProcess

import org.gradle.api.logging.Logger
import org.mockito.stubbing.Answer
import org.mockito.invocation.InvocationOnMock

import java.util.Map;
import java.util.concurrent.TimeUnit

import static org.junit.Assert.*
import static org.mockito.Mockito.*


public class RetryStashUntilConsistentTaskImplTest {
    StashRestApi mockStash
    Project project
    SyncNextPullRequestTask task

    @Before
    public void setup() {
        project = ProjectBuilder.builder().build()
        project.ext.stashRepo = project.ext.stashProject = project.ext.stashUser = project.ext.stashPassword = project.ext.stashHost = "foo"
        project.apply plugin: 'gradle-stash'
        mockStash = mock(StashRestApi.class)
        task = project.tasks.syncNextPullRequest
        task.stash = mockStash
        task.CONSISTENCY_POLL_RETRY_DELAY_MS = 0
        task.logger = mock(Logger.class)
    }

    @Test
    public void syncPullRequest() {
        def pr1 = [id: "10", version: "1", fromRef: [latestChangeset:"999"]]
        def pr2 = [id: "10", version: "2", fromRef: [latestChangeset:"1000"]]
        when(mockStash.getPullRequest(Integer.parseInt(pr1.id))).thenReturn(pr2)

        def x = task.retryStash(pr2.fromRef.latestChangeset, pr1)
        assertEquals(pr2.id, x.id)
        assertEquals(pr2.version, x.version)
        assertEquals(pr2.fromRef.latestChangeset, x.fromRef.latestChangeset)
        verify(mockStash, times(1)).getPullRequest(Integer.parseInt(pr1.id))
    }

    @Test
    public void retrySync() {
        def pr1 = [id: "10", version: "1", fromRef: [latestChangeset:"999", displayId: "myBranch"]]
        def pr2 = [id: "10", version: "2", fromRef: [latestChangeset:"1000", displayId: "myBranch"]]

        when(mockStash.getPullRequest(Integer.parseInt(pr1.id))).thenReturn(pr1, pr1, pr2)

        def x = task.retryStash(pr2.fromRef.latestChangeset, pr1)
        assertEquals(pr2.id, x.id)
        assertEquals(pr2.version, x.version)
        assertEquals(pr2.fromRef.latestChangeset, x.fromRef.latestChangeset)
        verify(mockStash, times(3)).getPullRequest(Integer.parseInt(pr1.id))
    }

    @Test
    public void retrySyncUntilRetryCountIsUp() {
        def pr1 = [id: "10", version: "1", fromRef: [latestChangeset:"999", displayId: "myBranch"]]
        def pr2 = [id: "10", version: "2", fromRef: [latestChangeset:"1000", displayId: "myBranch"]]
        task.CONSISTENCY_POLL_RETRY_COUNT = 5

        when(mockStash.getPullRequest(Integer.parseInt(pr1.id))).thenReturn(pr1, pr1, pr1, pr1, pr1)

        try {
            task.retryStash(pr2.fromRef.latestChangeset, pr1)
        } catch (GradleException e) {
            verify(mockStash, times(5)).getPullRequest(Integer.parseInt(pr1.id))
            return
        }
        fail()
    }
}

class MergeAndSyncPullRequestTest {
    ExternalProcess cmd
    Project project
    SyncNextPullRequestTask task

    @Before
    public void setup() {
        project = ProjectBuilder.builder().build()
        project.ext.stashRepo = project.ext.stashProject = project.ext.stashUser = project.ext.stashPassword = project.ext.stashHost = "foo"
        project.apply plugin: 'gradle-stash'
        task = project.tasks.syncNextPullRequest
        task.CONSISTENCY_POLL_RETRY_DELAY_MS = 0
        task.logger = mock(Logger.class)
        cmd = task.cmd = mock(ExternalProcess.class)
        task.checkoutDir = '/root/beer'
    }

    @Test
    public void mergeMasterIntoBranchAndPush() {
        def pr = [fromRef: [displayId: "fromBranchName", latestChangeset:"999 "], toRef: [displayId: "toBranchName"]]
        when(cmd.execute("git rev-parse HEAD", task.checkoutDir)).thenReturn(pr.fromRef.latestChangeset+"\n")
        when(cmd.execute("git merge origin/${pr.toRef.displayId} --commit", task.checkoutDir)).thenReturn("Okay no problem\n")

        def x = task.mergeAndSyncPullRequest(pr)

        verify(cmd).execute("git rev-parse HEAD", task.checkoutDir)
        verify(cmd).execute("git fetch origin", task.checkoutDir)
        verify(cmd).execute("git checkout origin/${pr.fromRef.displayId}", task.checkoutDir)
        verify(cmd).execute("git merge origin/${pr.toRef.displayId} --commit", task.checkoutDir)
        verify(cmd).execute("git push origin HEAD:${pr.fromRef.displayId}", task.checkoutDir)
    }

    @Test
    public void failIfMergeHadConflict() {
        def pr = [fromRef: [displayId: "fromBranchName", latestChangeset:"999 "], toRef: [displayId: "toBranchName"]]
        when(cmd.execute("git rev-parse HEAD", task.checkoutDir)).thenReturn(pr.fromRef.latestChangeset+"\n")
        when(cmd.execute("git merge origin/${pr.toRef.displayId} --commit", task.checkoutDir)).thenReturn("Automatic merge failed\nuehtnosahbuten soahtuneso ahtunse hoatnuse ohatns\n")

        try {
            task.mergeAndSyncPullRequest(pr)
        } catch (GradleException e) {
            verify(cmd).execute("git fetch origin", task.checkoutDir)
            verify(cmd).execute("git checkout origin/${pr.fromRef.displayId}", task.checkoutDir)
            verify(cmd).execute("git merge origin/${pr.toRef.displayId} --commit", task.checkoutDir)
            return
        }
        fail()
    }

    @Test
    public void attemptsSync() {
        StashRestApi mockStash = task.stash = mock(StashRestApi.class)
        def pr1 = [id: "10", version: "1", fromRef: [displayId: "fromBranchName", latestChangeset:"998 "], toRef: [displayId: "toBranchName"]]
        def pr2 = [id: "10", version: "2", fromRef: [displayId: "fromBranchName", latestChangeset:"999 "], toRef: [displayId: "toBranchName"]]
        when(cmd.execute("git rev-parse HEAD", task.checkoutDir)).thenReturn("999\n")
        when(cmd.execute("git merge origin/toBranchName --commit", task.checkoutDir)).thenReturn("Okay no problem\n")
        when(mockStash.getPullRequest(Integer.parseInt(pr1.id))).thenReturn(pr2)

        task.mergeAndSyncPullRequest(pr1)

        verify(cmd).execute("git rev-parse HEAD", task.checkoutDir)
        verify(cmd).execute("git fetch origin", task.checkoutDir)
        verify(cmd).execute("git checkout origin/${pr1.fromRef.displayId}", task.checkoutDir)
        verify(cmd).execute("git merge origin/${pr1.toRef.displayId} --commit", task.checkoutDir)
        verify(mockStash, times(1)).getPullRequest(Integer.parseInt(pr1.id))
        verify(cmd).execute("git push origin HEAD:${pr1.fromRef.displayId}", task.checkoutDir)
    }
}

public class IsValidPullRequestsTaskTest {
    StashRestApi mockStash
    Project project
    SyncNextPullRequestTask task


    @Before
    public void setup() {
        project = ProjectBuilder.builder().build()
        project.ext.stashRepo = project.ext.stashProject = project.ext.stashUser = project.ext.stashPassword = project.ext.stashHost = "foo"
        project.apply plugin: 'gradle-stash'
        mockStash = mock(StashRestApi.class)
        task = project.tasks.syncNextPullRequest
        task.stash = mockStash
        task.logger = mock(Logger.class)
    }

    @Test
    public void isValidPullRequestForNonInProgressRpmBuild() {
        def pr = [id: "10", version: "-2", fromRef: [latestChangeset:"999", cloneUrl: "abc.com/stash"], toRef: [cloneUrl: "abc.com/stash"]]

        Map b1 = new HashMap()
        b1.put("key", StashRestApi.RPM_BUILD_KEY)
        b1.put("state", StashRestApi.SUCCESSFUL_BUILD_STATE)
        Map b2 = new HashMap()
        b2.put("key", StashRestApi.BAKE_BUILD_KEY)
        b2.put("state", StashRestApi.INPROGRESS_BUILD_STATE)

        when(mockStash.getBuilds(pr.fromRef.latestChangeset.toString())).thenReturn([b1,b2])
        assertTrue(task.isValidPullRequest(pr))
    }

    @Test
    public void isInvalidPullRequestForNonInProgressRpmBuild() {
        def pr = [id: "10", version: "-2", fromRef: [latestChangeset:"999", cloneUrl: "abc.com/stash"], toRef: [cloneUrl: "abc.com/stash"]]

        Map b1 = new HashMap()
        b1.put("key", StashRestApi.RPM_BUILD_KEY)
        b1.put("state", StashRestApi.INPROGRESS_BUILD_STATE)

        when(mockStash.getBuilds(pr.fromRef.latestChangeset.toString())).thenReturn([b1])
        assertFalse(task.isValidPullRequest(pr))
    }

    @Test
    public void ignoresPullRequestsFromDifferentForks() {
        def pr = [id: "10", version: "-2", fromRef: [latestChangeset:"999", cloneUrl: "abc.com/stash"], toRef: [cloneUrl: "abc.com/somethingelse"]]
        assertFalse(task.isValidPullRequest(pr))
    }

}
