package com.decrux.pocketr.api

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class PocketrApiApplication

fun main(args: Array<String>) {
    runApplication<PocketrApiApplication>(*args)
}
