package com.example.assignment.chat

import com.example.assignment.member.Member
import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import java.time.LocalDateTime

@Entity
class Chat(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long?,
    var question: String,
    @Column(length = 5000)
    var answer: String,
    var createAt: LocalDateTime,
    @ManyToOne
    @JsonIgnore
    @JoinColumn(name = "member_id")
    var member: Member? = null
) {

    fun updateChat(
        question: String,
        answer: String,
        createAt: LocalDateTime,
    ) {
        this.question = question
        this.answer = answer
        this.createAt = createAt
    }

}
