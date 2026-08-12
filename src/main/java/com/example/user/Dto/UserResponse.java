package com.example.user.Dto;

public class UserResponse {
    private Long id;
    private String email;
    private String displayName;
    private String role;

    public UserResponse(Long id, String email, String displayName){
        this.id = id;
        this.email = email;
        this.displayName = displayName;
    }
}
