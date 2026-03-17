package com.agentopshub.dto;

import java.util.List;

public class LoginResponse {
    private String token;
    private long expiresIn;
    private UserInfo user;

    public LoginResponse(String token, long expiresIn, UserInfo user) {
        this.token = token;
        this.expiresIn = expiresIn;
        this.user = user;
    }

    public String getToken() { return token; }
    public long getExpiresIn() { return expiresIn; }
    public UserInfo getUser() { return user; }

    public static class UserInfo {
        private Long id;
        private String username;
        private List<String> roles;

        public UserInfo(Long id, String username, List<String> roles) {
            this.id = id;
            this.username = username;
            this.roles = roles;
        }

        public Long getId() { return id; }
        public String getUsername() { return username; }
        public List<String> getRoles() { return roles; }
    }
}
