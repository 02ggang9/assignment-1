package com.example.assignment.member

import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class MemberController(
    private val memberService: MemberService,
) {

    @PostMapping("/member")
    fun saveMember(@RequestBody @Valid requestDTO: SaveMemberRequestDTO): Long {
        return memberService.saveMember(requestDTO)
    }

    @PostMapping("/member/log-in")
    fun loginMember(@RequestBody @Valid requestDTO:LoginMemberRequestDTO): String {
        return memberService.loginMember(requestDTO)
    }



}

data class LoginMemberRequestDTO(
    val email: String,
    val password: String,
)

data class SaveMemberRequestDTO(
    val email: String,
    val password: String,
    val name: String,
    val role: String
)
