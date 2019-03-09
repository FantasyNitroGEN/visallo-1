package org.visallo.core.model.lock;

import org.apache.curator.framework.recipes.leader.LeaderLatchListener;

public class LeaderLatchListenerAdapter implements LeaderLatchListener {
    private final LeaderListener leaderListener;

    public LeaderLatchListenerAdapter(LeaderListener leaderListener) {
        this.leaderListener = leaderListener;
    }

    @Override
    public void isLeader() {
        try {
            this.leaderListener.isLeader();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void notLeader() {
        try {
            this.leaderListener.notLeader();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
