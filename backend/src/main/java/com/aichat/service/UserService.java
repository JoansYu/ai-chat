package com.aichat.service;


public interface UserService {

    void register(String username, String password);

    Long login(String username, String password);
}
