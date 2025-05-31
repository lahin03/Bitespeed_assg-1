package com.bitespeed.Service;

import com.bitespeed.Dto.IdentifyRequest;
import com.bitespeed.Dto.IdentifyResponse;

public interface IdentifyService {
    IdentifyResponse identifyContact(IdentifyRequest request);
}