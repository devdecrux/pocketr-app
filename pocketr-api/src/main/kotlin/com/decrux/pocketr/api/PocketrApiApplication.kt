package com.decrux.pocketr.api

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class PocketrApiApplication

fun main(args: Array<String>) {
    runApplication<PocketrApiApplication>(*args)
}
