package com.dealer.api.controller

import com.dealer.domain.dto.TokenResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/test")
class TestProtectedController {
    @GetMapping("/protected")
    fun protected(): TokenResponse = TokenResponse("ok", "ok")
}
