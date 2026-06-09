package com.acme.user.repository;

import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {
    public String findName(Long id) {
        return "user-" + id;
    }
}

