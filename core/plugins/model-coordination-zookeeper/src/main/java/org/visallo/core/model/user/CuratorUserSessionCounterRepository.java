package org.visallo.core.model.user;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.apache.commons.lang.SerializationException;
import org.apache.commons.lang.SerializationUtils;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.leader.LeaderSelector;
import org.apache.curator.framework.recipes.leader.LeaderSelectorListenerAdapter;
import org.apache.curator.utils.ZKPaths;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.data.Stat;
import org.visallo.core.config.Configuration;
import org.visallo.core.exception.VisalloException;
import org.visallo.core.util.VisalloLogger;
import org.visallo.core.util.VisalloLoggerFactory;

import java.io.Serializable;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * This implementation uses Apache Curator to track user sessions.
 */
@Singleton
public class CuratorUserSessionCounterRepository implements UserSessionCounterRepository {
    private static final VisalloLogger LOGGER = VisalloLoggerFactory.getLogger(CuratorUserSessionCounterRepository.class);

    protected static final String DEFAULT_BASE_PATH = "/visallo/userSessions";
    protected final static String IDS_SEGMENT = "/ids";
    private static final String LEADER_SEGMENT = "/leader";
    protected final static int SESSION_UPDATE_DURATION = 60000;  // 1 minute
    protected final static int UNSEEN_SESSION_DURATION = 300000; // 5 minutes

    protected final CuratorFramework curator;
    protected final String basePath;
    protected final String idsPath;

    @VisibleForTesting
    static class SessionData implements Serializable {
        final boolean autoDelete;

        SessionData(boolean autoDelete) {
            this.autoDelete = autoDelete;
        }
    }

    @Inject
    public CuratorUserSessionCounterRepository(
            final CuratorFramework curatorFramework,
            final Configuration configuration) {
        this.curator = curatorFramework;
        String confBasePath = configuration.get(Configuration.USER_SESSION_COUNTER_PATH_PREFIX, DEFAULT_BASE_PATH);
        this.basePath = confBasePath.endsWith("/") ? confBasePath.substring(0, confBasePath.length() - 1) : confBasePath;
        this.idsPath = basePath + IDS_SEGMENT;
        try {
            tryCreate(idsPath);
        } catch (Exception e) {
            throw new VisalloException("unable to create base path " + basePath, e);
        }
        startOldSessionCleanup();
    }

    @Override
    public int updateSession(String userId, String sessionId, boolean autoDelete) {
        String sessionPath = sessionPath(userId, sessionId);
        LOGGER.debug("updating user session %s", sessionPath);
        try {
            Stat sessionStat = curator.checkExists().forPath(sessionPath);
            if (sessionStat != null) {
                long now = System.currentTimeMillis();
                long lastUpdated = sessionStat.getMtime();
                if (now - lastUpdated > SESSION_UPDATE_DURATION) {
                    setSessionData(sessionPath, autoDelete);
                }
            } else {
                tryCreate(userPath(userId));
                curator.create().forPath(sessionPath);
                setSessionData(sessionPath, autoDelete);
            }

            int count = getSessionCount(userId);
            LOGGER.debug("user session count for %s is %d", userId, count);
            return count;
        } catch (Exception e) {
            throw new VisalloException("failed to update user session" + sessionPath, e);
        }
    }

    @Override
    public void deleteSessions(String userId) {
        checkNotNull(userId, "userId cannot be null");
        try {
            ZKPaths.deleteChildren(curator.getZookeeperClient().getZooKeeper(), userPath(userId), true);
        } catch (KeeperException.NoNodeException nne) {
            LOGGER.warn("there were no sessions to delete for user: %s [%s : %s]", userId, nne.getPath(), nne.getMessage());
        } catch (Exception e) {
            throw new VisalloException("failed to delete sessions for user: " + userId, e);
        }
    }

    @Override
    public int deleteSession(String userId, String sessionId) {
        checkNotNull(userId, "userId cannot be null");
        checkNotNull(sessionId, "sessionId cannot be null");
        String sessionPath = sessionPath(userId, sessionId);
        LOGGER.debug("deleting user session %s", sessionPath);
        int count = 0;
        try {
            Stat sessionStat = curator.checkExists().forPath(sessionPath);
            if (sessionStat != null) {
                curator.delete().forPath(sessionPath); // must be synchronous so count is accurate
            }
            count = getSessionCount(userId);
            LOGGER.debug("user session count for %s is %d", userId, count);
            if (count < 1) {
                LOGGER.debug("deleting user %s with no remaining sessions", userId);
                deleteInBackground(userPath(userId));
            }
            return count;
        } catch (KeeperException.NoNodeException nne) {
            LOGGER.warn("there was no session: %s and/or user: %s to delete [%s : %s]", sessionId, userId, nne.getPath(), nne.getMessage());
            return count;
        } catch (Exception e) {
            throw new VisalloException("failed to delete session: " + sessionId + " for user: " + userId, e);
        }
    }

    protected void tryCreate(String path) throws Exception {
        LOGGER.debug("creating path %s", path);
        try {
            curator.create().creatingParentsIfNeeded().forPath(path);
        } catch (KeeperException.NodeExistsException e) {
            LOGGER.debug("path %s is already created", path);
        }
    }

    protected void deleteInBackground(String path) throws Exception {
        curator.delete().deletingChildrenIfNeeded().inBackground().forPath(path);
    }

    protected String userPath(String userId) {
        return idsPath + "/" + userId;
    }

    protected String sessionPath(String userId, String sessionId) {
        return userPath(userId) + "/" + sessionId;
    }

    protected void deleteOldSessions() throws Exception {
        LOGGER.debug("deleting old user sessions");
        List<String> userIds = curator.getChildren().forPath(idsPath);
        for (String userId : userIds) {
            deleteOldSessionsForUserId(userId);
        }
    }

    protected void deleteOldSessionsForUserId(String userId) throws Exception {
        List<String> sessionIds = curator.getChildren().forPath(userPath(userId));
        for (String sessionId : sessionIds) {
            deleteSessionIfOld(userId, sessionId);
        }
    }

    private void deleteSessionIfOld(String userId, String sessionId) throws Exception {
        String sessionPath = sessionPath(userId, sessionId);
        Stat sessionStat = curator.checkExists().forPath(sessionPath);
        if (sessionStat != null) {
            long now = System.currentTimeMillis();
            long lastUpdated = sessionStat.getMtime();
            if (now - lastUpdated > UNSEEN_SESSION_DURATION) {
                byte[] data = curator.getData().forPath(sessionPath);
                boolean autoDelete = true;
                try {
                    SessionData sessionData = (SessionData) SerializationUtils.deserialize(data);
                    autoDelete = sessionData.autoDelete;
                } catch (SerializationException e) {
                    // data is somehow corrupt
                    LOGGER.debug("unable to deserialize SessionData for session %s", sessionPath);
                    LOGGER.debug("exception is:", e);
                }
                if (autoDelete) {
                    LOGGER.debug("deleting old user session %s", sessionPath);
                    deleteInBackground(sessionPath);
                }
            }
        }
    }

    private void setSessionData(String sessionPath, boolean autoDelete) throws Exception {
        SessionData sessionData = new SessionData(autoDelete);
        byte[] data = SerializationUtils.serialize(sessionData);
        curator.setData().forPath(sessionPath, data); // Stat last modification time (mtime) will change
    }

    @Override
    public int getSessionCount(String userId) {
        try {
            Stat userStat = curator.checkExists().forPath(userPath(userId));
            return userStat != null ? userStat.getNumChildren() : 0;
        } catch (Exception ex) {
            throw new VisalloException("Could not get session count for user " + userId, ex);
        }
    }

    protected void startOldSessionCleanup() {
        String leaderPath = basePath + LEADER_SEGMENT;
        try {
            tryCreate(leaderPath);
        } catch (Exception e) {
            throw new VisalloException("unable to create base path " + basePath, e);
        }
        LeaderSelector leaderSelector = new LeaderSelector(curator, leaderPath,
                new LeaderSelectorListenerAdapter() {
                    @Override
                    public void takeLeadership(CuratorFramework client) throws Exception {
                        try {
                            LOGGER.debug("starting user session cleanup");
                            while (true) {
                                try {
                                    deleteOldSessions();
                                } catch (Exception e) {
                                    LOGGER.error("failed to delete old sessions", e);
                                }
                                Thread.sleep(SESSION_UPDATE_DURATION);
                            }
                        } catch (InterruptedException e) {
                            LOGGER.debug("stopping user session cleanup");
                            throw e;
                        }
                    }
                });
        leaderSelector.autoRequeue();
        leaderSelector.start();
    }
}
