package com.starfinplugins.bedwars.service;

public interface ManagedService {
    String getName();

    void start() throws Exception;

    void stop() throws Exception;
}
