package com.dealer.api.controller

import com.dealer.domain.dto.FcmTokenRequest
import com.dealer.domain.dto.UpdateProfileRequest
import com.dealer.domain.dto.UserDto
import com.dealer.security.SecurityUtils
import com.dealer.service.UserService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/users")
class UserController(
    private val userService: UserService,
) {
    @GetMapping("/me")
    fun getMe(): UserDto = userService.getCurrentUser(SecurityUtils.getCurrentUserId())

    @PatchMapping("/me")
    fun updateMe(
        @Valid @RequestBody request: UpdateProfileRequest,
    ): UserDto = userService.updateProfile(SecurityUtils.getCurrentUserId(), request)

    @PostMapping("/me/fcm-token")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun saveFcmToken(
        @Valid @RequestBody request: FcmTokenRequest,
    ) = userService.saveFcmToken(SecurityUtils.getCurrentUserId(), request)

    @DeleteMapping("/me/fcm-token")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun removeFcmToken(
        @RequestParam fcmToken: String,
    ) = userService.removeFcmToken(SecurityUtils.getCurrentUserId(), fcmToken)
}
