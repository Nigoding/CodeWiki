package com.acme.pension.controller;

import com.acme.pension.service.KycService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/pension/kyc")
public class KycController {
    private final KycService kycService;

    public KycController(KycService kycService) {
        this.kycService = kycService;
    }

    @PostMapping("/status")
    public String queryStatus(@RequestBody Object req) {
        return kycService.queryStatus(req);
    }

    @PostMapping("/submit")
    public String submit(@RequestBody Object req) {
        return kycService.submit(req);
    }
}
