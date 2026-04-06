package com.dealer.exception

import org.springframework.http.HttpStatus

sealed class DealerException(
    message: String,
    val httpStatus: HttpStatus,
) : RuntimeException(message)

class NotFoundException(
    message: String,
) : DealerException(message, HttpStatus.NOT_FOUND)

class ConflictException(
    message: String,
) : DealerException(message, HttpStatus.CONFLICT)

class ForbiddenException(
    message: String,
) : DealerException(message, HttpStatus.FORBIDDEN)

class UnauthorizedException(
    message: String,
) : DealerException(message, HttpStatus.UNAUTHORIZED)

class ValidationException(
    message: String,
) : DealerException(message, HttpStatus.BAD_REQUEST)
