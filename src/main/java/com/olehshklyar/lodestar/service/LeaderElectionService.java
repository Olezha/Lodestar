package com.olehshklyar.lodestar.service;

/**
 * Service providing distributed leader election across horizontally scaled application instances.
 * Ensures singleton execution of polling workers (Master Poller pattern) to respect external API rate limits.
 */
public interface LeaderElectionService {

    /**
     * Checks if this instance currently holds the active leader lease.
     *
     * @return true if leader, false otherwise
     */
    boolean isLeader();

    /**
     * Attempts to acquire or renew the leader lease.
     *
     * @return true if leadership is acquired or renewed; false if another instance holds the lease
     */
    boolean tryAcquireOrRenewLease();

    /**
     * Releases the leader lease if currently held by this instance (e.g. on graceful shutdown).
     */
    void releaseLease();

    /**
     * Returns the unique identifier of this instance.
     *
     * @return instance ID string
     */
    String getInstanceId();
}
