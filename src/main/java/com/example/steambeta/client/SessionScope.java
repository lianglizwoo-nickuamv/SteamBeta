package com.example.steambeta.client;

/** UI-thread scope: a retired session or selection can never apply a late result. */
public final class SessionScope {
    private long generation;
    private long selection;
    public long reset() { selection++; return ++generation; }
    public long generation() { return generation; }
    public boolean current(long expected) { return generation==expected; }
    public long select() { return ++selection; }
    public boolean current(long expected,long request) { return current(expected) && selection==request; }
}
