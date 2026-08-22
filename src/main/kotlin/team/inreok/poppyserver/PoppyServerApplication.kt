package team.inreok.poppyserver

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class PoppyServerApplication

fun main(args: Array<String>) {
    runApplication<PoppyServerApplication>(*args)
}
