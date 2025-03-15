package com.example.assignment.member

import org.springframework.data.jpa.repository.JpaRepository

interface MemberRepository : JpaRepository<Member, Long> {

    fun findMemberByEmail(email: String): Member?

}
