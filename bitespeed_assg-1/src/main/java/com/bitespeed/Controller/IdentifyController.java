package com.bitespeed.Controller;

import com.bitespeed.Dto.IdentifyRequest;
import com.bitespeed.Dto.IdentifyResponse;
import com.bitespeed.Service.IdentifyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/identify")
public class IdentifyController {

    private final IdentifyService identifyService;

    @Autowired
    public IdentifyController(IdentifyService identifyService) {
        this.identifyService = identifyService;
    }

    @PostMapping
    public ResponseEntity<IdentifyResponse> identify(@RequestBody IdentifyRequest request) {
        return ResponseEntity.ok(identifyService.identifyContact(request));
    }
}

