package com.acme.account.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/personal-pension/account")
public class AccountController {

    @GetMapping("/list")
    public List<String> list() {
        return Collections.emptyList();
    }
}
