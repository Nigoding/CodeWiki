package com.acme.user.service;

import com.acme.user.repository.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class UserService {
    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public String findName(Long id) {
        return userRepository.findName(id);
    }
}

