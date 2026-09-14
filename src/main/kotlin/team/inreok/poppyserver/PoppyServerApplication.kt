package team.inreok.poppyserver

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class PoppyServerApplication

fun main(args: Array<String>) {
    runApplication<PoppyServerApplication>(*args)
}
